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

import com.intellij.codeInsight.daemon.impl.HighlightInfo
import com.intellij.codeInsight.daemon.impl.HighlightInfoFilter
import com.intellij.codeInsight.daemon.impl.HighlightInfoType
import com.intellij.codeInsight.completion.CompletionType
import com.intellij.codeInsight.lookup.Lookup
import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.facet.FacetManager
import com.intellij.lang.LanguageAnnotators
import com.intellij.lang.annotation.Annotator
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.lexer.DelegateLexer
import com.intellij.lexer.Lexer
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.runReadActionBlocking
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.impl.view.IterationState
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.newvfs.impl.VfsRootAccess
import com.intellij.profile.ProfileChangeAdapter
import com.intellij.profile.codeInspection.InspectionProjectProfileManager
import com.intellij.psi.PsiErrorElement
import com.intellij.psi.tree.IElementType
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.util.ui.UIUtil
import dev.karmakrafts.kcml.api.ExperimentalKcmlApi
import dev.karmakrafts.kcml.api.extension.AbstractExtension
import dev.karmakrafts.kcml.api.extension.ExtensionId
import dev.karmakrafts.kcml.api.extension.syntax.Completion
import dev.karmakrafts.kcml.api.extension.syntax.GrammarRule
import dev.karmakrafts.kcml.api.extension.syntax.Highlighting
import dev.karmakrafts.kcml.api.extension.syntax.Inspection
import dev.karmakrafts.kcml.api.extension.syntax.InspectionMode
import dev.karmakrafts.kcml.api.extension.syntax.InspectionRule
import dev.karmakrafts.kcml.api.extension.syntax.InspectionSeverity
import dev.karmakrafts.kcml.api.extension.syntax.ParsingContext
import dev.karmakrafts.kcml.api.extension.syntax.SyntaxExtension
import dev.karmakrafts.kcml.api.frontend.SyntaxFrontend
import dev.karmakrafts.kcml.api.plugin.CompilerPlugin
import dev.karmakrafts.kcml.api.plugin.InitializationContext
import dev.karmakrafts.kcml.api.plugin.Plugin
import dev.karmakrafts.kcml.api.plugin.PreInitializationContext
import dev.karmakrafts.kcml.intellij.util.TypeNames
import dev.karmakrafts.kcml.internal.org.objectweb.asm.ClassReader
import dev.karmakrafts.kcml.internal.org.objectweb.asm.ClassVisitor
import dev.karmakrafts.kcml.internal.org.objectweb.asm.ClassWriter
import dev.karmakrafts.kcml.internal.org.objectweb.asm.MethodVisitor
import dev.karmakrafts.kcml.internal.org.objectweb.asm.Opcodes
import dev.karmakrafts.kcml.internal.org.objectweb.asm.commons.ClassRemapper
import dev.karmakrafts.kcml.internal.org.objectweb.asm.commons.Remapper
import dev.karmakrafts.kcml.util.kcmlNativeTarget
import org.jetbrains.kotlin.KtNodeTypes
import org.jetbrains.kotlin.cli.common.arguments.K2JVMCompilerArguments
import org.jetbrains.kotlin.config.CompilerSettings
import org.jetbrains.kotlin.idea.KotlinLanguage
import org.jetbrains.kotlin.idea.facet.KotlinFacet
import org.jetbrains.kotlin.idea.facet.KotlinFacetType
import org.jetbrains.kotlin.idea.highlighter.KotlinHighlightingColors
import org.jetbrains.kotlin.konan.config.konanTarget
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.KtVisitorVoid
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.attribute.FileTime
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.jar.JarEntry
import java.util.jar.JarFile
import java.util.jar.JarOutputStream
import kotlin.test.assertFailsWith

@Plugin("ide_fixture")
class SyntaxFixturePlugin : CompilerPlugin {
    override fun preInit(context: PreInitializationContext) {
        context.addOption("flag", "value", "Fixture option") { value, _ -> check(value == "enabled") }
    }

    override fun init(context: InitializationContext) {
        (System.getProperties()["kcml.syntax.test.loadStarted"] as? CountDownLatch)?.let { started ->
            check(!ApplicationManager.getApplication().isDispatchThread)
            check(!ApplicationManager.getApplication().isReadAccessAllowed)
            val release = System.getProperties()["kcml.syntax.test.loadRelease"] as CountDownLatch
            started.countDown()
            check(release.await(30, TimeUnit.SECONDS))
        }
        context.extensionRegistry.register(SyntaxFixtureExtension())
    }
}

@OptIn(ExperimentalKcmlApi::class)
@ExtensionId("syntax")
class SyntaxFixtureExtension : AbstractExtension(), SyntaxExtension {
    override fun createLexer(delegate: Lexer, frontend: SyntaxFrontend): Lexer {
        check(frontend.pluginId == "ide_fixture")
        return object : DelegateLexer(delegate) {
            override fun getTokenType(): IElementType? =
                if (delegate.tokenType == KtTokens.IDENTIFIER && delegate.bufferSequence.subSequence(
                        delegate.tokenStart, delegate.tokenEnd
                    ).toString() == "fn"
                ) KtTokens.FUN_KEYWORD
                else delegate.tokenType
        }
    }

    override fun parse(context: ParsingContext, frontend: SyntaxFrontend): Boolean {
        val builder = context.builder
        if (context.rule != GrammarRule.EXPRESSION) return false
        if (builder.tokenText == "magic") {
            val marker = builder.mark()
            builder.advanceLexer()
            marker.done(KtNodeTypes.INTEGER_CONSTANT)
            return true
        }
        if (builder.tokenText == "unwrap") {
            val marker = builder.mark()
            builder.advanceLexer()
            check(context.parseKotlin())
            marker.drop()
            return true
        }
        return false
    }

