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
import dev.karmakrafts.kcml.api.ExperimentalKcmlApi
import dev.karmakrafts.kcml.api.extension.SyntaxExtension.Highlighting
import java.util.*

@OptIn(ExperimentalKcmlApi::class)
internal object SyntaxHighlighting {
    fun normalize(highlighting: List<Highlighting>, sourceLength: Int): List<Highlighting> {
        // Keep non-overlapping overrides indexed by start offset for ordered overlap lookups
        val ranges = TreeMap<Int, Highlighting>()
        for (highlight in highlighting) {
            val range = highlight.range
            val start = range.startOffset
            val end = range.endOffset
            // Ignore plugin ranges that extend outside the original source text
            if (start < 0 || end > sourceLength) continue
            // Include ranges starting inside this override, plus any overlapping predecessor
            val overlaps = ranges.subMap(start, true, end, false).values.toMutableList()
            ranges.lowerEntry(start)?.value?.takeIf { it.range.endOffset > start }?.let(overlaps::add)
            for (previous in overlaps) {
                ranges.remove(previous.range.startOffset)
                // Preserve portions of the previous override outside the replacement range
                if (previous.range.startOffset < start) {
                    ranges[previous.range.startOffset] =
                        previous.copy(range = TextRange(previous.range.startOffset, start))
                }
                if (previous.range.endOffset > end) {
                    ranges[end] = previous.copy(range = TextRange(end, previous.range.endOffset))
                }
            }
            // Later overrides take precedence wherever plugin-provided ranges overlap
            ranges[start] = highlight
        }
        // TreeMap iteration returns the final segments in source order
        return ranges.values.toList()
    }
}