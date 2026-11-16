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
import com.intellij.psi.PsiMember
import com.intellij.psi.util.PsiTreeUtil
import dev.karmakrafts.kcml.intellij.util.TypeNames
import dev.karmakrafts.kcml.intellij.util.isMixin
import org.jetbrains.annotations.NonNls
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtCallableDeclaration
import org.jetbrains.kotlin.psi.KtCallableReferenceExpression
import org.jetbrains.kotlin.psi.KtClassBody
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtImportDirective
import org.jetbrains.kotlin.psi.KtQualifiedExpression
import org.jetbrains.kotlin.psi.KtSimpleNameExpression
import org.jetbrains.kotlin.psi.KtTypeAlias
import org.jetbrains.kotlin.psi.KtUserType
import org.jetbrains.kotlin.psi.KtVisitorVoid

class MixinConstantTableUsageInspection : LocalInspectionTool() {
    override fun getID(): @NonNls String = "KCML_MIXIN_CONSTANT_TABLE_USAGE"

    override fun getShortName(): @NonNls String = "KcmlMixinConstantTableUsage"

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor =
        object : KtVisitorVoid() {
            override fun visitSimpleNameExpression(expression: KtSimpleNameExpression) {
                if (PsiTreeUtil.getParentOfType(expression, KtImportDirective::class.java) != null) return
                val target = expression.references.firstNotNullOfOrNull { it.resolve() } ?: return
                if (!isConstantTable(target, mutableSetOf())) {
                    val owner = when (target) {
                        is PsiMember -> target.containingClass
                        is KtCallableDeclaration -> (target.parent as? KtClassBody)?.parent
                        else -> null
                    } ?: return
                    if (!isConstantTable(owner, mutableSetOf())) return
                    val receiver = when (val parent = expression.parent) {
                        is KtCallableReferenceExpression -> parent.receiverExpression
                        else -> {
                            val call = (parent as? KtCallExpression) ?: expression
                            (call.parent as? KtQualifiedExpression)?.takeIf { it.selectorExpression == call }?.receiverExpression
                        }
                    }
                    val receiverReference = (receiver as? KtQualifiedExpression)?.selectorExpression ?: receiver
                    if (receiverReference?.references?.any {
                            it.resolve()?.let { resolved -> isConstantTable(resolved, mutableSetOf()) } == true
                        } == true) return
                }
                val containingClass = PsiTreeUtil.getParentOfType(expression, KtClassOrObject::class.java)
                if (containingClass?.annotationEntries?.any { it.isMixin() } == true) return
                val parent = expression.parent
                val receiver =
                    (parent as? KtQualifiedExpression)?.takeIf { it.selectorExpression == expression } ?: expression
                val qualifiedCall = (receiver.parent as? KtQualifiedExpression)?.takeIf {
                    it.receiverExpression == receiver && it.selectorExpression is KtCallExpression
                }
                val highlight =
                    (parent as? KtCallExpression)?.takeIf { it.calleeExpression == expression } ?: qualifiedCall
                    ?: expression
                holder.registerProblem(
                    highlight,
                    "ConstantTable must only be used in mixin classes",
                    ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
                    TextRange(expression.textRange.startOffset - highlight.textRange.startOffset, highlight.textLength)
                )
            }
        }

    private fun isConstantTable(target: PsiElement, visited: MutableSet<PsiElement>): Boolean {
        if (!visited.add(target)) return false
        return when (target) {
            is KtClassOrObject -> target.fqName?.asString() == TypeNames.CONSTANT_TABLE
            is PsiClass -> target.qualifiedName == TypeNames.CONSTANT_TABLE
            is KtTypeAlias -> {
                val reference = (target.getTypeReference()?.typeElement as? KtUserType)?.referenceExpression
                reference?.references?.any {
                    it.resolve()?.let { resolved -> isConstantTable(resolved, visited) } == true
                } == true
            }

            else -> false
        }
    }
}