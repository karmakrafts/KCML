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

package dev.karmakrafts.kcml.intellij.syntax

import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.Annotator
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.psi.PsiElement
import dev.karmakrafts.kcml.api.ExperimentalKcmlApi
import org.jetbrains.kotlin.psi.KtFile

@OptIn(ExperimentalKcmlApi::class)
internal class SyntaxAnnotator : Annotator {
    override fun annotate(element: PsiElement, holder: AnnotationHolder) {
        val file = element as? KtFile ?: return
        (val diagnostics, val highlighting, val inspections) = SyntaxAnalysis.result(file) ?: return
        for ((range, textAttributesKey) in highlighting) {
            holder.newSilentAnnotation(HighlightSeverity.INFORMATION)
                .range(range)
                .textAttributes(TextAttributesKey.createTextAttributesKey(textAttributesKey))
                .problemGroup(SyntaxProblemGroup)
                .create()
        }
        for ((val severity, val message, val range) in diagnostics) {
            // @formatter:off
            holder.newAnnotation(severity, message)
                .range(range)
                .problemGroup(SyntaxProblemGroup)
                .create()
            // @formatter:on
        }
        for ((val severity, val message, val range) in inspections) {
            holder.newAnnotation(severity, message).range(range).problemGroup(SyntaxProblemGroup).create()
        }
    }
}