/*
 * Copyright 2026 Karma Krafts
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package dev.karmakrafts.kcml.intellij.syntax

import com.intellij.lang.ASTFactory
import com.intellij.lang.ASTNode
import com.intellij.lang.ParserDefinition
import com.intellij.lang.ParserDefinition.SpaceRequirements
import com.intellij.lang.PsiBuilder
import com.intellij.lang.impl.PsiBuilderImpl
import com.intellij.openapi.progress.ProgressManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.psi.SingleRootFileViewProvider
import com.intellij.psi.impl.source.tree.CompositeElement
import com.intellij.psi.impl.source.tree.FileElement
import com.intellij.psi.tree.IElementType
import com.intellij.psi.tree.IFileElementType
import com.intellij.psi.tree.TokenSet
import com.intellij.testFramework.LightVirtualFile
import dev.karmakrafts.kcml.api.ExperimentalKcmlApi
import dev.karmakrafts.kcml.api.InternalKcmlApi
import dev.karmakrafts.kcml.api.extension.syntax.Completion
import dev.karmakrafts.kcml.api.extension.syntax.Highlighting
import dev.karmakrafts.kcml.api.extension.syntax.Inspection
import dev.karmakrafts.kcml.api.extension.syntax.InspectionRule
import dev.karmakrafts.kcml.intellij.util.TypeNames
import dev.karmakrafts.kcml.plugin.IdeSyntaxSession
import dev.karmakrafts.kcml.plugin.PluginLoaderImpl
import org.jetbrains.kotlin.KtNodeTypes
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.idea.KotlinFileType
import org.jetbrains.kotlin.idea.KotlinLanguage
import org.jetbrains.kotlin.lexer.KotlinLexer
import org.jetbrains.kotlin.parsing.KotlinParserDefinition
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtImplementationDetail
import org.jetbrains.kotlin.psi.KtLambdaExpression
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import java.net.URL
import java.util.concurrent.Executor
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

@OptIn(InternalKcmlApi::class, ExperimentalKcmlApi::class)
internal class SyntaxRuntime( // @formatter:off
    configuration: CompilerConfiguration,
    paths: List<URL>,
    options: List<String>,
    preparationExecutor: Executor? = null
) : AutoCloseable { // @formatter:on
    @OptIn(KtImplementationDetail::class)
    private companion object {
        val parserDefinition: ParserDefinition = object : ASTFactory(), ParserDefinition by KotlinParserDefinition() {
            override fun createComposite(type: IElementType): CompositeElement? =
                // Eager lambda markers need PSI-backed nodes, not the IDE's usual collapsed chameleons.
                if (type == KtNodeTypes.LAMBDA_EXPRESSION) KtLambdaExpression(null) else null

            override fun getWhitespaceTokens(): TokenSet = super.getWhitespaceTokens()

            override fun spaceExistenceTypeBetweenTokens(
                left: ASTNode?, right: ASTNode?
            ): SpaceRequirements = super.spaceExistenceTypeBetweenTokens(left, right)

            @Deprecated("Deprecated in Java", replaceWith = ReplaceWith("spaceExistenceTypeBetweenTokens"))
            override fun spaceExistanceTypeBetweenTokens(
                left: ASTNode?, right: ASTNode?
            ): SpaceRequirements = super.spaceExistanceTypeBetweenTokens(left, right)

            @Suppress("UnstableApiUsage")
            override fun reparseSpace(
                originalSpaceNode: ASTNode, newWhiteSpaceSequence: CharSequence
            ): ASTNode? = super.reparseSpace(originalSpaceNode, newWhiteSpaceSequence)
        }
    }

    data class Result(
        val lowered: KtFile,
        val highlighting: List<Highlighting>,
        val inspectionRules: List<InspectionRule>,
        val inspections: List<Inspection>
    )

    @Volatile
    private var closed: Boolean = false

    private val session: IdeSyntaxSession = PluginLoaderImpl.loadForIde(
        configuration, paths, options, ::SyntaxPluginClassLoader, preparationExecutor
    )

    private val loadedExtensionIds: List<String> = session.extensionIds.toList()
    private val lock: ReentrantLock = ReentrantLock()

    private val parser: Method by lazy {
        // Load the syntax-aware parser separately from the IDE's Kotlin parser
        SyntaxParserClassLoader(javaClass.classLoader).loadClass(TypeNames.KOTLIN_PARSER)
            .getMethod("parse", PsiBuilder::class.java, PsiFile::class.java)
    }

    val hasExtensions: Boolean get() = !closed && loadedExtensionIds.isNotEmpty()
    val extensionIds: List<String> get() = if (closed) emptyList() else loadedExtensionIds

    // Background requests may outlive their runtime, skip work if disposal won the lock
    fun lowerIfOpen(source: KtFile): KtFile? = withCancellableLock {
        if (closed) null else process(source, false).lowered
    }

    fun analyzeIfOpen(source: KtFile): Result? = withCancellableLock {
        if (closed) null else process(source, true)
    }

    fun completeIfOpen(source: KtFile, offset: Int, smart: Boolean): List<Completion>? = withCancellableLock {
        if (closed) null else parse(source) { session.complete(it, offset, smart) }
    }

    fun lower(source: KtFile): KtFile = withCancellableLock { process(source, false).lowered }

    private inline fun <T> withCancellableLock(action: () -> T): T {
        ProgressManager.checkCanceled()
        // Poll instead of blocking indefinitely so superseded editor analysis can be canceled
        while (!lock.tryLock(10, TimeUnit.MILLISECONDS)) ProgressManager.checkCanceled()
        try {
            ProgressManager.checkCanceled()
            return action()
        }
        finally {
            lock.unlock()
        }
    }

    // Custom Kotlin PSI has no public construction hook; keep implementation-detail access local.
    @OptIn(KtImplementationDetail::class)
    private fun <T> parse(source: KtFile, action: (KtFile) -> T): T = session.invoke {
        // Parse a non-physical copy, leaving the editor's original Kotlin PSI untouched
        val text = source.viewProvider.contents
        val virtualFile = LightVirtualFile(source.name, KotlinFileType.INSTANCE, text)
        val view = SingleRootFileViewProvider(PsiManager.getInstance(source.project), virtualFile, false)
        val root = object : IFileElementType("KCML_SYNTAX_FILE", KotlinLanguage.INSTANCE) {
            override fun doParseContents(chameleon: ASTNode, psi: PsiElement): ASTNode? {
                // Feed the syntax-aware parser with the plugins' lexer layered over Kotlin's lexer
                // The public factory cannot combine our parser definition with project/chameleon context.
                val builder = PsiBuilderImpl(
                    source.project, parserDefinition, session.createLexer(KotlinLexer()), chameleon, text
                )
                try {
                    return (parser.invoke(null, builder, psi.containingFile) as ASTNode).firstChildNode
                } catch (error: InvocationTargetException) {
                    // Preserve the actual parser failure, including cancellation exceptions
                    throw error.targetException
                }
            }
        }
        val parsed = object : KtFile(view, false) {
            override fun getVirtualFile(): LightVirtualFile = virtualFile
            override fun createFileElement(text: CharSequence): FileElement = FileElement(root, text)
        }
        parsed.originalFile = source
        // Force parsing while this module's frontend and plugin storage are installed
        parsed.node.firstChildNode
        ProgressManager.checkCanceled()
        action(parsed)
    }

    private fun process(source: KtFile, highlight: Boolean): Result = parse(source) { parsed ->
        // Collect editor overrides before lowering, while ranges still refer to the original text
        val highlighting =
            if (highlight) SyntaxHighlighting.normalize(session.highlight(parsed), parsed.textLength) else emptyList()
        val inspectionRules = if (highlight) session.inspectionRules(parsed).filter {
            it.range.endOffset <= parsed.textLength
        }
        else emptyList()
        val inspections = if (highlight) session.inspect(parsed).filter {
            it.range.endOffset <= parsed.textLength
        }
        else emptyList()
        ProgressManager.checkCanceled()
        // Desugar the copied PSI into Kotlin that the IDE can analyze
        val lowered = session.lower(parsed)
        ProgressManager.checkCanceled()
        // Retain the source-file association for downstream IDE analysis
        lowered.originalFile = source
        Result(lowered, highlighting, inspectionRules, inspections)
    }

    override fun close() {
        lock.withLock {
            // Reject subsequent optional requests before releasing the session's resources
            closed = true
            session.close()
        }
    }
}