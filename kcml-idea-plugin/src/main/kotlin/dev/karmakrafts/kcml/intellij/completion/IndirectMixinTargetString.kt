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

import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMethod
import com.intellij.psi.util.PsiTreeUtil
import dev.karmakrafts.kcml.intellij.util.TypeNames
import org.jetbrains.kotlin.psi.KtAnnotationEntry
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtEscapeStringTemplateEntry
import org.jetbrains.kotlin.psi.KtLiteralStringTemplateEntry
import org.jetbrains.kotlin.psi.KtStringTemplateExpression
import org.jetbrains.kotlin.psi.KtValueArgument
import org.jetbrains.kotlin.psi.KtValueArgumentList

internal data class IndirectMixinTargetString(val prefix: String, val isRawString: Boolean, val contentStart: Int) {
    companion object {
        fun find(position: PsiElement, offset: Int): IndirectMixinTargetString? {
            val expression =
                PsiTreeUtil.getParentOfType(position, KtStringTemplateExpression::class.java, false) ?: return null
            if (expression.entries.any { it !is KtLiteralStringTemplateEntry && it !is KtEscapeStringTemplateEntry }) return null
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

            val text = expression.text
            val isRawString = text.startsWith("\"\"\"")
            val delimiterLength = if (isRawString) 3 else 1
            val contentStart = expression.textRange.startOffset + delimiterLength
            if (offset < contentStart || offset > expression.textRange.endOffset - delimiterLength) return null
            var prefix = expression.containingFile.text.substring(
                contentStart, offset.coerceAtMost(expression.textRange.endOffset)
            )
            if (!isRawString) {
                prefix = prefix.replace("\\$", "$").replace("\\\\", "\\")
            }
            return IndirectMixinTargetString(prefix, isRawString, contentStart)
        }
    }
}

private fun KtAnnotationEntry.qualifiedName(): String? {
    val resolved = calleeExpression?.constructorReferenceExpression?.references?.firstNotNullOfOrNull { it.resolve() }
        ?: return null
    return when (resolved) {
        is PsiClass -> resolved.qualifiedName
        is KtClassOrObject -> resolved.fqName?.asString()
        is PsiMethod -> resolved.containingClass?.qualifiedName
        else -> PsiTreeUtil.getParentOfType(resolved, KtClassOrObject::class.java)?.fqName?.asString()
    }
}