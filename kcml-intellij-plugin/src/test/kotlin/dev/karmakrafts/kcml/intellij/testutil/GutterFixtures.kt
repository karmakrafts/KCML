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

package dev.karmakrafts.kcml.intellij.testutil

import com.intellij.codeInsight.daemon.GutterMark
import com.intellij.codeInsight.daemon.ImplicitUsageProvider
import com.intellij.codeInsight.daemon.LineMarkerInfo
import com.intellij.codeInsight.daemon.LineMarkerProvider
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertFalse
import junit.framework.TestCase.assertTrue
import org.jetbrains.kotlin.psi.KtNamedDeclaration
import javax.swing.Icon

internal fun ImplicitUsageProvider.assertUsage(element: PsiElement, expected: Boolean) {
    assertEquals(expected, isImplicitUsage(element))
    assertFalse(isImplicitRead(element))
    assertFalse(isImplicitWrite(element))
}

internal inline fun <reified T : PsiElement> PsiElement.firstDescendant(): T =
    PsiTreeUtil.findChildOfType(this, T::class.java)!!

internal inline fun <reified T : KtNamedDeclaration> PsiElement.namedDescendant(name: String): T =
    PsiTreeUtil.findChildrenOfType(this, T::class.java).single { it.name == name }

internal inline fun <reified T : KtNamedDeclaration> JavaCodeInsightTestFixture.declarationAtCaret(): T =
    PsiTreeUtil.getParentOfType(file.findElementAt(caretOffset), T::class.java)!!

internal fun LineMarkerProvider.collectSingleMarker(declaration: KtNamedDeclaration): LineMarkerInfo<*> {
    val markers = mutableListOf<LineMarkerInfo<*>>()
    collectSlowLineMarkers(listOf(declaration.nameIdentifier!!), markers)
    return markers.single()
}

internal fun <T : PsiElement> LineMarkerInfo<T>.navigate() {
    navigationHandler!!.navigate(null, element!!)
}

internal fun Project.assertNavigatesToFile(marker: LineMarkerInfo<*>, target: PsiFile) {
    marker.navigate()
    assertEquals(target.virtualFile, FileEditorManager.getInstance(this).selectedFiles.single())
}

internal fun JavaCodeInsightTestFixture.replaceTextAndCommit(old: String, new: String) {
    val document = editor.document
    WriteCommandAction.runWriteCommandAction(project) {
        val start = document.text.indexOf(old)
        document.replaceString(start, start + old.length, new)
        PsiDocumentManager.getInstance(project).commitDocument(document)
    }
}

internal fun JavaCodeInsightTestFixture.assertMarkersByIcon(expected: Int, icon: Icon, tooltip: String) {
    val markers = findAllGutters().filter { it.icon == icon }
    assertEquals(expected, markers.size)
    assertTrue(markers.all { it.tooltipText == tooltip })
    val caretMarkers = findGuttersAtCaret().filter { it.icon == icon }
    assertEquals(if (expected == 0) 0 else 1, caretMarkers.size)
}

internal fun JavaCodeInsightTestFixture.assertMarkersByTooltip(
    expected: Int, tooltip: String, assertIcons: (List<GutterMark>) -> Unit
) {
    val markers = findAllGutters().filter { it.tooltipText == tooltip }
    assertEquals(expected, markers.size)
    if (expected == 0) return
    assertIcons(markers)
    assertTrue(findGuttersAtCaret().any { it.tooltipText == tooltip })
}