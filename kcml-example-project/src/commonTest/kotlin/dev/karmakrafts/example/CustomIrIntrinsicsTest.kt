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
import kotlin.test.assertTrue

class CustomIrIntrinsicsTest {
    @Test
    fun `Custom IR intrinsic is lowered to constant value`() {
        assertEquals(44, customIrIntrinsic())
    }

    @Test
    fun `Custom compile target reaches the IR backend`() {
        val targetName = customCompileTargetName()
        assertTrue(
            targetName in setOf("Example JVM", "Example Android", "Example JS", "Example Wasm", "Example Native"),
            "Expected an example compile target, got $targetName"
        )
    }

    @Test
    fun `Custom compile target has its own type`() {
        val expectedAliases = mapOf(
            "Example JVM" to "example_jvm",
            "Example Android" to "example_android",
            "Example JS" to "example_js",
            "Example Wasm" to "example_wasm",
            "Example Native" to "example_native"
        )
        assertEquals(expectedAliases.getValue(customCompileTargetName()), customCompileTargetAlias())
    }
}