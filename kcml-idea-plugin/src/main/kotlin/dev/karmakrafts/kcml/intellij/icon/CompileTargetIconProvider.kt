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

package dev.karmakrafts.kcml.intellij.icon

import com.intellij.ide.IconProvider
import com.intellij.openapi.project.DumbService
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiClassOwner
import com.intellij.psi.PsiElement
import com.intellij.psi.util.InheritanceUtil
import com.intellij.ui.LayeredIcon
import com.intellij.util.IconUtil
import dev.karmakrafts.kcml.intellij.util.TypeNames
import dev.karmakrafts.kcml.intellij.util.lightClass
import org.jetbrains.kotlin.asJava.classes.KtLightClass
import org.jetbrains.kotlin.idea.KotlinIcons
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtFile
import javax.swing.Icon

class CompileTargetIconProvider : IconProvider() {
    private val kotlinIcon: Icon by lazy {
        LayeredIcon(2).apply {
            setIcon(Icons.target, 0)
            setIcon(IconUtil.scale(KotlinIcons.SMALL_LOGO, null, 0.5f), 1, 8, 8)
        }
    }

    override fun getIcon(element: PsiElement, flags: Int): Icon? {
        if (!element.isValid || element.project.isDisposed || DumbService.isDumb(element.project)) return null
        val matches = when (element) {
            is KtClassOrObject -> element.lightClass()?.isCompileTarget() == true
            is PsiClass -> element.isCompileTarget()
            is KtFile -> element.declarations.filterIsInstance<KtClassOrObject>().any {
                it.lightClass()?.isCompileTarget() == true
            }

            is PsiClassOwner -> element.classes.any { it.isCompileTarget() }
            else -> false
        }
        if (!matches) return null
        return if (element is KtClassOrObject || element is KtFile || element is KtLightClass) kotlinIcon
        else Icons.target
    }

    private fun PsiClass.isCompileTarget(): Boolean =
        qualifiedName == TypeNames.COMPILE_TARGET || InheritanceUtil.isInheritor(this, TypeNames.COMPILE_TARGET)
}