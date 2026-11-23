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

package dev.karmakrafts.kcml.intellij.gutter

import dev.karmakrafts.kcml.intellij.testutil.AnnotationFixtureTestCase
import dev.karmakrafts.kcml.intellij.testutil.assertUsage
import dev.karmakrafts.kcml.intellij.testutil.namedDescendant
import dev.karmakrafts.kcml.intellij.testutil.withAnnotationLibrary
import org.jetbrains.kotlin.psi.KtClassOrObject

class MixinImplicitUsageProviderTest : AnnotationFixtureTestCase() {
    private val provider = MixinImplicitUsageProvider()

    fun testDirectMixinClass() {
        addAnnotations()
        assertClassUsage("@DirectMixin(Any::class) class Example", true)
    }

    fun testIndirectMixinObject() {
        addAnnotations()
        assertClassUsage("@IndirectMixin(\"example.Target\") object Example", true)
    }

    fun testAliasedDirectMixin() {
        addAnnotations()
        assertClassUsage(
            """
                import dev.karmakrafts.kcml.api.mixin.DirectMixin as Mixin
                @Mixin(Any::class) class Example
            """.trimIndent(), true
        )
    }

    fun testQualifiedIndirectMixinOnNestedClass() {
        addAnnotations()
        assertClassUsage(
            """
                class Outer {
                    @dev.karmakrafts.kcml.api.mixin.IndirectMixin("example.Target") class Example
                }
            """.trimIndent(), true
        )
    }

    fun testBothMixinAnnotations() {
        addAnnotations()
        assertClassUsage("@DirectMixin(Any::class) @IndirectMixin(\"example.Target\") class Example", true)
    }

    fun testCompiledKotlinAnnotations() {
        myFixture.withAnnotationLibrary(
            "dev.karmakrafts.kcml.api.mixin.DirectMixin", "dev.karmakrafts.kcml.api.mixin.IndirectMixin"
        ) {
            assertClassUsage("@DirectMixin(Any::class) class Example", true)
            assertClassUsage("@IndirectMixin(\"example.Target\") object Example", true)
        }
    }

    fun testUnannotatedClass() {
        assertClassUsage("class Example", false)
    }

    fun testUnrelatedAnnotationsWithSameShortNames() {
        assertClassUsage(
            """
                annotation class DirectMixin
                annotation class IndirectMixin
                @DirectMixin @IndirectMixin class Example
            """.trimIndent(), false
        )
    }

    fun testUnresolvedAnnotation() {
        assertClassUsage("@missing.IndirectMixin(\"example.Target\") class Example", false)
    }

    fun testAnnotatedJavaClassesAndOtherElementTypes() {
        addAnnotations()
        val direct = myFixture.addClass(
            "@dev.karmakrafts.kcml.api.mixin.DirectMixin(target = Object.class) public class Direct {}"
        )
        val indirect = myFixture.addClass(
            """
                import dev.karmakrafts.kcml.api.mixin.IndirectMixin;
                @IndirectMixin(target = "example.Target") public class Indirect {
                    @IndirectMixin(target = "example.Target") private int field;
                    @IndirectMixin(target = "example.Target") private void method() {}
                }
            """.trimIndent()
        )
        provider.assertUsage(direct, true)
        provider.assertUsage(indirect, true)
        provider.assertUsage(indirect.fields.single(), false)
        provider.assertUsage(indirect.findMethodsByName("method", false).single(), false)
        provider.assertUsage(indirect.nameIdentifier!!, false)
        provider.assertUsage(indirect.containingFile, false)
    }

    private fun addAnnotations() {
        addAnnotationSource(
            "dev/karmakrafts/kcml/api/mixin/Mixins.kt", """
                package dev.karmakrafts.kcml.api.mixin
                annotation class DirectMixin(val target: kotlin.reflect.KClass<*>, val priority: Int = 0)
                annotation class IndirectMixin(val target: String, val priority: Int = 0)
            """.trimIndent()
        )
    }

    private fun assertClassUsage(text: String, expected: Boolean) {
        val file = myFixture.configureByText("Mixin.kt", "import dev.karmakrafts.kcml.api.mixin.*\n$text")
        val declaration = file.namedDescendant<KtClassOrObject>("Example")
        val lightClass = myFixture.findClass(declaration.fqName!!.asString())
        assertEquals(declaration, lightClass.navigationElement)
        provider.assertUsage(lightClass, expected)
        provider.assertUsage(declaration, false)
        provider.assertUsage(declaration.nameIdentifier!!, false)
        provider.assertUsage(file, false)
    }
}