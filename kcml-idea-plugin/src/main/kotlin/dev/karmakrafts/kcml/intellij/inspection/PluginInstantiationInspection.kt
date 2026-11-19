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
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.util.InheritanceUtil
import dev.karmakrafts.kcml.intellij.util.TypeNames
import dev.karmakrafts.kcml.intellij.util.lightClass
import dev.karmakrafts.kcml.intellij.util.qualifiedName
import org.jetbrains.annotations.NonNls
import org.jetbrains.kotlin.psi.KtClassOrObject

class PluginInstantiationInspection : LocalInspectionTool() {
    override fun getID(): @NonNls String = "KCML_PLUGIN_INSTANTIATION"

    override fun getShortName(): @NonNls String = "KcmlPluginInstantiation"

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor =
        ConstructorUsageVisitor(holder, "Plugin classes must not be instantiated") { target ->
            when (target) {
                is KtClassOrObject -> {
                    if (target.annotationEntries.any { it.qualifiedName() == TypeNames.PLUGIN }) true
                    else {
                        val psiClass = target.lightClass()
                        psiClass != null && InheritanceUtil.isInheritor(psiClass, TypeNames.COMPILER_PLUGIN)
                    }
                }

                is PsiClass -> target.hasAnnotation(TypeNames.PLUGIN) || InheritanceUtil.isInheritor(
                    target, TypeNames.COMPILER_PLUGIN
                )

                else -> false
            }
        }
}