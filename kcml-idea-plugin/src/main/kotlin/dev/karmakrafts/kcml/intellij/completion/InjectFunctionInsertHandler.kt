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
import dev.karmakrafts.kcml.intellij.util.argument
import org.jetbrains.kotlin.psi.KtAnnotationEntry
import org.jetbrains.kotlin.psi.KtValueArgument

internal class InjectFunctionInsertHandler(
    private val target: InjectTargetString,
    private val method: InjectTargetMethod,
    private val insertDescriptor: Boolean
) : InsertHandler<LookupElement> {
    override fun handleInsert(context: InsertionContext, item: LookupElement) {
        StringContentInsertHandler(target.content).handleInsert(context, item)
        context.tailOffset = context.editor.caretModel.offset
        if (!insertDescriptor) return
        insertMethodDescriptor(context)
    }

    private fun insertMethodDescriptor(context: InsertionContext) {
        context.commitDocument()
        val annotation = PsiTreeUtil.getParentOfType(
            context.file.findElementAt(target.content.contentStart), KtAnnotationEntry::class.java, false
        ) ?: return
        val name = annotation.argument("name", 0) ?: return
        val descriptorArgument = annotation.argument("descriptor", 1)
        val nameOffset = name.textRange.startOffset.takeIf {
            name.getArgumentName() == null && (descriptorArgument == null || descriptorArgument.getArgumentName() != null)
        }
        var end = insertOrReplaceDescriptor(context, name, descriptorArgument)
        if (nameOffset != null) {
            context.document.insertString(nameOffset, "name = ")
            if (nameOffset <= end) end += "name = ".length
        }
        context.editor.caretModel.moveToOffset(end)
        context.tailOffset = end
    }

    private fun insertOrReplaceDescriptor(
        context: InsertionContext, name: KtValueArgument, descriptorArgument: KtValueArgument?
    ): Int {
        val descriptor = "\"${method.descriptor.replace("$", "\\$")}\""
        val existing = descriptorArgument?.getArgumentExpression()
        if (existing != null) {
            val range = existing.textRange
            context.document.replaceString(range.startOffset, range.endOffset, descriptor)
            return range.startOffset + descriptor.length - 1
        }
        val offset = name.textRange.endOffset
        val text = ", descriptor = $descriptor"
        context.document.insertString(offset, text)
        return offset + text.length - 1
    }
}