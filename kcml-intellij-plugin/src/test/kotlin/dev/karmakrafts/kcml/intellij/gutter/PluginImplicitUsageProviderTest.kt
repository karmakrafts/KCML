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

class PluginImplicitUsageProviderTest : AnnotationFixtureTestCase() {
    private val provider = PluginImplicitUsageProvider()

    fun testAnnotatedClass() {
        addAnnotation()
        assertClassUsage("@Plugin(\"example\") class Example", true)
    }

    fun testAnnotatedObject() {
        addAnnotation()
        assertClassUsage("@Plugin(\"example\") object Example", true)
    }

    fun testAliasedAnnotation() {
        addAnnotation()
        assertClassUsage(
            """
                import dev.karmakrafts.kcml.api.plugin.Plugin as EntryPoint
                @EntryPoint("example") class Example
            """.trimIndent(), true
        )
    }

    fun testQualifiedAnnotationOnNestedClass() {
        addAnnotation()
        assertClassUsage(
            """
                class Outer {
                    @dev.karmakrafts.kcml.api.plugin.Plugin("example") class Example
                }
            """.trimIndent(), true
        )
    }

    fun testCompiledKotlinAnnotation() {
        myFixture.withAnnotationLibrary("dev.karmakrafts.kcml.api.plugin.Plugin") {
            assertClassUsage("@Plugin(\"example\") class Example", true)
        }
    }

    fun testUnannotatedClassAndObject() {
        assertClassUsage("class Example", false)
        assertClassUsage("object Example", false)
    }

    fun testUnrelatedAnnotationWithSameShortName() {
        assertClassUsage("annotation class Plugin(val id: String)\n@Plugin(\"example\") class Example", false)
    }

    fun testUnresolvedAnnotation() {
        assertClassUsage("@missing.Plugin(\"example\") class Example", false)
    }

    fun testAnnotatedJavaClassAndOtherElementTypes() {
        addAnnotation()
        val declaration = myFixture.addClass(
            """
                import dev.karmakrafts.kcml.api.plugin.Plugin;
                @Plugin(id = "example") public class Example {
                    @Plugin(id = "example") private int field;
                    @Plugin(id = "example") private void method() {}
                }
            """.trimIndent()
        )
        provider.assertUsage(declaration, true)
        provider.assertUsage(declaration.fields.single(), false)
        provider.assertUsage(declaration.findMethodsByName("method", false).single(), false)
        provider.assertUsage(declaration.nameIdentifier!!, false)
        provider.assertUsage(declaration.containingFile, false)
    }

    private fun addAnnotation() {
        addAnnotationSource(
            "dev/karmakrafts/kcml/api/plugin/Plugin.kt", """
                package dev.karmakrafts.kcml.api.plugin
                annotation class Plugin(val id: String)
            """.trimIndent()
        )
    }

    private fun assertClassUsage(text: String, expected: Boolean) {
        val file = myFixture.configureByText("Plugin.kt", "import dev.karmakrafts.kcml.api.plugin.*\n$text")
        val declaration = file.namedDescendant<KtClassOrObject>("Example")
        val lightClass = myFixture.findClass(declaration.fqName!!.asString())
        assertEquals(declaration, lightClass.navigationElement)
        provider.assertUsage(lightClass, expected)
        provider.assertUsage(declaration, false)
        provider.assertUsage(declaration.nameIdentifier!!, false)
        provider.assertUsage(file, false)
    }
}