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

package dev.karmakrafts.kcml.extension

import dev.karmakrafts.kcml.api.ExperimentalKcmlApi
import dev.karmakrafts.kcml.api.InternalKcmlApi
import dev.karmakrafts.kcml.api.extension.AbstractExtension
import dev.karmakrafts.kcml.api.extension.SyntaxExtension
import dev.karmakrafts.kcml.api.extension.SyntaxExtension.GrammarRule
import dev.karmakrafts.kcml.api.extension.SyntaxExtension.ParsingContext
import dev.karmakrafts.kcml.api.frontend.SyntaxFrontend
import dev.karmakrafts.kcml.frontend.SyntaxFrontendImpl
import dev.karmakrafts.kcml.log.MessageCollectorLoggerFactory
import dev.karmakrafts.kcml.plugin.PluginLoaderImpl
import org.jetbrains.kotlin.CoreEnvironmentDeprecation
import org.jetbrains.kotlin.KtNodeTypes
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.cli.extensionsStorage
import org.jetbrains.kotlin.cli.jvm.compiler.EnvironmentConfigFiles
import org.jetbrains.kotlin.cli.jvm.compiler.KotlinCoreEnvironment
import org.jetbrains.kotlin.cli.jvm.compiler.setupIdeaStandaloneExecution
import org.jetbrains.kotlin.com.intellij.lang.ASTNode
import org.jetbrains.kotlin.com.intellij.lang.PsiBuilder
import org.jetbrains.kotlin.com.intellij.lang.PsiBuilderFactory
import org.jetbrains.kotlin.com.intellij.lexer.DelegateLexer
import org.jetbrains.kotlin.com.intellij.lexer.Lexer
import org.jetbrains.kotlin.com.intellij.openapi.Disposable
import org.jetbrains.kotlin.com.intellij.openapi.application.ApplicationManager
import org.jetbrains.kotlin.com.intellij.openapi.util.Disposer
import org.jetbrains.kotlin.com.intellij.openapi.util.TextRange
import org.jetbrains.kotlin.com.intellij.psi.PsiErrorElement
import org.jetbrains.kotlin.com.intellij.psi.TokenType
import org.jetbrains.kotlin.compiler.plugin.CompilerPluginRegistrar.ExtensionStorage
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.config.CompilerConfiguration.Internals
import org.jetbrains.kotlin.lexer.KotlinLexer
import org.jetbrains.kotlin.parsing.KotlinParserDefinition
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtPsiFactory
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.test.fail

@OptIn(
    ExperimentalKcmlApi::class,
    CoreEnvironmentDeprecation::class,
    Internals::class,
    ExperimentalCompilerApi::class,
    InternalKcmlApi::class
)
class SyntaxExtensionAdapterTest {
    private abstract class TestExtension : AbstractExtension(), SyntaxExtension {
        override val id: String = "test"
    }

    private lateinit var disposable: Disposable
    private lateinit var environment: KotlinCoreEnvironment

    @BeforeTest
    fun setUp() {
        disposable = Disposer.newDisposable()
        setupIdeaStandaloneExecution()
        environment = KotlinCoreEnvironment.createForProduction(
            disposable,
            CompilerConfiguration().apply { extensionsStorage = ExtensionStorage() },
            EnvironmentConfigFiles.JVM_CONFIG_FILES
        )
    }

    @AfterTest
    fun tearDown() {
        ApplicationManager.getApplication().runWriteAction { Disposer.dispose(disposable) }
    }

    private fun builder(text: String): PsiBuilder = PsiBuilderFactory.getInstance().createBuilder(
        KotlinParserDefinition(), KotlinLexer(), text
    )

    private fun adapter(vararg extensions: SyntaxExtension): SyntaxExtensionAdapter =
        SyntaxExtensionAdapter(extensions.mapIndexed { index, extension -> "plugin-$index" to extension }, ::frontend)

    private fun frontend(pluginId: String): SyntaxFrontend = SyntaxFrontendImpl(
        pluginId,
        CompilerConfiguration(),
        MessageCollectorLoggerFactory(PluginLoaderImpl, MessageCollector.NONE),
        PluginLoaderImpl
    )

