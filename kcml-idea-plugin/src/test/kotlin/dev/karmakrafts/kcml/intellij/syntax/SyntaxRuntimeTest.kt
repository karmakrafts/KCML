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
import com.intellij.codeInsight.daemon.impl.HighlightInfoType
import com.intellij.facet.FacetManager
import com.intellij.lexer.DelegateLexer
import com.intellij.lexer.Lexer
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.runReadActionBlocking
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.components.service
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.newvfs.impl.VfsRootAccess
import com.intellij.psi.PsiErrorElement
import com.intellij.psi.tree.IElementType
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase
import dev.karmakrafts.kcml.api.ExperimentalKcmlApi
import dev.karmakrafts.kcml.api.extension.AbstractExtension
import dev.karmakrafts.kcml.api.extension.ExtensionId
import dev.karmakrafts.kcml.api.extension.SyntaxExtension
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
import org.jetbrains.kotlin.KtNodeTypes
import org.jetbrains.kotlin.cli.common.arguments.K2JVMCompilerArguments
import org.jetbrains.kotlin.idea.facet.KotlinFacet
import org.jetbrains.kotlin.idea.facet.KotlinFacetType
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtPsiFactory
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.FileTime
import java.util.concurrent.TimeUnit
import java.util.jar.JarEntry
import java.util.jar.JarOutputStream

@Plugin("ide_fixture")
class SyntaxFixturePlugin : CompilerPlugin {
    override fun preInit(context: PreInitializationContext) {
        context.addOption("flag", "value", "Fixture option") { value, _ -> check(value == "enabled") }
    }

    override fun init(context: InitializationContext) {
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

    override fun parse(context: SyntaxExtension.ParsingContext, frontend: SyntaxFrontend): Boolean {
        val builder = context.builder
        if (context.rule != SyntaxExtension.GrammarRule.EXPRESSION) return false
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
        check(PsiTreeUtil.collectElementsOfType(file, PsiErrorElement::class.java).isEmpty()) { file.node.toString() }
        val text = file.text.replace("fn ", "fun ").replace("magic", "42").replace("unwrap ", "")
        return KtPsiFactory(file.project).createFile(file.name, text)
    }
}

class SyntaxRuntimeTest : LightJavaCodeInsightFixtureTestCase() {
    private lateinit var jar: Path
    private var facet: KotlinFacet? = null

    override fun setUp() {
        super.setUp()
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
            Files.deleteIfExists(jar)
        }
        finally {
            super.tearDown()
        }
    }

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
        }
    }

    fun testExistingExamplePlugin() {
        val exampleJar = Path.of(System.getProperty("kcml.test.examplePlugin"))
        val configuration = SyntaxConfiguration.fromOptions(
            listOf("plugin:kcml.bootstrap:pluginClasspaths=$exampleJar", "plugin:kcml.bootstrap:compileTarget=jvm"),
            module.name
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
        val service = project.service<SyntaxExtensionService>()
        val configuration = configuration()
        val source = myFixture.configureByText("Example.kt", "fn answer() = magic") as KtFile
        val first = service.runtime(module, configuration)!!
        assertSame(first, service.runtime(module, configuration))
        assertEquals("fun answer() = 42", first.lower(source).text)
        val modified = Files.getLastModifiedTime(jar).toMillis()
        writePlugin("123")
        Files.setLastModifiedTime(jar, FileTime.fromMillis(modified + 2000))
        val refreshed = service.runtime(module, configuration)!!
        assertNotSame(first, refreshed)
        assertTrue(refreshed.hasExtensions)
        assertEquals("fun answer() = 123", refreshed.lower(source).text)
        service.refresh()
        assertNotSame(refreshed, service.runtime(module, configuration))
        assertNull(service.runtime(module, null))
    }

    @Suppress("JetBrainsInternalApiUsage")
    fun testExamplePluginDiagnosticsFromImportedFacet() {
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
        val result = ApplicationManager.getApplication().executeOnPooledThread<SyntaxAnalysis.Result?> {
            runReadActionBlocking { SyntaxAnalysis.result(source) }
        }.get(30, TimeUnit.SECONDS)
        assertNotNull(result)
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
        val highlights = myFixture.doHighlighting()
        assertTrue(highlights.toString(), highlights.any { info -> info.description?.contains("missingName") == true })
        assertFalse(
            highlights.joinToString { info -> "${info.description}: visitor=${info.isFromHighlightVisitor}, inspection=${info.isFromInspection}" },
            highlights.any { info -> info.description?.contains("Expecting") == true || info.description?.contains("magic") == true })

        myFixture.configureByText("Malformed.kt", "fn answer() { val result = magic; val bad = }")
        val malformed = myFixture.doHighlighting()
        assertTrue(
            malformed.toString(),
            malformed.any { info -> info.description?.contains("Expecting an expression") == true })
    }

    fun testVfsRefreshClosesAndReloadsSyntaxExtensions() {
        val virtualFile = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(jar)!!
        val service = project.service<SyntaxExtensionService>()
        val configuration = configuration()
        val source = myFixture.configureByText("Example.kt", "fn answer() = magic") as KtFile
        val first = service.runtime(module, configuration)!!
        val generation = service.modificationCount
        writePlugin("123")
        virtualFile.refresh(false, false)
        assertTrue(service.modificationCount > generation)
        assertNull(first.lowerIfOpen(source))
        val refreshed = service.runtime(module, configuration)!!
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

    private fun configuration(): SyntaxConfiguration = SyntaxConfiguration.fromOptions(
        listOf(
            "plugin:kcml.bootstrap:pluginClasspaths=$jar",
            "plugin:kcml.bootstrap:compileTarget=jvm",
            "plugin:kcml:ide_fixtureFlag=enabled",
            "plugin:other:ignored=value"
        ), module.name
    )!!

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