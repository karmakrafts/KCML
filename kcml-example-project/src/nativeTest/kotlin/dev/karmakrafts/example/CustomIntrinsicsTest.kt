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

package dev.karmakrafts.example

import kotlin.test.Test
import kotlin.test.assertEquals

@CustomIntrinsic
private fun intrinsicWithArguments(first: Int, second: Int): Int = error("Intrinsic not lowered")

class CustomIntrinsicsTest {
    @Test
    fun `Custom intrinsic is lowered to constant value`() {
        assertEquals(44, customIntrinsic())
    }

    @Test
    fun `Intrinsic arguments are evaluated once in order`() {
        val events = ArrayList<Int>()
        val result = intrinsicWithArguments(
            run { events += 1; 0x4B434D4C },
            run { events += 2; 2 }
        )
        assertEquals(44, result)
        assertEquals(listOf(1, 2), events)
    }
}