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
import org.objectweb.asm.tree.ClassNode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class ClassNodeExtensionsTest {
    private val annotationType = Type.getObjectType("example/Annotation")
    private val interfaceType = Type.getObjectType("example/Interface")
    private val superclassType = Type.getObjectType("example/Superclass")

    @Test
    fun `exposes dotted name and matching class metadata`() {
        val annotation = AnnotationNode(annotationType.descriptor)
        val node = ClassNode().apply {
            name = "example/deep/Subject"
            visibleAnnotations = mutableListOf(annotation)
            interfaces = mutableListOf(interfaceType.internalName)
            superName = superclassType.internalName
        }

        assertEquals("example.deep.Subject", node.dottedName)
        assertTrue(node.hasAnnotation(annotationType))
        assertSame(annotation, node.getAnnotation(annotationType))
        assertTrue(node.implements(interfaceType))
        assertTrue(node.extends(superclassType))
    }

    @Test
    fun `reports nonmatching class metadata`() {
        val node = ClassNode().apply {
            name = "example/Subject"
            visibleAnnotations = mutableListOf(AnnotationNode(annotationType.descriptor))
            invisibleAnnotations = mutableListOf(AnnotationNode(annotationType.descriptor))
            interfaces = mutableListOf(interfaceType.internalName)
            superName = "java/lang/Object"
        }
        val missingType = Type.getObjectType("example/Missing")

        assertFalse(node.hasAnnotation(missingType))
        assertNull(node.findVisibleAnnotation(missingType))
        assertNull(node.findInvisibleAnnotation(missingType))
        assertFalse(node.implements(missingType))
        assertFalse(node.extends(missingType))
        assertFailsWith<IllegalArgumentException> { node.getVisibleAnnotation(missingType) }
        assertFailsWith<IllegalArgumentException> { node.getInvisibleAnnotation(missingType) }
        assertFailsWith<IllegalStateException> { node.getAnnotation(missingType) }
    }

    @Test
    fun `finds invisible annotations and prefers visible annotations for combined lookup`() {
        val invisible = AnnotationNode(annotationType.descriptor)
        val visible = AnnotationNode(annotationType.descriptor)
        val node = ClassNode().apply {
            invisibleAnnotations = mutableListOf(invisible)
        }

        assertTrue(node.hasInvisibleAnnotation(annotationType))
        assertTrue(node.hasAnnotation(annotationType))
        assertFalse(node.hasVisibleAnnotation(annotationType))
        assertSame(invisible, node.findInvisibleAnnotation(annotationType))
        assertSame(invisible, node.getInvisibleAnnotation(annotationType))
        assertSame(invisible, node.getAnnotation(annotationType))

        node.visibleAnnotations = mutableListOf(visible)

        assertTrue(node.hasVisibleAnnotation(annotationType))
        assertSame(visible, node.findVisibleAnnotation(annotationType))
        assertSame(visible, node.getVisibleAnnotation(annotationType))
        assertSame(visible, node.getAnnotation(annotationType))
    }

    @Test
    fun `handles absent and empty annotation lists and a class without a superclass`() {
        val node = ClassNode()
        for (annotations in listOf(null, mutableListOf<AnnotationNode>())) {
            node.visibleAnnotations = annotations
            node.invisibleAnnotations = annotations

            assertFalse(node.hasAnnotation(annotationType))
            assertNull(node.findVisibleAnnotation(annotationType))
            assertNull(node.findInvisibleAnnotation(annotationType))
        }
        assertFalse(node.extends(superclassType))
        assertFalse(node.implements(interfaceType))
    }
}