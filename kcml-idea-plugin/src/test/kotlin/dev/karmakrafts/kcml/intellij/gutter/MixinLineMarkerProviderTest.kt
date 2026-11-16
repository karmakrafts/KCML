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
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.util.IconLoader
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import dev.karmakrafts.kcml.intellij.testutil.AnnotationFixtureTestCase
import dev.karmakrafts.kcml.intellij.testutil.withAnnotationLibrary
import org.jetbrains.kotlin.psi.KtClassOrObject

class MixinLineMarkerProviderTest : AnnotationFixtureTestCase() {

    fun testIndirectMixin() {
        addAnnotations()
        assertMarkers("@IndirectMixin(\"example.Target\") class <caret>Example", 1)
    }

    fun testDirectMixin() {
        addAnnotations()
        assertMarkers("@DirectMixin(Any::class) class <caret>Example", 1)
    }

    fun testDirectMixinNavigatesToKotlinTarget() {
        addAnnotations()
        val target = myFixture.addFileToProject("example/Target.kt", "package example\nclass Target")
        assertNavigation("@DirectMixin(example.Target::class) class <caret>Example", target, "Target")
    }

    fun testAliasedDirectMixinNavigatesToJavaNestedTarget() {
        addAnnotations()
        val target = myFixture.addClass("package example; public class Outer { public static class Nested {} }")
        assertNavigation(
            """
                import dev.karmakrafts.kcml.api.mixin.DirectMixin as Mixin
                import example.Outer.Nested as Target
                @Mixin(priority = 1, target = Target::class) class <caret>Example
            """.trimIndent(), target.containingFile, "Nested"
        )
    }

    fun testIndirectMixinNavigatesToKotlinTarget() {
        addAnnotations()
        val target = myFixture.addFileToProject("example/Target.kt", "package example\nprivate class Target")
        assertNavigation("@IndirectMixin(\"example.Target\") class <caret>Example", target, "Target")
    }

    fun testAliasedIndirectMixinNavigatesToKotlinNestedTarget() {
        addAnnotations()
        val target = myFixture.addFileToProject(
            "example/Outer.kt", "package example\nclass Outer { private class Nested }"
        )
        assertNavigation(
            """
                import dev.karmakrafts.kcml.api.mixin.IndirectMixin as Mixin
                @Mixin(priority = 1, target = "example/Outer\${'$'}Nested") class <caret>Example
            """.trimIndent(), target, "Nested"
        )
    }

    fun testIndirectMixinNavigatesToJavaNestedTarget() {
        addAnnotations()
        val target = myFixture.addClass("package example; public class Outer { private static class Nested {} }")
        assertNavigation(
            "@IndirectMixin(\"example.Outer\\${'$'}Nested\") class <caret>Example", target.containingFile, "Nested"
        )
    }

    fun testIndirectMixinRawStringNavigatesToTarget() {
        addAnnotations()
        val target = myFixture.addFileToProject("example/Target.kt", "package example\nclass Target")
        assertNavigation("@IndirectMixin(\"\"\"example.Target\"\"\") class <caret>Example", target, "Target")
    }

    fun testIndirectMixinRawStringNavigatesToNestedObject() {
        addAnnotations()
        val target = myFixture.addFileToProject(
            "example/Outer.kt", "package example\nclass Outer { object Target }"
        )
        assertNavigation(
            "@IndirectMixin(\"\"\"example/Outer${'$'}{'${'$'}'}Target\"\"\") class <caret>Example", target, "Target"
        )
    }

    fun testIndirectMixinNavigationBecomesAvailableAfterEditingTarget() {
        addAnnotations()
        myFixture.addFileToProject("example/Target.kt", "package example\nobject Target")
        val unresolved = configureMarker("@IndirectMixin(\"example.Missing\") class <caret>Example")
        val unresolvedRenderer = unresolved.createGutterRenderer()!!
        assertNull(unresolvedRenderer.clickAction)

        replaceTarget("example.Missing", "example.Target")
        val resolved = collectMarker()
        val resolvedRenderer = resolved.createGutterRenderer()!!
        assertNotNull(resolvedRenderer.clickAction)
        assertFalse("The gutter must refresh when navigation becomes available", unresolvedRenderer == resolvedRenderer)

        replaceTarget("example.Target", "example.Missing")
        val missingRenderer = collectMarker().createGutterRenderer()!!
        assertNull(missingRenderer.clickAction)
        assertFalse("The gutter must refresh when navigation becomes unavailable", resolvedRenderer == missingRenderer)
    }