    override fun lower(file: KtFile, frontend: SyntaxFrontend): KtFile {
        (System.getProperties()["kcml.syntax.test.lowerStarted"] as? CountDownLatch)?.let { started ->
            val release = System.getProperties()["kcml.syntax.test.lowerRelease"] as CountDownLatch
            started.countDown()
            check(release.await(30, TimeUnit.SECONDS))
        }
        check(PsiTreeUtil.collectElementsOfType(file, PsiErrorElement::class.java).isEmpty()) { file.node.toString() }
        val text = file.text.replace("fn ", "fun ").replace("magic", "42").replace("unwrap ", "")
            .replace("inspectMe", "val first = 1;\nval second = 2;\n")
        return KtPsiFactory(file.project).createFile(file.name, text)
    }

    override fun highlight(file: KtFile, frontend: SyntaxFrontend): List<Highlighting> =
        PsiTreeUtil.collectElementsOfType(file, KtProperty::class.java).filter { it.name == "themed" }.map {
            Highlighting(it.nameIdentifier!!.textRange, "DEFAULT_KEYWORD")
        }

    override fun complete(file: KtFile, offset: Int, smart: Boolean, frontend: SyntaxFrontend): List<Completion>? {
        check(offset in 0..file.textLength)
        if (file.text.startsWith("// suppressCompletion")) return emptyList()
        if (!file.text.startsWith("// customCompletion")) return null
        return listOf(
            Completion("customOne", "magic", typeText = if (smart) "smart" else "basic", element = file),
            Completion("customTwo", "42")
        )
    }

    override fun inspectionRules(file: KtFile, frontend: SyntaxFrontend): List<InspectionRule> {
        if (file.text.startsWith("// suppressAllInspections")) return listOf(
            InspectionRule(file, InspectionMode.SUPPRESS)
        )
        val mode = when {
            file.text.startsWith("// forwardInspections") -> InspectionMode.DESUGARED
            file.text.startsWith("// suppressTokenInspections") -> InspectionMode.SUPPRESS
            else -> return emptyList()
        }
        return Regex("\\binspectMe\\b").findAll(file.text).map {
            InspectionRule(file, mode, TextRange(it.range.first, it.range.last + 1))
        }.toList()
    }

    override fun inspect(file: KtFile, frontend: SyntaxFrontend): List<Inspection> {
        if (!file.text.startsWith("// suppressAllInspections custom")) return emptyList()
        return PsiTreeUtil.collectElementsOfType(file, KtProperty::class.java).map {
            Inspection(it.nameIdentifier!!, "Custom syntax inspection", InspectionSeverity.WEAK_WARNING)
        }
    }
}

class SyntaxRuntimeTest : LightJavaCodeInsightFixtureTestCase() {
    private lateinit var jar: Path
    private lateinit var syntaxService: SyntaxExtensionService
    private var facet: KotlinFacet? = null

    override fun setUp() {
        super.setUp()
        syntaxService = SyntaxExtensionService(project)
        Disposer.register(testRootDisposable, syntaxService)
        jar = Files.createTempFile(Path.of(System.getProperty("idea.system.path")), "kcml-syntax", ".jar")
        VfsRootAccess.allowRootAccess(
            testRootDisposable,
            jar.parent.toString(),
            Path.of(System.getProperty("kcml.test.examplePlugin")).parent.toString()
        )
        writePlugin("42")
    }

    override fun tearDown() {
        try {
            val currentFacet = facet
            if (currentFacet != null) {
                runWriteAction {
                    FacetManager.getInstance(module).createModifiableModel().apply {
                        removeFacet(currentFacet)
                        commit()
                    }
                }
            }
            project.service<SyntaxExtensionService>().refresh()
            Disposer.dispose(syntaxService)
            Files.deleteIfExists(jar)
        }
        finally {
            super.tearDown()
        }
    }

    @OptIn(ExperimentalKcmlApi::class)
    fun testImportedGradleOptionsAndSyntaxCallbacks() {
        val configuration = configuration()
        assertEquals(listOf(jar), configuration.paths)
        assertEquals(listOf("plugin:kcml:ide_fixtureFlag=enabled"), configuration.options)
        assertEquals("jvm", configuration.target)
        val source = myFixture.configureByText(
            "Example.kt", "fn answer() { val result = magic; val other = unwrap 1 }"
        ) as KtFile
        SyntaxRuntime(
            configuration.compilerConfiguration(), listOf(jar.toUri().toURL()), configuration.options
        ).use { runtime ->
            assertTrue(runtime.hasExtensions)
            val lowered = runtime.lower(source)
            assertEquals("fun answer() { val result = 42; val other = 1 }", lowered.text)
            assertSame(source, lowered.originalFile)
            assertEquals("fn answer() { val result = magic; val other = unwrap 1 }", source.text)
            assertEmpty(runtime.analyzeIfOpen(source)!!.highlighting)
        }
    }

    fun testExistingExamplePlugin() {
        checkExamplePlugin("jvm")
    }

    @OptIn(ExperimentalKcmlApi::class)
    fun testExamplePluginLowersSyntaxInsideLambdas() {
        val exampleJar = Path.of(System.getProperty("kcml.test.examplePlugin"))
        val configuration = configuration().copy(paths = listOf(exampleJar), options = emptyList())
        val text = """
            class LambdaTest {
                fn test() {
                    val action = { magic }
                    consume { value ->
                        fn nested() = magic
                        consume { nested() + value + magic }
                    }
                }
            }
        """.trimIndent()
        val source = myFixture.configureByText("LambdaTest.kt", text) as KtFile
        SyntaxRuntime(
            configuration.compilerConfiguration(), listOf(exampleJar.toUri().toURL()), emptyList()
        ).use { runtime ->
            val expected = text.replace("fn ", "fun ").replace("magic", "42")
            assertEquals(expected, runtime.lower(source).text)
            val result = runtime.analyzeIfOpen(source)!!
            assertEquals(expected, result.lowered.text)
            assertEquals(5, result.highlighting.size)
            assertTrue(PsiTreeUtil.collectElementsOfType(result.lowered, PsiErrorElement::class.java).isEmpty())
            assertEquals(text, source.text)
        }
    }

