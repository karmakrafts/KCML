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

import com.intellij.codeInsight.completion.InsertHandler
import com.intellij.codeInsight.completion.InsertionContext
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.kotlin.psi.KtStringTemplateExpression

internal class StringContentInsertHandler(
    private val isRawString: Boolean, private val contentStart: Int, private val interpolationPrefixLength: Int
) : InsertHandler<LookupElement> {
    override fun handleInsert(context: InsertionContext, item: LookupElement) {
        val insertedText =
            if (isRawString) item.lookupString.replace("$", "$".repeat(interpolationPrefixLength) + "{'${'$'}'}")
            else item.lookupString.replace("$", "\\$")
        context.commitDocument()
        val expression = PsiTreeUtil.getParentOfType(
            context.file.findElementAt(contentStart), KtStringTemplateExpression::class.java, false
        )
        val delimiterLength = if (isRawString) 3 else 1
        val contentEnd = expression?.let { it.textRange.endOffset - delimiterLength } ?: context.tailOffset
        context.document.replaceString(contentStart, contentEnd, insertedText)
        context.editor.caretModel.moveToOffset(contentStart + insertedText.length)
    }
}