    fun testIndirectMixinNavigationReadsCurrentTarget() {
        addAnnotations()
        myFixture.addFileToProject("example/First.kt", "package example\nclass First")
        val target = myFixture.addFileToProject("example/Other.kt", "package example\nobject Other")
        val marker = configureMarker("@IndirectMixin(\"example.First\") class <caret>Example")
        replaceTarget("example.First", "example.Other")
        marker.navigationHandler!!.navigate(null, marker.element!!)
        assertEquals(target.virtualFile, FileEditorManager.getInstance(project).selectedFiles.single())
    }

    fun testUnresolvedTargetHasNoNavigation() {
        addAnnotations()
        val marker = configureMarker("@IndirectMixin(\"missing.Target\") class <caret>Example")
        assertNull(marker.navigationHandler)
    }

    fun testUnresolvedDirectTargetHasNoNavigation() {
        addAnnotations()
        val marker = configureMarker("@DirectMixin(Missing::class) class <caret>Example")
        assertNull(marker.navigationHandler)
    }

    fun testInterpolatedTargetHasNoNavigation() {
        addAnnotations()
        val marker = configureMarker("@IndirectMixin(\"example.${'$'}missing\") class <caret>Example")
        assertNull(marker.navigationHandler)
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
        myFixture.withAnnotationLibrary(
            "dev.karmakrafts.kcml.api.mixin.DirectMixin", "dev.karmakrafts.kcml.api.mixin.IndirectMixin"
        ) {
            assertMarkers("@DirectMixin(Any::class) class <caret>Direct", 1)
            assertMarkers("@IndirectMixin(\"example.Target\") class <caret>Indirect", 1)
            val target = myFixture.addFileToProject("example/Target.kt", "package example\nclass Target")
            assertNavigation("@DirectMixin(example.Target::class) class <caret>Direct", target, "Target")
            assertNavigation("@IndirectMixin(\"example.Target\") class <caret>Indirect", target, "Target")
        }
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

    private fun configureMarker(text: String): LineMarkerInfo<PsiElement> {
        myFixture.configureByText("Mixin.kt", "import dev.karmakrafts.kcml.api.mixin.*\n$text")
        return collectMarker()
    }

    private fun replaceTarget(old: String, new: String) {
        val document = myFixture.editor.document
        WriteCommandAction.runWriteCommandAction(project) {
            val start = document.text.indexOf(old)
            document.replaceString(start, start + old.length, new)
            PsiDocumentManager.getInstance(project).commitDocument(document)
        }
    }

    private fun collectMarker(): LineMarkerInfo<PsiElement> {
        val declaration = PsiTreeUtil.getParentOfType(
            myFixture.file.findElementAt(myFixture.caretOffset), KtClassOrObject::class.java
        )!!
        val markers = mutableListOf<LineMarkerInfo<*>>()
        MixinLineMarkerProvider().collectSlowLineMarkers(listOf(declaration.nameIdentifier!!), markers)
        @Suppress("UNCHECKED_CAST") return markers.single() as LineMarkerInfo<PsiElement>
    }

    private fun assertNavigation(text: String, target: PsiFile, targetName: String) {
        val marker = configureMarker(text)
        val handler = marker.navigationHandler
        assertNotNull("Mixin marker should navigate to its target", handler)
        handler!!.navigate(null, marker.element!!)
        val editor = FileEditorManager.getInstance(project).selectedTextEditor!!
        assertEquals(target.virtualFile, FileEditorManager.getInstance(project).selectedFiles.single())
        assertEquals(
            targetName, editor.document.charsSequence.subSequence(
                editor.caretModel.offset, editor.caretModel.offset + targetName.length
            ).toString()
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