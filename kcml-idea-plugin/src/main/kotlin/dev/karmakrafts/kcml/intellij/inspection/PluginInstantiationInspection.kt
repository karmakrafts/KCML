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
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.PsiMethod
import com.intellij.psi.util.InheritanceUtil
import dev.karmakrafts.kcml.intellij.util.TypeNames
import dev.karmakrafts.kcml.intellij.util.qualifiedName
import org.jetbrains.annotations.NonNls
import org.jetbrains.kotlin.asJava.toLightClass
import org.jetbrains.kotlin.psi.KtCallElement
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtCallableReferenceExpression
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtConstructor
import org.jetbrains.kotlin.psi.KtConstructorCalleeExpression
import org.jetbrains.kotlin.psi.KtConstructorDelegationCall
import org.jetbrains.kotlin.psi.KtSuperTypeCallEntry
import org.jetbrains.kotlin.psi.KtTypeAlias
import org.jetbrains.kotlin.psi.KtUserType
import org.jetbrains.kotlin.psi.KtVisitorVoid

class PluginInstantiationInspection : LocalInspectionTool() {
    override fun getID(): @NonNls String = "KCML_PLUGIN_INSTANTIATION"

    override fun getShortName(): @NonNls String = "KcmlPluginInstantiation"

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor =
        object : KtVisitorVoid() {
            override fun visitCallExpression(expression: KtCallExpression) = checkCall(expression)

            override fun visitSuperTypeCallEntry(call: KtSuperTypeCallEntry) = checkCall(call)

            override fun visitConstructorDelegationCall(call: KtConstructorDelegationCall) = checkCall(call)

            override fun visitCallableReferenceExpression(expression: KtCallableReferenceExpression) {
                checkReference(expression.callableReference)
            }

            private fun checkCall(call: KtCallElement) {
                val callee = call.calleeExpression ?: return
                checkReference((callee as? KtConstructorCalleeExpression)?.constructorReferenceExpression ?: callee)
            }

            private fun checkReference(reference: PsiElement) {
                val target = reference.references.firstNotNullOfOrNull { it.resolve() } ?: return
                if (!isPluginConstructor(target, mutableSetOf())) return
                holder.registerProblem(
                    reference, "Plugin classes must not be instantiated", ProblemHighlightType.GENERIC_ERROR_OR_WARNING
                )
            }
        }

    private fun isPluginConstructor(target: PsiElement, visited: MutableSet<PsiElement>): Boolean {
        if (!visited.add(target)) return false
        return when (target) {
            is KtConstructor<*> -> isPluginConstructor(target.getContainingClassOrObject(), visited)
            is KtClassOrObject -> target.annotationEntries.any { it.qualifiedName() == TypeNames.PLUGIN } || target.toLightClass()
                ?.let { InheritanceUtil.isInheritor(it, TypeNames.COMPILER_PLUGIN) } == true

            is PsiMethod -> target.isConstructor && target.containingClass?.let {
                isPluginConstructor(it, visited)
            } == true

            is PsiClass -> target.hasAnnotation(TypeNames.PLUGIN) || InheritanceUtil.isInheritor(
                target,
                TypeNames.COMPILER_PLUGIN
            )

            is KtTypeAlias -> {
                val reference = (target.getTypeReference()?.typeElement as? KtUserType)?.referenceExpression
                reference?.references?.any {
                    it.resolve()?.let { resolved -> isPluginConstructor(resolved, visited) } == true
                } == true
            }

            else -> false
        }
    }
}