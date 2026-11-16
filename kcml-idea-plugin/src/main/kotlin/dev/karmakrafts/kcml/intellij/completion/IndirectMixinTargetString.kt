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

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import dev.karmakrafts.kcml.intellij.util.TypeNames
import dev.karmakrafts.kcml.intellij.util.qualifiedName
import org.jetbrains.kotlin.psi.KtAnnotationEntry
import org.jetbrains.kotlin.psi.KtConstantExpression
import org.jetbrains.kotlin.psi.KtEscapeStringTemplateEntry
import org.jetbrains.kotlin.psi.KtLiteralStringTemplateEntry
import org.jetbrains.kotlin.psi.KtStringTemplateEntry
import org.jetbrains.kotlin.psi.KtStringTemplateEntryWithExpression
import org.jetbrains.kotlin.psi.KtStringTemplateExpression
import org.jetbrains.kotlin.psi.KtValueArgument
import org.jetbrains.kotlin.psi.KtValueArgumentList

internal data class IndirectMixinTargetString(
    val prefix: String, val isRawString: Boolean, val contentStart: Int, val interpolationPrefixLength: Int
) {
    companion object {
        fun find(position: PsiElement, offset: Int): IndirectMixinTargetString? {
            val expression = targetExpression(position) ?: return null
            val text = expression.text
            val dollarCount = text.takeWhile { it == '$' }.length
            val isRawString = text.startsWith("\"\"\"", dollarCount)
            val delimiterLength = if (isRawString) 3 else 1
            val contentStart = expression.textRange.startOffset + dollarCount + delimiterLength
            if (offset < contentStart || offset > expression.textRange.endOffset - delimiterLength) return null
            val prefix = buildString {
                for (entry in expression.entries) {
                    if (entry.textRange.startOffset >= offset) break
                    if (entry.textRange.endOffset <= offset) append(entry.literalValue() ?: return null)
                    else {
                        if (entry !is KtLiteralStringTemplateEntry) return null
                        append(entry.text.take(offset - entry.textRange.startOffset))
                    }
                }
            }
            return IndirectMixinTargetString(prefix, isRawString, contentStart, dollarCount.coerceAtLeast(1))
        }

        fun isTargetString(position: PsiElement): Boolean = targetExpression(position) != null

        fun isTargetStringAt(file: PsiFile, offset: Int): Boolean {
            val position = file.findElementAt(offset) ?: return false
            return isTargetString(position)
        }

        private fun targetExpression(position: PsiElement): KtStringTemplateExpression? {
            val expression =
                PsiTreeUtil.getParentOfType(position, KtStringTemplateExpression::class.java, false) ?: return null
            if (expression.entries.any { it.literalValue() == null }) return null
            val argument = PsiTreeUtil.getParentOfType(expression, KtValueArgument::class.java, false) ?: return null
            if (argument.getArgumentExpression() != expression) return null
            val argumentList =
                PsiTreeUtil.getParentOfType(argument, KtValueArgumentList::class.java, false) ?: return null
            val argumentName = argument.getArgumentName()?.asName?.identifier
            if (argumentName != null) {
                if (argumentName != "target") return null
            }
            else if (argumentList.arguments.indexOf(argument) != 0) {
                return null
            }
            val annotation =
                PsiTreeUtil.getParentOfType(argumentList, KtAnnotationEntry::class.java, false) ?: return null
            if (annotation.qualifiedName() != TypeNames.INDIRECT_MIXIN) return null
            return expression
        }

        private fun KtStringTemplateEntry.literalValue(): String? = when (this) {
            is KtLiteralStringTemplateEntry -> text
            is KtEscapeStringTemplateEntry -> unescapedValue
            is KtStringTemplateEntryWithExpression -> if ((expression as? KtConstantExpression)?.text == "'${'$'}'") "$" else null
            else -> null
        }
    }
}

