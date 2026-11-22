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

class SyntaxExamplesTest {
    @Test
    fun `Custom declarations and nested expressions are lowered to Kotlin`() {
        assertEquals(42, syntaxAnswer())
        assertEquals(43, 1 + magic)
    }

    @Test
    fun `Lowering preserves strings and escaped identifiers`() {
        val `magic` = 7
        val `fn` = "fn magic"
        assertEquals(7, `magic`)
        assertEquals("fn" + " magic", `fn`)
        assertEquals(42, magic)
    }
}