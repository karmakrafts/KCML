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

import org.objectweb.asm.Opcodes
import org.objectweb.asm.tree.AnnotationNode
import org.objectweb.asm.tree.InsnNode
import org.objectweb.asm.tree.LabelNode
import org.objectweb.asm.tree.LocalVariableNode
import org.objectweb.asm.tree.MethodNode
import kotlin.test.Test
import kotlin.test.assertEquals

class CaptureTest {
    private fun createTarget(): MethodNode = MethodNode().apply {
        val start = LabelNode()
        val end = LabelNode()
        instructions.add(start)
        instructions.add(InsnNode(Opcodes.NOP))
        instructions.add(end)
        instructions.add(InsnNode(Opcodes.RETURN))
        localVariables = mutableListOf(
            LocalVariableNode("first", "I", null, start, end, 0), LocalVariableNode("second", "J", null, start, end, 2)
        )
    }

    @Test
    fun `reads annotation values and supplies default index`() {
        val defaultCapture = Capture.fromAnnotation(AnnotationNode("Lexample/Capture;").apply {
            values = mutableListOf()
        })
        val configuredCapture = Capture.fromAnnotation(AnnotationNode("Lexample/Capture;").apply {
            values = mutableListOf("name", "value", "index", 3)
        })

        assertEquals(Capture(name = null, index = Capture.ANY_INDEX), defaultCapture)
        assertEquals(Capture(name = "value", index = 3), configuredCapture)
    }

    @Test
    fun `capture scope accounts for insertion before or after a boundary`() {
        val target = createTarget()
        val capture = Capture(name = "first", index = Capture.ANY_INDEX)
        val start = target.instructions.first
        val inside = start.next
        val end = inside.next

        assertEquals(Capture.NOT_FOUND, capture.findStackIndexAt(target, start, Order.BEFORE, null))
        assertEquals(0, capture.findStackIndexAt(target, start, Order.AFTER, null))
        for (order in Order.entries) {
            assertEquals(0, capture.findStackIndexAt(target, inside, order, null))
            assertEquals(Capture.NOT_FOUND, capture.findStackIndexAt(target, end.next, order, null))
        }
        assertEquals(0, capture.findStackIndexAt(target, end, Order.BEFORE, null))
        assertEquals(Capture.NOT_FOUND, capture.findStackIndexAt(target, end, Order.AFTER, null))
    }

    @Test
    fun `explicit capture selectors override the implicit name and must agree with each other`() {
        val target = createTarget()
        val point = target.instructions[1]
        val cases = listOf(
            Capture(null, Capture.ANY_INDEX) to 2,
            Capture("first", Capture.ANY_INDEX) to 0,
            Capture(null, 0) to 0,
            Capture("second", 2) to 2,
            Capture("first", 2) to Capture.NOT_FOUND,
            Capture("missing", Capture.ANY_INDEX) to Capture.NOT_FOUND,
            Capture(null, 1) to Capture.NOT_FOUND
        )

        for ((capture, expected) in cases) {
            assertEquals(expected, capture.findStackIndexAt(target, point, Order.BEFORE, "second"), capture.toString())
        }
    }

    @Test
    fun `returns not found when no local variables are available`() {
        val target = createTarget().apply { localVariables.clear() }

        assertEquals(
            Capture.NOT_FOUND,
            Capture(null, Capture.ANY_INDEX).findStackIndexAt(target, target.instructions[1], Order.BEFORE, null)
        )
    }
}