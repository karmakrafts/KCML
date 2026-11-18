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
import org.jetbrains.kotlin.psi.KtAnnotationEntry

internal data class InjectTargetString( // @formatter:off
    val annotation: KtAnnotationEntry,
    val argumentName: String,
    val content: StringTemplateContent
) { // @formatter:on
    companion object {
        private const val NAME_PARAMETER: String = "name"
        private const val DESCRIPTOR_PARAMETER: String = "descriptor"

        fun find(position: PsiElement, offset: Int): InjectTargetString? {
            (val annotation, val name, val expression) = position.annotationStringArgument(
                TypeNames.INJECT, NAME_PARAMETER, DESCRIPTOR_PARAMETER
            ) ?: return null
            val content = expression.contentAt(offset) ?: return null
            return InjectTargetString(annotation, name, content)
        }

        fun find(file: PsiFile, offset: Int): InjectTargetString? =
            file.findElementAt(offset)?.let { find(it, offset) } ?: file.findElementAt((offset - 1).coerceAtLeast(0))
                ?.let { find(it, offset) }

        fun isTargetStringAt(file: PsiFile, offset: Int): Boolean = find(file, offset) != null
    }
}