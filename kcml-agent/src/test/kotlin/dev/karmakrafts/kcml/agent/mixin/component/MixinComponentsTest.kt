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
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type
import org.objectweb.asm.tree.AnnotationNode
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.FieldNode
import org.objectweb.asm.tree.MethodNode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertSame

class MixinComponentsTest {
    private class TestComponent : AbstractMixinComponent() {
        override fun apply(context: ComponentContext): Boolean = false
    }

    @Test
    fun `rejects duplicate factories in each registry`() {
        val components = MixinComponents(NoopLogger)
        val type = Type.getObjectType("example/Component")
        val factory: (ClassNode) -> MixinComponent = { TestComponent() }
        components.registerComponent(factory)
        components.registerMethodComponent(type) { _, _, _ -> TestComponent() }
        components.registerFieldComponent(type) { _, _, _ -> TestComponent() }
        components.registerInterfaceComponent(type, factory)

        assertFailsWith<IllegalArgumentException> { components.registerComponent(factory) }
        assertFailsWith<IllegalArgumentException> {
            components.registerMethodComponent(type) { _, _, _ -> TestComponent() }
        }
        assertFailsWith<IllegalArgumentException> {
            components.registerFieldComponent(type) { _, _, _ -> TestComponent() }
        }
    }

    @Test
    fun `rejects duplicate interface factory without replacing the original`() {
        val components = MixinComponents(NoopLogger)
        val type = Type.getObjectType("example/Interface")
        val original = TestComponent()
        val mixinClass = ClassNode().apply { interfaces.add(type.internalName) }
        components.registerInterfaceComponent(type) { original }

        assertFailsWith<IllegalArgumentException> {
            components.registerInterfaceComponent(type) { TestComponent() }
        }
        assertSame(original, components.tryCreateInterfaceComponent(type, mixinClass))
    }

    @Test
    fun `creates interface components only for implemented registered interfaces`() {
        val components = MixinComponents(NoopLogger)
        val type = Type.getObjectType("example/Interface")
        val unknown = Type.getObjectType("example/Unknown")
        val mixinClass = ClassNode()
        val expected = TestComponent()
        components.registerInterfaceComponent(type) {
            assertSame(mixinClass, it)
            expected
        }

        assertNull(components.tryCreateInterfaceComponent(type, mixinClass))
        mixinClass.interfaces.addAll(listOf(type.internalName, unknown.internalName))
        assertSame(expected, components.tryCreateInterfaceComponent(type, mixinClass))
        assertNull(components.tryCreateInterfaceComponent(unknown, mixinClass))
        assertFailsWith<IllegalArgumentException> {
            components.registerInterfaceComponent(Types.Mixin.thisAware) { TestComponent() }
        }
    }

    @Test
    fun `finds and creates method and field components from either annotation visibility`() {
        for (visible in listOf(false, true)) {
            val components = MixinComponents(NoopLogger)
            val type = Type.getObjectType("example/Annotation")
            val mixinClass = ClassNode()
            val method = MethodNode(Opcodes.ACC_STATIC, "method", "()V", null, null)
            val field = FieldNode(Opcodes.ACC_STATIC, "field", "I", null, null)
            val methodAnnotation = AnnotationNode(type.descriptor)
            val fieldAnnotation = AnnotationNode(type.descriptor)
            val methodComponent = TestComponent()
            val fieldComponent = TestComponent()
            components.registerMethodComponent(type) { owner, node, annotation ->
                assertSame(mixinClass, owner)
                assertSame(method, node)
                assertSame(methodAnnotation, annotation)
                methodComponent
            }
            components.registerFieldComponent(type) { owner, node, annotation ->
                assertSame(mixinClass, owner)
                assertSame(field, node)
                assertSame(fieldAnnotation, annotation)
                fieldComponent
            }
            assertNull(components.findMethodComponentType(method))
            assertNull(components.findFieldComponentType(field))
            method.visitAnnotation("Lexample/Unregistered;", visible)
            field.visitAnnotation("Lexample/Unregistered;", visible)
            if (visible) {
                method.visibleAnnotations.add(methodAnnotation)
                field.visibleAnnotations.add(fieldAnnotation)
            }
            else {
                method.invisibleAnnotations.add(methodAnnotation)
                field.invisibleAnnotations.add(fieldAnnotation)
            }

            assertEquals(type, components.findMethodComponentType(method))
            assertEquals(type, components.findFieldComponentType(field))
            assertSame(methodComponent, components.tryCreateMethodComponent(type, mixinClass, method))
            assertSame(fieldComponent, components.tryCreateFieldComponent(type, mixinClass, field))
        }
    }
}