    fun testCustomCompletionOverridesKotlinAndInsertsCustomSyntax() {
        loadRuntime(configuration(), project.service())
        for (type in listOf(CompletionType.BASIC, CompletionType.SMART)) {
            myFixture.configureByText("Custom.kt", "// customCompletion\nfun customKotlin() = 1\nfun usage() = cus<caret>")
            val variants = myFixture.complete(type).orEmpty()
            assertSameElements(variants.map { it.lookupString }, "customOne", "customTwo")
            assertSame(myFixture.file, variants.single { it.lookupString == "customOne" }.psiElement!!.containingFile.originalFile)
            myFixture.lookup.currentItem = variants.single { it.lookupString == "customOne" }
            myFixture.finishLookup(Lookup.NORMAL_SELECT_CHAR)
            myFixture.checkResult("// customCompletion\nfun customKotlin() = 1\nfun usage() = magic<caret>")
        }
    }

    fun testEmptyCustomCompletionSuppressesKotlinInUnchangedSource() {
        loadRuntime(configuration(), project.service())
        for (type in listOf(CompletionType.BASIC, CompletionType.SMART)) {
            myFixture.configureByText("Custom.kt", "// suppressCompletion\nfun customOne() = 1\nfun usage() = cus<caret>")
            assertEmpty(myFixture.complete(type).orEmpty().toList())
            myFixture.checkResult("// suppressCompletion\nfun customOne() = 1\nfun usage() = cus<caret>")
        }
    }

    fun testAllLoweredSubnodeInspectionsAreShownOnOneSourceToken() {
        enableSemicolonInspection()
        loadRuntime(configuration(), project.service())
        myFixture.configureByText("Inspections.kt", "// forwardInspections\nfun usage() { inspectMe }")
        val source = myFixture.file as KtFile
        val tokenStart = source.text.indexOf("inspectMe")
        val expected = TextRange(tokenStart, tokenStart + "inspectMe".length)
        val analysis = syntaxAnalysis(source)
        assertSize(2, analysis.inspections)
        assertEquals(listOf(expected, expected), analysis.inspections.map { it.range })
        val warnings = myFixture.doHighlighting().filter {
            it.problemGroup === SyntaxProblemGroup && it.description?.contains("semicolon", true) == true
        }
        assertFalse(warnings.toString(), warnings.isEmpty())
        assertTrue(warnings.toString(), warnings.all { it.startOffset == expected.startOffset && it.endOffset == expected.endOffset })
    }

    fun testInspectionRuleSuppressesLoweredSubnodesButNotOtherFindings() {
        enableSemicolonInspection()
        loadRuntime(configuration(), project.service())
        myFixture.configureByText("Inspections.kt", "// suppressTokenInspections\nfun usage() { inspectMe }\nval unrelated = 3;")
        val source = myFixture.file as KtFile
        val analysis = syntaxAnalysis(source)
        assertSize(1, analysis.inspections)
        assertEquals(source.text.lastIndexOf(';'), analysis.inspections.single().range.startOffset)
        val warnings = myFixture.doHighlighting().filter { it.description?.contains("semicolon", true) == true }
        assertSize(1, warnings)
        assertEquals(source.text.lastIndexOf(';'), warnings.single().startOffset)
    }

    fun testInspectionRuleSuppressesNormalInspectionsWithoutDesugaring() {
        enableSemicolonInspection()
        loadRuntime(configuration(), project.service())
        myFixture.configureByText("Inspections.kt", "// suppressAllInspections\nval value = 1;")
        assertEmpty(syntaxAnalysis(myFixture.file as KtFile).inspections)
        assertEmpty(myFixture.doHighlighting().filter { it.description?.contains("semicolon", true) == true })
    }

    fun testCustomInspectionsAreInvokedByIdeAndSurviveNormalInspectionSuppression() {
        enableSemicolonInspection()
        loadRuntime(configuration(), project.service())
        myFixture.configureByText("Inspections.kt", "// suppressAllInspections custom\nval value = magic;")
        val highlights = myFixture.doHighlighting()
        val custom = highlights.single { it.description == "Custom syntax inspection" }
        assertEquals(HighlightSeverity.WEAK_WARNING, custom.severity)
        assertEquals("value", myFixture.file.text.substring(custom.startOffset, custom.endOffset))
        assertSame(SyntaxProblemGroup, custom.problemGroup)
        assertEmpty(highlights.filter { it.description?.contains("semicolon", true) == true })
    }

    fun testInspectionProfileChangesInvalidateDesugaredFindings() {
        enableSemicolonInspection()
        val service = project.service<SyntaxExtensionService>()
        loadRuntime(configuration(), service)
        myFixture.configureByText("Inspections.kt", "// forwardInspections\nfun usage() { inspectMe }")
        val source = myFixture.file as KtFile
        val before = syntaxAnalysis(source)
        assertSize(2, before.inspections)
        val runtime = service.runtime(module)
        val profile = InspectionProjectProfileManager.getInstance(project).currentProfile
        profile.getTools("RedundantSemicolon", project).apply {
            isEnabled = false
            defaultState.isEnabled = false
        }
        project.messageBus.syncPublisher(ProfileChangeAdapter.TOPIC).profileChanged(profile)
        val after = syntaxAnalysis(source)
        assertTrue(after.generation > before.generation)
        assertEmpty(after.inspections)
        assertSame("Profile changes must not reload the syntax runtime", runtime, service.runtime(module))
    }

    private fun enableSemicolonInspection() {
        val inspection = Class.forName(
            "org.jetbrains.kotlin.idea.codeInsight.inspections.RedundantSemicolonInspection", true,
            KotlinLanguage.INSTANCE.javaClass.classLoader
        ).getDeclaredConstructor().newInstance() as LocalInspectionTool
        myFixture.enableInspections(inspection)
    }

