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

import com.intellij.codeInsight.daemon.HighlightDisplayKey
import com.intellij.codeInsight.daemon.impl.SeverityRegistrar
import com.intellij.codeInspection.InspectionEngine
import com.intellij.codeInspection.ProblemDescriptorBase
import com.intellij.codeInspection.ProblemDescriptorUtil
import com.intellij.codeInspection.ex.LocalInspectionToolWrapper
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.profile.codeInspection.InspectionProjectProfileManager
import com.intellij.psi.PsiManager
import com.intellij.psi.SingleRootFileViewProvider
import com.intellij.testFramework.LightVirtualFile
import dev.karmakrafts.kcml.api.ExperimentalKcmlApi
import dev.karmakrafts.kcml.api.extension.syntax.InspectionMode
import dev.karmakrafts.kcml.api.extension.syntax.InspectionRule
import org.jetbrains.kotlin.idea.KotlinFileType
import org.jetbrains.kotlin.idea.KotlinLanguage
import org.jetbrains.kotlin.psi.KtFile

@OptIn(ExperimentalKcmlApi::class)
internal object SyntaxInspections {
    data class Problem( // @formatter:off
        val severity: HighlightSeverity,
        val message: String,
        val range: TextRange
    ) // @formatter:on

    data class Result(val problems: List<Problem>, val toolIds: Set<String>)

    fun collect(source: KtFile, lowered: KtFile, offsets: SyntaxOffsetMap, rules: List<InspectionRule>): Result {
        val profile = InspectionProjectProfileManager.getInstance(source.project).currentProfile
        val tools =
            profile.getInspectionTools(source).filterIsInstance<LocalInspectionToolWrapper>().filter { wrapper ->
                ProgressManager.checkCanceled()
                val key = HighlightDisplayKey.find(wrapper.shortName)
                key != null && profile.isToolEnabled(key, source) && (wrapper.language?.let {
                    source.language.isKindOf(
                        it
                    )
                } != false) && generateSequence<Class<*>>(wrapper.tool.javaClass) { it.superclass }.any {
                    it.name.startsWith(
                        "org.jetbrains.kotlin."
                    )
                }
            }
        if (tools.isEmpty()) return Result(emptyList(), emptySet())
        // Problem descriptors require physical PSI, but never anchor fixes in the editor's source tree.
        // Directory-based inspections must still see the source location, not a parentless scratch file.
        val virtualFile = object : LightVirtualFile(source.name, KotlinFileType.INSTANCE, lowered.text) {
            override fun getParent(): VirtualFile? = source.virtualFile?.parent
        }
        val view = SingleRootFileViewProvider(PsiManager.getInstance(source.project), virtualFile, true)
        val inspectionFile = (view.getPsi(KotlinLanguage.INSTANCE) as KtFile).apply {
            originalFile = source.originalFile
        }
        SyntaxAnalysisContext.attach(inspectionFile, source)
        // runInspectionOnFile uses batch mode and drops on-the-fly-only Kotlin findings.
        @Suppress("JetBrainsInternalApiUsage")
        val descriptors = InspectionEngine.inspectEx(
            tools, inspectionFile, inspectionFile.textRange, inspectionFile.textRange, true, false, true
        ) { _, _ -> true }
        val registrar = SeverityRegistrar.getSeverityRegistrar(source.project)
        val problems = buildList {
            for ([wrapper, findings] in descriptors) {
                val key = HighlightDisplayKey.find(wrapper.shortName) ?: continue
                val level = profile.getErrorLevel(key, source).severity
                for (descriptor in findings) {
                    ProgressManager.checkCanceled()
                    val element = descriptor.psiElement ?: continue
                    if (element.containingFile != inspectionFile) continue
                    val loweredRange =
                        (descriptor as? ProblemDescriptorBase)?.textRange ?: descriptor.textRangeInElement?.shiftRight(
                            element.textRange.startOffset
                        ) ?: element.textRange
                    val mappedRange = offsets.toSource(loweredRange)
                    val rule = rules.firstOrNull { it.range.intersects(mappedRange) }
                    if (rule?.mode == InspectionMode.SUPPRESS) continue
                    val severity = ProblemDescriptorUtil.highlightTypeFromDescriptor(descriptor, level, registrar)
                        .getSeverity(element)
                    val message = ProblemDescriptorUtil.renderDescriptionMessage(descriptor, element)
                    add(Problem(severity, message, rule?.range ?: mappedRange))
                }
            }
        }
        return Result(problems, buildSet {
            for (tool in tools) {
                add(tool.shortName)
                add(tool.id)
            }
        })
    }
}