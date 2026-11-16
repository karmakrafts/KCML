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

package dev.karmakrafts.kcml.intellij.inspection

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.util.InheritanceUtil
import dev.karmakrafts.kcml.intellij.util.TypeNames
import dev.karmakrafts.kcml.intellij.util.isMixin
import org.jetbrains.annotations.NonNls
import org.jetbrains.kotlin.asJava.toLightClass
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtTypeAlias
import org.jetbrains.kotlin.psi.KtUserType
import org.jetbrains.kotlin.psi.KtVisitorVoid

class ThisAwareImplementationInspection : LocalInspectionTool() {
    override fun getID(): @NonNls String = "KCML_THIS_AWARE_IMPLEMENTATION"

    override fun getShortName(): @NonNls String = "KcmlThisAwareImplementation"

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor =
        object : KtVisitorVoid() {
            override fun visitClassOrObject(classOrObject: KtClassOrObject) {
                val psiClass = classOrObject.toLightClass() ?: return
                if (psiClass.isInterface || psiClass.isAnnotationType) return
                if (classOrObject.annotationEntries.any { it.isMixin() }) return
                for (entry in classOrObject.superTypeListEntries) {
                    val type = entry.typeReference?.typeElement as? KtUserType ?: continue
                    val reference = type.referenceExpression ?: continue
                    if (!reference.references.any {
                            it.resolve()?.let { target -> isThisAware(target, mutableSetOf()) } == true
                        }) continue
                    holder.registerProblem(
                        type,
                        "ThisAware implementations must be mixin classes",
                        ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
                        TextRange(reference.startOffsetInParent, type.textLength)
                    )
                }
            }
        }

    private fun isThisAware(target: PsiElement, visited: MutableSet<PsiElement>): Boolean {
        if (!visited.add(target)) return false
        return when (target) {
            is KtClassOrObject -> target.toLightClass()?.let { isThisAware(it, visited) } == true
            is PsiClass -> target.qualifiedName == TypeNames.THIS_AWARE || InheritanceUtil.isInheritor(
                target,
                TypeNames.THIS_AWARE
            )

            is KtTypeAlias -> {
                val reference = (target.getTypeReference()?.typeElement as? KtUserType)?.referenceExpression
                reference?.references?.any {
                    it.resolve()?.let { resolved -> isThisAware(resolved, visited) } == true
                } == true
            }

            else -> false
        }
    }
}