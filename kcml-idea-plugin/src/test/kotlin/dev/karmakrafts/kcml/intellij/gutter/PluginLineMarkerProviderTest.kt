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

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.openapi.roots.OrderRootType
import com.intellij.openapi.util.IconLoader
import com.intellij.psi.PsiFile
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase
import java.util.jar.JarEntry
import java.util.jar.JarOutputStream
import kotlin.io.path.Path
import kotlin.io.path.createTempDirectory
import kotlin.io.path.outputStream

class PluginLineMarkerProviderTest : LightJavaCodeInsightFixtureTestCase() {
    private var annotation: PsiFile? = null

    override fun tearDown() {
        try {
            WriteCommandAction.runWriteCommandAction(project) {
                annotation?.takeIf { it.isValid }?.delete()
            }
        }
        finally {
            annotation = null
            super.tearDown()
        }
    }

    fun testPluginClass() {
        addAnnotation()
        assertMarkers("@Plugin(\"example\") class <caret>Example", 1)
    }

    fun testAliasedAnnotation() {
        addAnnotation()
        assertMarkers(
            """
                import dev.karmakrafts.kcml.api.plugin.Plugin as CompilerPlugin
                @CompilerPlugin("example") class <caret>Example
            """.trimIndent(), 1
        )
    }

    fun testQualifiedAnnotationOnNestedClass() {
        addAnnotation()
        assertMarkers(
            """
                class Outer {
                    @dev.karmakrafts.kcml.api.plugin.Plugin("example")
                    class <caret>Nested
                }
            """.trimIndent(), 1
        )
    }

    fun testPluginObject() {
        addAnnotation()
        assertMarkers("@Plugin(\"example\") object <caret>Example", 1)
    }

    fun testMultiplePluginClasses() {
        addAnnotation()
        assertMarkers(
            """
                @Plugin("first") class <caret>First
                class Unannotated
                @Plugin("second") class Second
            """.trimIndent(), 2
        )
    }

    fun testUnannotatedClassHasNoMarker() {
        assertMarkers("class <caret>Example", 0)
    }

    fun testUnrelatedAnnotationHasNoMarker() {
        assertMarkers(
            """
                annotation class Plugin(val id: String)
                @Plugin("example") class <caret>Example
            """.trimIndent(), 0
        )
    }

    fun testUnresolvedAnnotationHasNoMarker() {
        assertMarkers("@missing.Plugin(\"example\") class <caret>Example", 0)
    }

    fun testCompiledKotlinAnnotation() {
        val directory = createTempDirectory(Path("build"), "kcml-plugin-gutter-library-")
        val jar = directory.resolve("annotations.jar")
        val libraryUrl = "jar://${jar.toAbsolutePath()}!/"
        try {
            JarOutputStream(jar.outputStream()).use { output ->
                val classFile = "dev/karmakrafts/kcml/api/plugin/Plugin.class"
                output.putNextEntry(JarEntry(classFile))
                javaClass.classLoader.getResourceAsStream(classFile)!!.use { it.copyTo(output) }
                output.closeEntry()
            }
            WriteCommandAction.runWriteCommandAction(project) {
                ModuleRootModificationUtil.addModuleLibrary(myFixture.module, libraryUrl)
            }
            assertMarkers("@Plugin(\"example\") class <caret>Example", 1)
        }
        finally {
            WriteCommandAction.runWriteCommandAction(project) {
                ModuleRootModificationUtil.updateModel(myFixture.module) { model ->
                    val table = model.moduleLibraryTable
                    table.libraries.filter { libraryUrl in it.getUrls(OrderRootType.CLASSES) }
                        .forEach(table::removeLibrary)
                }
            }
            directory.toFile().deleteRecursively()
        }
    }

    private fun addAnnotation() {
        annotation = myFixture.addFileToProject(
            "dev/karmakrafts/kcml/api/plugin/Plugin.kt", """
                package dev.karmakrafts.kcml.api.plugin
                annotation class Plugin(val id: String)
            """.trimIndent()
        )
    }

    private fun assertMarkers(text: String, expected: Int) {
        myFixture.configureByText("Plugin.kt", "import dev.karmakrafts.kcml.api.plugin.*\n$text")
        val markers = myFixture.findAllGutters().filter { it.tooltipText == "KCML plugin" }
        assertEquals(expected, markers.size)
        if (expected == 0) return
        val icon = IconLoader.getIcon("/icons/dev/karmakrafts/kcml/intellij/kcml.svg", javaClass)
        assertTrue(markers.all { it.icon == icon })
        assertTrue(myFixture.findGuttersAtCaret().any { it.tooltipText == "KCML plugin" })
    }
}