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
import dev.karmakrafts.kcml.api.extension.syntax.Highlighting
import org.junit.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalKcmlApi::class)
class SyntaxHighlightingTest {
    private fun highlight(start: Int, end: Int, key: String) = Highlighting(TextRange(start, end), key)

    @Test
    fun nonOverlappingRangesAreSortedAndInvalidRangesAreIgnored() {
        val first = highlight(0, 2, "first")
        val second = highlight(4, 6, "second")
        assertEquals(
            listOf(first, second),
            SyntaxHighlighting.normalize(listOf(second, highlight(6, 9, "invalid"), first), 6)
        )
        assertEquals(emptyList(), SyntaxHighlighting.normalize(listOf(first), 0))
        assertEquals(emptyList(), SyntaxHighlighting.normalize(emptyList(), 6))
    }

    @Test
    fun laterRangesSplitEarlierRangesWithoutChangingTheirRemainingColors() {
        assertEquals(
            listOf(highlight(0, 2, "first"), highlight(2, 4, "second"), highlight(4, 10, "first")),
            SyntaxHighlighting.normalize(listOf(highlight(0, 10, "first"), highlight(2, 4, "second")), 10)
        )
    }

    @Test
    fun laterRangesCanReplaceMultipleEarlierRanges() {
        assertEquals(
            listOf(highlight(0, 1, "first"), highlight(1, 7, "last"), highlight(7, 8, "third")),
            SyntaxHighlighting.normalize(
                listOf(
                    highlight(0, 2, "first"),
                    highlight(3, 5, "second"),
                    highlight(6, 8, "third"),
                    highlight(1, 7, "last")
                ), 8
            )
        )
    }

    @Test
    fun identicalRangesUseTheLastColorAndAdjacentRangesDoNotOverlap() {
        assertEquals(
            listOf(highlight(0, 2, "last"), highlight(2, 4, "adjacent")), SyntaxHighlighting.normalize(
                listOf(
                    highlight(0, 2, "first"), highlight(2, 4, "adjacent"), highlight(0, 2, "last")
                ), 4
            )
        )
    }
}