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
import org.objectweb.asm.Type
import org.objectweb.asm.tree.AnnotationNode
import org.objectweb.asm.tree.MethodNode
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

class MethodNodeExtensionsTest {
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