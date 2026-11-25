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
import org.objectweb.asm.tree.FieldNode
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class FieldNodeExtensionsTest {
    private val annotationType = Type.getObjectType("example/Annotation")

    @Test
    fun `finds invisible annotations and prefers visible annotations for combined lookup`() {
        val invisible = AnnotationNode(annotationType.descriptor)
        val visible = AnnotationNode(annotationType.descriptor)
        val field = FieldNode(ACC_PUBLIC, "field", "I", null, null).apply {
            invisibleAnnotations = mutableListOf(invisible)
        }

        assertTrue(field.hasInvisibleAnnotation(annotationType))
        assertTrue(field.hasAnnotation(annotationType))
        assertFalse(field.hasVisibleAnnotation(annotationType))
        assertSame(invisible, field.findInvisibleAnnotation(annotationType))
        assertSame(invisible, field.getInvisibleAnnotation(annotationType))
        assertSame(invisible, field.getAnnotation(annotationType))

        field.visibleAnnotations = mutableListOf(visible)

        assertTrue(field.hasVisibleAnnotation(annotationType))
        assertSame(visible, field.findVisibleAnnotation(annotationType))
        assertSame(visible, field.getVisibleAnnotation(annotationType))
        assertSame(visible, field.getAnnotation(annotationType))

        field.invisibleAnnotations = null

        assertTrue(field.hasAnnotation(annotationType))
        assertSame(visible, field.getAnnotation(annotationType))
    }

    @Test
    fun `handles absent and empty field annotation lists`() {
        val field = FieldNode(ACC_PUBLIC, "field", "I", null, null)
        for (annotations in listOf(null, mutableListOf<AnnotationNode>())) {
            field.visibleAnnotations = annotations
            field.invisibleAnnotations = annotations

            assertFalse(field.hasAnnotation(annotationType))
            assertNull(field.findVisibleAnnotation(annotationType))
            assertNull(field.findInvisibleAnnotation(annotationType))
        }
    }

    @Test
    fun `does not find unrelated annotations and rejects required missing annotations`() {
        val field = FieldNode(ACC_PUBLIC, "field", "I", null, null).apply {
            visibleAnnotations = mutableListOf(AnnotationNode("Lexample/Other;"))
            invisibleAnnotations = mutableListOf(AnnotationNode("Lexample/Other;"))
        }

        assertFalse(field.hasAnnotation(annotationType))
        assertNull(field.findVisibleAnnotation(annotationType))
        assertNull(field.findInvisibleAnnotation(annotationType))
        assertFailsWith<IllegalArgumentException> { field.getVisibleAnnotation(annotationType) }
        assertFailsWith<IllegalArgumentException> { field.getInvisibleAnnotation(annotationType) }
        assertFailsWith<IllegalStateException> { field.getAnnotation(annotationType) }
    }
}