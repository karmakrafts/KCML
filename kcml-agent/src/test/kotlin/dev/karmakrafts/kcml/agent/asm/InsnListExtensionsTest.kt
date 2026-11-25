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
import org.objectweb.asm.Opcodes.F_SAME
import org.objectweb.asm.Opcodes.GOTO
import org.objectweb.asm.Opcodes.ICONST_0
import org.objectweb.asm.Opcodes.ISTORE
import org.objectweb.asm.Opcodes.RETURN
import org.objectweb.asm.tree.FrameNode
import org.objectweb.asm.tree.InsnList
import org.objectweb.asm.tree.InsnNode
import org.objectweb.asm.tree.JumpInsnNode
import org.objectweb.asm.tree.LabelNode
import org.objectweb.asm.tree.LineNumberNode
import org.objectweb.asm.tree.TableSwitchInsnNode
import org.objectweb.asm.tree.VarInsnNode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotSame
import kotlin.test.assertSame
import kotlin.test.assertTrue

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
    fun `does not relocate exempt variable instructions`() {
        val exempt = VarInsnNode(ALOAD, 3)
        val instructions = InsnList().apply {
            add(exempt)
            add(VarInsnNode(ALOAD, 5))
            add(VarInsnNode(ISTORE, 8))
        }

        instructions.relocateStack(10, setOf(exempt))

        assertEquals(3, exempt.`var`)
        assertEquals(10, (instructions[1] as VarInsnNode).`var`)
        assertEquals(13, (instructions[2] as VarInsnNode).`var`)
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

    @Test
    fun `copy remaps shared switch targets and debug metadata to copied labels`() {
        val target = LabelNode()
        val instructions = InsnList().apply {
            add(TableSwitchInsnNode(0, 1, target, target, target))
            add(target)
            add(LineNumberNode(12, target))
            add(FrameNode(F_SAME, 0, null, 0, null))
            add(InsnNode(RETURN))
        }

        val copied = instructions.copy()
        val copiedTarget = copied[1] as LabelNode
        val copiedSwitch = copied[0] as TableSwitchInsnNode

        assertNotSame(target, copiedTarget)
        assertSame(copiedTarget, copiedSwitch.dflt)
        assertTrue(copiedSwitch.labels.all { it === copiedTarget })
        assertEquals(2, copiedSwitch.labels.size)
        assertEquals(12, (copied[2] as LineNumberNode).line)
        assertSame(copiedTarget, (copied[2] as LineNumberNode).start)
        assertEquals(F_SAME, (copied[3] as FrameNode).type)
        assertEquals(RETURN, copied[4].opcode)
    }

    @Test
    fun `handles empty instruction lists`() {
        val instructions = InsnList()

        assertEquals(0, instructions.copy().size())
        assertSame(instructions, instructions.relocateStack(3))
        assertEquals(0, emptyList<InsnNode>().toInsnList().size())
    }

    @Test
    fun `converts a list in order without cloning nodes`() {
        val first = InsnNode(ICONST_0)
        val last = InsnNode(RETURN)

        val instructions = listOf(first, last).toInsnList()

        assertEquals(2, instructions.size())
        assertSame(first, instructions.first)
        assertSame(last, instructions.last)
        assertSame(last, first.next)
    }

    @Test
    fun `leaves variables unchanged when every variable is exempt`() {
        val variable = VarInsnNode(ALOAD, 5)
        val instructions = InsnList().apply { add(variable) }

        assertSame(instructions, instructions.relocateStack(0, setOf(variable)))
        assertEquals(5, variable.`var`)
    }

    @Test
    fun `can relocate variables to lower slots while preserving gaps`() {
        val instructions = InsnList().apply {
            add(VarInsnNode(ALOAD, 7))
            add(VarInsnNode(ISTORE, 4))
        }

        instructions.relocateStack(0)

        assertEquals(3, (instructions[0] as VarInsnNode).`var`)
        assertEquals(0, (instructions[1] as VarInsnNode).`var`)
    }
}