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

package dev.karmakrafts.kcml.intellij.syntax.runtime

import com.intellij.openapi.util.TextRange
import org.junit.Test
import kotlin.test.assertEquals

class SyntaxOffsetMapTest {
    @Test
    fun sourceCaretsMapToLoweredTextAcrossEdits() {
        val map = SyntaxOffsetMap("fn answer() = magic\nfun use() = ans", "fun answer() = 42\nfun use() = ans")
        assertEquals(0, map.toLowered(0))
        assertEquals(4, map.toLowered(3))
        assertEquals(17, map.toLowered(19))
        assertEquals(32, map.toLowered(34))
        assertEquals("fun answer() = 42\nfun use() = ans".length, map.toLowered(Int.MAX_VALUE))
        assertEquals(0, map.toLowered(-1))
    }

    @Test
    fun identicalTextPreservesRanges() {
        val map = SyntaxOffsetMap("abcdef", "abcdef")
        assertEquals(TextRange(1, 4), map.toSource(TextRange(1, 4)))
        assertEquals(TextRange(0, 6), map.toSource(TextRange(0, 6)))
        assertEquals(TextRange(6, 6), map.toSource(TextRange(6, 6)))
    }

    @Test
    fun insertionMapsToSourceAnchor() {
        val map = SyntaxOffsetMap("abcd", "abXYZcd")
        assertEquals(TextRange(0, 2), map.toSource(TextRange(0, 2)))
        assertEquals(TextRange(2, 2), map.toSource(TextRange(2, 5)))
        assertEquals(TextRange(2, 2), map.toSource(TextRange(3, 4)))
        assertEquals(TextRange(2, 4), map.toSource(TextRange(5, 7)))
        assertEquals(TextRange(1, 3), map.toSource(TextRange(1, 6)))
    }

    @Test
    fun deletionPreservesAdjacentUnchangedRanges() {
        val map = SyntaxOffsetMap("abXYcd", "abcd")
        assertEquals(TextRange(0, 2), map.toSource(TextRange(0, 2)))
        assertEquals(TextRange(4, 6), map.toSource(TextRange(2, 4)))
        assertEquals(TextRange(1, 5), map.toSource(TextRange(1, 3)))
        assertEquals(TextRange(4, 4), map.toSource(TextRange(2, 2)))
    }

    @Test
    fun expandedReplacementMapsGeneratedTextToOriginalSpan() {
        val map = SyntaxOffsetMap("abXcd", "ab123cd")
        assertEquals(TextRange(2, 3), map.toSource(TextRange(2, 5)))
        assertEquals(TextRange(2, 3), map.toSource(TextRange(3, 4)))
        assertEquals(TextRange(2, 3), map.toSource(TextRange(3, 3)))
        assertEquals(TextRange(1, 3), map.toSource(TextRange(1, 4)))
        assertEquals(TextRange(2, 4), map.toSource(TextRange(3, 6)))
        assertEquals(TextRange(0, 2), map.toSource(TextRange(0, 2)))
        assertEquals(TextRange(3, 5), map.toSource(TextRange(5, 7)))
    }

    @Test
    fun shrunkenReplacementMapsToWholeOriginalSpan() {
        val map = SyntaxOffsetMap("ab123cd", "abXcd")
        assertEquals(TextRange(2, 5), map.toSource(TextRange(2, 3)))
        assertEquals(TextRange(5, 7), map.toSource(TextRange(3, 5)))
        assertEquals(TextRange(0, 2), map.toSource(TextRange(0, 2)))
        assertEquals(TextRange(1, 6), map.toSource(TextRange(1, 4)))
    }