    private fun errorDescriptions(node: ASTNode): List<String> = buildList {
        if (node.elementType == TokenType.ERROR_ELEMENT) {
            add((node.psi as PsiErrorElement).errorDescription)
        }
        node.getChildren(null).forEach { addAll(errorDescriptions(it)) }
    }

    @Test
    fun `lexer wrappers compose in extension order`() {
        val baseLexer = KotlinLexer()
        val firstLexer = object : DelegateLexer(baseLexer) {}
        val secondLexer = object : DelegateLexer(firstLexer) {}
        val calls = mutableListOf<String>()
        val first = object : TestExtension() {
            override fun createLexer(delegate: Lexer, frontend: SyntaxFrontend): Lexer {
                assertEquals("plugin-0", frontend.pluginId)
                calls += "first"
                assertSame(baseLexer, delegate)
                return firstLexer
            }
        }
        val second = object : TestExtension() {
            override fun createLexer(delegate: Lexer, frontend: SyntaxFrontend): Lexer {
                assertEquals("plugin-1", frontend.pluginId)
                calls += "second"
                assertSame(firstLexer, delegate)
                return secondLexer
            }
        }

        assertSame(secondLexer, adapter(first, second).createLexer(baseLexer))
        assertEquals(listOf("first", "second"), calls)
    }

    @Test
    fun `declined parse rolls back tokens and diagnostics before the next extension`() {
        val builder = builder("first second")
        val root = builder.mark()
        val calls = mutableListOf<String>()
        val declining = object : TestExtension() {
            override fun parse(context: ParsingContext, frontend: SyntaxFrontend): Boolean {
                assertEquals("plugin-0", frontend.pluginId)
                calls += "decline"
                context.builder.error("discarded diagnostic")
                val marker = context.builder.mark()
                context.builder.advanceLexer()
                marker.error("discarded marked diagnostic")
                context.builder.advanceLexer()
                assertTrue(context.builder.eof())
                return false
            }
        }
        val accepting = object : TestExtension() {
            override fun parse(context: ParsingContext, frontend: SyntaxFrontend): Boolean {
                assertEquals("plugin-1", frontend.pluginId)
                calls += "accept"
                assertSame(builder, context.builder)
                assertEquals(0, context.builder.currentOffset)
                assertEquals("first", context.builder.tokenText)
                context.builder.advanceLexer()
                context.builder.error("retained diagnostic")
                return true
            }
        }

        assertTrue(adapter(declining, accepting).parse(builder, GrammarRule.EXPRESSION) { fail("Unexpected fallback") })
        assertEquals(listOf("decline", "accept"), calls)
        assertEquals("second", builder.tokenText)
        builder.advanceLexer()
        root.done(KtNodeTypes.KT_FILE)
        assertEquals(listOf("retained diagnostic"), errorDescriptions(builder.treeBuilt))
    }

    @Test
    fun `all declined parses leave the original token for the caller`() {
        val builder = builder("value")
        val declining = object : TestExtension() {
            override fun parse(context: ParsingContext, frontend: SyntaxFrontend): Boolean {
                context.builder.advanceLexer()
                return false
            }
        }

        assertFalse(adapter(declining, TestNoopExtension()).parse(builder, GrammarRule.STATEMENT) {
            fail("The adapter must not invoke Kotlin fallback itself")
        })
        assertEquals(0, builder.currentOffset)
        assertEquals("value", builder.tokenText)
    }

    @Test
    fun `first successful claim stops later callbacks`() {
        val builder = builder("first second")
        var claims = 0
        val accepting = object : TestExtension() {
            override fun parse(context: ParsingContext, frontend: SyntaxFrontend): Boolean {
                claims++
                context.builder.advanceLexer()
                return true
            }
        }
        val later = object : TestExtension() {
            override fun parse(context: ParsingContext, frontend: SyntaxFrontend): Boolean =
                fail("Later extension must not run")
        }

        assertTrue(adapter(accepting, later).parse(builder, GrammarRule.TOP_LEVEL_DECLARATION) {
            fail("Claimed syntax must not invoke fallback")
        })
        assertEquals(1, claims)
        assertEquals("second", builder.tokenText)
    }

