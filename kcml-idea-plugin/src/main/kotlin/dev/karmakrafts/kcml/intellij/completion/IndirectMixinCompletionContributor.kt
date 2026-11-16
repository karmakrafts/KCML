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
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.codeInsight.lookup.LookupElementPresentation
import com.intellij.codeInsight.lookup.LookupElementRenderer
import com.intellij.icons.AllIcons
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.progress.ProgressManager
import com.intellij.patterns.PlatformPatterns
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.util.ProcessingContext
import dev.karmakrafts.kcml.intellij.util.ClassIndex
import dev.karmakrafts.kcml.intellij.util.ClassNameUtils

class IndirectMixinCompletionContributor : CompletionContributor() {
    init {
        for (type in listOf(CompletionType.BASIC, CompletionType.SMART)) {
            extend(
                type, PlatformPatterns.psiElement(), object : CompletionProvider<CompletionParameters>() {
                    override fun addCompletions(
                        parameters: CompletionParameters, context: ProcessingContext, result: CompletionResultSet
                    ) {
                        // Only complete target strings, using PSI without completion's dummy text
                        val position = parameters.originalFile.findElementAt(parameters.offset) ?: return
                        val target = IndirectMixinTargetString.find(
                            position, parameters.offset
                        ) ?: return
                        result.stopHere()
                        // Accept slash-separated JVM names as well as dotted names
                        val matcher = PlainPrefixMatcher(target.prefix.replace('/', '.'))
                        val scopedResult = result.withPrefixMatcher(matcher)
                        val file = parameters.originalFile
                        val module = ModuleUtilCore.findModuleForPsiElement(file)
                        // Include module dependencies alongside the file's resolve scope
                        val scope = module?.let {
                            GlobalSearchScope.union(
                                arrayOf(
                                    GlobalSearchScope.moduleWithDependenciesAndLibrariesScope(it), file.resolveScope
                                )
                            )
                        } ?: file.resolveScope
                        // Find all applicable candidates in the given scope
                        for (candidate in ClassIndex.findCandidates(
                            parameters.position.project, scope
                        )) {
                            ProgressManager.checkCanceled()
                            // Match qualified, short, and nested-class name variants
                            val lookupNames = ClassNameUtils.lookupNames(candidate)
                            if (lookupNames.none(matcher::prefixMatches)) continue
                            val project = parameters.position.project
                            var lookupElement = LookupElementBuilder.create(candidate)
                                .withIcon(AllIcons.Nodes.Class)
                                .withExpensiveRenderer(object : LookupElementRenderer<LookupElement>() {
                                    override fun renderElement(
                                        element: LookupElement, presentation: LookupElementPresentation
                                    ) {
                                        presentation.itemText = candidate
                                        presentation.icon = ClassIndex.findClass(project, scope, candidate)?.getIcon(0)
                                            ?: AllIcons.Nodes.Class
                                    }
                                })
                            for (lookupName in lookupNames) {
                                lookupElement = lookupElement.withLookupString(lookupName)
                            }
                            scopedResult.addElement(
                                lookupElement.withInsertHandler(
                                    // Preserve string boundaries and raw-string handling on insertion
                                    StringContentInsertHandler(target)
                                )
                            )
                        }
                    }
                })
        }
    }
}