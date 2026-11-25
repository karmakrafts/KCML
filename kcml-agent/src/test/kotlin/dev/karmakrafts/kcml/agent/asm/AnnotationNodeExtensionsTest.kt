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

import org.objectweb.asm.Type
import org.objectweb.asm.tree.AnnotationNode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AnnotationNodeExtensionsTest {
    private enum class Order {
        BEFORE, AFTER
    }

    @Test
    fun `gets a value when its name and type match`() {
        val node = AnnotationNode("Lexample/Annotation;").apply {
            values = mutableListOf("name", "value", "count", 3)
        }

        assertEquals("value", node.getValue<String>("name"))
        assertEquals(3, node.getValue<Int>("count"))
    }

    @Test
    fun `returns null for absent names and incompatible value types`() {
        val node = AnnotationNode("Lexample/Annotation;").apply {
            values = mutableListOf("name", "value")
        }

        assertNull(node.getValue<String>("missing"))
        assertNull(node.getValue<Int>("name"))
    }

    @Test
    fun `returns null when the annotation has no values`() {
        assertNull(AnnotationNode("Lexample/Annotation;").getValue<String>("name"))
    }

    @Test
    fun `decodes ASM enum values by constant name`() {
        val node = AnnotationNode("Lexample/Annotation;").apply {
            visitEnum("order", Type.getDescriptor(Order::class.java), "AFTER")
        }

        assertEquals(Order.AFTER, node.getValue<Order>("order"))
        assertNull(node.getValue<String>("order"))
    }

    @Test
    fun `returns null for an unknown enum constant`() {
        val node = AnnotationNode("Lexample/Annotation;").apply {
            visitEnum("order", Type.getDescriptor(Order::class.java), "UNKNOWN")
        }

        assertNull(node.getValue<Order>("order"))
    }
}