    @Test
    fun multipleEditsAccumulateOffsetChanges() {
        val map = SyntaxOffsetMap("abXcdYef", "ab123cdZef")
        assertEquals(TextRange(2, 3), map.toSource(TextRange(3, 4)))
        assertEquals(TextRange(3, 5), map.toSource(TextRange(5, 7)))
        assertEquals(TextRange(5, 6), map.toSource(TextRange(7, 8)))
        assertEquals(TextRange(6, 8), map.toSource(TextRange(8, 10)))
        assertEquals(TextRange(2, 6), map.toSource(TextRange(3, 8)))
        assertEquals(TextRange(8, 8), map.toSource(TextRange(10, 10)))
    }

    @Test
    fun separateInsertionsAndDeletionsAccumulateOffsetChanges() {
        val map = SyntaxOffsetMap("abXXcdYYef", "abcdZZef")
        assertEquals(TextRange(4, 6), map.toSource(TextRange(2, 4)))
        assertEquals(TextRange(6, 8), map.toSource(TextRange(4, 6)))
        assertEquals(TextRange(8, 10), map.toSource(TextRange(6, 8)))
        assertEquals(TextRange(1, 9), map.toSource(TextRange(1, 7)))
    }

    @Test
    fun leadingEditsPreserveStartBoundary() {
        val inserted = SyntaxOffsetMap("ab", "XYab")
        assertEquals(TextRange(0, 0), inserted.toSource(TextRange(0, 2)))
        assertEquals(TextRange(0, 2), inserted.toSource(TextRange(2, 4)))
        val deleted = SyntaxOffsetMap("XYab", "ab")
        assertEquals(TextRange(2, 4), deleted.toSource(TextRange(0, 2)))
        assertEquals(TextRange(2, 2), deleted.toSource(TextRange(0, 0)))
    }

    @Test
    fun trailingInsertionMapsEofToOriginalEof() {
        val map = SyntaxOffsetMap("ab", "abXYZ")
        assertEquals(TextRange(2, 2), map.toSource(TextRange(3, 5)))
        assertEquals(TextRange(2, 2), map.toSource(TextRange(5, 5)))
    }

    @Test
    fun trailingDeletionKeepsRangeEndBeforeDeletedTextAndCaretAtEof() {
        val map = SyntaxOffsetMap("abXYZ", "ab")
        assertEquals(TextRange(0, 2), map.toSource(TextRange(0, 2)))
        assertEquals(TextRange(5, 5), map.toSource(TextRange(2, 2)))
    }

    @Test
    fun trailingReplacementMapsEofToOriginalEof() {
        val map = SyntaxOffsetMap("abX", "ab123")
        assertEquals(TextRange(2, 3), map.toSource(TextRange(3, 5)))
        assertEquals(TextRange(3, 3), map.toSource(TextRange(5, 5)))
    }

    @Test
    fun emptyInputsHaveValidRanges() {
        assertEquals(TextRange(0, 0), SyntaxOffsetMap("", "").toSource(TextRange(0, 0)))
        assertEquals(TextRange(0, 0), SyntaxOffsetMap("", "abc").toSource(TextRange(0, 3)))
        assertEquals(TextRange(3, 3), SyntaxOffsetMap("abc", "").toSource(TextRange(0, 0)))
        assertEquals(TextRange(0, 3), SyntaxOffsetMap("abc", "XYZ").toSource(TextRange(1, 2)))
    }

    @Test
    fun outOfBoundsRangesAreClamped() {
        val map = SyntaxOffsetMap("abX", "ab123")
        assertEquals(TextRange(0, 3), map.toSource(TextRange(0, 100)))
        assertEquals(TextRange(3, 3), map.toSource(TextRange(100, Int.MAX_VALUE)))
        assertEquals(TextRange(0, 0), SyntaxOffsetMap("", "").toSource(TextRange(10, 20)))
    }

    @Test
    fun offsetsUseUtf16CharactersRatherThanCodePoints() {
        val map = SyntaxOffsetMap("a\uD83D\uDE00b", "aXY\uD83D\uDE00b")
        assertEquals(TextRange(1, 3), map.toSource(TextRange(3, 5)))
        assertEquals(TextRange(3, 4), map.toSource(TextRange(5, 6)))
    }
}