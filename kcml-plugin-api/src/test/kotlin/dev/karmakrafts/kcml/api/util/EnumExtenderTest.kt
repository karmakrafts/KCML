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

package dev.karmakrafts.kcml.api.util

import dev.karmakrafts.kcml.api.DelicateKcmlApi
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame

private enum class EnumExtenderBasic {
    FIRST, SECOND
}

private enum class EnumExtenderDuplicate {
    FIRST, SECOND
}

private enum class EnumExtenderEmpty

private enum class EnumExtenderArguments(val number: Int, val text: String?) {
    EXISTING(1, "existing")
}

private enum class EnumExtenderSpecificity(val selected: String, val value: Any?) {
    EXISTING("existing", "existing");

    constructor(value: Any?) : this("any", value)
    constructor(value: CharSequence?) : this("charSequence", value)
    constructor(value: String?) : this("string", value)
}

private enum class EnumExtenderAmbiguous(val value: Any?) {
    EXISTING("existing");

    constructor(value: String?) : this(value as Any?)
    constructor(value: Int?) : this(value as Any?)
}

private enum class EnumExtenderInvalidArguments(val number: Int) {
    EXISTING(1)
}

private enum class EnumExtenderThrowing(val value: String) {
    EXISTING("existing");

    init {
        check(value != "throw") { "constructor failed" }
    }
}

@OptIn(DelicateKcmlApi::class)
class EnumExtenderTest {
    @Test
    fun `defines constants with sequential ordinals and refreshes enum caches`() {
        EnumExtenderBasic.values()
        EnumExtenderBasic.valueOf("FIRST")
        EnumExtenderBasic::class.java.enumConstants
        EnumExtenderBasic.entries

        val third = EnumExtender.define<EnumExtenderBasic>("THIRD")
        val fourth = EnumExtender.define<EnumExtenderBasic>("FOURTH")

        assertEquals(2, third.ordinal)
        assertEquals(3, fourth.ordinal)
        assertSame(third, EnumExtenderBasic.values()[2])
        assertSame(fourth, EnumExtenderBasic.valueOf("FOURTH"))
        assertSame(fourth, EnumExtenderBasic::class.java.enumConstants.last())
        assertSame(fourth, EnumExtenderBasic.entries.last())
    }

    @Test
    fun `supports empty enums primitive arguments reference arguments and null`() {
        val empty = EnumExtender.define<EnumExtenderEmpty>("FIRST")
        assertEquals(0, empty.ordinal)
        assertSame(empty, EnumExtenderEmpty.values().single())
        assertSame(empty, EnumExtenderEmpty.valueOf("FIRST"))
        assertSame(empty, EnumExtenderEmpty.entries.single())

        EnumExtenderArguments.entries
        EnumExtenderArguments.valueOf("EXISTING")
        val added = EnumExtender.define<EnumExtenderArguments>("ADDED", 7, "custom")
        val withNull = EnumExtender.define<EnumExtenderArguments>("WITH_NULL", 8, null)

        assertEquals(7, added.number)
        assertEquals("custom", added.text)
        assertEquals(8, withNull.number)
        assertEquals(null, withNull.text)
        assertSame(added, EnumExtenderArguments.valueOf("ADDED"))
        assertSame(withNull, EnumExtenderArguments.entries.last())
    }

    @Test
    fun `selects the most specific applicable constructor`() {
        val value = EnumExtender.define<EnumExtenderSpecificity>("ADDED", "specific")

        assertEquals("string", value.selected)
        assertEquals("specific", value.value)
    }

    @Test
    fun `rejects duplicate and empty names ambiguous constructors and invalid arguments`() {
        EnumExtender.define<EnumExtenderDuplicate>("THIRD")
        assertFailsWith<IllegalArgumentException> {
            EnumExtender.define<EnumExtenderDuplicate>("THIRD")
        }
        assertFailsWith<IllegalArgumentException> {
            EnumExtender.define<EnumExtenderEmpty>("")
        }
        assertFailsWith<IllegalArgumentException> {
            EnumExtender.define<EnumExtenderAmbiguous>("AMBIGUOUS", null)
        }
        assertFailsWith<IllegalArgumentException> {
            EnumExtender.define<EnumExtenderInvalidArguments>("INVALID", "not an integer")
        }
        assertEquals(3, EnumExtenderDuplicate.values().size)
        assertEquals(0, EnumExtenderEmpty.values().size)
        assertEquals(1, EnumExtenderAmbiguous.values().size)
        assertEquals(1, EnumExtenderInvalidArguments.values().size)
    }

    @Test
    fun `constructor failure does not publish a new enum constant`() {
        assertFailsWith<IllegalStateException> {
            EnumExtender.define<EnumExtenderThrowing>("THROW", "throw")
        }

        assertEquals(1, EnumExtenderThrowing.values().size)
        assertSame(EnumExtenderThrowing.EXISTING, EnumExtenderThrowing.valueOf("EXISTING"))
    }
}