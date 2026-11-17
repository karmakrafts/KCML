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
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.json.psi.JsonFile
import com.intellij.json.psi.JsonProperty
import com.intellij.json.psi.JsonStringLiteral
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.ElementManipulators
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import dev.karmakrafts.kcml.intellij.util.TypeNames
import dev.karmakrafts.kcml.intellij.util.annotationStringArgument
import dev.karmakrafts.kcml.intellij.util.contentAt

internal data class PluginIdString(
    val prefix: String, val inAnnotation: Boolean, val insertHandler: InsertHandler<LookupElement>
) {
    companion object {
        fun find(file: PsiFile, offset: Int): PluginIdString? {
            val position = file.findElementAt(offset) ?: return null
            if (file.language.id == "kotlin") {
                val content = position.annotationStringArgument(TypeNames.PLUGIN, "id")?.expression?.contentAt(offset)
                    ?: return null
                return PluginIdString(content.prefix, true, StringContentInsertHandler(content))
            }
            val json = file as? JsonFile ?: return null
            if (!PluginIds.isMetadata(file)) return null
            val literal = PsiTreeUtil.getParentOfType(position, JsonStringLiteral::class.java, false) ?: return null
            val property = literal.parent as? JsonProperty ?: return null
            if (property.name != "id" || property.value != literal || property.parent != json.topLevelValue) return null
            val range = ElementManipulators.getValueTextRange(literal)
            val relativeOffset = offset - literal.textRange.startOffset
            if (relativeOffset !in range.startOffset..range.endOffset) return null
            val prefix = StringUtil.unescapeStringCharacters(literal.text.substring(range.startOffset, relativeOffset))
            val contentStart = literal.textRange.startOffset + range.startOffset
            return PluginIdString(prefix, false, InsertHandler { context, item ->
                context.commitDocument()
                val current = PsiTreeUtil.getParentOfType(
                    context.file.findElementAt(contentStart), JsonStringLiteral::class.java, false
                ) ?: return@InsertHandler
                val contentEnd =
                    current.textRange.startOffset + ElementManipulators.getValueTextRange(current).endOffset
                val insertedText = StringUtil.escapeStringCharacters(item.lookupString)
                context.document.replaceString(contentStart, contentEnd, insertedText)
                val end = contentStart + insertedText.length
                context.tailOffset = end
                context.editor.caretModel.moveToOffset(end)
            })
        }
    }
}