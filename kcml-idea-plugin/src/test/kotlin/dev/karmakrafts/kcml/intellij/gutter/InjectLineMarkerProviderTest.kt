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

import com.intellij.openapi.util.IconLoader
import dev.karmakrafts.kcml.intellij.testutil.AnnotationFixtureTestCase
import dev.karmakrafts.kcml.intellij.testutil.withAnnotationLibrary

class InjectLineMarkerProviderTest : AnnotationFixtureTestCase() {

    fun testTopLevelFunction() {
        addAnnotation()
        assertMarkers("@Inject(\"target\") fun <caret>inject() {}", 1)
    }

    fun testMemberFunction() {
        addAnnotation()
        assertMarkers("class Example { @Inject(\"target\") fun <caret>inject() {} }", 1)
    }

    fun testAliasedAnnotation() {
        addAnnotation()
        assertMarkers(
            """
                import dev.karmakrafts.kcml.api.mixin.Inject as Injection
                @Injection("target") fun <caret>inject() {}
            """.trimIndent(), 1
        )
    }

    fun testQualifiedAnnotationOnObjectMember() {
        addAnnotation()
        assertMarkers(
            """
                object Example {
                    @dev.karmakrafts.kcml.api.mixin.Inject("target")
                    fun <caret>inject() {}
                }
            """.trimIndent(), 1
        )
    }

    fun testMultipleFunctions() {
        addAnnotation()
        assertMarkers(
            """
                @Inject("first") fun <caret>first() {}
                fun unannotated() {}
                @Inject("second") fun second() {}
            """.trimIndent(), 2
        )
    }

    fun testUnannotatedFunctionHasNoMarker() {
        assertMarkers("fun <caret>example() {}", 0)
    }

    fun testUnrelatedAnnotationHasNoMarker() {
        assertMarkers(
            """
                annotation class Inject(val name: String)
                @Inject("target") fun <caret>inject() {}
            """.trimIndent(), 0
        )
    }

    fun testUnresolvedAnnotationHasNoMarker() {
        assertMarkers("@missing.Inject(\"target\") fun <caret>inject() {}", 0)
    }

    fun testClassHasNoMarker() {
        addAnnotation()
        assertMarkers("@Inject(\"target\") class <caret>Example", 0)
    }

    fun testObjectHasNoMarker() {
        addAnnotation()
        assertMarkers("@Inject(\"target\") object <caret>Example", 0)
    }

    fun testPropertyHasNoMarker() {
        addAnnotation()
        assertMarkers("@Inject(\"target\") val <caret>example = 0", 0)
    }

    fun testCompiledKotlinAnnotation() {
        myFixture.withAnnotationLibrary("dev.karmakrafts.kcml.api.mixin.Inject") {
            assertMarkers("@Inject(\"target\") fun <caret>inject() {}", 1)
        }
    }

    private fun addAnnotation() {
        addAnnotationSource(
            "dev/karmakrafts/kcml/api/mixin/Inject.kt", """
                package dev.karmakrafts.kcml.api.mixin
                annotation class Inject(val name: String)
            """.trimIndent()
        )
    }

    private fun assertMarkers(text: String, expected: Int) {
        myFixture.configureByText("Injection.kt", "import dev.karmakrafts.kcml.api.mixin.*\n$text")
        val icon = IconLoader.getIcon("/icons/dev/karmakrafts/kcml/intellij/injection.svg", javaClass)
        val markers = myFixture.findAllGutters().filter { it.icon == icon }
        assertEquals(expected, markers.size)
        assertTrue(markers.all { it.tooltipText == "KCML plugin" })
        val caretMarkers = myFixture.findGuttersAtCaret().filter { it.icon == icon }
        assertEquals(if (expected == 0) 0 else 1, caretMarkers.size)
    }
}