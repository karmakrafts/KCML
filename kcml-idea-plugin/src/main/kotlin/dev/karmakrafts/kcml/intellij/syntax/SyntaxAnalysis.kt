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

import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiErrorElement
import com.intellij.psi.util.PsiModificationTracker
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.kotlin.analysis.api.components.KaDiagnosticCheckerFilter
import org.jetbrains.kotlin.analysis.api.components.collectDiagnostics
import org.jetbrains.kotlin.analysis.api.diagnostics.KaSeverity
import org.jetbrains.kotlin.analysis.api.session.analyze
import org.jetbrains.kotlin.psi.KtFile

internal object SyntaxAnalysis {
    data class Diagnostic( // @formatter:off
        val severity: HighlightSeverity,
        val message: String,
        val range: TextRange
    ) // @formatter:on

    data class Result( // @formatter:off
        val stamp: Long,
        val generation: Long,
        val diagnostics: List<Diagnostic>
    ) // @formatter:on

    private val key: Key<Result> = Key.create("kcml.syntax.diagnostics")
    private val analyzing: ThreadLocal<Boolean> = ThreadLocal.withInitial { false }

    fun result(file: KtFile): Result? {
        if (!file.isPhysical || analyzing.get() || DumbService.isDumb(file.project)) return null
        if (ApplicationManager.getApplication().isDispatchThread) return null
        val module = ModuleUtilCore.findModuleForPsiElement(file) ?: return null
        val service = file.project.service<SyntaxExtensionService>()
        val runtime = service.runtime(module) ?: return null
        if (!runtime.hasExtensions) return null
        val stamp = PsiModificationTracker.getInstance(file.project).modificationCount
        // Reuse diagnostics only while both PSI and the plugin session are unchanged.
        val cached = file.getUserData(key)
        if (cached != null && cached.stamp == stamp && cached.generation == service.modificationCount) return cached
        // Analysis of lowered PSI must not trigger syntax analysis recursively.
        analyzing.set(true)
        try {
            val generation = service.modificationCount
            val lowered = runtime.lowerIfOpen(file) ?: return null
            // Analyze desugared Kotlin, but report ranges in the original document.
            val offsets = SyntaxOffsetMap(file.text, lowered.text)
            val diagnostics = collectSyntaxDiagnostics(lowered, offsets) + collectSemanticDiagnostics(lowered, offsets)
            // Never replace diagnostics with results from a session invalidated during analysis.
            if (generation != service.modificationCount) return null
            val result = Result(stamp, generation, diagnostics)
            file.putUserData(key, result)
            return result
        }
        finally {
            analyzing.remove()
        }
    }

    private fun collectSyntaxDiagnostics(file: KtFile, offsets: SyntaxOffsetMap): List<Diagnostic> = buildList {
        for (error in PsiTreeUtil.collectElementsOfType(file, PsiErrorElement::class.java)) {
            add(Diagnostic(HighlightSeverity.ERROR, error.errorDescription, offsets.toSource(error.textRange)))
        }
    }

    private fun collectSemanticDiagnostics(file: KtFile, offsets: SyntaxOffsetMap): List<Diagnostic> = buildList {
        analyze(file) {
            for (diagnostic in file.collectDiagnostics(KaDiagnosticCheckerFilter.EXTENDED_AND_COMMON_CHECKERS)) {
                val severity = when (diagnostic.severity) {
                    KaSeverity.ERROR -> HighlightSeverity.ERROR
                    KaSeverity.WARNING -> HighlightSeverity.WARNING
                    KaSeverity.INFO -> HighlightSeverity.INFORMATION
                }
                for (range in diagnostic.textRanges) {
                    add(Diagnostic(severity, diagnostic.defaultMessage, offsets.toSource(range)))
                }
            }
        }
    }
}
