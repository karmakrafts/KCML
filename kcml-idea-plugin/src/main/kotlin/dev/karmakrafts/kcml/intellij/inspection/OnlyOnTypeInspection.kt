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
import com.intellij.openapi.progress.ProgressManager
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import dev.karmakrafts.kcml.intellij.util.TypeNames
import dev.karmakrafts.kcml.intellij.util.qualifiedName
import org.jetbrains.annotations.NonNls
import org.jetbrains.kotlin.psi.KtAnnotationEntry
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtFunctionType
import org.jetbrains.kotlin.psi.KtNullableType
import org.jetbrains.kotlin.psi.KtTreeVisitorVoid
import org.jetbrains.kotlin.psi.KtTypeAlias
import org.jetbrains.kotlin.psi.KtTypeElement
import org.jetbrains.kotlin.psi.KtTypeReference
import org.jetbrains.kotlin.psi.KtUserType
import org.jetbrains.kotlin.psi.KtVisitorVoid

class OnlyOnTypeInspection : LocalInspectionTool() {
    override fun getID(): @NonNls String = "KCML_ONLY_ON_TYPE"

    override fun getShortName(): @NonNls String = "KcmlOnlyOnType"

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor =
        object : KtVisitorVoid() {
            override fun visitTypeReference(typeReference: KtTypeReference) {
                if (typeReference.annotationEntries.isEmpty()) return
                val file = typeReference.containingFile as? KtFile ?: return
                val invalid = OnlyOnAnalysis.lookup(file, key) { invalidAnnotations(file) } ?: return
                for (annotation in typeReference.annotationEntries) {
                    if (annotation !in invalid) continue
                    holder.registerProblem(
                        annotation, "@OnlyOn is only allowed on function types", ProblemHighlightType.GENERIC_ERROR
                    )
                }
            }
        }

    private fun invalidAnnotations(file: KtFile): Set<KtAnnotationEntry> = buildSet {
        file.accept(object : KtTreeVisitorVoid() {
            override fun visitTypeReference(typeReference: KtTypeReference) {
                ProgressManager.checkCanceled()
                val annotations = typeReference.annotationEntries.filter { it.isOnlyOn() }
                if (annotations.isNotEmpty() && !isFunctionType(typeReference.typeElement, mutableSetOf())) {
                    addAll(annotations)
                }
                super.visitTypeReference(typeReference)
            }
        })
    }

    private fun KtAnnotationEntry.isOnlyOn(): Boolean {
        if (qualifiedName() == TypeNames.ONLY_ON) return true
        var target = calleeExpression?.constructorReferenceExpression?.references?.firstNotNullOfOrNull { it.resolve() }
        val visited = mutableSetOf<PsiElement>()
        while (target is KtTypeAlias && visited.add(target)) {
            val reference = (target.getTypeReference()?.typeElement as? KtUserType)?.referenceExpression ?: return false
            target = reference.references.firstNotNullOfOrNull { it.resolve() }
        }
        return when (target) {
            is KtClassOrObject -> target.fqName?.asString() == TypeNames.ONLY_ON
            is PsiClass -> target.qualifiedName == TypeNames.ONLY_ON
            else -> false
        }
    }

    private fun isFunctionType(type: KtTypeElement?, visited: MutableSet<PsiElement>): Boolean {
        if (type == null || !visited.add(type)) return false
        return when (type) {
            is KtFunctionType -> true
            is KtNullableType -> isFunctionType(type.innerType, visited)
            is KtUserType -> {
                val alias = type.referenceExpression?.references?.firstNotNullOfOrNull { it.resolve() } as? KtTypeAlias
                isFunctionType(alias?.getTypeReference()?.typeElement, visited)
            }

            else -> false
        }
    }

    companion object {
        private val key = OnlyOnAnalysis.key<Set<KtAnnotationEntry>>("kcml.onlyOn.types")
    }
}