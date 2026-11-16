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

class MixinLineMarkerProviderTest : LightJavaCodeInsightFixtureTestCase() {
    private var annotations: PsiFile? = null

    override fun tearDown() {
        try {
            WriteCommandAction.runWriteCommandAction(project) {
                annotations?.takeIf { it.isValid }?.delete()
            }
        }
        finally {
            annotations = null
            super.tearDown()
        }
    }

    fun testIndirectMixin() {
        addAnnotations()
        assertMarkers("@IndirectMixin(\"example.Target\") class <caret>Example", 1)
    }

    fun testDirectMixin() {
        addAnnotations()
        assertMarkers("@DirectMixin(Any::class) class <caret>Example", 1)
    }

    fun testAliasedAnnotation() {
        addAnnotations()
        assertMarkers(
            """
                import dev.karmakrafts.kcml.api.mixin.DirectMixin as Mixin
                @Mixin(Any::class) class <caret>Example
            """.trimIndent(), 1
        )
    }

    fun testQualifiedAnnotationOnNestedClass() {
        addAnnotations()
        assertMarkers(
            """
                class Outer {
                    @dev.karmakrafts.kcml.api.mixin.IndirectMixin("example.Target")
                    class <caret>Nested
                }
            """.trimIndent(), 1
        )
    }

    fun testBothAnnotationsProduceOneMarker() {
        addAnnotations()
        assertMarkers(
            """
                @DirectMixin(Any::class)
                @IndirectMixin("example.Target")
                class <caret>Example
            """.trimIndent(), 1
        )
    }

    fun testUnannotatedClassHasNoMarker() {
        assertMarkers("class <caret>Example", 0)
    }

    fun testUnrelatedAnnotationsHaveNoMarker() {
        assertMarkers(
            """
                annotation class DirectMixin
                annotation class IndirectMixin
                @DirectMixin @IndirectMixin class <caret>Example
            """.trimIndent(), 0
        )
    }

    fun testUnresolvedAnnotationHasNoMarker() {
        assertMarkers("@missing.IndirectMixin(\"example.Target\") class <caret>Example", 0)
    }

    fun testCompiledKotlinAnnotations() {
        val directory = createTempDirectory(Path("build"), "kcml-gutter-library-")
        val jar = directory.resolve("annotations.jar")
        val libraryUrl = "jar://${jar.toAbsolutePath()}!/"
        try {
            JarOutputStream(jar.outputStream()).use { output ->
                for (name in listOf("DirectMixin", "IndirectMixin")) {
                    val classFile = "dev/karmakrafts/kcml/api/mixin/$name.class"
                    output.putNextEntry(JarEntry(classFile))
                    javaClass.classLoader.getResourceAsStream(classFile)!!.use { it.copyTo(output) }
                    output.closeEntry()
                }
            }
            WriteCommandAction.runWriteCommandAction(project) {
                ModuleRootModificationUtil.addModuleLibrary(myFixture.module, libraryUrl)
            }
            assertMarkers("@DirectMixin(Any::class) class <caret>Direct", 1)
            assertMarkers("@IndirectMixin(\"example.Target\") class <caret>Indirect", 1)
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

    private fun addAnnotations() {
        annotations = myFixture.addFileToProject(
            "dev/karmakrafts/kcml/api/mixin/Mixins.kt", """
                package dev.karmakrafts.kcml.api.mixin
                annotation class DirectMixin(val target: kotlin.reflect.KClass<*>)
                annotation class IndirectMixin(val target: String)
            """.trimIndent()
        )
    }

    private fun assertMarkers(text: String, expected: Int) {
        myFixture.configureByText(
            "Mixin.kt", "import dev.karmakrafts.kcml.api.mixin.*\n$text"
        )
        val markers = myFixture.findAllGutters().filter { it.tooltipText == "KCML mixin" }
        assertEquals(expected, markers.size)
        if (expected == 0) return
        val icon =
            IconLoader.getIcon("/icons/dev/karmakrafts/kcml/intellij/mixin.svg", MixinLineMarkerProvider::class.java)
        assertEquals(icon, markers.single().icon)
        assertTrue(myFixture.findGuttersAtCaret().any { it.tooltipText == "KCML mixin" })
    }
}