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
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.util.InheritanceUtil
import dev.karmakrafts.kcml.intellij.util.TypeNames
import dev.karmakrafts.kcml.intellij.util.lightClass
import dev.karmakrafts.kcml.intellij.util.qualifiedName
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtVisitorVoid

class PluginDeclarationInspection : LocalInspectionTool() {
    override fun getShortName(): String = "KcmlPluginDeclaration"

    override fun getID(): String = "KCML_PLUGIN_PARTIAL_DECLARATION"

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor =
        object : KtVisitorVoid() {
            override fun visitClassOrObject(classOrObject: KtClassOrObject) {
                val name = classOrObject.nameIdentifier ?: return
                val psiClass = classOrObject.lightClass() ?: return
                if (psiClass.isInterface || psiClass.isAnnotationType) return
                val annotated = classOrObject.annotationEntries.any { it.qualifiedName() == TypeNames.PLUGIN }
                val implementsPlugin = InheritanceUtil.isInheritor(psiClass, TypeNames.COMPILER_PLUGIN)
                val message = when {
                    annotated && !implementsPlugin -> "Classes annotated with @Plugin must implement CompilerPlugin"
                    !annotated && implementsPlugin -> "CompilerPlugin implementations must be annotated with @Plugin"
                    else -> return
                }
                holder.registerProblem(name, message, ProblemHighlightType.WARNING)
            }
        }
}