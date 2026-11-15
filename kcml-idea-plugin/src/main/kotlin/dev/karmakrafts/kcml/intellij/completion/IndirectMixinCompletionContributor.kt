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

import com.intellij.codeInsight.completion.CompletionContributor
import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionProvider
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.completion.CompletionType
import com.intellij.codeInsight.completion.PlainPrefixMatcher
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.openapi.progress.ProgressManager
import com.intellij.patterns.PlatformPatterns
import com.intellij.util.ProcessingContext
import dev.karmakrafts.kcml.intellij.util.ClassNameUtils

class IndirectMixinCompletionContributor : CompletionContributor() {
    init {
        for (type in listOf(CompletionType.BASIC, CompletionType.SMART)) {
            extend(
                type, PlatformPatterns.psiElement(), object : CompletionProvider<CompletionParameters>() {
                    override fun addCompletions(
                        parameters: CompletionParameters, context: ProcessingContext, result: CompletionResultSet
                    ) {
                        val position = parameters.originalFile.findElementAt(parameters.offset) ?: return
                        (val prefix, val isRawString, val contentStart) = IndirectMixinTargetString.find(
                            position, parameters.offset
                        ) ?: return
                        val matcher = PlainPrefixMatcher(prefix.replace('/', '.'))
                        val scopedResult = result.withPrefixMatcher(matcher)
                        for (candidate in ClassNameUtils.findCandidates(
                            parameters.position.project, parameters.originalFile.resolveScope
                        )) {
                            ProgressManager.checkCanceled()
                            if (!matcher.prefixMatches(candidate)) continue
                            scopedResult.addElement(
                                LookupElementBuilder.create(candidate).withInsertHandler(
                                    StringContentInsertHandler(isRawString, contentStart)
                                )
                            )
                        }
                    }
                })
        }
    }
}