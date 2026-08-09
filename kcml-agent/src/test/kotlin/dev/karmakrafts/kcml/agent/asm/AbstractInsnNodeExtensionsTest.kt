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

package dev.karmakrafts.kcml.agent.asm

import org.objectweb.asm.Opcodes.ICONST_0
import org.objectweb.asm.Opcodes.ICONST_1
import org.objectweb.asm.Opcodes.RETURN
import org.objectweb.asm.tree.InsnList
import org.objectweb.asm.tree.InsnNode
import kotlin.test.Test
import kotlin.test.assertNull
import kotlin.test.assertSame

class AbstractInsnNodeExtensionsTest {
    @Test
    fun `shifts to instructions within list bounds`() {
        val first = InsnNode(ICONST_0)
        val second = InsnNode(ICONST_1)
        val third = InsnNode(RETURN)
        val instructions = InsnList().apply {
            add(first)
            add(second)
            add(third)
        }

        assertSame(third, first.shift(instructions, 2))
        assertSame(first, second.shift(instructions, -1))
        assertSame(second, second.shift(instructions, 0))
    }

    @Test
    fun `returns null when shifting outside list or from an unrelated instruction`() {
        val first = InsnNode(ICONST_0)
        val last = InsnNode(RETURN)
        val instructions = InsnList().apply {
            add(first)
            add(last)
        }

        assertNull(first.shift(instructions, -1))
        assertNull(last.shift(instructions, 1))
        assertNull(InsnNode(ICONST_1).shift(instructions, 0))
    }
}