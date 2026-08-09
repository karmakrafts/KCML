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

import org.objectweb.asm.Opcodes.ALOAD
import org.objectweb.asm.Opcodes.ICONST_0
import org.objectweb.asm.Opcodes.ISTORE
import org.objectweb.asm.Opcodes.RETURN
import org.objectweb.asm.tree.InsnList
import org.objectweb.asm.tree.InsnNode
import org.objectweb.asm.tree.VarInsnNode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

class InsnListExtensionsTest {
    @Test
    fun `relocates variable indexes and preserves all instructions`() {
        val instructions = InsnList().apply {
            add(VarInsnNode(ALOAD, 5))
            add(InsnNode(ICONST_0))
            add(VarInsnNode(ISTORE, 2))
            add(InsnNode(RETURN))
        }

        val relocated = instructions.relocateStack(7)

        assertSame(instructions, relocated)
        assertEquals(4, relocated.size())
        assertEquals(10, (relocated[0] as VarInsnNode).`var`)
        assertEquals(ICONST_0, relocated[1].opcode)
        assertEquals(7, (relocated[2] as VarInsnNode).`var`)
        assertEquals(RETURN, relocated[3].opcode)
    }

    @Test
    fun `leaves lists without variable instructions unchanged`() {
        val instructions = InsnList().apply {
            add(InsnNode(ICONST_0))
            add(InsnNode(RETURN))
        }

        val relocated = instructions.relocateStack(4)

        assertSame(instructions, relocated)
        assertEquals(2, relocated.size())
        assertEquals(ICONST_0, relocated[0].opcode)
        assertEquals(RETURN, relocated[1].opcode)
    }
}