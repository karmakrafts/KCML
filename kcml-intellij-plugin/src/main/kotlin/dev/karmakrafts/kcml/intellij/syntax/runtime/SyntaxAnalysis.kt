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

package dev.karmakrafts.kcml.intellij.syntax.runtime

import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiErrorElement
import com.intellij.psi.util.PsiModificationTracker
import com.intellij.psi.util.PsiTreeUtil
import dev.karmakrafts.kcml.api.ExperimentalKcmlApi
import dev.karmakrafts.kcml.api.extension.syntax.Highlighting
import dev.karmakrafts.kcml.api.extension.syntax.Inspection
import dev.karmakrafts.kcml.api.extension.syntax.InspectionRule
import dev.karmakrafts.kcml.api.extension.syntax.InspectionSeverity
import dev.karmakrafts.kcml.intellij.syntax.SyntaxExtensionService
import org.jetbrains.kotlin.analysis.api.components.KaDiagnosticCheckerFilter
import org.jetbrains.kotlin.analysis.api.components.collectDiagnostics
import org.jetbrains.kotlin.analysis.api.diagnostics.KaSeverity
import org.jetbrains.kotlin.analysis.api.session.analyze
import org.jetbrains.kotlin.psi.KtFile

@Suppress("KCML_TARGET_SPECIFIC_CODE") // In IDE-land, we don't really care about compile targets
@OptIn(ExperimentalKcmlApi::class)
internal object SyntaxAnalysis {
    data class Diagnostic( // @formatter:off
        val severity: HighlightSeverity,
        val message: String,
        val range: TextRange
    ) // @formatter:on

    data class Result( // @formatter:off
        val stamp: Long,
        val generation: Long,
        val diagnostics: List<Diagnostic>,
        val highlighting: List<Highlighting>,
        val inspections: List<SyntaxInspections.Problem>,
        val inspectionToolIds: Set<String>,
        val inspectionRules: List<InspectionRule>,
        val desugared: Boolean
    ) // @formatter:on

    private val key: Key<Result> = Key.create("kcml.syntax.diagnostics")
    private val analyzing: ThreadLocal<Boolean> = ThreadLocal.withInitial { false }

    fun cachedResult(file: KtFile): Result? {
        val cached = file.getUserData(key) ?: return null
        if (file.project.isDisposed) return null
        val stamp = PsiModificationTracker.getInstance(file.project).modificationCount
        val generation = file.project.service<SyntaxExtensionService>().modificationCount
        return cached.takeIf { (val stamp1 = stamp, val generation1 = generation) ->
            stamp1 == stamp && generation1 == generation
        }
    }

    fun result(file: KtFile): Result? {
        if (!file.isPhysical || analyzing.get() || DumbService.isDumb(file.project)) return null
        val module = sourceModule(file) ?: return null
        val service = file.project.service<SyntaxExtensionService>()
        val stamp = PsiModificationTracker.getInstance(file.project).modificationCount
        // Reuse diagnostics only while both PSI and the plugin session are unchanged.
        val cached = cachedResult(file)
        // Filters also run on the UI thread: reuse completed analysis without doing work there.
        if (cached != null || ApplicationManager.getApplication().isDispatchThread) return cached
        val runtime = service.runtime(module) ?: return null
        if (!runtime.hasExtensions) return null
        return computeResult(file, runtime, service, stamp)
    }

    private fun sourceModule(file: KtFile): Module? {
        val virtualFile = file.virtualFile ?: return null
        val index = ProjectFileIndex.getInstance(file.project)
        if (!index.isInSourceContent(virtualFile) || index.isInLibrarySource(virtualFile) || index.isInLibraryClasses(
                virtualFile
            )
        ) return null
        return index.getModuleForFile(virtualFile)
    }

    private fun computeResult(
        file: KtFile, runtime: SyntaxRuntime, service: SyntaxExtensionService, stamp: Long
    ): Result? {
        // Analysis of lowered PSI must not trigger syntax analysis recursively.
        analyzing.set(true)
        try {
            val generation = service.modificationCount
            (val lowered, val highlighting, val inspectionRules, val inspections) = runtime.analyzeIfOpen(file)
                ?: return null
            SyntaxAnalysisContext.attach(lowered, file)
            // Analyze desugared Kotlin, but report ranges in the original document.
            val offsets = SyntaxOffsetMap(file.text, lowered.text)
            val diagnostics = collectSyntaxDiagnostics(lowered, offsets) + collectSemanticDiagnostics(lowered, offsets)
            val desugared = file.text != lowered.text
            val colors = collectHighlighting(file, lowered, offsets, highlighting, desugared)
            (val problems, val toolIds) = if (desugared || inspectionRules.isNotEmpty()) {
                SyntaxInspections.collect(file, lowered, offsets, inspectionRules)
            }
            else SyntaxInspections.Result(emptyList(), emptySet())
            // Never replace diagnostics with results from a session invalidated during analysis.
            if (generation != service.modificationCount) return null
            val result = Result(
                stamp,
                generation,
                diagnostics,
                colors,
                mergeInspections(problems, inspections, inspectionRules, desugared),
                toolIds,
                inspectionRules,
                desugared
            )
            file.putUserData(key, result)
            return result
        }
        finally {
            analyzing.remove()
        }
    }

    private fun collectHighlighting(
        file: KtFile, lowered: KtFile, offsets: SyntaxOffsetMap, highlighting: List<Highlighting>, desugared: Boolean
    ): List<Highlighting> = if (desugared) SyntaxHighlighting.normalize(
        SyntaxHighlighting.collect(lowered, offsets) + highlighting, file.textLength
    )
    else highlighting

    private fun mergeInspections(
        problems: List<SyntaxInspections.Problem>,
        inspections: List<Inspection>,
        inspectionRules: List<InspectionRule>,
        desugared: Boolean
    ): List<SyntaxInspections.Problem> = problems.filter { problem ->
        desugared || inspectionRules.any { it.range.intersects(problem.range) }
    } + inspections.map {
        val severity = when (it.severity) {
            InspectionSeverity.ERROR -> HighlightSeverity.ERROR
            InspectionSeverity.WARNING -> HighlightSeverity.WARNING
            InspectionSeverity.WEAK_WARNING -> HighlightSeverity.WEAK_WARNING
            InspectionSeverity.INFORMATION -> HighlightSeverity.INFORMATION
        }
        SyntaxInspections.Problem(severity, it.message, it.range)
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