    private fun syntaxAnalysis(file: KtFile): SyntaxAnalysis.Result = AppExecutorUtil.getAppExecutorService().submit<SyntaxAnalysis.Result> {
        var result: SyntaxAnalysis.Result? = null
        ProgressManager.getInstance().executeProcessUnderProgress({
            result = runReadActionBlocking { SyntaxAnalysis.result(file)!! }
        }, EmptyProgressIndicator())
        result!!
    }.get(30, TimeUnit.SECONDS)

    // The editor's rendered color is only observable through IterationState; no public API merges markup layers.
    @Suppress("JetBrainsInternalApiUsage")
    fun testExampleKeywordUsesEditorKeywordColor() {
        val exampleJar = Path.of(System.getProperty("kcml.test.examplePlugin"))
        assertTrue("Missing example plugin JAR: $exampleJar", Files.isRegularFile(exampleJar))
        val configuration = configuration().copy(paths = listOf(exampleJar), options = emptyList())
        val text = "fn answer() = magic\nfun ordinary() = 42"
        myFixture.configureByText("Example.kt", text)
        assertEquals(listOf("example:syntax_example"), loadRuntime(configuration, project.service()).extensionIds)
        val highlights = myFixture.doHighlighting(HighlightInfoType.SYMBOL_TYPE_SEVERITY)
        val editor = myFixture.editor as EditorEx
        val expected = editor.colorsScheme.getAttributes(KotlinHighlightingColors.KEYWORD).foregroundColor
        assertNotNull("The active scheme must define a keyword color", expected)
        for (offset in listOf(0, 1, text.indexOf("fun ordinary"))) {
            val state = IterationState(editor, offset, offset + 1, null, false, false, false, false)
            assertEquals(
                "Incorrect editor keyword color at $offset: $highlights", expected, state.mergedAttributes.foregroundColor
            )
        }
    }

    @OptIn(ExperimentalKcmlApi::class)
    fun testExampleHighlightingUsesParsedSourceAndPreservesEscapedIdentifiers() {
        val exampleJar = Path.of(System.getProperty("kcml.test.examplePlugin"))
        val configuration = configuration().copy(paths = listOf(exampleJar), options = emptyList())
        val text = """
            // fn magic
            class Example {
                fn answer(): Int {
                    val text = "fn magic"
                    val `fn` = 1
                    fun `magic`() = 2
                    fn local() = magic
                    return local() + magic + `fn` + `magic`()
                }
            }
        """.trimIndent()
        val source = myFixture.configureByText("Example.kt", text) as KtFile
        SyntaxRuntime(configuration.compilerConfiguration(), listOf(exampleJar.toUri().toURL()), emptyList()).use { runtime ->
            val result = runtime.analyzeIfOpen(source)!!
            val expected = listOf(
                text.indexOf("fn answer") to "KOTLIN_KEYWORD", text.indexOf("fn local") to "KOTLIN_KEYWORD",
                text.indexOf("magic", text.indexOf("fn local")) to "KOTLIN_NUMBER", text.indexOf("magic +") to "KOTLIN_NUMBER"
            ).map { (start, key) ->
                Highlighting(TextRange(start, start + if (key == "KOTLIN_KEYWORD") 2 else 5), key)
            }
            assertEquals(expected.sortedBy { it.range.startOffset }, result.highlighting.sortedBy { it.range.startOffset })
            assertEquals(
                text.replace("fn answer", "fun answer").replace("fn local", "fun local")
                    .replace("= magic", "= 42").replace("+ magic", "+ 42"), result.lowered.text
            )
            assertEmpty(PsiTreeUtil.collectElementsOfType(result.lowered, PsiErrorElement::class.java))
            assertSame(source, result.lowered.originalFile)
            assertEquals(text, source.text)

            val incomplete = myFixture.configureByText("Incomplete.kt", "fn broken() { val result = magic; val bad = }") as KtFile
            val recovered = runtime.analyzeIfOpen(incomplete)!!
            assertEquals(listOf("fn", "magic"), recovered.highlighting.map {
                incomplete.text.substring(it.range.startOffset, it.range.endOffset)
            })
            assertFalse(PsiTreeUtil.collectElementsOfType(recovered.lowered, PsiErrorElement::class.java).isEmpty())
            runtime.close()
            assertNull(runtime.analyzeIfOpen(incomplete))
        }
    }

    fun testDevelopmentExamplePluginCustomTargets() {
        for (target in listOf("common", "exampleJvm", "exampleJs", "exampleWasm")) {
            checkExamplePlugin(target)
        }
    }

    fun testDevelopmentExamplePluginNativeTarget() {
        checkExamplePlugin("exampleNative", listOf("plugin:kcml.bootstrap:konanTarget=linux_x64"))
    }

    fun testNativeBootstrapOptionsArePreserved() {
        val descriptor = jar.resolveSibling("native-target.json").toAbsolutePath().normalize()
        val configuration = SyntaxConfiguration.fromOptions(
            listOf(
                "plugin:kcml.bootstrap:pluginClasspaths=$jar",
                "plugin:kcml.bootstrap:compileTarget=exampleNative",
                "plugin:kcml.bootstrap:konanTarget=linux_x64",
                "plugin:kcml.bootstrap:nativeTarget=$descriptor"
            ), module.name
        )!!
        val compilerConfiguration = configuration.compilerConfiguration()
        assertEquals("linux_x64", compilerConfiguration.konanTarget)
        assertEquals(descriptor.toString(), compilerConfiguration.kcmlNativeTarget)
        assertEquals(listOf(jar, descriptor), configuration.watchedPaths)
    }

