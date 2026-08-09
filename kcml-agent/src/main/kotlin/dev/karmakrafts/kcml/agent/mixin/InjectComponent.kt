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

import dev.karmakrafts.kcml.agent.asm.dottedName
import dev.karmakrafts.kcml.agent.asm.getValue
import org.objectweb.asm.Type
import org.objectweb.asm.tree.AnnotationNode
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.MethodNode

/**
 * A mixin component for injection constructed from the `Inject` annotation exposed by the runtime API.
 */
internal data class InjectComponent( // @formatter:off
    val mixinClass: ClassNode,
    val mixinMethod: MethodNode,
    val name: String,
    val descriptor: Type?,
    val slice: Slice,
    val target: Target
) : MixinComponent { // @formatter:on
    companion object {
        fun fromAnnotation( // @formatter:off
            mixinClass: ClassNode,
            mixinMethod: MethodNode,
            node: AnnotationNode
        ): InjectComponent = InjectComponent( // @formatter:on
            mixinClass = mixinClass,
            mixinMethod = mixinMethod,
            name = requireNotNull(node.getValue("name")) { "InjectComponent requires name" },
            descriptor = node.getValue<String>("descriptor")?.let(Type::getMethodType),
            slice = node.getValue<AnnotationNode>("slice")?.let(Slice::fromAnnotation) ?: Slice(),
            target = node.getValue<AnnotationNode>("target")?.let(Target::fromAnnotation) ?: Target()
        )
    }

    private fun injectIntoTarget(targetClass: ClassNode, targetMethod: MethodNode) {
        val instructions = targetMethod.instructions
        val needle = with(slice) { target.findWithin(instructions) }
            ?: error("Could not find injection target for ${targetClass.dottedName}.${targetMethod.name}")
        // TODO: implement this
    }

    override fun apply(target: ClassNode): Boolean {
        // First we need to find the target method
        for (targetMethod in target.methods) when {
            targetMethod.name != name -> continue
            descriptor != null && targetMethod.desc != descriptor.descriptor -> continue
            else -> {
                injectIntoTarget(target, targetMethod)
                return true
            }
        }
        return false
    }
}