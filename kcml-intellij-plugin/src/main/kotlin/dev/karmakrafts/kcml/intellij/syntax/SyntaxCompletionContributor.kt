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

import com.intellij.codeInsight.completion.CompletionContributor
import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.completion.CompletionType
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.openapi.components.service
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.roots.ProjectFileIndex
import dev.karmakrafts.kcml.api.ExperimentalKcmlApi
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.analysisContext

@OptIn(ExperimentalKcmlApi::class)
internal class SyntaxCompletionContributor : CompletionContributor() {
    override fun fillCompletionVariants(parameters: CompletionParameters, result: CompletionResultSet) {
        if (parameters.completionType != CompletionType.BASIC && parameters.completionType != CompletionType.SMART) return
        val source = parameters.originalFile as? KtFile ?: return
        if (DumbService.isDumb(source.project)) return
        val virtualFile = source.virtualFile ?: return
        val index = ProjectFileIndex.getInstance(source.project)
        if (!index.isInSourceContent(virtualFile)) return
        val module = index.getModuleForFile(virtualFile) ?: return
        val runtime = source.project.service<SyntaxExtensionService>().runtime(module) ?: return
        if (!runtime.hasExtensions) return
        val custom =
            runtime.completeIfOpen(source, parameters.offset, parameters.completionType == CompletionType.SMART)
        if (custom != null) {
            for ((val lookupString, val insertText, val tailText, val typeText, val element) in custom) {
                result.addElement(
                    LookupElementBuilder.create(element ?: lookupString, lookupString)
                        .withTailText(tailText)
                        .withTypeText(typeText)
                        .withInsertHandler { context, _ ->
                            context.document.replaceString(context.startOffset, context.tailOffset, insertText)
                            context.tailOffset = context.startOffset + insertText.length
                            context.editor.caretModel.moveToOffset(context.tailOffset)
                        })
            }
            result.stopHere()
            return
        }
        // Keep the completion dummy identifier so Kotlin can analyze incomplete expressions.
        val copy = parameters.position.containingFile as? KtFile ?: return
        val lowered = runtime.lowerIfOpen(copy) ?: return
        if (lowered.text == copy.text) return
        val offset = SyntaxOffsetMap(copy.text, lowered.text).toLowered(parameters.offset)
        val position = lowered.findElementAt(offset) ?: return
        val context = SyntaxAnalysisContext.sourceModule(source) ?: return
        val original = runtime.lowerIfOpen(source) ?: return
        original.originalFile = original
        lowered.originalFile = original
        lowered.analysisContext = original
        SyntaxAnalysisContext.attach(listOf(original, lowered), context)
        // withPosition retains the source originalFile; Kotlin contributors need both lowered files.
        @Suppress("JetBrainsInternalApiUsage") //
        val redirected = CompletionParameters(
            position,
            original,
            parameters.completionType,
            offset,
            parameters.invocationCount,
            parameters.editor,
            parameters.process
        ).apply {
            isTestingMode = parameters.isTestingMode
            isCompleteOnlyNotImported = parameters.isCompleteOnlyNotImported
        }
        // Reuse Kotlin's contributors, sorters and insertion handlers with the source editor intact.
        result.runRemainingContributors(redirected, result::passResult)
        result.stopHere()
    }
}