    fun testDevelopmentExamplePluginWithTransitiveDependencies() {
        val paths = System.getProperty("kcml.test.examplePluginClasspath").split(';').map(Path::of)
        val configuration = configuration().copy(paths = paths, options = emptyList(), target = "exampleJvm")
        val source = myFixture.configureByText("Example.kt", "fn answer() = magic") as KtFile
        SyntaxRuntime(
            configuration.compilerConfiguration(), paths.map { it.toUri().toURL() }, configuration.options
        ).use { runtime ->
            assertTrue(runtime.hasExtensions)
            assertEquals("fun answer() = 42", runtime.lower(source).text)
        }
    }

    private fun checkExamplePlugin(target: String, bootstrapOptions: List<String> = emptyList()) {
        val exampleJar = Path.of(System.getProperty("kcml.test.examplePlugin"))
        val configuration = SyntaxConfiguration.fromOptions(
            listOf(
                "plugin:kcml.bootstrap:pluginClasspaths=$exampleJar",
                "plugin:kcml.bootstrap:compileTarget=$target"
            ) + bootstrapOptions, module.name
        )!!
        val text = "fn answer() { val result = magic + 1 }"
        val source = myFixture.configureByText("Example.kt", text) as KtFile
        SyntaxRuntime(
            configuration.compilerConfiguration(), listOf(exampleJar.toUri().toURL()), emptyList()
        ).use { runtime ->
            assertTrue(runtime.hasExtensions)
            val lowered = runtime.lower(source)
            assertEquals("fun answer() { val result = 42 + 1 }", lowered.text)
            assertTrue(PsiTreeUtil.collectElementsOfType(lowered, PsiErrorElement::class.java).isEmpty())
            assertEquals(text, source.text)
        }
    }

    fun testJarReplacementAndConfigurationRemoval() {
        val service = syntaxService
        val configuration = configuration()
        val source = myFixture.configureByText("Example.kt", "fn answer() = magic") as KtFile
        val first = loadRuntime(configuration)
        assertSame(first, service.runtime(module, configuration))
        assertEquals("fun answer() = 42", first.lower(source).text)
        val modified = Files.getLastModifiedTime(jar).toMillis()
        writePlugin("123")
        Files.setLastModifiedTime(jar, FileTime.fromMillis(modified + 2000))
        // Lookups do not poll the filesystem; rebuilds are invalidated by refresh events.
        assertSame(first, service.runtime(module, configuration))
        service.refresh()
        val refreshed = loadRuntime(configuration)
        assertNotSame(first, refreshed)
        assertTrue(refreshed.hasExtensions)
        assertEquals("fun answer() = 123", refreshed.lower(source).text)
        service.refresh()
        assertNotSame(refreshed, service.runtime(module, configuration))
        assertNull(service.runtime(module, null))
    }

    fun testEdtRuntimeLookupDoesNotLoadPlugins() {
        assertTrue(ApplicationManager.getApplication().isDispatchThread)
        withBlockedPlugin("load") { started, _ ->
            val service = syntaxService
            assertNull("Runtime lookup must schedule loading instead of doing plugin I/O on the EDT",
                service.runtime(module, configuration()))
            assertTrue(started.await(10, TimeUnit.SECONDS))
            assertEmpty(service.loadedExtensions())
            val pending = service.loadRuntime(module, configuration())
            repeat(10) { assertSame(pending, service.loadRuntime(module, configuration())) }
            assertFalse(pending.isDone)
            service.refresh()
            assertNull(pending.get(10, TimeUnit.SECONDS))
        }
        assertEmpty(syntaxService.loadedExtensions())
    }

    fun testBlockedLoadDoesNotBlockReplacementOrSurviveDisposal() {
        val service = syntaxService
        withBlockedPlugin("load") { started, _ ->
            val pending = service.loadRuntime(module, configuration())
            assertTrue(started.await(10, TimeUnit.SECONDS))
            assertFalse(pending.isDone)
            val example = configuration().copy(
                paths = listOf(Path.of(System.getProperty("kcml.test.examplePlugin"))), options = emptyList()
            )
            val parallel = service.loadRuntime(module, example).get(10, TimeUnit.SECONDS)!!
            assertTrue(parallel.hasExtensions)
            assertSame(parallel, service.runtime(module))
            assertNull(pending.get(10, TimeUnit.SECONDS))
            Disposer.dispose(service)
            assertEmpty(service.loadedExtensions())
        }
        assertEmpty(service.loadedExtensions())
    }

    fun testMetadataAndRefreshDoNotWaitForActivePluginCallbacks() {
        val service = syntaxService
        val runtime = loadRuntime(configuration())
        val source = myFixture.configureByText("Busy.kt", "fn answer() = magic") as KtFile
        withBlockedPlugin("lower") { started, release ->
            val processing = AppExecutorUtil.getAppExecutorService().submit<KtFile> {
                runReadActionBlocking { runtime.lower(source) }
            }
            try {
                assertTrue(started.await(10, TimeUnit.SECONDS))
                assertTrue(runtime.hasExtensions)
                assertEquals(listOf("ide_fixture:syntax"), runtime.extensionIds)
                assertEquals(listOf("ide_fixture:syntax"), service.loadedExtensions().single().extensionIds)
                val canceled = AppExecutorUtil.getAppExecutorService().submit<KtFile?> {
                    val indicator = EmptyProgressIndicator()
                    var lowered: KtFile? = null
                    ProgressManager.getInstance().executeProcessUnderProgress({
                        indicator.cancel()
                        lowered = runtime.lowerIfOpen(source)
                    }, indicator)
                    lowered
                }
                val failure = assertFailsWith<ExecutionException> { canceled.get(5, TimeUnit.SECONDS) }
                assertTrue("Canceled analysis must not wait for a busy runtime", failure.cause is ProcessCanceledException)
                service.refresh()
                assertEmpty(service.loadedExtensions())
            }
            finally {
                release.countDown()
                processing.get(10, TimeUnit.SECONDS)
            }
        }
        PlatformTestUtil.waitWithEventsDispatching("Runtime closes after its active callback", { !runtime.hasExtensions }, 10)
    }

