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

import dev.karmakrafts.kcml.agent.log.NoopLogger
import dev.karmakrafts.kcml.agent.mixin.component.AbstractMixinComponent
import dev.karmakrafts.kcml.agent.mixin.component.ComponentContext
import dev.karmakrafts.kcml.agent.mixin.component.DependsOn
import org.objectweb.asm.Type
import org.objectweb.asm.tree.ClassNode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

class MixinTest {
    private open class TestComponent : AbstractMixinComponent() {
        override fun apply(context: ComponentContext): Boolean = false
    }

    private class PrerequisiteComponent : TestComponent()

    @DependsOn(PrerequisiteComponent::class)
    private class IntermediateComponent : TestComponent()

    @DependsOn(IntermediateComponent::class)
    private class DependentComponent : TestComponent()

    private class RepeatedPrerequisiteComponent : TestComponent()

    @DependsOn(RepeatedPrerequisiteComponent::class)
    private class RepeatedDependentComponent : TestComponent()

    private class MissingComponent : TestComponent()

    @DependsOn(MissingComponent::class)
    private class MissingDependentComponent : TestComponent()

    private class RecordingComponent(
        override val retransformable: Boolean = true, private val changed: Boolean = false
    ) : AbstractMixinComponent() {
        val contexts = mutableListOf<ComponentContext>()

        override fun apply(context: ComponentContext): Boolean {
            contexts += context
            return changed
        }
    }

    private fun createMixin(registerComponents: MixinLoader.() -> Unit): Mixin {
        val loader = MixinLoader(NoopLogger).apply(registerComponents)
        return Mixin(
            mixinClass = ClassNode().apply { name = "example/Mixin" },
            target = Type.getObjectType("example/Target"),
            priority = 0,
            logger = NoopLogger,
            loader = loader
        )
    }

    @Test
    fun `sorts components after their transitive dependencies`() {
        assertEquals(listOf(IntermediateComponent::class), DependentComponent().dependencies)
        assertEquals(listOf(PrerequisiteComponent::class), IntermediateComponent().dependencies)
        val mixin = createMixin {
            components.registerComponent { DependentComponent() }
            components.registerComponent { IntermediateComponent() }
            components.registerComponent { PrerequisiteComponent() }
        }

        val componentTypes = mixin.components.map { component -> component::class }
        assertEquals(1, componentTypes.count { it == PrerequisiteComponent::class })
        assertEquals(1, componentTypes.count { it == IntermediateComponent::class })
        assertEquals(1, componentTypes.count { it == DependentComponent::class })
        assertTrue(
            componentTypes.indexOf(PrerequisiteComponent::class) < componentTypes.indexOf(IntermediateComponent::class),
            componentTypes.toString()
        )
        assertTrue(
            componentTypes.indexOf(IntermediateComponent::class) < componentTypes.indexOf(DependentComponent::class),
            componentTypes.toString()
        )
    }

    @Test
    fun `sorts components after every dependency instance`() {
        val mixin = createMixin {
            components.registerComponent { RepeatedDependentComponent() }
            components.registerComponent { RepeatedPrerequisiteComponent() }
            components.registerComponent { RepeatedPrerequisiteComponent() }
        }

        val dependentIndex = mixin.components.indexOfFirst { it is RepeatedDependentComponent }
        val prerequisiteIndices = mixin.components.mapIndexedNotNull { index, component ->
            index.takeIf { component is RepeatedPrerequisiteComponent }
        }
        assertEquals(1, mixin.components.count { it is RepeatedDependentComponent })
        assertEquals(2, prerequisiteIndices.size)
        assertTrue(prerequisiteIndices.all { index -> index < dependentIndex }, mixin.components.toString())
    }

    @Test
    fun `keeps components whose dependencies are absent`() {
        val mixin = createMixin {
            components.registerComponent { MissingDependentComponent() }
        }

        assertTrue(mixin.components.any { component -> component is MissingDependentComponent })
    }

    @Test
    fun `does not apply components to a different target class`() {
        val component = RecordingComponent(changed = true)
        val mixin = createMixin { components.registerComponent { component } }

        assertFalse(mixin.apply(ClassNode().apply { name = "example/Other" }))
        assertTrue(component.contexts.isEmpty())
    }

    @Test
    fun `applies every selected component and combines their change results`() {
        for ((firstChanged, secondChanged) in listOf(false to false, true to false, false to true, true to true)) {
            val first = RecordingComponent(changed = firstChanged)
            val second = RecordingComponent(changed = secondChanged)
            val mixin = createMixin {
                components.registerComponent { first }
                components.registerComponent { second }
            }
            val target = ClassNode().apply { name = "example/Target" }

            assertEquals(firstChanged || secondChanged, mixin.apply(target))
            assertSame(target, first.contexts.single().targetClass)
            assertSame(target, second.contexts.single().targetClass)
        }
    }

    @Test
    fun `separates load time and retransformable components in application contexts`() {
        val loadTime = RecordingComponent(retransformable = false, changed = true)
        val retransformable = RecordingComponent(changed = true)
        val peer = RecordingComponent()
        val mixin = createMixin {
            components.registerComponent { loadTime }
            components.registerComponent { retransformable }
            components.registerComponent { peer }
        }
        val target = ClassNode().apply { name = "example/Target" }

        assertTrue(mixin.apply(target, retransformable = false))
        assertEquals(1, loadTime.contexts.size)
        assertTrue(retransformable.contexts.isEmpty())
        assertTrue(peer.contexts.isEmpty())

        assertTrue(mixin.apply(target))
        assertEquals(1, loadTime.contexts.size)
        val context = retransformable.contexts.single()
        assertTrue(peer in context.otherComponents)
        assertFalse(retransformable in context.otherComponents)
        assertTrue(context.otherComponents.all { it.retransformable })
        assertTrue(loadTime.contexts.single().otherComponents.none { it.retransformable })
    }
}