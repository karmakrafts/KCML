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
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.completion.PlainPrefixMatcher
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.codeInsight.lookup.LookupElementPresentation
import com.intellij.codeInsight.lookup.LookupElementRenderer
import com.intellij.icons.AllIcons
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import com.intellij.psi.search.GlobalSearchScope
import dev.karmakrafts.kcml.intellij.util.ClassIndex
import dev.karmakrafts.kcml.intellij.util.ClassNameUtils

internal class IndirectMixinCompletionContributor : CompletionContributor() {
    init {
        extendStringCompletion(::addTargetCompletions)
    }

    private fun addTargetCompletions(parameters: CompletionParameters, result: CompletionResultSet) {
        val position = parameters.originalFile.findElementAt(parameters.offset) ?: return
        val target = IndirectMixinTargetString.find(position, parameters.offset) ?: return
        result.stopHere()
        val matcher = PlainPrefixMatcher(target.prefix.replace('/', '.'))
        val scopedResult = result.withPrefixMatcher(matcher)
        val scope = findSearchScope(parameters.originalFile)
        val project = parameters.position.project
        for (candidate in ClassIndex.findCandidates(project, scope)) {
            ProgressManager.checkCanceled()
            val lookupNames = ClassNameUtils.lookupNames(candidate)
            if (lookupNames.none(matcher::prefixMatches)) continue
            scopedResult.addElement(
                createLookupElement(
                    candidate, lookupNames, project, scope
                ).withInsertHandler(StringContentInsertHandler(target))
            )
        }
    }

    private fun findSearchScope(file: PsiFile): GlobalSearchScope {
        val module = ModuleUtilCore.findModuleForPsiElement(file) ?: return file.resolveScope
        return GlobalSearchScope.union(
            arrayOf(
                GlobalSearchScope.moduleWithDependenciesAndLibrariesScope(module), file.resolveScope
            )
        )
    }

    private fun createLookupElement(
        candidate: String, lookupNames: Collection<String>, project: Project, scope: GlobalSearchScope
    ): LookupElementBuilder {
        var lookupElement = LookupElementBuilder.create(candidate)
            .withIcon(AllIcons.Nodes.Class)
            .withExpensiveRenderer(object : LookupElementRenderer<LookupElement>() {
                override fun renderElement(element: LookupElement, presentation: LookupElementPresentation) {
                    presentation.itemText = candidate
                    presentation.icon =
                        ClassIndex.findClass(project, scope, candidate)?.getIcon(0) ?: AllIcons.Nodes.Class
                }
            })
        for (lookupName in lookupNames) {
            lookupElement = lookupElement.withLookupString(lookupName)
        }
        return lookupElement
    }
}