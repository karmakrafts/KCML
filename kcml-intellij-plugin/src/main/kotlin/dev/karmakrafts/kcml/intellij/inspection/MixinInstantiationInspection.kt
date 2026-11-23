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
import dev.karmakrafts.kcml.intellij.util.isMixin
import org.jetbrains.annotations.NonNls
import org.jetbrains.kotlin.psi.KtClassOrObject

class MixinInstantiationInspection : LocalInspectionTool() {
    override fun getShortName(): @NonNls String = "KcmlMixinInstantiation"

    override fun getID(): @NonNls String = "KCML_MIXIN_INSTANTIATION"

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor =
        ConstructorUsageVisitor(holder, "Mixin classes must not be instantiated") { target ->
            when (target) {
                is KtClassOrObject -> target.annotationEntries.any { it.isMixin() }
                is PsiClass -> target.isMixin()
                else -> false
            }
        }
}