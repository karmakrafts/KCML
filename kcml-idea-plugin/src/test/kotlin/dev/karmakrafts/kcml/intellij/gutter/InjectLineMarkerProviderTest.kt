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

class InjectLineMarkerProviderTest : LightJavaCodeInsightFixtureTestCase() {
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
        val directory = createTempDirectory(Path("build"), "kcml-inject-gutter-library-")
        val jar = directory.resolve("annotations.jar")
        val libraryUrl = "jar://${jar.toAbsolutePath()}!/"
        try {
            JarOutputStream(jar.outputStream()).use { output ->
                val classFile = "dev/karmakrafts/kcml/api/mixin/Inject.class"
                output.putNextEntry(JarEntry(classFile))
                javaClass.classLoader.getResourceAsStream(classFile)!!.use { it.copyTo(output) }
                output.closeEntry()
            }
            WriteCommandAction.runWriteCommandAction(project) {
                ModuleRootModificationUtil.addModuleLibrary(myFixture.module, libraryUrl)
            }
            assertMarkers("@Inject(\"target\") fun <caret>inject() {}", 1)
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