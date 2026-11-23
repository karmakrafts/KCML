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
import com.intellij.openapi.project.IndexNotReadyException
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.PsiNavigateUtil
import dev.karmakrafts.kcml.intellij.icon.Icons
import dev.karmakrafts.kcml.intellij.util.ClassIndex
import dev.karmakrafts.kcml.intellij.util.TypeNames
import dev.karmakrafts.kcml.intellij.util.argument
import dev.karmakrafts.kcml.intellij.util.asPsiClass
import dev.karmakrafts.kcml.intellij.util.literalValue
import dev.karmakrafts.kcml.intellij.util.qualifiedName
import dev.karmakrafts.kcml.intellij.util.resolveMixinTarget
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtStringTemplateExpression

class InjectLineMarkerProvider : LineMarkerProvider {
    override fun getLineMarkerInfo(element: PsiElement): LineMarkerInfo<*>? = null

    override fun collectSlowLineMarkers(elements: List<PsiElement>, result: MutableCollection<in LineMarkerInfo<*>>) {
        for (element in elements) {
            val declaration = element.parent as? KtNamedFunction ?: continue
            if (declaration.nameIdentifier != element) continue
            if (declaration.annotationEntries.none { it.qualifiedName() == TypeNames.INJECT }) continue
            val target = declaration.resolveInjectionSite()
            val navigationHandler = if (target == null) null
            else GutterIconNavigationHandler<PsiElement> { _, source ->
                val function = source.parent as? KtNamedFunction ?: return@GutterIconNavigationHandler
                val current = function.resolveInjectionSite() ?: return@GutterIconNavigationHandler
                PsiNavigateUtil.navigate(current.navigationElement, true)
            }
            result.add(
                NavigationLineMarkerInfo(
                    element, Icons.injection, "KCML plugin", navigationHandler
                )
            )
        }
    }

    private fun KtNamedFunction.resolveInjectionSite(): PsiElement? {
        return try {
            val annotation = annotationEntries.firstOrNull { it.qualifiedName() == TypeNames.INJECT } ?: return null
            val name =
                (annotation.argument("name", 0)?.getArgumentExpression() as? KtStringTemplateExpression)?.literalValue()
                    ?: return null
            val descriptorArgument = annotation.argument("descriptor", 1)
            val descriptor = if (descriptorArgument == null) ""
            else {
                (descriptorArgument.getArgumentExpression() as? KtStringTemplateExpression)?.literalValue()
                    ?: return null
            }
            val mixin = PsiTreeUtil.getParentOfType(this, KtClassOrObject::class.java) ?: return null
            val target =
                mixin.annotationEntries.firstNotNullOfOrNull { it.resolveMixinTarget()?.asPsiClass() } ?: return null
            ClassIndex.findMethod(target, name, descriptor)
        } catch (_: IndexNotReadyException) {
            null
        }
    }
}