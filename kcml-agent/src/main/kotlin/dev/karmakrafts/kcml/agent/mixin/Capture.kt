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

import dev.karmakrafts.kcml.agent.asm.getValue
import org.objectweb.asm.tree.AbstractInsnNode
import org.objectweb.asm.tree.AnnotationNode
import org.objectweb.asm.tree.MethodNode

internal data class Capture( // @formatter:off
    val name: String?,
    val index: Int
) { // @formatter:on
    companion object {
        const val ANY_INDEX: Int = -1
        const val NOT_FOUND: Int = -1

        fun fromAnnotation(annotation: AnnotationNode): Capture = Capture( // @formatter:off
            name = annotation.getValue("name"),
            index = annotation.getValue("index") ?: ANY_INDEX
        ) // @formatter:on
    }

    fun findStackIndexAt( // @formatter:off
        targetMethod: MethodNode,
        injectionPoint: AbstractInsnNode,
        order: Order,
        implicitName: String?
    ): Int { // @formatter:on
        val injectionIndex = targetMethod.instructions.indexOf(injectionPoint)
        for (local in targetMethod.localVariables) {
            val startIndex = targetMethod.instructions.indexOf(local.start)
            val endIndex = targetMethod.instructions.indexOf(local.end)
            val isLive = when (order) {
                Order.BEFORE -> startIndex < injectionIndex && injectionIndex <= endIndex
                Order.AFTER -> startIndex <= injectionIndex && injectionIndex < endIndex
            }
            when {
                !isLive -> continue
                index != ANY_INDEX && index != local.index -> continue
                name != null && name != local.name -> continue
                implicitName != null && index == ANY_INDEX && name == null && local.name != implicitName -> continue
                else -> return local.index
            }
        }
        return NOT_FOUND
    }
}