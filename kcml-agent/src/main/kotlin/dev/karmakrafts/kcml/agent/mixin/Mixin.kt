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

import dev.karmakrafts.kcml.agent.log.Logger
import org.objectweb.asm.Type
import org.objectweb.asm.tree.ClassNode

internal data class Mixin( // @formatter:off
    val mixinClass: ClassNode,
    val target: Type,
    val priority: Int,
    private val logger: Logger,
    private val loader: MixinLoader
) : Comparable<Mixin> { // @formatter:on
    val components: List<MixinComponent> = buildList {
        // Handle creating all required method based mixin components
        val components = loader.components
        for (method in mixinClass.methods) {
            val componentType = components.findMixinComponentType(method) ?: continue
            this += components.tryCreateComponent(componentType, mixinClass, method) ?: continue
        }
    }

    fun apply(classNode: ClassNode): Boolean {
        if (classNode.name != target.internalName) return false
        val componentContext = ComponentContext( // @formatter:off
            target = classNode,
            loader = loader,
            logger = logger
        ) // @formatter:on
        var wasChanged = false
        for (component in components) {
            wasChanged = wasChanged or component.apply(componentContext)
        }
        return wasChanged
    }

    override operator fun compareTo(other: Mixin): Int = priority.compareTo(other.priority)
}