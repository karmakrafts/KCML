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
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.PsiMember
import com.intellij.psi.PsiModifierListOwner
import com.intellij.psi.util.PsiTreeUtil
import dev.karmakrafts.kcml.intellij.util.TypeNames
import dev.karmakrafts.kcml.intellij.util.isMixin
import dev.karmakrafts.kcml.intellij.util.qualifiedName
import org.jetbrains.annotations.NonNls
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtCallableReferenceExpression
import org.jetbrains.kotlin.psi.KtClassBody
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtConstructor
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.psi.KtImportDirective
import org.jetbrains.kotlin.psi.KtModifierListOwner
import org.jetbrains.kotlin.psi.KtQualifiedExpression
import org.jetbrains.kotlin.psi.KtSimpleNameExpression
import org.jetbrains.kotlin.psi.KtTypeAlias
import org.jetbrains.kotlin.psi.KtUserType
import org.jetbrains.kotlin.psi.KtVisitorVoid

class OnlyInsideMixinUsageInspection : LocalInspectionTool() {
    override fun getID(): @NonNls String = "KCML_ONLY_INSIDE_MIXIN"

    override fun getShortName(): @NonNls String = "KcmlOnlyInsideMixinUsage"

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor =
        object : KtVisitorVoid() {
            override fun visitSimpleNameExpression(expression: KtSimpleNameExpression) {
                if (PsiTreeUtil.getParentOfType(expression, KtImportDirective::class.java) != null) return
                val target = expression.references.firstNotNullOfOrNull { it.resolve() } ?: return
                if (!isRestricted(target, mutableSetOf())) return
                val usageReceiver = when (val parent = expression.parent) {
                    is KtCallableReferenceExpression -> parent.receiverExpression.takeIf {
                        parent.callableReference == expression
                    }

                    else -> {
                        val call = (parent as? KtCallExpression) ?: expression
                        (call.parent as? KtQualifiedExpression)?.takeIf { it.selectorExpression == call }?.receiverExpression
                    }
                }
                val receiverReference = (usageReceiver as? KtQualifiedExpression)?.selectorExpression ?: usageReceiver
                if (receiverReference?.references?.any {
                        it.resolve()?.let { resolved -> isRestricted(resolved, mutableSetOf()) } == true
                    } == true) return
                val containingClass = PsiTreeUtil.getParentOfType(expression, KtClassOrObject::class.java)
                if (containingClass?.annotationEntries?.any { it.isMixin() } == true) return
                val parent = expression.parent
                val receiver =
                    (parent as? KtQualifiedExpression)?.takeIf { it.selectorExpression == expression } ?: expression
                val qualifiedCall = (receiver.parent as? KtQualifiedExpression)?.takeIf {
                    it.receiverExpression == receiver && it.selectorExpression is KtCallExpression
                }
                val call = (parent as? KtCallExpression)?.takeIf { it.calleeExpression == expression }
                val owner = when (target) {
                    is PsiMember -> target.containingClass
                    is KtDeclaration -> (target.parent as? KtClassBody)?.parent
                    else -> null
                }
                val qualifiedMemberCall = (call?.parent as? KtQualifiedExpression)?.takeIf {
                    it.selectorExpression == call && owner?.let { owner -> isRestricted(owner, mutableSetOf()) } == true
                }
                val highlight = qualifiedMemberCall ?: call ?: qualifiedCall
                ?: (parent as? KtUserType)?.takeIf { it.referenceExpression == expression } ?: expression
                val startOffset = if (highlight == qualifiedMemberCall) highlight.textRange.startOffset
                else expression.textRange.startOffset
                holder.registerProblem(
                    highlight,
                    "API marked @OnlyInsideMixin must only be used in mixin classes",
                    ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
                    TextRange(startOffset - highlight.textRange.startOffset, highlight.textLength)
                )
            }
        }

    private fun isRestricted(target: PsiElement, visited: MutableSet<PsiElement>): Boolean {
        if (!visited.add(target)) return false
        if (target is KtModifierListOwner && target.annotationEntries.any {
                it.qualifiedName() == TypeNames.ONLY_INSIDE_MIXIN
            }) return true
        if (target is PsiModifierListOwner && target.hasAnnotation(TypeNames.ONLY_INSIDE_MIXIN)) return true
        return when (target) {
            is KtConstructor<*> -> isRestricted(target.getContainingClassOrObject(), visited)
            is KtTypeAlias -> {
                val reference = (target.getTypeReference()?.typeElement as? KtUserType)?.referenceExpression
                reference?.references?.any {
                    it.resolve()?.let { resolved -> isRestricted(resolved, visited) } == true
                } == true
            }

            is PsiMember -> target.containingClass?.let { isRestricted(it, visited) } == true
            is KtDeclaration -> ((target.parent as? KtClassBody)?.parent)?.let {
                isRestricted(it, visited)
            } == true

            else -> false
        }
    }
}