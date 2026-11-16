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

package dev.karmakrafts.kcml.intellij.util

import com.intellij.openapi.progress.ProgressManager
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.kotlin.psi.KtAnnotationEntry
import org.jetbrains.kotlin.psi.KtConstantExpression
import org.jetbrains.kotlin.psi.KtEscapeStringTemplateEntry
import org.jetbrains.kotlin.psi.KtLiteralStringTemplateEntry
import org.jetbrains.kotlin.psi.KtStringTemplateEntry
import org.jetbrains.kotlin.psi.KtStringTemplateEntryWithExpression
import org.jetbrains.kotlin.psi.KtStringTemplateExpression
import org.jetbrains.kotlin.psi.KtValueArgument

internal fun KtStringTemplateEntry.literalValue(): String? = when (this) {
    is KtLiteralStringTemplateEntry -> text
    is KtEscapeStringTemplateEntry -> unescapedValue
    is KtStringTemplateEntryWithExpression -> if ((expression as? KtConstantExpression)?.text == "'${'$'}'") "$" else null
    else -> null
}

internal fun KtStringTemplateExpression.literalValue(): String? = buildString {
    for (entry in entries) {
        ProgressManager.checkCanceled()
        append(entry.literalValue() ?: return null)
    }
}

internal data class StringTemplateContent(
    val prefix: String, val isRawString: Boolean, val contentStart: Int, val interpolationPrefixLength: Int
)

internal fun KtStringTemplateExpression.contentAt(offset: Int): StringTemplateContent? {
    val dollarCount = text.takeWhile { it == '$' }.length
    val isRawString = text.startsWith("\"\"\"", dollarCount)
    val delimiterLength = if (isRawString) 3 else 1
    val contentStart = textRange.startOffset + dollarCount + delimiterLength
    if (offset < contentStart || offset > textRange.endOffset - delimiterLength) return null
    val prefix = buildString {
        for (entry in entries) {
            ProgressManager.checkCanceled()
            if (entry.textRange.startOffset >= offset) break
            if (entry.textRange.endOffset <= offset) append(entry.literalValue() ?: return null)
            else {
                if (entry !is KtLiteralStringTemplateEntry) return null
                append(entry.text.take(offset - entry.textRange.startOffset))
            }
        }
    }
    return StringTemplateContent(prefix, isRawString, contentStart, dollarCount.coerceAtLeast(1))
}

internal data class AnnotationStringArgument(
    val annotation: KtAnnotationEntry, val name: String, val expression: KtStringTemplateExpression
)

internal fun PsiElement.annotationStringArgument(
    annotationName: String, vararg parameterNames: String
): AnnotationStringArgument? {
    val expression = PsiTreeUtil.getParentOfType(this, KtStringTemplateExpression::class.java, false) ?: return null
    if (expression.entries.any { it.literalValue() == null }) return null
    val argument = expression.parent as? KtValueArgument ?: return null
    val annotation = argument.parent?.parent as? KtAnnotationEntry ?: return null
    if (annotation.qualifiedName() != annotationName) return null
    val name = argument.getArgumentName()?.asName?.identifier ?: parameterNames.getOrNull(
        annotation.valueArguments.indexOf(argument)
    ) ?: return null
    if (name !in parameterNames) return null
    return AnnotationStringArgument(annotation, name, expression)
}