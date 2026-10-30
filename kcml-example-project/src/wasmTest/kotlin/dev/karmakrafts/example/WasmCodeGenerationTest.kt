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

private fun wasmDeclarationTransform(): Int = 43
private fun wasmFileTransform(): Int = 43
private fun wasmBodyTransform(): Int = 43
private var wasmFieldTransform: Int = 43
private var wasmConstantInitializer: Int = 0x4B434D4C

@Suppress("UNUSED_PARAMETER")
private fun wasmTransformedIntrinsic(first: Int, second: Int, result: Int): Int = error("Not intrinsified")

private class WasmDeclarationTransformClass {
    fun value(): Int = 43
}

class WasmCodeGenerationTest {
    private fun identity(value: Int): Int = value

    private fun transformedReturn(): Int = 0x4B434D4D

    @Test
    fun `replaces nested non call expressions`() {
        assertEquals(45, identity(0x4B434D4C))
    }

    @Test
    fun `transforms return expressions and continues normal generation`() {
        assertEquals(46, transformedReturn())
    }

    @Test
    fun `transforms function declarations before generating their bodies`() {
        assertEquals(47, wasmDeclarationTransform())
    }

    @Test
    fun `transforms local variables before generating their initializer`() {
        val wasmLocalTransform = identity(43)
        assertEquals(48, wasmLocalTransform)
    }

    @Test
    fun `transforms files before generating declarations`() {
        assertEquals(49, wasmFileTransform())
    }

    @Test
    fun `transforms classes before generating members`() {
        assertEquals(50, WasmDeclarationTransformClass().value())
    }

    @Test
    fun `transforms fields before generating initializers`() {
        assertEquals(51, wasmFieldTransform)
    }

    @Test
    fun `transforms function bodies before generating statements`() {
        assertEquals(52, wasmBodyTransform())
    }

    @Test
    fun `replaces constant initializers outside function bodies`() {
        assertEquals(45, wasmConstantInitializer)
    }

    @Test
    fun `intrinsics observe transforms and generate nested arguments once in order`() {
        val events = ArrayList<Int>()
        fun argument(value: Int): Int {
            events += value
            return value
        }

        assertEquals(53, wasmTransformedIntrinsic(argument(0x4B434D4C), argument(2), 43))
        assertEquals(listOf(45, 2), events)
    }

    @Test
    fun `unselected expressions retain normal generation and argument order`() {
        val events = ArrayList<Int>()
        fun argument(value: Int): Int {
            events += value
            return value
        }
        fun combine(first: Int, second: Int): Int = first * 10 + second

        assertEquals(12, combine(argument(1), argument(2)))
        assertEquals(listOf(1, 2), events)
        assertEquals(43, identity(43))
    }
}