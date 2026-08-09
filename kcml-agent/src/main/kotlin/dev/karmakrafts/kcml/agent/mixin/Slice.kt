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
import org.objectweb.asm.tree.InsnList

internal data class Slice( // @formatter:off
    val start: Target = Target(),
    val end: Target = Target()
) { // @formatter:on
    companion object {
        fun fromAnnotation(node: AnnotationNode): Slice = Slice(
            start = node.getValue<AnnotationNode>("start")?.let(Target::fromAnnotation) ?: Target(),
            end = node.getValue<AnnotationNode>("end")?.let(Target::fromAnnotation) ?: Target()
        )
    }

    fun resolve(instructions: InsnList): Pair<AbstractInsnNode?, AbstractInsnNode?> =
        start.takeUnless { it == Target() }?.find(instructions) to end.takeUnless { it == Target() }?.find(instructions)
}