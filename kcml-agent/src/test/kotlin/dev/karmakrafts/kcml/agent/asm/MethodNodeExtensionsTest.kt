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

import org.objectweb.asm.Opcodes.ACC_FINAL
import org.objectweb.asm.Opcodes.ACC_PUBLIC
import org.objectweb.asm.Opcodes.ACC_STATIC
import org.objectweb.asm.Type
import org.objectweb.asm.tree.AnnotationNode
import org.objectweb.asm.tree.LabelNode
import org.objectweb.asm.tree.LocalVariableNode
import org.objectweb.asm.tree.MethodNode
import org.objectweb.asm.tree.ParameterNode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class MethodNodeExtensionsTest {
    @Test
    fun `restores instance parameters using their local variable slots`() {
        val start = LabelNode()
        val end = LabelNode()
        val method = MethodNode(
            ACC_PUBLIC, "method", "(JDLjava/util/List;)V", "(JDLjava/util/List<Ljava/lang/String;>;)V", null
        ).apply {
            instructions.add(start)
            instructions.add(end)
            parameters = mutableListOf(ParameterNode("stale", 0))
            localVariables = mutableListOf(
                LocalVariableNode("this", "Lexample/Owner;", null, start, end, 0),
                LocalVariableNode("first", "J", null, start, end, 1),
                LocalVariableNode("second", "D", null, start, end, 3),
                LocalVariableNode(
                    "third", "Ljava/util/List;", "Ljava/util/List<Ljava/lang/String;>;", start, end, 5
                )
            )
        }

        method.restoreParameters()

        assertEquals(listOf("first", "second", "third"), method.parameters.map { it.name })
        assertTrue(method.parameters.all { it.access == 0 })
    }

    @Test
    fun `restores static parameters without a receiver slot`() {
        val start = LabelNode()
        val end = LabelNode()
        val method = MethodNode(ACC_PUBLIC or ACC_STATIC, "method", "(ILjava/lang/String;)V", null, null).apply {
            instructions.add(start)
            instructions.add(end)
            localVariables = mutableListOf(
                LocalVariableNode("first", "I", null, start, end, 0),
                LocalVariableNode("second", "Ljava/lang/String;", null, start, end, 1)
            )
        }

        method.restoreParameters()

        assertEquals(listOf("first", "second"), method.parameters.map { it.name })
    }

    @Test
    fun `restores unnamed parameters when local variable metadata is absent`() {
        val method = MethodNode(ACC_PUBLIC, "method", "(ILjava/lang/String;)V", null, null)

        method.restoreParameters()

        assertEquals(2, method.parameters.size)
        assertTrue(method.parameters.all { it.access == 0 })
        assertTrue(method.parameters.all { it.name == null })
    }

    @Test
    fun `finds a matching visible annotation`() {
        val type = Type.getObjectType("example/Annotation")
        val annotation = AnnotationNode(type.descriptor)
        val method = MethodNode(ACC_PUBLIC, "method", "()V", null, null).apply {
            visibleAnnotations = mutableListOf(annotation)
        }

        assertTrue(method.hasAnnotation(type))
        assertSame(annotation, method.getAnnotation(type))
    }

    @Test
    fun `does not find unrelated annotations and rejects required missing annotations`() {
        val method = MethodNode(ACC_PUBLIC, "method", "()V", null, null).apply {
            visibleAnnotations = mutableListOf(AnnotationNode("Lexample/Other;"))
            invisibleAnnotations = mutableListOf(AnnotationNode("Lexample/Other;"))
        }
        val type = Type.getObjectType("example/Missing")

        assertFalse(method.hasAnnotation(type))
        assertNull(method.findVisibleAnnotation(type))
        assertNull(method.findInvisibleAnnotation(type))
        assertFailsWith<IllegalArgumentException> { method.getVisibleAnnotation(type) }
        assertFailsWith<IllegalArgumentException> { method.getInvisibleAnnotation(type) }
        assertFailsWith<IllegalStateException> { method.getAnnotation(type) }
    }

    @Test
    fun `keeps complete parameter names and access flags without debug metadata`() {
        val method = MethodNode(ACC_PUBLIC, "method", "(I)V", null, null).apply {
            parameters = mutableListOf(ParameterNode("declared", ACC_FINAL))
            localVariables = null
        }

        method.restoreParameters()

        assertEquals(listOf("declared"), method.parameters.map { it.name })
        assertEquals(listOf(ACC_FINAL), method.parameters.map { it.access })
    }

    @Test
    fun `restores an empty parameter list for a no argument method`() {
        val method = MethodNode(ACC_PUBLIC, "method", "()V", null, null).apply {
            parameters = mutableListOf(ParameterNode("stale", 0))
        }

        method.restoreParameters()

        assertTrue(method.parameters.isEmpty())
    }

    @Test
    fun `restores names from earliest matching scopes rather than local table order`() {
        val start = LabelNode()
        val later = LabelNode()
        val end = LabelNode()
        val method = MethodNode(ACC_PUBLIC or ACC_STATIC, "method", "(II)V", null, null).apply {
            instructions.add(start)
            instructions.add(later)
            instructions.add(end)
            localVariables = mutableListOf(
                LocalVariableNode("reused", "I", null, later, end, 0),
                LocalVariableNode("wrongType", "F", null, start, end, 0),
                LocalVariableNode("parameter", "I", null, start, later, 0),
                LocalVariableNode("wrongSlot", "I", null, start, end, 2)
            )
        }

        method.restoreParameters()

        assertEquals(listOf("parameter", null), method.parameters.map { it.name })
    }

    @Test
    fun `restores unnamed parameters when the local variable table is null`() {
        val method = MethodNode(ACC_PUBLIC or ACC_STATIC, "method", "(JI)V", null, null).apply {
            localVariables = null
        }

        method.restoreParameters()

        assertEquals(listOf(null, null), method.parameters.map { it.name })
    }

    @Test
    fun `finds invisible annotations and prefers visible annotations for combined lookup`() {
        val type = Type.getObjectType("example/Annotation")
        val invisible = AnnotationNode(type.descriptor)
        val visible = AnnotationNode(type.descriptor)
        val method = MethodNode(ACC_PUBLIC, "method", "()V", null, null).apply {
            invisibleAnnotations = mutableListOf(invisible)
        }

        assertTrue(method.hasInvisibleAnnotation(type))
        assertTrue(method.hasAnnotation(type))
        assertFalse(method.hasVisibleAnnotation(type))
        assertSame(invisible, method.findInvisibleAnnotation(type))
        assertSame(invisible, method.getInvisibleAnnotation(type))
        assertSame(invisible, method.getAnnotation(type))

        method.visibleAnnotations = mutableListOf(visible)

        assertTrue(method.hasVisibleAnnotation(type))
        assertSame(visible, method.findVisibleAnnotation(type))
        assertSame(visible, method.getVisibleAnnotation(type))
        assertSame(visible, method.getAnnotation(type))
    }

    @Test
    fun `handles absent and empty method annotation lists`() {
        val type = Type.getObjectType("example/Annotation")
        val method = MethodNode(ACC_PUBLIC, "method", "()V", null, null)
        for (annotations in listOf(null, mutableListOf<AnnotationNode>())) {
            method.visibleAnnotations = annotations
            method.invisibleAnnotations = annotations

            assertFalse(method.hasAnnotation(type))
            assertNull(method.findVisibleAnnotation(type))
            assertNull(method.findInvisibleAnnotation(type))
        }
    }

    @Test
    fun `finds invisible parameter annotations only at valid matching positions`() {
        val type = Type.getObjectType("example/Annotation")
        val annotation = AnnotationNode(type.descriptor)
        val method = MethodNode(ACC_PUBLIC, "method", "(III)V", null, null)

        assertNull(method.findInvisibleParameterAnnotation(0, type))

        method.invisibleParameterAnnotations = arrayOf(
            mutableListOf(AnnotationNode("Lexample/Other;")), null, mutableListOf(annotation)
        )

        assertNull(method.findInvisibleParameterAnnotation(0, type))
        assertNull(method.findInvisibleParameterAnnotation(1, type))
        assertSame(annotation, method.findInvisibleParameterAnnotation(2, type))
        assertNull(method.findInvisibleParameterAnnotation(-1, type))
        assertNull(method.findInvisibleParameterAnnotation(-2, type))
        assertNull(method.findInvisibleParameterAnnotation(3, type))
    }
}