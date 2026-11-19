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

import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMethod
import dev.karmakrafts.kcml.intellij.util.referencedType
import dev.karmakrafts.kcml.intellij.util.resolveReference
import org.jetbrains.kotlin.psi.KtCallElement
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtCallableReferenceExpression
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtConstructor
import org.jetbrains.kotlin.psi.KtConstructorCalleeExpression
import org.jetbrains.kotlin.psi.KtConstructorDelegationCall
import org.jetbrains.kotlin.psi.KtSuperTypeCallEntry
import org.jetbrains.kotlin.psi.KtTypeAlias
import org.jetbrains.kotlin.psi.KtVisitorVoid

internal class ConstructorUsageVisitor(
    private val holder: ProblemsHolder,
    private val message: String,
    private val isRestrictedClass: (PsiElement) -> Boolean
) : KtVisitorVoid() {
    override fun visitCallExpression(expression: KtCallExpression) = checkCall(expression)

    override fun visitSuperTypeCallEntry(call: KtSuperTypeCallEntry) = checkCall(call)

    override fun visitConstructorDelegationCall(call: KtConstructorDelegationCall) = checkCall(call)

    override fun visitCallableReferenceExpression(expression: KtCallableReferenceExpression) {
        checkReference(expression.callableReference)
    }

    private fun checkCall(call: KtCallElement) {
        val callee = call.calleeExpression ?: return
        checkReference((callee as? KtConstructorCalleeExpression)?.constructorReferenceExpression ?: callee, call)
    }

    private fun checkReference(reference: PsiElement, highlight: PsiElement = reference) {
        val target = reference.resolveReference() ?: return
        if (!isRestrictedConstructor(target, mutableSetOf())) return
        holder.registerProblem(
            highlight, message, ProblemHighlightType.GENERIC_ERROR_OR_WARNING, TextRange(0, highlight.textLength)
        )
    }

    private fun isRestrictedConstructor(target: PsiElement, visited: MutableSet<PsiElement>): Boolean {
        if (!visited.add(target)) return false
        return when (target) {
            is KtConstructor<*> -> isRestrictedConstructor(target.getContainingClassOrObject(), visited)
            is KtClassOrObject, is PsiClass -> isRestrictedClass(target)
            is PsiMethod -> {
                if (!target.isConstructor) return false
                val containingClass = target.containingClass ?: return false
                isRestrictedConstructor(containingClass, visited)
            }

            is KtTypeAlias -> target.referencedType()?.referenceExpression?.references?.any {
                val resolved = it.resolve()
                resolved != null && isRestrictedConstructor(resolved, visited)
            } == true

            else -> false
        }
    }
}