    fun testLoadedExtensionSnapshotsAndListenerLifecycle() {
        val service = syntaxService
        assertEmpty(service.loadedExtensions())
        val changes = AtomicInteger()
        service.addListener(testRootDisposable) { changes.incrementAndGet() }
        val disposable = Disposer.newDisposable()
        var disposedChanges = 0
        service.addListener(disposable) { disposedChanges++ }
        Disposer.dispose(disposable)

        val configuration = configuration()
        val first = loadRuntime(configuration)
        val snapshot = service.loadedExtensions().single()
        assertSame(module, snapshot.module)
        assertEquals("jvm", snapshot.target)
        assertEquals(listOf("ide_fixture:syntax"), snapshot.extensionIds)
        assertEquals(1, changes.get())
        assertSame(first, service.runtime(module, configuration))
        assertEquals(1, changes.get())

        val empty = loadRuntime(configuration.copy(paths = emptyList(), options = emptyList()))
        assertFalse(empty.hasExtensions)
        PlatformTestUtil.waitWithEventsDispatching("Replaced runtime closes", { first.extensionIds.isEmpty() }, 10)
        assertEmpty(service.loadedExtensions())
        assertEquals(listOf("ide_fixture:syntax"), snapshot.extensionIds)
        assertEquals(2, changes.get())

        loadRuntime(configuration)
        assertEquals(listOf("ide_fixture:syntax"), service.loadedExtensions().single().extensionIds)
        assertEquals(3, changes.get())
        service.loadRuntime(module, null).get(10, TimeUnit.SECONDS)
        assertEmpty(service.loadedExtensions())
        assertEquals(4, changes.get())
        loadRuntime(configuration)
        service.refresh()
        assertEmpty(service.loadedExtensions())
        assertEquals(6, changes.get())
        assertEquals(0, disposedChanges)
    }

    fun testDevelopmentClassDirectoryReloadsNestedClasses() {
        val directory = Files.createTempDirectory(jar.parent, "kcml-syntax-classes")
        try {
            unpackPlugin(directory)
            val service = syntaxService
            val configuration = configuration().copy(paths = listOf(directory))
            val source = myFixture.configureByText("Example.kt", "fn answer() = magic") as KtFile
            val first = loadRuntime(configuration)
            assertTrue(first.hasExtensions)
            assertEquals("fun answer() = 42", first.lower(source).text)
            assertSame(first, service.runtime(module, configuration))
            val modified = Files.getLastModifiedTime(directory)
            writePlugin("123")
            unpackPlugin(directory)
            Files.setLastModifiedTime(directory, modified)
            service.refresh()
            val refreshed = loadRuntime(configuration)
            assertNotSame(first, refreshed)
            PlatformTestUtil.waitWithEventsDispatching("Replaced runtime closes", { !first.hasExtensions }, 10)
            assertNull(first.lowerIfOpen(source))
            assertEquals("fun answer() = 123", refreshed.lower(source).text)
            assertSame(refreshed, service.runtime(module, configuration))
        }
        finally {
            syntaxService.refresh()
            directory.toFile().deleteRecursively()
        }
    }

    fun testVfsRefreshClosesAndReloadsDevelopmentClassDirectory() {
        val directory = Files.createTempDirectory(jar.parent, "kcml-syntax-classes")
        try {
            unpackPlugin(directory)
            val virtualDirectory = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(directory)!!
            virtualDirectory.refresh(false, true)
            val service = syntaxService
            val configuration = configuration().copy(paths = listOf(directory))
            val source = myFixture.configureByText("Example.kt", "fn answer() = magic") as KtFile
            val first = loadRuntime(configuration)
            UIUtil.dispatchAllInvocationEvents()
            val generation = service.modificationCount
            writePlugin("123")
            unpackPlugin(directory)
            virtualDirectory.refresh(false, true)
            PlatformTestUtil.waitWithEventsDispatching("Plugin file change invalidates runtime", {
                service.modificationCount > generation && !first.hasExtensions
            }, 10)
            assertTrue(service.modificationCount > generation)
            assertNull(first.lowerIfOpen(source))
            val refreshed = loadRuntime(configuration)
            assertNotSame(first, refreshed)
            assertEquals("fun answer() = 123", refreshed.lower(source).text)
        }
        finally {
            syntaxService.refresh()
            directory.toFile().deleteRecursively()
        }
    }

    fun testExamplePluginFromAdditionalCompilerArguments() {
        val exampleJar = System.getProperty("kcml.test.examplePlugin")
        val manager = FacetManager.getInstance(module)
        val kotlinFacet = manager.createFacet(KotlinFacetType.INSTANCE, "Kotlin", null)
        facet = kotlinFacet
        kotlinFacet.configuration.settings.apply {
            useProjectSettings = false
            compilerArguments = K2JVMCompilerArguments()
            compilerSettings = CompilerSettings().apply {
                additionalArguments = "-P \"plugin:kcml.bootstrap:pluginClasspaths=$exampleJar\" " +
                    "-P plugin:kcml.bootstrap:compileTarget=exampleJvm"
            }
        }
        runWriteAction {
            manager.createModifiableModel().apply {
                addFacet(kotlinFacet)
                commit()
            }
        }
        val service = project.service<SyntaxExtensionService>()
        service.start()
        PlatformTestUtil.waitWithEventsDispatching("Additional compiler arguments runtime loads", {
            service.runtime(module) != null
        }, 15)
        val runtime = service.runtime(module)
        assertNotNull("KCML options in additional compiler arguments must be discovered", runtime)
        val source = myFixture.configureByText("Example.kt", "fn answer() = magic") as KtFile
        assertEquals("fun answer() = 42", runtime!!.lower(source).text)
    }

