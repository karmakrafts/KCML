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

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import dev.karmakrafts.kcml.intellij.util.StringTemplateContent
import dev.karmakrafts.kcml.intellij.util.TypeNames
import dev.karmakrafts.kcml.intellij.util.annotationStringArgument
import dev.karmakrafts.kcml.intellij.util.contentAt

internal object IndirectMixinTargetString {
    fun find(position: PsiElement, offset: Int): StringTemplateContent? =
        position.annotationStringArgument(TypeNames.INDIRECT_MIXIN, "target")?.expression?.contentAt(offset)

    fun isTargetString(position: PsiElement): Boolean =
        position.annotationStringArgument(TypeNames.INDIRECT_MIXIN, "target") != null

    fun isTargetStringAt(file: PsiFile, offset: Int): Boolean =
        file.findElementAt(offset)?.let(::isTargetString) == true
}

