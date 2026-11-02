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

private var jsConstantInitializer: Int = 0x4B434D4C
private fun jsBodyTransform(): Int = 43

@Suppress("UNUSED_PARAMETER")
private fun jsTransformedIntrinsic(first: Int, second: Int, result: Int): Int = error("Not intrinsified")

class JsCodeGenerationTest {
    private fun identity(value: Int): Int = value

    private fun transformedReturn(): Int = 0x4B434D4D

    private fun replacedReturn(): Int = 0x4B434D4E

    @Test
    fun `replaces nested non call expressions`() {
        assertEquals(45, identity(0x4B434D4C))
    }

    @Test
    fun `transforms return expressions and continues normal generation`() {
        assertEquals(46, transformedReturn())
    }

    @Test
    fun `replaces statement generation with a JavaScript AST node`() {
        assertEquals(47, replacedReturn())
    }

    @Test
    fun `transforms local variables before generating their initializer`() {
        val jsLocalTransform = identity(43)
        assertEquals(48, jsLocalTransform)
    }

    @Test
    fun `replaces constant initializers outside function bodies`() {
        assertEquals(45, jsConstantInitializer)
    }

    @Test
    fun `replaces bodies and generates their nested statements`() {
        assertEquals(52, jsBodyTransform())
    }

    @Test
    fun `intrinsics observe transforms and generate nested arguments once in order`() {
        val events = ArrayList<Int>()
        fun argument(value: Int): Int {
            events += value
            return value
        }

        assertEquals(53, jsTransformedIntrinsic(argument(0x4B434D4C), argument(2), 43))
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