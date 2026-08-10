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
import org.objectweb.asm.Opcodes.GOTO
import org.objectweb.asm.Opcodes.ICONST_0
import org.objectweb.asm.Opcodes.ISTORE
import org.objectweb.asm.Opcodes.RETURN
import org.objectweb.asm.tree.InsnList
import org.objectweb.asm.tree.InsnNode
import org.objectweb.asm.tree.JumpInsnNode
import org.objectweb.asm.tree.LabelNode
import org.objectweb.asm.tree.VarInsnNode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotSame
import kotlin.test.assertSame

class InsnListExtensionsTest {
    @Test
    fun `copies instructions without moving or sharing nodes`() {
        val target = LabelNode()
        val instructions = InsnList().apply {
            add(VarInsnNode(ALOAD, 2))
            add(JumpInsnNode(GOTO, target))
            add(target)
            add(InsnNode(RETURN))
        }

        val copied = instructions.copy()

        assertEquals(4, instructions.size())
        assertEquals(4, copied.size())
        instructions.toArray().zip(copied.toArray()).forEach { (original, copy) ->
            assertNotSame(original, copy)
        }
        assertSame(target, (instructions[1] as JumpInsnNode).label)
        assertSame(copied[2], (copied[1] as JumpInsnNode).label)

        (copied[0] as VarInsnNode).`var` = 7
        assertEquals(2, (instructions[0] as VarInsnNode).`var`)
    }

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