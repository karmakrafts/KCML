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
import com.intellij.codeInsight.daemon.LineMarkerProvider
import com.intellij.openapi.editor.markup.GutterIconRenderer
import com.intellij.openapi.util.IconLoader
import com.intellij.psi.PsiElement
import dev.karmakrafts.kcml.intellij.util.TypeNames
import dev.karmakrafts.kcml.intellij.util.qualifiedName
import org.jetbrains.kotlin.psi.KtNamedFunction
import javax.swing.Icon

class InjectLineMarkerProvider : LineMarkerProvider {
    private val icon: Icon = IconLoader.getIcon("/icons/dev/karmakrafts/kcml/intellij/injection.svg", javaClass)

    override fun getLineMarkerInfo(element: PsiElement): LineMarkerInfo<*>? = null

    override fun collectSlowLineMarkers(elements: List<PsiElement>, result: MutableCollection<in LineMarkerInfo<*>>) {
        for (element in elements) {
            val declaration = element.parent as? KtNamedFunction ?: continue
            if (declaration.nameIdentifier != element) continue
            if (declaration.annotationEntries.none { it.qualifiedName() == TypeNames.INJECT }) continue
            result.add(
                LineMarkerInfo(
                    element,
                    element.textRange,
                    icon,
                    { "KCML plugin" },
                    null,
                    GutterIconRenderer.Alignment.LEFT,
                    { "KCML plugin" })
            )
        }
    }
}