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

import com.intellij.codeInsight.daemon.LineMarkerInfo
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.util.IconLoader
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.testFramework.PsiTestUtil
import dev.karmakrafts.kcml.intellij.testutil.AnnotationFixtureTestCase
import dev.karmakrafts.kcml.intellij.testutil.assertMarkersByTooltip
import dev.karmakrafts.kcml.intellij.testutil.assertNavigatesToFile
import dev.karmakrafts.kcml.intellij.testutil.collectSingleMarker
import dev.karmakrafts.kcml.intellij.testutil.firstDescendant
import dev.karmakrafts.kcml.intellij.testutil.withAnnotationLibrary
import org.jetbrains.jps.model.java.JavaResourceRootType
import org.jetbrains.kotlin.psi.KtClassOrObject

class PluginLineMarkerProviderTest : AnnotationFixtureTestCase() {

    fun testNavigatesToMetadata() {
        addAnnotation()
        val target = addResource("META-INF/example.json")
        val marker = configureMarker("@Plugin(\"example\") class <caret>Example")
        assertNotNull(marker.createGutterRenderer()!!.clickAction)
        project.assertNavigatesToFile(marker, target)
    }

    fun testNamedIdOnAliasedAnnotationNavigatesToMetadata() {
        addAnnotation()
        val target = addResource("META-INF/example.json")
        val marker = configureMarker(
            "import dev.karmakrafts.kcml.api.plugin.Plugin as CompilerPlugin\n" + "@CompilerPlugin(id = \"example\") object <caret>Example"
        )
        project.assertNavigatesToFile(marker, target)
    }

    fun testMissingMetadataHasNoNavigation() {
        addAnnotation()
        val marker = configureMarker("@Plugin(\"example\") class <caret>Example")
        assertNull(marker.navigationHandler)
        assertNull(marker.createGutterRenderer()!!.clickAction)
    }

    fun testMetadataAtResourceRootHasNoNavigation() {
        addAnnotation()
        addResource("example.json")
        assertNull(configureMarker("@Plugin(\"example\") class <caret>Example").navigationHandler)
    }

    fun testMetadataInNestedMetaInfHasNoNavigation() {
        addAnnotation()
        addResource("nested/META-INF/example.json")
        assertNull(configureMarker("@Plugin(\"example\") class <caret>Example").navigationHandler)
    }

    fun testMetadataOutsideResourceRootHasNoNavigation() {
        addAnnotation()
        myFixture.addFileToProject("META-INF/example.json", "{}")
        assertNull(configureMarker("@Plugin(\"example\") class <caret>Example").navigationHandler)
    }

    fun testDifferentPluginMetadataHasNoNavigation() {
        addAnnotation()
        addResource("META-INF/other.json")
        assertNull(configureMarker("@Plugin(\"example\") class <caret>Example").navigationHandler)
    }

    fun testInterpolatedIdHasNoNavigation() {
        addAnnotation()
        addResource("META-INF/example.json")
        assertNull(configureMarker("@Plugin(\"${'$'}id\") class <caret>Example").navigationHandler)
    }

    fun testDirectoryWithMetadataNameHasNoNavigation() {
        addAnnotation()
        addResource("META-INF/example.json/child.json")
        assertNull(configureMarker("@Plugin(\"example\") class <caret>Example").navigationHandler)
    }

    fun testIdCannotEscapeMetaInf() {
        addAnnotation()
        addResource("example.json")
        assertNull(configureMarker("@Plugin(\"../example\") class <caret>Example").navigationHandler)
    }

    fun testProductionPluginCannotNavigateToTestResources() {
        addAnnotation()
        val target = myFixture.addFileToProject("testResources/META-INF/example.json", "{}")
        PsiTestUtil.addSourceRoot(module, target.virtualFile.parent.parent, JavaResourceRootType.TEST_RESOURCE)
        assertNull(configureMarker("@Plugin(\"example\") class <caret>Example").navigationHandler)
    }

    fun testTestPluginNavigatesToTestResources() {
        addAnnotation()
        val target = myFixture.addFileToProject("testResources/META-INF/example.json", "{}")
        PsiTestUtil.addSourceRoot(module, target.virtualFile.parent.parent, JavaResourceRootType.TEST_RESOURCE)
        val source = myFixture.addFileToProject(
            "testSources/Example.kt",
            "import dev.karmakrafts.kcml.api.plugin.Plugin\n@Plugin(\"example\") class Example"
        )
        PsiTestUtil.addSourceRoot(module, source.virtualFile.parent, true)
        myFixture.configureFromExistingVirtualFile(source.virtualFile)
        val marker = collectMarker()
        project.assertNavigatesToFile(marker, target)
    }

    fun testNavigationReadsCurrentId() {
        addAnnotation()
        addResource("META-INF/example.json")
        val target = myFixture.addFileToProject("resources/META-INF/other.json", "{}")
        val marker = configureMarker("@Plugin(\"example\") class <caret>Example")
        replaceId("example", "other")
        project.assertNavigatesToFile(marker, target)
    }

    fun testNavigationAvailabilityRefreshesAfterEditingId() {
        addAnnotation()
        addResource("META-INF/example.json")
        val missing = configureMarker("@Plugin(\"missing\") class <caret>Example").createGutterRenderer()!!
        assertNull(missing.clickAction)
        replaceId("missing", "example")
        val resolved = collectMarker().createGutterRenderer()!!
        assertNotNull(resolved.clickAction)
        assertFalse(missing == resolved)
        replaceId("example", "missing")
        val missingAgain = collectMarker().createGutterRenderer()!!
        assertNull(missingAgain.clickAction)
        assertFalse(resolved == missingAgain)
    }

    private fun addResource(path: String): PsiFile {
        val file = myFixture.addFileToProject("resources/$path", "{}")
        PsiTestUtil.addSourceRoot(module, myFixture.findFileInTempDir("resources"), JavaResourceRootType.RESOURCE)
        return file
    }

    private fun configureMarker(text: String): LineMarkerInfo<PsiElement> {
        myFixture.configureByText("Plugin.kt", "import dev.karmakrafts.kcml.api.plugin.*\n$text")
        return collectMarker()
    }

    private fun collectMarker(): LineMarkerInfo<PsiElement> =
        PluginLineMarkerProvider().collectSingleMarker(myFixture.file.firstDescendant<KtClassOrObject>())

    private fun replaceId(old: String, new: String) {
        WriteCommandAction.runWriteCommandAction(project) {
            val document = myFixture.editor.document
            val start = document.text.indexOf("\"$old\"") + 1
            document.replaceString(start, start + old.length, new)
        }
        PsiDocumentManager.getInstance(project).commitAllDocuments()
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
        myFixture.withAnnotationLibrary("dev.karmakrafts.kcml.api.plugin.Plugin") {
            assertMarkers("@Plugin(\"example\") class <caret>Example", 1)
        }
    }

    private fun addAnnotation() {
        addAnnotationSource(
            "dev/karmakrafts/kcml/api/plugin/Plugin.kt", """
                package dev.karmakrafts.kcml.api.plugin
                annotation class Plugin(val id: String)
            """.trimIndent()
        )
    }

    private fun assertMarkers(text: String, expected: Int) {
        myFixture.configureByText("Plugin.kt", "import dev.karmakrafts.kcml.api.plugin.*\n$text")
        myFixture.assertMarkersByTooltip(expected, "KCML plugin") { markers ->
            val icon = IconLoader.getIcon("/icons/dev/karmakrafts/kcml/intellij/kcml.svg", javaClass)
            assertTrue(markers.all { it.icon == icon })
        }
    }
}