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

import dev.karmakrafts.kcml.agent.asm.Types
import dev.karmakrafts.kcml.agent.asm.getValue
import org.objectweb.asm.Type
import org.objectweb.asm.tree.AbstractInsnNode
import org.objectweb.asm.tree.AnnotationNode
import org.objectweb.asm.tree.FieldInsnNode
import org.objectweb.asm.tree.InsnList
import org.objectweb.asm.tree.MethodInsnNode
import org.objectweb.asm.tree.VarInsnNode

internal data class Target(
    val owner: Type? = null,
    val name: String? = null,
    val methodOrFieldType: Type? = null,
    val opcode: Int = ANY_OPCODE,
    val index: Int = ANY_INDEX,
    val ordinal: Int = 0,
    val offset: Int = 0
) {
    companion object {
        const val ANY_OPCODE: Int = -1
        const val ANY_INDEX: Int = -1

        fun fromAnnotation(annotation: AnnotationNode): Target = Target(
            owner = annotation.getValue<String>("owner")?.let(Type::getObjectType),
            name = annotation.getValue("name"),
            methodOrFieldType = annotation.getValue<String>("descriptor")?.let(Types::getMethodOrFieldType),
            opcode = annotation.getValue("opcode") ?: ANY_OPCODE,
            index = annotation.getValue("index") ?: ANY_INDEX,
            ordinal = annotation.getValue("ordinal") ?: 0,
            offset = annotation.getValue("offset") ?: 0
        )
    }

    /**
     * Locates the instruction in the given method with the
     * properties given by this target instance.
     * If no instruction can be found, null is returned.
     */
    fun find(instructions: InsnList): AbstractInsnNode? = find(instructions, 0, instructions.size())

    private fun find(instructions: InsnList, startIndex: Int, endIndex: Int): AbstractInsnNode? {
        val possibleTargets = ArrayList<AbstractInsnNode>()
        for (currentIndex in startIndex until endIndex) {
            val insn = instructions[currentIndex]
            val currentOpcode = insn.opcode
            // First we filter by opcode itself
            if (opcode != ANY_OPCODE && currentOpcode != opcode) continue
            // Then we apply specified target filter based on target opcode
            when (insn) {
                is MethodInsnNode -> when {
                    owner != null && insn.owner != owner.internalName -> continue
                    name != null && insn.name != name -> continue
                    methodOrFieldType != null && insn.desc != methodOrFieldType.descriptor -> continue
                }

                is FieldInsnNode -> when {
                    owner != null && insn.owner != owner.internalName -> continue
                    name != null && insn.name != name -> continue
                    methodOrFieldType != null && insn.desc != methodOrFieldType.descriptor -> continue
                }

                is VarInsnNode -> when {
                    index != ANY_INDEX && insn.`var` != index -> continue
                }
            }
            // Buffer all instructions that went through the filters so far
            possibleTargets += insn
        }
        // The ordinal is the nth element in the possibleTarget list
        val targetIndex = possibleTargets.getOrNull(ordinal)?.let(instructions::indexOf) ?: return null
        val offsetIndex = targetIndex + offset
        return if (offsetIndex in startIndex until endIndex) instructions[offsetIndex] else null
    }

    /**
     * Perform a [find] operation within the given slice.
     */
    context(slice: Slice)
    fun findWithin(instructions: InsnList): AbstractInsnNode? {
        val (start, end) = slice.resolve(instructions)
        val startIndex = start?.let(instructions::indexOf) ?: 0
        val endIndex = end?.let(instructions::indexOf) ?: instructions.size()
        return find(instructions, startIndex, endIndex)
    }
}