    @Test
    fun `successful parse without consuming a token throws`() {
        val builder = builder("value")
        val nonConsuming = object : TestExtension() {
            override fun parse(context: ParsingContext, frontend: SyntaxFrontend): Boolean = true
        }

        val exception = assertFailsWith<IllegalStateException> {
            adapter(nonConsuming).parse(builder, GrammarRule.EXPRESSION) { fail("Unexpected fallback") }
        }
        assertTrue(exception.message.orEmpty().contains("plugin-0:test"))
        assertTrue(exception.message.orEmpty().contains("without consuming a token"))
    }

    @Test
    fun `end of input invokes no callbacks`() {
        val extension = object : TestExtension() {
            override fun parse(context: ParsingContext, frontend: SyntaxFrontend): Boolean =
                fail("Extension must not run at EOF")
        }
        for (text in listOf("", "   \n")) {
            val builder = builder(text)
            assertTrue(builder.eof())
            assertFalse(
                adapter(extension).parse(
                    builder, GrammarRule.EXPRESSION
                ) { fail("Fallback must not run at EOF") })
        }
    }

    @Test
    fun `Kotlin delegation forwards the requested rule and result`() {
        val builder = builder("value")
        val rules = mutableListOf<GrammarRule>()
        val extension = object : TestExtension() {
            override fun parse(context: ParsingContext, frontend: SyntaxFrontend): Boolean {
                assertSame(builder, context.builder)
                assertEquals(GrammarRule.MEMBER_DECLARATION, context.rule)
                assertFalse(context.parseKotlin())
                assertFalse(context.parseKotlin(GrammarRule.TYPE))
                assertTrue(context.parseKotlin(GrammarRule.EXPRESSION))
                return true
            }
        }

        assertTrue(adapter(extension).parse(builder, GrammarRule.MEMBER_DECLARATION) { rule ->
            rules += rule
            if (rule == GrammarRule.EXPRESSION) {
                builder.advanceLexer()
                true
            }
            else false
        })
        assertEquals(listOf(GrammarRule.MEMBER_DECLARATION, GrammarRule.TYPE, GrammarRule.EXPRESSION), rules)
        assertTrue(builder.eof())
    }

    @Test
    fun `custom grammar rule dispatch and default delegation are open`() {
        val rule = GrammarRule("test.custom_construct")
        val builder = builder("value")
        var delegated = false
        val extension = object : TestExtension() {
            override fun parse(context: ParsingContext, frontend: SyntaxFrontend): Boolean {
                assertEquals(GrammarRule("test.custom_construct"), context.rule)
                assertEquals("plugin-0", frontend.pluginId)
                return context.parseKotlin()
            }
        }

        assertTrue(adapter(extension).parse(builder, rule) { requestedRule ->
            assertEquals(rule, requestedRule)
            delegated = true
            builder.advanceLexer()
            true
        })
        assertTrue(delegated)
        assertTrue(builder.eof())
    }

    @Test
    fun `grammar rule IDs must not be blank`() {
        for (id in listOf("", " ", "\t\n")) {
            assertFailsWith<IllegalArgumentException> { GrammarRule(id) }
        }
    }

