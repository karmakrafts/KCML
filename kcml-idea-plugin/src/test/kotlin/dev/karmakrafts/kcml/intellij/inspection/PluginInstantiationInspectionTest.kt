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

package dev.karmakrafts.kcml.intellij.inspection

import com.intellij.codeInspection.InspectionManager
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.util.TextRange
import com.intellij.psi.util.PsiTreeUtil
import dev.karmakrafts.kcml.intellij.testutil.AnnotationFixtureTestCase
import dev.karmakrafts.kcml.intellij.testutil.withAnnotationLibrary
import org.jetbrains.kotlin.psi.KtCallExpression

class PluginInstantiationInspectionTest : AnnotationFixtureTestCase() {
    override fun setUp() {
        super.setUp()
        myFixture.enableInspections(PluginInstantiationInspection())
    }

    fun testAnnotationOrInterfaceOrBoth() = check(
        """
            @Plugin("annotated") class Annotated
            class Implementation : CompilerPlugin
            @Plugin("both") class Both : CompilerPlugin
            val annotated = <warning descr="Plugin classes must not be instantiated">Annotated()</warning>
            val implementation = <warning descr="Plugin classes must not be instantiated">Implementation()</warning>
            val both = <warning descr="Plugin classes must not be instantiated">Both()</warning>
        """.trimIndent()
    )

    fun testInheritedImplementation() = check(
        """
            interface Extension : CompilerPlugin
            open class Base : Extension
            class Derived : <warning descr="Plugin classes must not be instantiated">Base()</warning>
            val derived = <warning descr="Plugin classes must not be instantiated">Derived()</warning>
        """.trimIndent()
    )

    fun testAliasesAndQualifiedCalls() = check(
        """
            import dev.karmakrafts.kcml.api.plugin.Plugin as EntryPoint
            import dev.karmakrafts.kcml.api.plugin.CompilerPlugin as PluginInterface
            @EntryPoint("example") class Example
            class Implementation : PluginInterface
            typealias Alias = Example
            typealias AliasChain = Alias
            typealias ImplementationAlias = Implementation
            val qualified = sample.<warning descr="Plugin classes must not be instantiated">Example()</warning>
            val alias = <warning descr="Plugin classes must not be instantiated">Alias()</warning>
            val chain = <warning descr="Plugin classes must not be instantiated">AliasChain()</warning>
            val implementation = <warning descr="Plugin classes must not be instantiated">ImplementationAlias()</warning>
        """.trimIndent()
    )

    fun testSecondaryConstructorsAndDelegation() = check(
        """
            @Plugin("base") open class Base(val value: Int) : CompilerPlugin {
                constructor() : <warning descr="Plugin classes must not be instantiated">this(0)</warning>
            }
            class Derived : Base {
                constructor() : <warning descr="Plugin classes must not be instantiated">super(1)</warning>
            }
            val base = <warning descr="Plugin classes must not be instantiated">Base()</warning>
            val secondary = <warning descr="Plugin classes must not be instantiated">Derived()</warning>
        """.trimIndent()
    )

    fun testConstructorReferences() = check(
        """
            @Plugin("example") class Example
            class Implementation : CompilerPlugin
            typealias Alias = Example
            val annotated = ::<warning descr="Plugin classes must not be instantiated">Example</warning>
            val implementation = ::<warning descr="Plugin classes must not be instantiated">Implementation</warning>
            val alias = ::<warning descr="Plugin classes must not be instantiated">Alias</warning>
        """.trimIndent()
    )

    fun testConstructorCallHighlightRanges() = check(
        """
            @Plugin("example") class Example<T>(val value: T, val action: () -> Unit)
            fun generic(value: List<Int>) = <warning descr="Plugin classes must not be instantiated">Example<List<Int>>(value = value, action = {})</warning>.value
            val nested = <warning descr="Plugin classes must not be instantiated">Example(value = <warning descr="Plugin classes must not be instantiated">Example(1) {}</warning>) {}</warning>
            val multiline = <warning descr="Plugin classes must not be instantiated">Example(
                value = 1,
                action = {}
            )</warning>
        """.trimIndent()
    )

    fun testExplicitConstructorCallRangesIncludeParentheses() {
        check(
            """
                @Plugin("example") class Example(val value: Int = 0)
                val empty = <warning descr="Plugin classes must not be instantiated">Example()</warning>
                val positional = <warning descr="Plugin classes must not be instantiated">Example(1)</warning>
                val named = <warning descr="Plugin classes must not be instantiated">Example(value = 2)</warning>
            """.trimIndent()
        )
        for (isOnTheFly in listOf(true, false)) {
            val holder = ProblemsHolder(InspectionManager.getInstance(project), myFixture.file, isOnTheFly)
            val visitor = PluginInstantiationInspection().buildVisitor(holder, isOnTheFly)
            PsiTreeUtil.collectElementsOfType(myFixture.file, KtCallExpression::class.java).forEach {
                it.accept(visitor)
            }
            assertEquals(3, holder.results.size)
            for (problem in holder.results) {
                assertEquals(TextRange(0, problem.psiElement.textLength), problem.textRangeInElement)
            }
        }
    }

