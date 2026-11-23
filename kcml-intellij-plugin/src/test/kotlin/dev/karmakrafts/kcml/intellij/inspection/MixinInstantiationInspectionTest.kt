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

import com.intellij.openapi.util.TextRange
import dev.karmakrafts.kcml.intellij.testutil.InspectionFixtureTestCase
import dev.karmakrafts.kcml.intellij.testutil.collectProblems
import dev.karmakrafts.kcml.intellij.testutil.withAnnotationLibrary
import org.jetbrains.kotlin.psi.KtCallExpression

class MixinInstantiationInspectionTest : InspectionFixtureTestCase(MixinInstantiationInspection()) {

    fun testExplicitConstructorCallRangesIncludeParentheses() {
        check(
            """
                @DirectMixin(Any::class) class Direct(val value: Int = 0)
                @IndirectMixin("Target") class Indirect(val value: Int = 0)
                val empty = <warning descr="Mixin classes must not be instantiated">Direct()</warning>
                val positional = <warning descr="Mixin classes must not be instantiated">Direct(1)</warning>
                val named = <warning descr="Mixin classes must not be instantiated">Direct(value = 2)</warning>
                val indirect = <warning descr="Mixin classes must not be instantiated">Indirect()</warning>
            """.trimIndent()
        )
        for (isOnTheFly in listOf(true, false)) {
            val problems = myFixture.collectProblems<KtCallExpression>(MixinInstantiationInspection(), isOnTheFly)
            assertEquals(4, problems.size)
            for (problem in problems) {
                assertTrue(problem.psiElement is KtCallExpression)
                assertEquals(TextRange(0, problem.psiElement.textLength), problem.textRangeInElement)
            }
        }
    }

    fun testPrimaryAndSecondaryConstructors() = check(
        """
            @DirectMixin(Any::class) class Direct(val value: Int = 0)
            @IndirectMixin("Target") class Indirect {
                constructor(value: Int)
                constructor(value: String)
            }
            val direct = <warning descr="Mixin classes must not be instantiated">Direct()</warning>
            val indirect = <warning descr="Mixin classes must not be instantiated">Indirect(1)</warning>
            val secondary = <warning descr="Mixin classes must not be instantiated">Indirect("value")</warning>
        """.trimIndent()
    )

    fun testAliasesAndQualifiedCalls() = check(
        """
            import dev.karmakrafts.kcml.api.mixin.DirectMixin as Mixin
            @Mixin(Any::class) class Example
            typealias Alias = Example
            typealias AliasChain = Alias
            val direct = sample.<warning descr="Mixin classes must not be instantiated">Example()</warning>
            val alias = <warning descr="Mixin classes must not be instantiated">Alias()</warning>
            val chain = <warning descr="Mixin classes must not be instantiated">AliasChain()</warning>
        """.trimIndent()
    )

    fun testConstructorCallHighlightRanges() = check(
        """
            @DirectMixin(Any::class) class Example<T>(val value: T, val action: () -> Unit)
            fun generic(value: List<Int>) = <warning descr="Mixin classes must not be instantiated">Example<List<Int>>(value = value, action = {})</warning>.value
            val nested = <warning descr="Mixin classes must not be instantiated">Example(value = <warning descr="Mixin classes must not be instantiated">Example(1) {}</warning>) {}</warning>
            val multiline = <warning descr="Mixin classes must not be instantiated">Example(
                value = 1,
                action = {}
            )</warning>
        """.trimIndent()
    )

    fun testNestedInnerAndGenericClasses() = check(
        """
            class Outer {
                @IndirectMixin("Target") class Nested<T>(val value: T)
                @DirectMixin(Any::class) inner class Inner
            }
            val nested = Outer.<warning descr="Mixin classes must not be instantiated">Nested(1)</warning>
            val inner = Outer().<warning descr="Mixin classes must not be instantiated">Inner()</warning>
        """.trimIndent()
    )

    fun testConstructorReferences() = check(
        """
            @DirectMixin(Any::class) class Direct
            @IndirectMixin("Target") class Indirect
            typealias Alias = Direct
            val direct = ::<warning descr="Mixin classes must not be instantiated">Direct</warning>
            val indirect = ::<warning descr="Mixin classes must not be instantiated">Indirect</warning>
            val alias = ::<warning descr="Mixin classes must not be instantiated">Alias</warning>
        """.trimIndent()
    )

