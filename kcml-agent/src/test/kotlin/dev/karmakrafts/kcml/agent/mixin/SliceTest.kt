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

package dev.karmakrafts.kcml.agent.mixin

import org.objectweb.asm.Opcodes.ALOAD
import org.objectweb.asm.Opcodes.GETFIELD
import org.objectweb.asm.Opcodes.INVOKEVIRTUAL
import org.objectweb.asm.tree.AnnotationNode
import org.objectweb.asm.tree.FieldInsnNode
import org.objectweb.asm.tree.InsnList
import org.objectweb.asm.tree.MethodInsnNode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame

class SliceTest {
    @Test
    fun `reads nested target annotations and supplies default boundaries`() {
        val defaultSlice = Slice.fromAnnotation(AnnotationNode("Lexample/Slice;").apply {
            values = mutableListOf()
        })
        val configuredSlice = Slice.fromAnnotation(AnnotationNode("Lexample/Slice;").apply {
            values = mutableListOf("start", AnnotationNode("Lexample/Target;").apply {
                values = mutableListOf("opcode", ALOAD, "index", 1)
            }, "end", AnnotationNode("Lexample/Target;").apply {
                values = mutableListOf("opcode", GETFIELD, "name", "value")
            })
        })

        assertEquals(Slice(), defaultSlice)
        assertEquals(Target(opcode = ALOAD, index = 1), configuredSlice.start)
        assertEquals(Target(name = "value", opcode = GETFIELD), configuredSlice.end)
    }

    @Test
    fun `resolves boundaries and restricts target lookup to their range`() {
        val start = MethodInsnNode(INVOKEVIRTUAL, "example/Owner", "start", "()V", false)
        val end = MethodInsnNode(INVOKEVIRTUAL, "example/Owner", "end", "()V", false)
        val instructions = InsnList().apply {
            add(MethodInsnNode(INVOKEVIRTUAL, "example/Owner", "before", "()V", false))
            add(start)
            add(FieldInsnNode(GETFIELD, "example/Owner", "value", "I"))
            add(end)
            add(FieldInsnNode(GETFIELD, "example/Owner", "value", "I"))
        }
        val slice = Slice(
            start = Target(name = "start", opcode = INVOKEVIRTUAL), end = Target(name = "end", opcode = INVOKEVIRTUAL)
        )

        assertSame(start, slice.resolve(instructions).first)
        assertSame(end, slice.resolve(instructions).second)
        with(slice) {
            val target = Target(name = "value", opcode = GETFIELD).findWithin(instructions) as FieldInsnNode
            assertEquals("example/Owner", target.owner)
            assertEquals("value", target.name)
            assertEquals("I", target.desc)
        }
    }

    @Test
    fun `findWithin defaults missing boundaries to the complete instruction list`() {
        val first = MethodInsnNode(INVOKEVIRTUAL, "example/Owner", "first", "()V", false)
        val start = MethodInsnNode(INVOKEVIRTUAL, "example/Owner", "start", "()V", false)
        val end = MethodInsnNode(INVOKEVIRTUAL, "example/Owner", "end", "()V", false)
        val last = MethodInsnNode(INVOKEVIRTUAL, "example/Owner", "last", "()V", false)
        val instructions = InsnList().apply {
            add(first)
            add(start)
            add(end)
            add(last)
        }

        assertSame(last, with(Slice()) { Target(name = "last", opcode = INVOKEVIRTUAL).findWithin(instructions) })
        assertSame(last, with(Slice(start = Target(name = "start", opcode = INVOKEVIRTUAL))) {
            Target(name = "last", opcode = INVOKEVIRTUAL).findWithin(instructions)
        })
        assertSame(first, with(Slice(end = Target(name = "end", opcode = INVOKEVIRTUAL))) {
            Target(name = "first", opcode = INVOKEVIRTUAL).findWithin(instructions)
        })
    }

    @Test
    fun `findWithin honors boundaries ordinals and offsets`() {
        val beforeStart = MethodInsnNode(INVOKEVIRTUAL, "example/Owner", "candidate", "()V", false)
        val start = MethodInsnNode(INVOKEVIRTUAL, "example/Owner", "start", "()V", false)
        val firstCandidate = MethodInsnNode(INVOKEVIRTUAL, "example/Owner", "candidate", "()V", false)
        val secondCandidate = MethodInsnNode(INVOKEVIRTUAL, "example/Owner", "candidate", "()V", false)
        val end = MethodInsnNode(INVOKEVIRTUAL, "example/Owner", "end", "()V", false)
        val afterEnd = MethodInsnNode(INVOKEVIRTUAL, "example/Owner", "candidate", "()V", false)
        val instructions = InsnList().apply {
            add(beforeStart)
            add(start)
            add(firstCandidate)
            add(secondCandidate)
            add(end)
            add(afterEnd)
        }
        val slice = Slice(
            start = Target(name = "start", opcode = INVOKEVIRTUAL), end = Target(name = "end", opcode = INVOKEVIRTUAL)
        )

        with(slice) {
            assertSame(start, Target(name = "start", opcode = INVOKEVIRTUAL).findWithin(instructions))
            assertNull(Target(name = "end", opcode = INVOKEVIRTUAL).findWithin(instructions))
            assertSame(
                secondCandidate,
                Target(name = "candidate", opcode = INVOKEVIRTUAL, ordinal = 1).findWithin(instructions)
            )
            assertNull(Target(name = "candidate", opcode = INVOKEVIRTUAL, ordinal = 2).findWithin(instructions))
            assertSame(
                secondCandidate, Target(name = "candidate", opcode = INVOKEVIRTUAL, offset = 1).findWithin(instructions)
            )
            assertNull(
                Target(name = "candidate", opcode = INVOKEVIRTUAL, ordinal = 1, offset = 1).findWithin(
                    instructions
                )
            )
        }
    }

    @Test
    fun `findWithin returns null for empty reversed and unmatched ranges`() {
        val first = MethodInsnNode(INVOKEVIRTUAL, "example/Owner", "first", "()V", false)
        val middle = MethodInsnNode(INVOKEVIRTUAL, "example/Owner", "middle", "()V", false)
        val last = MethodInsnNode(INVOKEVIRTUAL, "example/Owner", "last", "()V", false)
        val instructions = InsnList().apply {
            add(first)
            add(middle)
            add(last)
        }

        assertNull(with(Slice(start = Target(name = "middle"), end = Target(name = "middle"))) {
            Target(name = "middle", opcode = INVOKEVIRTUAL).findWithin(instructions)
        })
        assertNull(with(Slice(start = Target(name = "last"), end = Target(name = "first"))) {
            Target(opcode = INVOKEVIRTUAL).findWithin(instructions)
        })
        assertNull(with(Slice()) { Target(name = "missing", opcode = INVOKEVIRTUAL).findWithin(instructions) })
    }
}