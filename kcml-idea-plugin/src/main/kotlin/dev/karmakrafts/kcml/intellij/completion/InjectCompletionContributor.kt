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
import com.intellij.codeInsight.completion.PrefixMatcher
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.icons.AllIcons
import com.intellij.openapi.progress.ProgressManager
import dev.karmakrafts.kcml.intellij.util.JvmMethod
import dev.karmakrafts.kcml.intellij.util.argument
import dev.karmakrafts.kcml.intellij.util.literalValue
import org.jetbrains.kotlin.psi.KtStringTemplateExpression

internal class InjectCompletionContributor : CompletionContributor() {
    companion object {
        private const val NAME_PARAMETER: String = "name"
    }

    init {
        extendStringCompletion(::addTargetCompletions)
    }

    private fun addTargetCompletions(parameters: CompletionParameters, result: CompletionResultSet) {
        val target = InjectTargetString.find(parameters.originalFile, parameters.offset) ?: return
        result.stopHere()
        val methods = InjectTargetMethods.find(target.annotation)
        val isName = target.argumentName == NAME_PARAMETER
        val name = if (isName) null else findTargetName(target) ?: return
        val scopedResult = result.withPrefixMatcher(FunctionPrefixMatcher(target.content.prefix))
        val overloadCounts = methods.groupingBy(JvmMethod::name).eachCount()
        for (method in methods) {
            ProgressManager.checkCanceled()
            if (!isName && method.name != name) continue
            scopedResult.addElement(
                createLookupElement(target, method, isName, isName && overloadCounts.getValue(method.name) > 1)
            )
        }
    }

    private fun findTargetName(target: InjectTargetString): String? {
        val expression =
            target.annotation.argument(NAME_PARAMETER, 0)?.getArgumentExpression() as? KtStringTemplateExpression
                ?: return null
        return expression.literalValue()
    }

    private fun createLookupElement(
        target: InjectTargetString, method: JvmMethod, isName: Boolean, insertDescriptor: Boolean
    ): LookupElementBuilder = LookupElementBuilder.create(method, if (isName) method.name else method.descriptor)
        .withPresentableText(method.name)
        .withTailText(method.descriptor, true)
        .withIcon(AllIcons.Nodes.Method)
        .withInsertHandler(InjectFunctionInsertHandler(target, method, insertDescriptor))

    private class FunctionPrefixMatcher(prefix: String) : PrefixMatcher(prefix) {
        override fun prefixMatches(name: String): Boolean = name.startsWith(prefix, ignoreCase = true)

        override fun cloneWithPrefix(prefix: String): PrefixMatcher = FunctionPrefixMatcher(prefix)
    }
}