    @Test
    fun `callbacks share one cached frontend per owning plugin ID`() {
        val created = mutableMapOf<String, SyntaxFrontend>()
        val calls = mutableListOf<SyntaxFrontend>()
        val extension = object : TestExtension() {
            override fun createLexer(delegate: Lexer, frontend: SyntaxFrontend): Lexer {
                calls += frontend
                return delegate
            }

            override fun parse(context: ParsingContext, frontend: SyntaxFrontend): Boolean {
                calls += frontend
                return false
            }

            override fun lower(file: KtFile, frontend: SyntaxFrontend): KtFile {
                calls += frontend
                return file
            }

            override fun highlight(file: KtFile, frontend: SyntaxFrontend): List<SyntaxExtension.Highlighting> {
                calls += frontend
                return emptyList()
            }
        }
        val adapter = SyntaxExtensionAdapter(
            listOf("first" to extension, "second" to extension, "first" to extension)
        ) { pluginId ->
            assertFalse(created.containsKey(pluginId), "Frontend must be created only once per plugin")
            frontend(pluginId).also { created[pluginId] = it }
        }

        adapter.createLexer(KotlinLexer())
        assertFalse(adapter.parse(builder("value"), GrammarRule.EXPRESSION) { fail("Unexpected fallback") })
        adapter.lower(KtPsiFactory(environment.project).createFile("val value = 0"))
        adapter.highlight(KtPsiFactory(environment.project).createFile("val value = 0"))

        assertEquals(setOf("first", "second"), created.keys)
        assertEquals(List(4) { listOf("first", "second", "first") }.flatten(), calls.map { it.pluginId })
        calls.forEach { assertSame(created.getValue(it.pluginId), it) }
    }

    @Test
    fun `highlighting collects original source ranges in extension order`() {
        val file = KtPsiFactory(environment.project).createFile("val value = 0")
        val first = SyntaxExtension.Highlighting(TextRange(0, 3), "DEFAULT_KEYWORD")
        val second = SyntaxExtension.Highlighting(TextRange(12, 13), "DEFAULT_NUMBER")
        fun extension(expectedPlugin: String, highlighting: SyntaxExtension.Highlighting) = object : TestExtension() {
            override fun highlight(source: KtFile, frontend: SyntaxFrontend): List<SyntaxExtension.Highlighting> {
                assertSame(file, source)
                assertEquals(expectedPlugin, frontend.pluginId)
                return listOf(highlighting)
            }
        }
        val adapter = adapter(extension("plugin-0", first), TestNoopExtension(), extension("plugin-2", second))
        assertEquals(listOf(first, second), adapter.highlight(file))
        assertEquals("val value = 0", file.text)
    }

    @Test
    fun `highlighting rejects empty ranges and blank keys`() {
        assertFailsWith<IllegalArgumentException> { SyntaxExtension.Highlighting(TextRange(1, 1), "DEFAULT_KEYWORD") }
        for (key in listOf("", " ", "\t\n")) {
            assertFailsWith<IllegalArgumentException> { SyntaxExtension.Highlighting(TextRange(0, 1), key) }
        }
    }

    @Test
    fun `lowering passes each result to the next extension in order`() {
        val factory = KtPsiFactory(environment.project)
        val original = factory.createFile("val original = 0")
        val intermediate = factory.createFile("val intermediate = 1")
        val lowered = factory.createFile("val lowered = 2")
        val calls = mutableListOf<String>()
        val first = object : TestExtension() {
            override fun lower(file: KtFile, frontend: SyntaxFrontend): KtFile {
                assertEquals("plugin-0", frontend.pluginId)
                calls += "first"
                assertSame(original, file)
                return intermediate
            }
        }
        val second = object : TestExtension() {
            override fun lower(file: KtFile, frontend: SyntaxFrontend): KtFile {
                assertEquals("plugin-2", frontend.pluginId)
                calls += "second"
                assertSame(intermediate, file)
                return lowered
            }
        }

        assertSame(lowered, adapter(first, TestNoopExtension(), second).lower(original))
        assertEquals(listOf("first", "second"), calls)
        assertEquals("val original = 0", original.text)
    }

    @Test
    fun `empty adapter and default callbacks preserve their inputs`() {
        val lexer = KotlinLexer()
        val file = KtPsiFactory(environment.project).createFile("val value = 0")
        for (adapter in listOf(adapter(), adapter(TestNoopExtension()))) {
            assertSame(lexer, adapter.createLexer(lexer))
            assertSame(file, adapter.lower(file))
            assertTrue(adapter.highlight(file).isEmpty())
            val builder = builder("value")
            assertFalse(adapter.parse(builder, GrammarRule.LOCAL_DECLARATION) { fail("Unexpected fallback") })
            assertEquals(0, builder.currentOffset)
            assertEquals("value", builder.tokenText)
        }
    }

    private class TestNoopExtension : TestExtension()
}