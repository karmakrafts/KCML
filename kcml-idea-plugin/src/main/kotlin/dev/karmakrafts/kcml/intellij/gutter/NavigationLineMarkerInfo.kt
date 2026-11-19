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

import com.intellij.codeInsight.daemon.GutterIconNavigationHandler
import com.intellij.codeInsight.daemon.LineMarkerInfo
import com.intellij.openapi.editor.markup.GutterIconRenderer
import com.intellij.psi.PsiElement
import javax.swing.Icon

internal class NavigationLineMarkerInfo(
    element: PsiElement, icon: Icon, tooltip: String, navigationHandler: GutterIconNavigationHandler<PsiElement>?
) : LineMarkerInfo<PsiElement>(
    element,
    element.textRange,
    icon,
    { tooltip },
    navigationHandler,
    GutterIconRenderer.Alignment.LEFT,
    { tooltip }) {
    override fun createGutterRenderer(): GutterIconRenderer = object : LineMarkerGutterIconRenderer<PsiElement>(this) {
        override fun looksTheSameAs(renderer: LineMarkerGutterIconRenderer<*>): Boolean =
            super.looksTheSameAs(renderer) && isNavigateAction == renderer.isNavigateAction
    }
}