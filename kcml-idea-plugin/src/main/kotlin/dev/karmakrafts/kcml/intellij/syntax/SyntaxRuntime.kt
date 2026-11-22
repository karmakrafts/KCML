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

import com.intellij.lang.ASTNode
import com.intellij.lang.PsiBuilder
import com.intellij.lang.PsiBuilderFactory
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.psi.SingleRootFileViewProvider
import com.intellij.psi.impl.source.tree.FileElement
import com.intellij.psi.tree.IFileElementType
import com.intellij.testFramework.LightVirtualFile
import dev.karmakrafts.kcml.intellij.util.TypeNames
import dev.karmakrafts.kcml.plugin.IdeSyntaxSession
import dev.karmakrafts.kcml.plugin.PluginLoaderImpl
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.idea.KotlinFileType
import org.jetbrains.kotlin.idea.KotlinLanguage
import org.jetbrains.kotlin.lexer.KotlinLexer
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtImplementationDetail
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import java.net.URL

@OptIn(KtImplementationDetail::class)
internal class SyntaxRuntime( // @formatter:off
    configuration: CompilerConfiguration,
    paths: List<URL>,
    options: List<String>
) : AutoCloseable { // @formatter:on
    private var closed: Boolean = false

    private val session: IdeSyntaxSession = PluginLoaderImpl.loadForIde(
        configuration, paths, options, ::SyntaxPluginClassLoader
    )

    private val parser: Method by lazy {
        SyntaxParserClassLoader(javaClass.classLoader).loadClass(TypeNames.KOTLIN_PARSER)
            .getMethod("parse", PsiBuilder::class.java, PsiFile::class.java)
    }

    val hasExtensions: Boolean get() = synchronized(this) { !closed && session.hasExtensions }

    @Synchronized
    fun lowerIfOpen(source: KtFile): KtFile? = if (closed) null else lower(source)

    @Synchronized
    fun lower(source: KtFile): KtFile = session.invoke {
        val text = source.viewProvider.contents
        val virtualFile = LightVirtualFile(source.name, KotlinFileType.INSTANCE, text)
        val view = SingleRootFileViewProvider(PsiManager.getInstance(source.project), virtualFile, false)
        val root = object : IFileElementType("KCML_SYNTAX_FILE", KotlinLanguage.INSTANCE) {
            override fun doParseContents(chameleon: ASTNode, psi: PsiElement): ASTNode? {
                val builder = PsiBuilderFactory.getInstance().createBuilder(
                    source.project, chameleon, session.createLexer(KotlinLexer()), KotlinLanguage.INSTANCE, text
                )
                try {
                    return (parser.invoke(null, builder, psi.containingFile) as ASTNode).firstChildNode
                } catch (error: InvocationTargetException) {
                    throw error.targetException
                }
            }
        }
        val parsed = object : KtFile(view, false) {
            override fun getVirtualFile(): LightVirtualFile = virtualFile
            override fun createFileElement(text: CharSequence): FileElement = FileElement(root, text)
        }
        parsed.originalFile = source
        // Force parsing while this module's frontend and plugin storage are installed.
        parsed.node.firstChildNode
        val lowered = session.lower(parsed)
        lowered.originalFile = source
        lowered
    }

    @Synchronized
    override fun close() {
        closed = true
        session.close()
    }
}