    // Producer assertions need the daemon's internal origin queries, just like the production filter.
    @Suppress("JetBrainsInternalApiUsage")
    fun testExamplePluginDiagnosticsFromImportedFacet() {
        assertTrue("KCML diagnostic filter must be registered with the daemon", HighlightInfoFilter.EXTENSION_POINT_NAME
            .extensionList.any { it is SyntaxHighlightInfoFilter })
        val exampleJar = System.getProperty("kcml.test.examplePlugin")
        val manager = FacetManager.getInstance(module)
        val kotlinFacet = manager.createFacet(KotlinFacetType.INSTANCE, "Kotlin", null)
        facet = kotlinFacet
        kotlinFacet.configuration.settings.apply {
            useProjectSettings = false
            compilerArguments = K2JVMCompilerArguments().apply {
                pluginOptions = arrayOf(
                    "plugin:kcml.bootstrap:pluginClasspaths=$exampleJar", "plugin:kcml.bootstrap:compileTarget=jvm"
                )
            }
            updateMergedArguments()
        }
        runWriteAction {
            manager.createModifiableModel().apply {
                addFacet(kotlinFacet)
                commit()
            }
        }
        val text = "fn answer() { val result = magic; val bad = missingName }"
        val source = myFixture.configureByText("Example.kt", text) as KtFile
        loadRuntime(SyntaxConfiguration.fromModule(module)!!, project.service())
        val result = ApplicationManager.getApplication().executeOnPooledThread<SyntaxAnalysis.Result?> {
            runReadActionBlocking { SyntaxAnalysis.result(source) }
        }.get(30, TimeUnit.SECONDS)
        assertNotNull(result)
        assertSame("Highlight filters on the UI thread must reuse completed analysis", result, SyntaxAnalysis.result(source))
        val unresolved = result!!.diagnostics.filter { diagnostic -> diagnostic.message.contains("missingName") }
        assertFalse(result.diagnostics.toString(), unresolved.isEmpty())
        assertTrue(unresolved.all { diagnostic ->
            text.substring(
                diagnostic.range.startOffset, diagnostic.range.endOffset
            ) == "missingName"
        })
        assertFalse(result.diagnostics.any { diagnostic ->
            diagnostic.message.contains("magic") || diagnostic.message.contains(
                "Expecting"
            )
        })
        assertEquals(text, source.text)
        LanguageAnnotators.INSTANCE.addExplicitExtension(KotlinLanguage.INSTANCE, Annotator { element, holder ->
            if (element is KtProperty && element.name == "result") {
                holder.newAnnotation(HighlightSeverity.WARNING, "Unrelated annotation").range(element.nameIdentifier!!).create()
            }
        }, testRootDisposable)
        myFixture.enableInspections(object : LocalInspectionTool() {
            override fun getShortName(): String = "SyntaxFixtureInspection"
            override fun getDisplayName(): String = "Syntax fixture inspection"
            override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): KtVisitorVoid = object : KtVisitorVoid() {
                override fun visitProperty(property: KtProperty) {
                    if (property.name == "result") holder.registerProblem(property.nameIdentifier!!, "Unrelated inspection")
                }
            }
        })
        val highlights = myFixture.doHighlighting(HighlightSeverity.INFORMATION)
        assertTrue("Unrelated annotations must remain visible: $highlights", highlights.any { it.description == "Unrelated annotation" })
        assertTrue("Inspections must remain visible: $highlights", highlights.any {
            it.description == "Unrelated inspection" && it.isFromInspection
        })
        assertTrue("Unexpected source diagnostics: $highlights", highlights.filter { it.severity == HighlightSeverity.ERROR }.all {
            text.substring(it.startOffset, it.endOffset) == "missingName"
        })
        for ((token, key) in listOf("fn" to KotlinHighlightingColors.KEYWORD, "magic" to KotlinHighlightingColors.NUMBER)) {
            val start = text.indexOf(token)
            assertTrue("Missing $token highlighting: $highlights", highlights.any { info ->
                info.startOffset == start && info.endOffset == start + token.length && info.forcedTextAttributesKey == key
            })
        }
        assertTrue(highlights.toString(), highlights.any { info -> info.description?.contains("missingName") == true })
        assertFalse(
            highlights.joinToString { info -> "${info.description}: visitor=${info.isFromHighlightVisitor}, inspection=${info.isFromInspection}" },
            highlights.any { info -> info.description?.contains("Expecting") == true || info.description?.contains("magic") == true })

        myFixture.configureByText("Malformed.kt", "fn answer() { val result = magic; val bad = }")
        val malformed = myFixture.doHighlighting()
        assertTrue(
            malformed.toString(),
            malformed.any { info -> info.description?.contains("Expecting an expression") == true })

