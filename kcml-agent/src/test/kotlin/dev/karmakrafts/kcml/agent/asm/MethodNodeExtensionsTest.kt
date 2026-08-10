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
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

class MethodNodeExtensionsTest {
    @Test
    fun `restores instance parameters using their local variable slots`() {
        val start = LabelNode()
        val end = LabelNode()
        val method = MethodNode(
            ACC_PUBLIC,
            "method",
            "(JDLjava/util/List;)V",
            "(JDLjava/util/List<Ljava/lang/String;>;)V",
            null
        ).apply {
            parameters = mutableListOf(ParameterNode("stale", 0))
            localVariables = mutableListOf(
                LocalVariableNode("this", "Lexample/Owner;", null, start, end, 0),
                LocalVariableNode("first", "J", null, start, end, 1),
                LocalVariableNode("second", "D", null, start, end, 3),
                LocalVariableNode(
                    "third",
                    "Ljava/util/List;",
                    "Ljava/util/List<Ljava/lang/String;>;",
                    start,
                    end,
                    5
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
    fun `does not find a nonmatching visible annotation`() {
        val method = MethodNode(ACC_PUBLIC, "method", "()V", null, null).apply {
            visibleAnnotations = mutableListOf()
        }

        assertFalse(method.hasAnnotation(Type.getObjectType("example/Missing")))
    }
}