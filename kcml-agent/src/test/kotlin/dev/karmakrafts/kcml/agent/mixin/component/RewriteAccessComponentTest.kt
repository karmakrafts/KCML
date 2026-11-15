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

package dev.karmakrafts.kcml.agent.mixin.component

import dev.karmakrafts.kcml.agent.asm.Types
import dev.karmakrafts.kcml.agent.log.NoopLogger
import dev.karmakrafts.kcml.agent.mixin.MixinLoader
import org.objectweb.asm.Opcodes
import org.objectweb.asm.tree.AnnotationNode
import org.objectweb.asm.tree.ClassNode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RewriteAccessComponentTest {
    private fun createContext(targetClass: ClassNode): ComponentContext = ComponentContext(
        targetClass = targetClass, loader = MixinLoader(NoopLogger), logger = NoopLogger, otherComponents = emptyList()
    )

    private fun createAnnotation(value: Any): AnnotationNode =
        AnnotationNode(Types.Mixin.rewriteAccess.descriptor).apply {
            values = mutableListOf("value", value)
        }

    private fun createMixinClass(annotation: AnnotationNode, visible: Boolean): ClassNode = ClassNode().apply {
        if (visible) {
            visibleAnnotations = mutableListOf(annotation)
        }
        else {
            invisibleAnnotations = mutableListOf(annotation)
        }
    }

    @Test
    fun `rewrites access flags from visible and invisible annotations`() {
        val cases = listOf(
            true to 0, false to (Opcodes.ACC_PRIVATE or Opcodes.ACC_FINAL)
        )
        for ([visible, newAccess] in cases) {
            val mixinClass = createMixinClass(createAnnotation(newAccess), visible)
            val targetClass = ClassNode().apply { access = Opcodes.ACC_PUBLIC or Opcodes.ACC_ABSTRACT }

            assertTrue(RewriteAccessComponent(mixinClass).apply(createContext(targetClass)))

            assertEquals(newAccess, targetClass.access)
        }
    }

    @Test
    fun `prefers visible annotation when both visibilities are present`() {
        val mixinClass = ClassNode().apply {
            visibleAnnotations = mutableListOf(createAnnotation(Opcodes.ACC_PRIVATE))
            invisibleAnnotations = mutableListOf(createAnnotation(Opcodes.ACC_PUBLIC))
        }
        val targetClass = ClassNode().apply { access = Opcodes.ACC_FINAL }

        assertTrue(RewriteAccessComponent(mixinClass).apply(createContext(targetClass)))

        assertEquals(Opcodes.ACC_PRIVATE, targetClass.access)
    }

    @Test
    fun `does not change access without a rewrite access annotation`() {
        val mixinClasses = listOf(
            ClassNode(),
            ClassNode().apply { visibleAnnotations = mutableListOf(AnnotationNode("Lexample/Unrelated;")) })
        for (mixinClass in mixinClasses) {
            val targetClass = ClassNode().apply { access = Opcodes.ACC_PUBLIC or Opcodes.ACC_FINAL }

            assertFalse(RewriteAccessComponent(mixinClass).apply(createContext(targetClass)))

            assertEquals(Opcodes.ACC_PUBLIC or Opcodes.ACC_FINAL, targetClass.access)
        }
    }

    @Test
    fun `does not change access when rewrite access value is missing or not an integer`() {
        val annotations = listOf(
            AnnotationNode(Types.Mixin.rewriteAccess.descriptor), createAnnotation("not an integer")
        )
        for (annotation in annotations) {
            val mixinClass = createMixinClass(annotation, visible = true)
            val targetClass = ClassNode().apply { access = Opcodes.ACC_PUBLIC or Opcodes.ACC_FINAL }

            assertFalse(RewriteAccessComponent(mixinClass).apply(createContext(targetClass)))

            assertEquals(Opcodes.ACC_PUBLIC or Opcodes.ACC_FINAL, targetClass.access)
        }
    }

    @Test
    fun `clears permitted subclasses when super access is removed`() {
        val mixinClass = createMixinClass(createAnnotation(Opcodes.ACC_PUBLIC), visible = true)
        val targetClass = ClassNode().apply {
            access = Opcodes.ACC_PUBLIC or Opcodes.ACC_SUPER
            permittedSubclasses = mutableListOf("example/Child")
        }

        assertTrue(RewriteAccessComponent(mixinClass).apply(createContext(targetClass)))

        assertEquals(Opcodes.ACC_PUBLIC, targetClass.access)
        assertTrue(targetClass.permittedSubclasses.orEmpty().isEmpty())
    }

    @Test
    fun `preserves permitted subclasses unless super access is removed`() {
        val cases = listOf(
            Opcodes.ACC_PUBLIC to Opcodes.ACC_PRIVATE,
            Opcodes.ACC_PUBLIC to (Opcodes.ACC_PRIVATE or Opcodes.ACC_SUPER),
            (Opcodes.ACC_PUBLIC or Opcodes.ACC_SUPER) to (Opcodes.ACC_PRIVATE or Opcodes.ACC_SUPER)
        )
        for ([oldAccess, newAccess] in cases) {
            val mixinClass = createMixinClass(createAnnotation(newAccess), visible = true)
            val permittedSubclasses = mutableListOf("example/Child")
            val targetClass = ClassNode().apply {
                access = oldAccess
                this.permittedSubclasses = permittedSubclasses
            }

            assertTrue(RewriteAccessComponent(mixinClass).apply(createContext(targetClass)))

            assertEquals(listOf("example/Child"), targetClass.permittedSubclasses)
        }
    }

    @Test
    fun `removes super access when permitted subclasses are null`() {
        val mixinClass = createMixinClass(createAnnotation(Opcodes.ACC_PUBLIC), visible = true)
        val targetClass = ClassNode().apply { access = Opcodes.ACC_PUBLIC or Opcodes.ACC_SUPER }

        assertTrue(RewriteAccessComponent(mixinClass).apply(createContext(targetClass)))

        assertEquals(Opcodes.ACC_PUBLIC, targetClass.access)
        assertEquals(null, targetClass.permittedSubclasses)
    }
}