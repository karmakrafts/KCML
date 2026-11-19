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
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.openapi.progress.ProgressManager
import dev.karmakrafts.kcml.intellij.icon.Icons

class PluginIdCompletionContributor : CompletionContributor() {
    init {
        extendStringCompletion(::addIdCompletions)
    }

    private fun addIdCompletions(parameters: CompletionParameters, result: CompletionResultSet) {
        val file = parameters.originalFile
        (val prefix, val inAnnotation, val insertHandler) = PluginIdString.find(file, parameters.offset) ?: return
        result.stopHere()
        val scopedResult = result.withPrefixMatcher(PlainPrefixMatcher(prefix))
        val ids = if (inAnnotation) PluginIds.fromMetadata(file) else PluginIds.fromAnnotations(file)
        for (id in ids) {
            ProgressManager.checkCanceled()
            scopedResult.addElement(
                LookupElementBuilder.create(id).withIcon(Icons.kcml).withInsertHandler(insertHandler)
            )
        }
    }
}