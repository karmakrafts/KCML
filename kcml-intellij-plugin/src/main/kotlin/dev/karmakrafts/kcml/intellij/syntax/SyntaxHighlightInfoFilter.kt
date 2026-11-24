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

import com.intellij.codeInsight.daemon.impl.HighlightInfo
import com.intellij.codeInsight.daemon.impl.HighlightInfoFilter
import com.intellij.codeInsight.daemon.impl.HighlightVisitor
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.psi.PsiFile
import dev.karmakrafts.kcml.api.ExperimentalKcmlApi
import dev.karmakrafts.kcml.intellij.syntax.runtime.SyntaxAnalysis
import org.jetbrains.kotlin.psi.KtFile

@OptIn(ExperimentalKcmlApi::class)
internal class SyntaxHighlightInfoFilter : HighlightInfoFilter {
    private val stackWalker: StackWalker = StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE)

    private fun isKotlinVisitor(type: Class<*>): Boolean =
        type.name.startsWith("org.jetbrains.kotlin.idea.") && HighlightVisitor::class.java.isAssignableFrom(type)

    // The daemon exposes producer identity only through internal APIs; keep unrelated inspections intact.
    @Suppress("JetBrainsInternalApiUsage")
    override fun accept(info: HighlightInfo, file: PsiFile?): Boolean {
        if (file !is KtFile || info.problemGroup === SyntaxProblemGroup) return true
        (val highlighting, val inspectionToolIds, val inspectionRules, val desugared) = SyntaxAnalysis.result(file)
            ?: return true
        if (info.isFromInspection) {
            if (info.inspectionToolId !in inspectionToolIds) return true
            return !desugared && inspectionRules.none {
                it.range.startOffset < info.endOffset && info.startOffset < it.range.endOffset
            }
        }
        if (info.severity < HighlightSeverity.WARNING && highlighting.none {
                it.range.startOffset < info.endOffset && info.startOffset < it.range.endOffset
            }) return true
        val producer = info.toolId
        if (producer != null) return producer !is Class<*> || !isKotlinVisitor(producer)
        // Modern daemon runners assign toolId after holder filters and bypass post-filters.
        // Inspect only the active Kotlin visitor, never diagnostic text or other plugins' output.
        return !stackWalker.walk { frames -> frames.anyMatch { isKotlinVisitor(it.declaringClass) } }
    }
}