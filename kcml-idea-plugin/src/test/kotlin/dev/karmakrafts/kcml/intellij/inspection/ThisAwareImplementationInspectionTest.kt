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

import dev.karmakrafts.kcml.intellij.testutil.AnnotationFixtureTestCase
import dev.karmakrafts.kcml.intellij.testutil.withAnnotationLibrary

class ThisAwareImplementationInspectionTest : AnnotationFixtureTestCase() {
    override fun setUp() {
        super.setUp()
        myFixture.enableInspections(ThisAwareImplementationInspection())
    }

    fun testNonMixinClassesAndObjects() = check(
        """
            class Ordinary : <warning descr="ThisAware implementations must be mixin classes">ThisAware<Any></warning>
            abstract class Abstract : <warning descr="ThisAware implementations must be mixin classes">ThisAware<Any></warning>
            object Singleton : <warning descr="ThisAware implementations must be mixin classes">ThisAware<Any></warning>
            val anonymous = object : <warning descr="ThisAware implementations must be mixin classes">ThisAware<Any></warning> {}
        """.trimIndent()
    )

    fun testDirectAndIndirectMixins() = check(
        """
            import dev.karmakrafts.kcml.api.mixin.DirectMixin as Mixin
            @Mixin(Any::class) class Direct : ThisAware<Any>
            @IndirectMixin("Target") class Indirect : ThisAware<Any>
            @Mixin(Any::class) class Unaware
            class Ordinary
        """.trimIndent()
    )

    fun testInheritedImplementation() = check(
        """
            interface Aware : ThisAware<Any>
            interface DerivedAware : Aware
            @DirectMixin(Any::class) open class Base : Aware
            class Derived : <warning descr="ThisAware implementations must be mixin classes">Base</warning>()
            class Implementation : <warning descr="ThisAware implementations must be mixin classes">DerivedAware</warning>
            @IndirectMixin("Target") class Mixin : Base()
        """.trimIndent()
    )

    fun testMultipleSupertypesAndDelegation() = check(
        """
            interface Unrelated
            interface Aware : ThisAware<Any>
            class Implementation : Unrelated, <warning descr="ThisAware implementations must be mixin classes">ThisAware<Any></warning>
            class Multiple : <warning descr="ThisAware implementations must be mixin classes">Aware</warning>, <warning descr="ThisAware implementations must be mixin classes">ThisAware<Any></warning>
            class Delegated(aware: Aware) : Unrelated, <warning descr="ThisAware implementations must be mixin classes">Aware</warning> by aware
        """.trimIndent()
    )

    fun testAliasesAndQualifiedTypes() = check(
        """
            import dev.karmakrafts.kcml.api.mixin.ThisAware as Aware
            typealias Alias<T> = Aware<T>
            class Aliased : <warning descr="ThisAware implementations must be mixin classes">Alias<Any></warning>
            class Qualified : dev.karmakrafts.kcml.api.mixin.<warning descr="ThisAware implementations must be mixin classes">ThisAware<Any></warning>
        """.trimIndent()
    )

    fun testNestedTypeArgumentsAndGenericSupertypes() = check(
        """
            interface Aware<T> : ThisAware<T>
            @DirectMixin(Any::class) open class Base<T> : Aware<T>
            class Nested : <warning descr="ThisAware implementations must be mixin classes">ThisAware<Map<String, List<Any?>>></warning>
            class Derived : <warning descr="ThisAware implementations must be mixin classes">Base<List<Any>></warning>()
            class Delegated(aware: Aware<List<Any>>) : <warning descr="ThisAware implementations must be mixin classes">Aware<List<Any>></warning> by aware
        """.trimIndent()
    )

    fun testLocalAndNestedClasses() = check(
        """
            class Outer {
                class Nested : <warning descr="ThisAware implementations must be mixin classes">ThisAware<Any></warning>
                fun example() {
                    class Local : <warning descr="ThisAware implementations must be mixin classes">ThisAware<Any></warning>
                }
            }
        """.trimIndent()
    )

    fun testUnrelatedNamesAndUnresolvedTypes() = check(
        """
            annotation class DirectMixin(val target: String)
            annotation class IndirectMixin(val target: String)
            @DirectMixin("Target") class Direct : dev.karmakrafts.kcml.api.mixin.<warning descr="ThisAware implementations must be mixin classes">ThisAware<Any></warning>
            @IndirectMixin("Target") class Indirect : dev.karmakrafts.kcml.api.mixin.<warning descr="ThisAware implementations must be mixin classes">ThisAware<Any></warning>
            interface ThisAware<T>
            class Ordinary : ThisAware<Any>
            class Unresolved : <error descr="[UNRESOLVED_REFERENCE] Unresolved reference 'MissingType'.">MissingType</error>
        """.trimIndent()
    )

    fun testSuppression() = check(
        """
            @Suppress("KCML_THIS_AWARE_IMPLEMENTATION")
            class Suppressed : ThisAware<Any>
        """.trimIndent()
    )

    fun testCompiledApi() {
        myFixture.withAnnotationLibrary(
            "dev.karmakrafts.kcml.api.mixin.ThisAware",
            "dev.karmakrafts.kcml.api.mixin.DirectMixin",
            "dev.karmakrafts.kcml.api.mixin.IndirectMixin"
        ) {
            check(
                """
                    class Ordinary : <warning descr="ThisAware implementations must be mixin classes">ThisAware<Any></warning>
                    @DirectMixin(Any::class) class Direct : ThisAware<Any>
                    @IndirectMixin("Target") class Indirect : ThisAware<Any>
                """.trimIndent(), false
            )
        }
    }

    private fun check(text: String, sourceApi: Boolean = true) {
        if (sourceApi) {
            addAnnotationSource(
                "dev/karmakrafts/kcml/api/mixin/Mixins.kt", """
                    package dev.karmakrafts.kcml.api.mixin
                    interface ThisAware<T>
                    annotation class DirectMixin(val target: kotlin.reflect.KClass<*>)
                    annotation class IndirectMixin(val target: String)
                """.trimIndent()
            )
        }
        myFixture.configureByText("Example.kt", "package sample\nimport dev.karmakrafts.kcml.api.mixin.*\n$text")
        myFixture.checkHighlighting(true, false, false)
    }
}