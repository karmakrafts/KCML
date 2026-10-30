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

private fun nativeDeclarationTransform(): Int = 43

class NativeCodeGenerationTest {
    private fun replacement(): Int = 0x4B434D4C

    private fun transformed(): Int = 0x4B434D4D

    @Test
    fun `Non-call expressions can replace LLVM generation`() {
        assertEquals(45, replacement())
    }

    @Test
    fun `Expression transforms continue normal LLVM generation`() {
        assertEquals(46, transformed())
    }

    @Test
    fun `Declarations can be transformed before their bodies are generated`() {
        assertEquals(47, nativeDeclarationTransform())
    }

    @Test
    fun `Local variables can be transformed before initialization`() {
        var nativeLocalTransform = 43
        nativeLocalTransform += 1
        assertEquals(49, nativeLocalTransform)
    }

    @Test
    fun `Unselected expressions retain normal generation`() {
        assertEquals(9, listOf(4, 5).sum())
    }
}