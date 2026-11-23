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

package dev.karmakrafts.kcml.intellij.syntax

import com.intellij.openapi.util.TextRange
import com.intellij.util.diff.Diff
import com.intellij.util.diff.Diff.Change

internal class SyntaxOffsetMap(source: String, lowered: String) {
    private val sourceLength: Int = source.length
    private val loweredLength: Int = lowered.length

    // @formatter:off
    private val changes: Change? = Diff.buildChanges(
        IntArray(source.length) { source[it].code },
        IntArray(lowered.length) { lowered[it].code }
    )
    // @formatter:on

    fun toSource(range: TextRange): TextRange {
        val start = toSourceOffset(range.startOffset.coerceIn(0, loweredLength), false).coerceIn(0, sourceLength)
        val end = toSourceOffset(range.endOffset.coerceIn(0, loweredLength), true).coerceIn(start, sourceLength)
        return TextRange(start, end)
    }

    fun toLowered(offset: Int): Int {
        val sourceOffset = offset.coerceIn(0, sourceLength)
        var delta = 0
        var change = changes
        while (change != null) {
            if (sourceOffset < change.line0) break
            if (change.deleted > 0) {
                if (sourceOffset == change.line0) return change.line1
                if (sourceOffset < change.line0 + change.deleted) return change.line1
            }
            delta = change.line1 + change.inserted - change.line0 - change.deleted
            change = change.link
        }
        return (sourceOffset + delta).coerceIn(0, loweredLength)
    }

    private fun toSourceOffset(offset: Int, end: Boolean): Int {
        var delta = 0
        var change = changes
        while (change != null) {
            if (offset < change.line1) return offset + delta
            if (change.inserted == 0) {
                // Ends stay before deleted text; starts continue after it.
                if (offset == change.line1 && end) return change.line0
            }
            else {
                if (offset == change.line1) return change.line0
                if (offset < change.line1 + change.inserted) {
                    return change.line0 + if (end) change.deleted else 0
                }
            }
            delta = change.line0 + change.deleted - change.line1 - change.inserted
            change = change.link
        }
        return offset + delta
    }
}