    fun testNestedInnerAndGenericClasses() = check(
        """
            class Outer {
                @Plugin("nested") class Nested<T>(val value: T)
                inner class Inner : CompilerPlugin
            }
            val nested = Outer.<warning descr="Plugin classes must not be instantiated">Nested(1)</warning>
            val inner = Outer().<warning descr="Plugin classes must not be instantiated">Inner()</warning>
        """.trimIndent()
    )

    fun testImportedAliasAndTrailingLambda() {
        addAnnotationSource(
            "other/Example.kt", """
                package other
                @dev.karmakrafts.kcml.api.plugin.Plugin("example")
                class Example(val action: () -> Unit)
            """.trimIndent()
        )
        check(
            """
                import other.Example as Aliased
                val example = <warning descr="Plugin classes must not be instantiated">Aliased {}</warning>
                val reference = ::<warning descr="Plugin classes must not be instantiated">Aliased</warning>
            """.trimIndent()
        )
    }

    fun testSuperclassAndAnonymousObjectCalls() = check(
        """
            @Plugin("base") open class Base
            class Derived : <warning descr="Plugin classes must not be instantiated">Base()</warning>
            val anonymous = object : <warning descr="Plugin classes must not be instantiated">Base()</warning> {}
        """.trimIndent()
    )

    fun testNonInstantiatingReferencesAndOrdinaryCalls() = check(
        """
            @Plugin("example") class Example {
                companion object { fun create(): Example? = null }
            }
            class Ordinary
            fun Example(value: Int): Example? = null
            val type = Example::class
            val factory = Example.create()
            val function = Example(1)
            val reference: (Int) -> Example? = ::Example
            val ordinary = Ordinary()
            val constructor = ::Ordinary
            fun accept(value: Example?) = value
        """.trimIndent()
    )

    fun testUnrelatedNames() = check(
        """
            annotation class Plugin(val id: String)
            interface CompilerPlugin
            @Plugin("example") class Example
            class Implementation : CompilerPlugin
            val example = Example()
            val implementation = Implementation()
        """.trimIndent()
    )

    fun testSuppression() = check(
        """
            @Plugin("example") class Example
            @Suppress("KCML_PLUGIN_INSTANTIATION")
            val example = Example()
        """.trimIndent()
    )

    fun testCompiledPluginClasses() {
        myFixture.withAnnotationLibrary(
            "dev.karmakrafts.kcml.api.plugin.Plugin",
            "dev.karmakrafts.kcml.api.plugin.CompilerPlugin",
            "dev.karmakrafts.kcml.intellij.inspection.CompiledAnnotatedPlugin",
            "dev.karmakrafts.kcml.intellij.inspection.CompiledPluginImplementation"
        ) {
            check(
                """
                    import dev.karmakrafts.kcml.intellij.inspection.CompiledAnnotatedPlugin
                    import dev.karmakrafts.kcml.intellij.inspection.CompiledPluginImplementation
                    val annotated = <warning descr="Plugin classes must not be instantiated">CompiledAnnotatedPlugin()</warning>
                    val implementation = <warning descr="Plugin classes must not be instantiated">CompiledPluginImplementation()</warning>
                    val reference = ::<warning descr="Plugin classes must not be instantiated">CompiledAnnotatedPlugin</warning>
                    class Derived : <warning descr="Plugin classes must not be instantiated">CompiledPluginImplementation()</warning>
                    val derived = <warning descr="Plugin classes must not be instantiated">Derived()</warning>
                """.trimIndent(), false
            )
        }
    }

    private fun check(text: String, sourceApi: Boolean = true) {
        if (sourceApi) {
            addAnnotationSource(
                "dev/karmakrafts/kcml/api/plugin/Plugin.kt", """
                    package dev.karmakrafts.kcml.api.plugin
                    annotation class Plugin(val id: String)
                    interface CompilerPlugin
                """.trimIndent()
            )
        }
        myFixture.configureByText("Example.kt", "package sample\nimport dev.karmakrafts.kcml.api.plugin.*\n$text")
        myFixture.checkHighlighting(true, false, false)
    }
}

@dev.karmakrafts.kcml.api.plugin.Plugin("compiled")
class CompiledAnnotatedPlugin

open class CompiledPluginImplementation : dev.karmakrafts.kcml.api.plugin.CompilerPlugin {
    override fun init(context: dev.karmakrafts.kcml.api.plugin.InitializationContext) = Unit
}