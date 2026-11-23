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
import org.jetbrains.kotlin.com.intellij.psi.PsiElement
import org.jetbrains.kotlin.com.intellij.psi.PsiErrorElement
import org.jetbrains.kotlin.com.intellij.psi.PsiNamedElement
import org.jetbrains.kotlin.com.intellij.psi.TokenType
import org.jetbrains.kotlin.com.intellij.psi.impl.source.tree.LeafElement
import org.jetbrains.kotlin.compiler.plugin.CompilerPluginRegistrar.ExtensionStorage
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.config.CompilerConfiguration.Internals
import org.jetbrains.kotlin.lexer.KotlinLexer
import org.jetbrains.kotlin.parsing.KotlinParserDefinition
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtPsiFactory
import java.lang.reflect.Modifier
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
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
    fun `syntax API models are top-level types in the syntax package`() {
        val types = listOf(
            SyntaxExtension::class.java,
            GrammarRule::class.java,
            ParsingContext::class.java,
            Highlighting::class.java,
            Completion::class.java,
            InspectionRule::class.java,
            InspectionMode::class.java,
            Inspection::class.java,
            InspectionSeverity::class.java
        )
        for (type in types) {
            assertEquals("dev.karmakrafts.kcml.api.extension.syntax.${type.simpleName}", type.name)
            assertNull(type.enclosingClass)
        }
        assertEquals(
            Boolean::class.javaPrimitiveType,
            ParsingContext::class.java.getMethod("parseKotlin", GrammarRule::class.java).returnType
        )
        val factory = Completion::class.java.getMethod(
            "forElement", PsiNamedElement::class.java, String::class.java, String::class.java, String::class.java
        )
        assertTrue(Modifier.isStatic(factory.modifiers))
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

            override fun highlight(file: KtFile, frontend: SyntaxFrontend): List<Highlighting> {
                calls += frontend
                return emptyList()
            }

            override fun complete(file: KtFile, offset: Int, smart: Boolean, frontend: SyntaxFrontend): List<Completion>? {
                calls += frontend
                return null
            }

            override fun inspectionRules(file: KtFile, frontend: SyntaxFrontend): List<InspectionRule> {
                calls += frontend
                return emptyList()
            }

            override fun inspect(file: KtFile, frontend: SyntaxFrontend): List<Inspection> {
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
        assertNull(adapter.complete(KtPsiFactory(environment.project).createFile("val value = 0"), 4, false))
        assertTrue(adapter.inspectionRules(KtPsiFactory(environment.project).createFile("val value = 0")).isEmpty())
        assertTrue(adapter.inspect(KtPsiFactory(environment.project).createFile("val value = 0")).isEmpty())

        assertEquals(setOf("first", "second"), created.keys)
        assertEquals(List(7) { listOf("first", "second", "first") }.flatten(), calls.map { it.pluginId })
        calls.forEach { assertSame(created.getValue(it.pluginId), it) }
    }

    @Test
    fun `highlighting collects original source ranges in extension order`() {
        val file = KtPsiFactory(environment.project).createFile("val value = 0")
        val first = Highlighting(TextRange(0, 3), "DEFAULT_KEYWORD")
        val second = Highlighting(TextRange(12, 13), "DEFAULT_NUMBER")
        fun extension(expectedPlugin: String, highlighting: Highlighting) = object : TestExtension() {
            override fun highlight(source: KtFile, frontend: SyntaxFrontend): List<Highlighting> {
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
        assertFailsWith<IllegalArgumentException> { Highlighting(TextRange(1, 1), "DEFAULT_KEYWORD") }
        for (key in listOf("", " ", "\t\n")) {
            assertFailsWith<IllegalArgumentException> { Highlighting(TextRange(0, 1), key) }
        }
    }

    @Test
    fun `declined completions visit extensions in reverse order with original source arguments`() {
        val file = KtPsiFactory(environment.project).createFile("val value = 0")
        val offset = file.textLength
        val calls = mutableListOf<String>()
        fun extension(expectedPlugin: String) = object : TestExtension() {
            override fun complete(source: KtFile, offset: Int, smart: Boolean, frontend: SyntaxFrontend): List<Completion>? {
                assertSame(file, source)
                assertEquals(file.textLength, offset)
                assertTrue(smart)
                assertEquals(expectedPlugin, frontend.pluginId)
                calls += frontend.pluginId
                return null
            }

            override fun lower(file: KtFile, frontend: SyntaxFrontend): KtFile =
                fail("Completion must not lower the original source")
        }

        assertNull(adapter(extension("plugin-0"), extension("plugin-1")).complete(file, offset, true))
        assertEquals(listOf("plugin-1", "plugin-0"), calls)
        assertEquals("val value = 0", file.text)
    }

    @Test
    fun `later completion overrides earlier extensions including empty results`() {
        val file = KtPsiFactory(environment.project).createFile("val value = 0")
        val element = file.declarations.single() as KtProperty
        val custom = listOf(Completion("custom", "custom()", "()", "Unit", element))
        for (result in listOf(emptyList(), custom)) {
            val calls = mutableListOf<String>()
            val earlier = object : TestExtension() {
                override fun complete(file: KtFile, offset: Int, smart: Boolean, frontend: SyntaxFrontend): List<Completion> =
                    fail("An earlier completion override must not run")
            }
            val accepting = object : TestExtension() {
                override fun complete(source: KtFile, offset: Int, smart: Boolean, frontend: SyntaxFrontend): List<Completion> {
                    assertSame(file, source)
                    assertEquals(4, offset)
                    assertFalse(smart)
                    assertEquals("plugin-1", frontend.pluginId)
                    calls += "accept"
                    return result
                }
            }
            val declining = object : TestExtension() {
                override fun complete(file: KtFile, offset: Int, smart: Boolean, frontend: SyntaxFrontend): List<Completion>? {
                    assertEquals("plugin-2", frontend.pluginId)
                    calls += "decline"
                    return null
                }
            }

            assertSame(result, adapter(earlier, accepting, declining).complete(file, 4, false))
            assertEquals(listOf("decline", "accept"), calls)
        }
    }

    @Test
    fun `completion defaults preserve lookup text and reject blank lookup strings`() {
        val completion = Completion("custom")
        assertEquals("custom", completion.lookupString)
        assertEquals("custom", completion.insertText)
        assertNull(completion.tailText)
        assertNull(completion.typeText)
        assertNull(completion.element)
        assertEquals("", Completion("custom", "").insertText)
        for (lookupString in listOf("", " ", "\t\n")) {
            assertFailsWith<IllegalArgumentException> { Completion(lookupString) }
        }
    }

    @Test
    fun `completion infers PSI names and retains anchors with optional presentation`() {
        val file = KtPsiFactory(environment.project).createFile("val `escaped value` = 0\nval anonymous = fun() {}")
        val named = file.declarations[0] as KtProperty
        val completion = Completion.forElement(named)
        assertEquals("escaped value", completion.lookupString)
        assertEquals("escaped value", completion.insertText)
        assertNull(completion.tailText)
        assertNull(completion.typeText)
        assertSame(named, completion.element)

        val custom = Completion.forElement(named, "replacement()", "()", "Int")
        assertEquals("escaped value", custom.lookupString)
        assertEquals("replacement()", custom.insertText)
        assertEquals("()", custom.tailText)
        assertEquals("Int", custom.typeText)
        assertSame(named, custom.element)

        val unnamed = (file.declarations[1] as KtProperty).initializer as KtNamedFunction
        assertNull(unnamed.name)
        val fallback = Completion.forElement(unnamed)
        assertEquals(unnamed.text, fallback.lookupString)
        assertEquals(unnamed.text, fallback.insertText)
        assertSame(unnamed, fallback.element)
    }

    @Test
    fun `inspection rules retain all ranges in reverse extension order and original per-extension order`() {
        val source = "// \uD83D\uDE00\nfun example() { test }"
        val file = KtPsiFactory(environment.project).createFile(source)
        val start = source.indexOf("test")
        val whole = TextRange(start, start + 4)
        val element = file.findElementAt(start)!!
        assertEquals(whole, element.textRange)
        val earlier = listOf(InspectionRule(element), InspectionRule(element, InspectionMode.SUPPRESS, TextRange(0, 2)))
        val later = listOf(InspectionRule(element, InspectionMode.SUPPRESS), InspectionRule(element))
        val calls = mutableListOf<String>()
        fun extension(expectedPlugin: String, rules: List<InspectionRule>) = object : TestExtension() {
            override fun inspectionRules(original: KtFile, frontend: SyntaxFrontend): List<InspectionRule> {
                assertSame(file, original)
                assertEquals(source, original.text)
                assertEquals("test", original.text.substring(whole.startOffset, whole.endOffset))
                assertEquals(expectedPlugin, frontend.pluginId)
                calls += frontend.pluginId
                return rules
            }

            override fun lower(file: KtFile, frontend: SyntaxFrontend): KtFile =
                fail("Inspection rules must not lower the original source")
        }
        val adapter = adapter(extension("plugin-0", earlier), TestNoopExtension(), extension("plugin-2", later))

        val rules = adapter.inspectionRules(file)
        assertEquals(later + earlier, rules)
        (later + earlier).zip(rules).forEach { (expected, actual) -> assertSame(expected, actual) }
        rules.forEach { assertSame(element, it.element) }
        assertEquals(listOf(whole, whole, whole, TextRange(start, start + 2)), rules.map { it.range })
        assertEquals(listOf("plugin-2", "plugin-0"), calls)
        assertEquals(source, file.text)
    }

    @Test
    fun `inspection rules default to desugared and validate PSI subranges for every mode`() {
        val factory = KtPsiFactory(environment.project)
        val file = factory.createFile("val value = 0")
        val element = (file.declarations.single() as KtProperty).nameIdentifier!!
        val empty = factory.createFile("")
        val rule = InspectionRule(element)
        assertSame(element, rule.element)
        assertEquals(TextRange(4, 9), rule.range)
        assertNull(rule.rangeInElement)
        assertEquals(InspectionMode.DESUGARED, rule.mode)
        for (mode in InspectionMode.entries) {
            assertEquals(mode, InspectionRule(element, mode).mode)
            assertEquals(rule.range, InspectionRule(element, mode, TextRange(0, element.textLength)).range)
            val subrange = TextRange(1, 4)
            val partial = InspectionRule(element, mode, subrange)
            assertSame(subrange, partial.rangeInElement)
            assertEquals(TextRange(5, 8), partial.range)
            assertFailsWith<IllegalArgumentException> { InspectionRule(empty, mode) }
            assertFailsWith<IllegalArgumentException> { rule.copy(element = empty, mode = mode) }
            for (range in listOf(TextRange(0, 0), TextRange(5, 5), TextRange(0, 6), TextRange(5, 6))) {
                assertFailsWith<IllegalArgumentException> { InspectionRule(element, mode, range) }
                assertFailsWith<IllegalArgumentException> { rule.copy(mode = mode, rangeInElement = range) }
            }
        }
    }

    @Test
    fun `custom inspections aggregate in registration order without deduplication or rule suppression`() {
        val source = "// \uD83D\uDE00\nfun example() { test }"
        val file = KtPsiFactory(environment.project).createFile(source)
        val start = source.indexOf("test")
        val range = TextRange(start, start + 4)
        val element = file.findElementAt(start)!!
        assertEquals(range, element.textRange)
        val findings = InspectionSeverity.entries.map { Inspection(element, "Prefer foo", it) }
        val earlier = listOf(findings[0], findings[1], findings[0])
        val later = listOf(findings[2], findings[3], findings[0])
        val calls = mutableListOf<String>()
        fun extension(expectedPlugin: String, inspections: List<Inspection>) = object : TestExtension() {
            override fun inspect(original: KtFile, frontend: SyntaxFrontend): List<Inspection> {
                assertSame(file, original)
                assertEquals(source, original.text)
                assertEquals("test", original.text.substring(range.startOffset, range.endOffset))
                assertEquals(expectedPlugin, frontend.pluginId)
                calls += frontend.pluginId
                return inspections
            }

            override fun inspectionRules(file: KtFile, frontend: SyntaxFrontend): List<InspectionRule> =
                listOf(InspectionRule(element, InspectionMode.SUPPRESS))

            override fun lower(file: KtFile, frontend: SyntaxFrontend): KtFile =
                fail("Custom inspections must not lower the original source")
        }
        val adapter = adapter(extension("plugin-0", earlier), TestNoopExtension(), extension("plugin-2", later))

        assertEquals(List(2) { InspectionRule(element, InspectionMode.SUPPRESS) }, adapter.inspectionRules(file))
        val inspections = adapter.inspect(file)
        assertEquals(earlier + later, inspections)
        (earlier + later).zip(inspections).forEach { (expected, actual) -> assertSame(expected, actual) }
        inspections.forEach {
            assertSame(element, it.element)
            assertEquals(range, it.range)
        }
        assertEquals(listOf("plugin-0", "plugin-2"), calls)
        assertEquals(source, file.text)
    }

    @Test
    fun `custom inspections default to warning and validate PSI subranges and messages for every severity`() {
        val factory = KtPsiFactory(environment.project)
        val file = factory.createFile("val value = 0")
        val element = (file.declarations.single() as KtProperty).nameIdentifier!!
        val empty = factory.createFile("")
        val inspection = Inspection(element, "Prefer foo")
        assertSame(element, inspection.element)
        assertEquals(TextRange(4, 9), inspection.range)
        assertNull(inspection.rangeInElement)
        assertEquals("Prefer foo", inspection.message)
        assertEquals(InspectionSeverity.WARNING, inspection.severity)
        for (severity in InspectionSeverity.entries) {
            assertEquals(severity, Inspection(element, "Prefer foo", severity).severity)
            assertEquals(
                inspection.range, Inspection(element, "Prefer foo", severity, TextRange(0, element.textLength)).range
            )
            val subrange = TextRange(1, 4)
            val partial = Inspection(element, "Prefer foo", severity, subrange)
            assertSame(subrange, partial.rangeInElement)
            assertEquals(TextRange(5, 8), partial.range)
            assertFailsWith<IllegalArgumentException> { Inspection(empty, "Prefer foo", severity) }
            assertFailsWith<IllegalArgumentException> { inspection.copy(element = empty, severity = severity) }
            for (range in listOf(TextRange(0, 0), TextRange(5, 5), TextRange(0, 6), TextRange(5, 6))) {
                assertFailsWith<IllegalArgumentException> { Inspection(element, "Prefer foo", severity, range) }
                assertFailsWith<IllegalArgumentException> {
                    inspection.copy(rangeInElement = range, severity = severity)
                }
            }
            for (message in listOf("", " ", "\t\n")) {
                assertFailsWith<IllegalArgumentException> { Inspection(element, message, severity) }
                assertFailsWith<IllegalArgumentException> { inspection.copy(message = message, severity = severity) }
            }
        }
    }

    @Test
    fun `inspection ranges are captured before in-place PSI lowering changes offsets and lengths`() {
        val file = KtPsiFactory(environment.project).createFile("val prefix = 0\nval value = 1")
        val prefix = file.declarations[0] as KtProperty
        val element = file.declarations[1] as KtProperty
        val subrange = TextRange(4, 9)
        val rule = InspectionRule(element)
        val partialRule = InspectionRule(element, rangeInElement = subrange)
        val inspection = Inspection(element, "Prefer foo")
        val partialInspection = Inspection(element, "Prefer foo", rangeInElement = subrange)
        val whole = element.textRange
        val partial = subrange.shiftRight(whole.startOffset)
        fun rename(name: PsiElement, text: String) {
            val node = name.node as LeafElement
            val parent = node.treeParent
            node.rawReplaceWithText(text)
            parent.subtreeChanged()
        }
        val extension = object : TestExtension() {
            override fun lower(source: KtFile, frontend: SyntaxFrontend): KtFile {
                assertSame(file, source)
                ApplicationManager.getApplication().runWriteAction {
                    rename(prefix.nameIdentifier!!, "longerPrefix")
                    rename(element.nameIdentifier!!, "longerValue")
                }
                return source
            }
        }

        assertSame(file, adapter(extension).lower(file))
        assertEquals("val longerPrefix = 0\nval longerValue = 1", file.text)
        assertTrue(element.textRange.startOffset > whole.startOffset)
        assertTrue(element.textLength > whole.length)
        assertEquals(whole, rule.range)
        assertEquals(whole, inspection.range)
        assertEquals(partial, partialRule.range)
        assertEquals(partial, partialInspection.range)
        assertSame(element, rule.element)
        assertSame(element, inspection.element)
        assertEquals(element.textRange, rule.copy().range)
        assertEquals(element.textRange, inspection.copy().range)
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
            assertNull(adapter.complete(file, 0, false))
            assertNull(adapter.complete(file, file.textLength, true))
            assertTrue(adapter.inspectionRules(file).isEmpty())
            assertTrue(adapter.inspect(file).isEmpty())
            val builder = builder("value")
            assertFalse(adapter.parse(builder, GrammarRule.LOCAL_DECLARATION) { fail("Unexpected fallback") })
            assertEquals(0, builder.currentOffset)
            assertEquals("value", builder.tokenText)
        }
    }

    private class TestNoopExtension : TestExtension()
}