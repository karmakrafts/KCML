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
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementDecorator
import com.intellij.codeInsight.lookup.LookupElementPresentation
import com.intellij.codeInsight.lookup.LookupElementRenderer
import dev.karmakrafts.kcml.intellij.icon.CompileTargetIconProvider
import javax.swing.Icon

internal class CompileTargetCompletionContributor : CompletionContributor() {
    private val iconProvider: CompileTargetIconProvider = CompileTargetIconProvider()

    override fun fillCompletionVariants(parameters: CompletionParameters, result: CompletionResultSet) {
        result.runRemainingContributors(parameters) { completion ->
            val item = completion.lookupElement
            val element = item.psiElement
            val icon = if (element == null) null else iconProvider.getIcon(element, 0)
            val decorated = if (icon == null) completion
            else completion.withLookupElement(TargetLookupElement(item, icon))
            result.passResult(decorated)
        }
    }

    private class TargetLookupElement(
        delegate: LookupElement, private val icon: Icon
    ) : LookupElementDecorator<LookupElement>(delegate) {
        override fun renderElement(presentation: LookupElementPresentation) {
            super.renderElement(presentation)
            presentation.icon = icon
        }

        override fun getExpensiveRenderer(): LookupElementRenderer<out LookupElement>? {
            @Suppress("UNCHECKED_CAST") val renderer =
                super.getExpensiveRenderer() as? LookupElementRenderer<LookupElement> ?: return null
            return object : LookupElementRenderer<LookupElement>() {
                override fun renderElement(element: LookupElement, presentation: LookupElementPresentation) {
                    renderer.renderElement(element, presentation)
                    presentation.icon = icon
                }
            }
        }
    }
}