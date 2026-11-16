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
import com.intellij.codeInsight.completion.PrefixMatcher
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.icons.AllIcons
import com.intellij.openapi.progress.ProgressManager
import com.intellij.patterns.PlatformPatterns
import com.intellij.util.ProcessingContext
import dev.karmakrafts.kcml.intellij.util.argument
import dev.karmakrafts.kcml.intellij.util.literalValue
import org.jetbrains.kotlin.psi.KtStringTemplateExpression

class InjectCompletionContributor : CompletionContributor() {
    init {
        for (type in listOf(CompletionType.BASIC, CompletionType.SMART)) {
            extend(type, PlatformPatterns.psiElement(), object : CompletionProvider<CompletionParameters>() {
                override fun addCompletions(
                    parameters: CompletionParameters, context: ProcessingContext, result: CompletionResultSet
                ) {
                    val target = InjectTargetString.find(parameters.originalFile, parameters.offset) ?: return
                    result.stopHere()
                    val methods = InjectTargetMethods.find(target.annotation)
                    val isName = target.argumentName == "name"
                    val name = if (isName) null
                    else {
                        val expression = target.annotation.argument("name", 0)
                            ?.getArgumentExpression() as? KtStringTemplateExpression ?: return
                        expression.literalValue() ?: return
                    }
                    val scopedResult = result.withPrefixMatcher(FunctionPrefixMatcher(target.content.prefix))
                    val overloadCounts = methods.groupingBy { it.name }.eachCount()
                    for (method in methods) {
                        ProgressManager.checkCanceled()
                        if (!isName && method.name != name) continue
                        scopedResult.addElement(
                            LookupElementBuilder.create(method, if (isName) method.name else method.descriptor)
                                .withPresentableText(method.name)
                                .withTailText(method.descriptor, true)
                                .withIcon(AllIcons.Nodes.Method)
                                .withInsertHandler(
                                    InjectFunctionInsertHandler(
                                        target, method, isName && overloadCounts.getValue(method.name) > 1
                                    )
                                )
                        )
                    }
                }
            })
        }
    }

    private class FunctionPrefixMatcher(prefix: String) : PrefixMatcher(prefix) {
        override fun prefixMatches(name: String): Boolean = name.startsWith(prefix, ignoreCase = true)

        override fun cloneWithPrefix(prefix: String): PrefixMatcher = FunctionPrefixMatcher(prefix)
    }
}