        myFixture.configureByText("Valid.kt", "fn answer() { val result = magic }")
        val valid = myFixture.doHighlighting(HighlightSeverity.INFORMATION)
        assertTrue("Valid extension syntax must not produce Kotlin errors: $valid", valid.none { it.severity == HighlightSeverity.ERROR })
        assertNull(SyntaxAnalysis.cachedResult(source))
    }

    fun testVfsRefreshClosesAndReloadsSyntaxExtensions() {
        val virtualFile = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(jar)!!
        val service = syntaxService
        val configuration = configuration()
        val source = myFixture.configureByText("Example.kt", "fn answer() = magic") as KtFile
        val first = loadRuntime(configuration)
        val generation = service.modificationCount
        writePlugin("123")
        virtualFile.refresh(false, false)
        PlatformTestUtil.waitWithEventsDispatching("Plugin JAR change invalidates runtime", {
            service.modificationCount > generation && !first.hasExtensions
        }, 10)
        assertTrue(service.modificationCount > generation)
        assertNull(first.lowerIfOpen(source))
        val refreshed = loadRuntime(configuration)
        assertNotSame(first, refreshed)
        assertEquals("fun answer() = 123", refreshed.lower(source).text)
    }

    fun testNoKcmlConfigurationLeavesKotlinAlone() {
        assertNull(SyntaxConfiguration.fromOptions(listOf("plugin:other:pluginClasspaths=unused"), "example"))
        assertNull(SyntaxConfiguration.fromOptions(listOf("plugin:kcml.bootstrap:pluginClasspaths="), "example"))
        val file = myFixture.configureByText("Example.kt", "fun value() = missingName") as KtFile
        assertNull(SyntaxAnalysis.result(file))
        val error = HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR)
            .range(14, 25)
            .descriptionAndTooltip("Unresolved reference: missingName")
            .create()!!
        assertTrue(SyntaxHighlightInfoFilter().accept(error, file))
    }

    // Origin flags are the only way to tell host semantic highlighting from the KCML override.
    @Suppress("JetBrainsInternalApiUsage")
    fun testHighlightingOverridesOnlyDeclaredRangesAndKeepsOtherSemanticColors() {
        val manager = FacetManager.getInstance(module)
        val kotlinFacet = manager.createFacet(KotlinFacetType.INSTANCE, "Kotlin", null)
        facet = kotlinFacet
        kotlinFacet.configuration.settings.apply {
            useProjectSettings = false
            compilerArguments = K2JVMCompilerArguments().apply {
                pluginOptions = arrayOf(
                    "plugin:kcml.bootstrap:pluginClasspaths=$jar",
                    "plugin:kcml.bootstrap:compileTarget=jvm",
                    "plugin:kcml:ide_fixtureFlag=enabled"
                )
            }
            updateMergedArguments()
        }
        runWriteAction {
            manager.createModifiableModel().apply {
                addFacet(kotlinFacet)
                commit()
            }
        }
        val text = "fun answer(): Int { val themed = 1; return themed }"
        myFixture.configureByText("Themed.kt", text)
        loadRuntime(SyntaxConfiguration.fromModule(module)!!, project.service())
        val highlights = myFixture.doHighlighting(HighlightInfoType.SYMBOL_TYPE_SEVERITY)
        val declaration = text.indexOf("themed")
        val reference = text.lastIndexOf("themed")
        assertTrue("Missing custom declaration color: $highlights", highlights.any {
            it.startOffset == declaration && it.endOffset == declaration + 6 &&
                it.forcedTextAttributesKey?.externalName == "DEFAULT_KEYWORD"
        })
        assertFalse("Host coloring must not compete with the override: $highlights", highlights.any {
            it.startOffset == declaration && it.isFromHighlightVisitor
        })
        assertTrue("Unrelated semantic coloring must be retained: $highlights", highlights.any {
            it.startOffset == reference && it.isFromHighlightVisitor && it.severity < HighlightSeverity.WARNING
        })
    }

    private fun loadRuntime(configuration: SyntaxConfiguration, service: SyntaxExtensionService = syntaxService): SyntaxRuntime =
        service.loadRuntime(module, configuration).get(15, TimeUnit.SECONDS)!!

    private fun withBlockedPlugin(phase: String, action: (CountDownLatch, CountDownLatch) -> Unit) {
        val started = CountDownLatch(1)
        val release = CountDownLatch(1)
        System.getProperties()["kcml.syntax.test.${phase}Started"] = started
        System.getProperties()["kcml.syntax.test.${phase}Release"] = release
        try {
            action(started, release)
        }
        finally {
            release.countDown()
            System.getProperties().remove("kcml.syntax.test.${phase}Started")
            System.getProperties().remove("kcml.syntax.test.${phase}Release")
        }
    }

    private fun configuration(): SyntaxConfiguration = SyntaxConfiguration.fromOptions(
        listOf(
            "plugin:kcml.bootstrap:pluginClasspaths=$jar",
            "plugin:kcml.bootstrap:compileTarget=jvm",
            "plugin:kcml:ide_fixtureFlag=enabled",
            "plugin:other:ignored=value"
        ), module.name
    )!!

    private fun unpackPlugin(directory: Path) {
        JarFile(jar.toFile()).use { archive ->
            for (entry in archive.entries()) {
                val file = directory.resolve(entry.name)
                Files.createDirectories(file.parent)
                archive.getInputStream(entry).use { input ->
                    Files.copy(input, file, StandardCopyOption.REPLACE_EXISTING)
                }
            }
        }
    }

    private fun writePlugin(replacement: String) {
        val fixture = SyntaxFixturePlugin::class.java.name.removeSuffix("Plugin").replace('.', '/')
        val relocatedFixture = fixture.replace("/intellij/", "/fixture/")
        val intellijPrefix = TypeNames.INTELLIJ_PACKAGE.replace('.', '/')
        val kotlinPrefix = TypeNames.KOTLIN_PACKAGE.replace('.', '/')
        val remapper = object : Remapper(Opcodes.ASM9) {
            override fun map(internalName: String): String = when {
                internalName.startsWith(fixture) -> internalName.replace(fixture, relocatedFixture)
                internalName.startsWith(intellijPrefix) -> "$kotlinPrefix$internalName"
                else -> internalName
            }
        }
        JarOutputStream(Files.newOutputStream(jar)).use { output ->
            for (suffix in listOf("Plugin", "Extension", $$"Extension$createLexer$1")) {
                val name = "$fixture$suffix"
                val writer = ClassWriter(0)
                javaClass.classLoader.getResourceAsStream("$name.class")!!.use { input ->
                    ClassReader(input).accept(ClassRemapper(object : ClassVisitor(Opcodes.ASM9, writer) {
                        override fun visitMethod(
                            access: Int,
                            name: String,
                            descriptor: String,
                            signature: String?,
                            exceptions: Array<out String>?
                        ): MethodVisitor = object : MethodVisitor(
                            Opcodes.ASM9, super.visitMethod(access, name, descriptor, signature, exceptions)
                        ) {
                            override fun visitLdcInsn(value: Any) =
                                super.visitLdcInsn(if (value == "42") replacement else value)
                        }
                    }, remapper), 0)
                }
                output.putNextEntry(JarEntry("${remapper.map(name)}.class"))
                output.write(writer.toByteArray())
                output.closeEntry()
            }
        }
    }
}