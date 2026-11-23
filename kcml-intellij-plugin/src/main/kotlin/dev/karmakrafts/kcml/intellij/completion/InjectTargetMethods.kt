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

package dev.karmakrafts.kcml.intellij.completion

import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.IndexNotReadyException
import com.intellij.psi.util.PsiTreeUtil
import dev.karmakrafts.kcml.intellij.util.ClassIndex
import dev.karmakrafts.kcml.intellij.util.JvmMethod
import dev.karmakrafts.kcml.intellij.util.TypeNames
import dev.karmakrafts.kcml.intellij.util.asPsiClass
import dev.karmakrafts.kcml.intellij.util.qualifiedName
import dev.karmakrafts.kcml.intellij.util.resolveMixinTarget
import org.jetbrains.kotlin.psi.KtAnnotationEntry
import org.jetbrains.kotlin.psi.KtClassOrObject

internal typealias InjectTargetMethod = JvmMethod

internal object InjectTargetMethods {
    fun find(annotation: KtAnnotationEntry): List<InjectTargetMethod> {
        ProgressManager.checkCanceled()
        return try {
            if (annotation.qualifiedName() != TypeNames.INJECT) return emptyList()
            val mixin = PsiTreeUtil.getParentOfType(annotation, KtClassOrObject::class.java) ?: return emptyList()
            val target = mixin.annotationEntries.firstNotNullOfOrNull {
                ProgressManager.checkCanceled()
                it.resolveMixinTarget()?.asPsiClass()
            } ?: return emptyList()
            ClassIndex.findMethods(target)
        } catch (_: IndexNotReadyException) {
            emptyList()
        }
    }
}