    fun testImportedConstructorAliasAndTrailingLambda() {
        addAnnotationSource(
            "other/Example.kt", """
                package other
                @dev.karmakrafts.kcml.api.mixin.IndirectMixin("Target")
                class Example(val action: () -> Unit)
            """.trimIndent()
        )
        check(
            """
                import other.Example as Aliased
                val example = <warning descr="Mixin classes must not be instantiated">Aliased {}</warning>
                val reference = ::<warning descr="Mixin classes must not be instantiated">Aliased</warning>
            """.trimIndent()
        )
    }

    fun testSuperclassAndAnonymousObjectCalls() = check(
        """
            @DirectMixin(Any::class) open class Base
            class Derived : <warning descr="Mixin classes must not be instantiated">Base()</warning>
            val anonymous = object : <warning descr="Mixin classes must not be instantiated">Base()</warning> {}
        """.trimIndent()
    )

    fun testConstructorDelegation() = check(
        """
            @IndirectMixin("Target") open class Base(val value: Int) {
                constructor() : <warning descr="Mixin classes must not be instantiated">this(0)</warning>
            }
            class Derived : Base {
                constructor() : <warning descr="Mixin classes must not be instantiated">super(1)</warning>
            }
        """.trimIndent()
    )

    fun testNonInstantiatingReferencesAndOrdinaryCalls() = check(
        """
            @DirectMixin(Any::class) class Example {
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

    fun testUnrelatedAnnotationNames() = check(
        """
            annotation class DirectMixin(val target: String)
            annotation class IndirectMixin(val target: String)
            @DirectMixin("Target") class Direct
            @IndirectMixin("Target") class Indirect
            val direct = Direct()
            val indirect = Indirect()
        """.trimIndent()
    )

    fun testSuppression() = check(
        """
            @DirectMixin(Any::class) class Example
            @Suppress("KCML_MIXIN_INSTANTIATION")
            val example = Example()
        """.trimIndent()
    )

    fun testCompiledAnnotations() {
        myFixture.withAnnotationLibrary(
            "dev.karmakrafts.kcml.api.mixin.DirectMixin", "dev.karmakrafts.kcml.api.mixin.IndirectMixin"
        ) {
            check(
                """
                    @DirectMixin(Any::class) class Direct
                    @IndirectMixin("Target") class Indirect
                    val direct = <warning descr="Mixin classes must not be instantiated">Direct()</warning>
                    val indirect = <warning descr="Mixin classes must not be instantiated">Indirect()</warning>
                """.trimIndent(), false
            )
        }
    }

    fun testCompiledMixinClasses() {
        myFixture.withAnnotationLibrary(
            "dev.karmakrafts.kcml.api.mixin.DirectMixin",
            "dev.karmakrafts.kcml.api.mixin.IndirectMixin",
            "dev.karmakrafts.kcml.intellij.inspection.CompiledDirectMixin",
            "dev.karmakrafts.kcml.intellij.inspection.CompiledIndirectMixin"
        ) {
            check(
                """
                    import dev.karmakrafts.kcml.intellij.inspection.CompiledDirectMixin
                    import dev.karmakrafts.kcml.intellij.inspection.CompiledIndirectMixin
                    val direct = <warning descr="Mixin classes must not be instantiated">CompiledDirectMixin()</warning>
                    val indirect = <warning descr="Mixin classes must not be instantiated">CompiledIndirectMixin()</warning>
                    val reference = ::<warning descr="Mixin classes must not be instantiated">CompiledDirectMixin</warning>
                    class Derived : <warning descr="Mixin classes must not be instantiated">CompiledIndirectMixin()</warning>
                """.trimIndent(), false
            )
        }
    }

    private fun check(text: String, sourceAnnotations: Boolean = true) {
        if (sourceAnnotations) {
            addAnnotationSource(
                "dev/karmakrafts/kcml/api/mixin/Mixins.kt", """
                    package dev.karmakrafts.kcml.api.mixin
                    annotation class DirectMixin(val target: kotlin.reflect.KClass<*>)
                    annotation class IndirectMixin(val target: String)
                """.trimIndent()
            )
        }
        checkKotlinHighlighting(text, "package sample\nimport dev.karmakrafts.kcml.api.mixin.*")
    }
}

@dev.karmakrafts.kcml.api.mixin.DirectMixin(Any::class)
class CompiledDirectMixin

@dev.karmakrafts.kcml.api.mixin.IndirectMixin("Target")
open class CompiledIndirectMixin