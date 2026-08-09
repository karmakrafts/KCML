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
import kotlin.test.assertFalse
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
            visibleAnnotations = mutableListOf()
            interfaces = mutableListOf()
            superName = "java/lang/Object"
        }
        val missingType = Type.getObjectType("example/Missing")

        assertFalse(node.hasAnnotation(missingType))
        assertFalse(node.implements(missingType))
        assertFalse(node.extends(missingType))
    }
}