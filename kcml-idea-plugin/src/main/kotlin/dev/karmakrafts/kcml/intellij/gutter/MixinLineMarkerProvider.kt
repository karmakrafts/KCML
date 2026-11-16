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
import com.intellij.codeInsight.daemon.LineMarkerProvider
import com.intellij.openapi.editor.markup.GutterIconRenderer
import com.intellij.openapi.util.IconLoader
import com.intellij.psi.PsiElement
import com.intellij.util.PsiNavigateUtil
import dev.karmakrafts.kcml.intellij.util.isMixin
import dev.karmakrafts.kcml.intellij.util.resolveMixinTarget
import org.jetbrains.kotlin.psi.KtClassOrObject
import javax.swing.Icon

class MixinLineMarkerProvider : LineMarkerProvider {
    private val icon: Icon = IconLoader.getIcon("/icons/dev/karmakrafts/kcml/intellij/mixin.svg", javaClass)

    override fun getLineMarkerInfo(element: PsiElement): LineMarkerInfo<*>? = null

    override fun collectSlowLineMarkers(elements: List<PsiElement>, result: MutableCollection<in LineMarkerInfo<*>>) {
        for (element in elements) {
            val declaration = element.parent as? KtClassOrObject ?: continue
            if (declaration.nameIdentifier != element) continue
            val annotations = declaration.annotationEntries.filter { it.isMixin() }
            if (annotations.isEmpty()) continue
            val target = annotations.firstNotNullOfOrNull { it.resolveMixinTarget() }
            result.add(object : LineMarkerInfo<PsiElement>(
                element,
                element.textRange,
                icon,
                { "KCML mixin" },
                target?.let {
                    GutterIconNavigationHandler { _, source ->
                        val current =
                            (source.parent as? KtClassOrObject)?.annotationEntries?.firstNotNullOfOrNull { it.resolveMixinTarget() }
                        current?.let { PsiNavigateUtil.navigate(it.navigationElement, true) }
                    }
                },
                GutterIconRenderer.Alignment.LEFT,
                { "KCML mixin" }) {
                override fun createGutterRenderer(): GutterIconRenderer =
                    object : LineMarkerGutterIconRenderer<PsiElement>(this) {
                        override fun looksTheSameAs(renderer: LineMarkerGutterIconRenderer<*>): Boolean =
                            super.looksTheSameAs(renderer) && isNavigateAction == renderer.isNavigateAction
                    }
            })
        }
    }
}