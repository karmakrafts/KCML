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

package dev.karmakrafts.kcml.test

import kotlin.test.Test
import kotlin.test.assertEquals

private fun jvmIntrinsic(value: Int): Int = error("JVM intrinsic was not replaced: $value")
private fun jvmInitializationCount(): Int = -1
private fun jvmReturnTransform(): Int = 0x4B434D4D
private fun jvmUnchanged(value: Int): Int = value + 1

class JvmCodeGenerationTest {
    @Test
    fun `initializes once for all generated classes`() {
        assertEquals(1, jvmInitializationCount())
    }

    @Test
    fun `replaces nested constant generation`() {
        assertEquals(55, jvmIntrinsic(0x4B434D4C))
    }

    @Test
    fun `transforms returns and continues normal generation`() {
        assertEquals(46, jvmReturnTransform())
    }

    @Test
    fun `transforms local declarations`() {
        val jvmLocalTransform = 43
        assertEquals(48, jvmLocalTransform)
    }

    @Test
    fun `generates nested intrinsic arguments exactly once`() {
        var evaluations = 0
        assertEquals(21, jvmIntrinsic(jvmIntrinsic(run { evaluations++; 1 })))
        assertEquals(1, evaluations)
    }

    @Test
    fun `unrecognized calls use normal generation`() {
        assertEquals(8, jvmUnchanged(7))
    }
}