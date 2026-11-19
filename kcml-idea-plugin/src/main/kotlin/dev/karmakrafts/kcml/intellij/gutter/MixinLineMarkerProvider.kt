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
import com.intellij.psi.PsiElement
import com.intellij.util.PsiNavigateUtil
import dev.karmakrafts.kcml.intellij.icon.Icons
import dev.karmakrafts.kcml.intellij.util.isMixin
import dev.karmakrafts.kcml.intellij.util.resolveMixinTarget
import org.jetbrains.kotlin.psi.KtClassOrObject

class MixinLineMarkerProvider : LineMarkerProvider {
    override fun getLineMarkerInfo(element: PsiElement): LineMarkerInfo<*>? = null

    override fun collectSlowLineMarkers(elements: List<PsiElement>, result: MutableCollection<in LineMarkerInfo<*>>) {
        for (element in elements) {
            val declaration = element.parent as? KtClassOrObject ?: continue
            if (declaration.nameIdentifier != element) continue
            val annotations = declaration.annotationEntries.filter { it.isMixin() }
            if (annotations.isEmpty()) continue
            val target = annotations.firstNotNullOfOrNull { it.resolveMixinTarget() }
            val navigationHandler = if (target == null) null
            else GutterIconNavigationHandler<PsiElement> { _, source ->
                val mixin = source.parent as? KtClassOrObject ?: return@GutterIconNavigationHandler
                val current = mixin.annotationEntries.firstNotNullOfOrNull { it.resolveMixinTarget() }
                    ?: return@GutterIconNavigationHandler
                PsiNavigateUtil.navigate(current.navigationElement, true)
            }
            result.add(
                NavigationLineMarkerInfo(
                    element, Icons.mixin, "KCML mixin", navigationHandler
                )
            )
        }
    }
}