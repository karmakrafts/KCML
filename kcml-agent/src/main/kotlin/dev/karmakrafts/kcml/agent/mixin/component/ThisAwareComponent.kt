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
import dev.karmakrafts.kcml.agent.asm.implements
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.InsnList
import org.objectweb.asm.tree.MethodInsnNode
import org.objectweb.asm.tree.VarInsnNode

internal class ThisAwareComponent(
    private val mixinClass: ClassNode
) : MixinComponent {
    private fun InsnList.findGetThisCalls(): List<MethodInsnNode> {
        val getThisDescriptor = Type.getMethodDescriptor(Types.any)
        return filterIsInstance<MethodInsnNode>().filter { instruction -> // @formatter:off
            instruction.opcode == Opcodes.INVOKEVIRTUAL
                && instruction.owner == mixinClass.name
                && instruction.name == "getThis"
                && instruction.desc == getThisDescriptor
        } // @formatter:on
    }

    private fun MethodInsnNode.findReceiver(): VarInsnNode {
        var receiver = previous
        while (receiver != null && receiver.opcode == -1) receiver = receiver.previous
        return checkNotNull(receiver as? VarInsnNode) {
            "ThisAware.getThis() requires the mixin receiver from a local"
        }.also { instruction ->
            check(instruction.opcode == Opcodes.ALOAD) {
                "ThisAware.getThis() requires the mixin receiver from a local"
            }
        }
    }

    internal fun prepareInjection(instructions: InsnList, relocated: MutableSet<VarInsnNode>) {
        if (!mixinClass.implements(Types.Mixin.thisAware)) return
        for (call in instructions.findGetThisCalls()) {
            val receiver = call.findReceiver()
            check(receiver.`var` == 0) {
                "ThisAware.getThis() requires the mixin receiver from local 0"
            }
            relocated += receiver
        }
    }

    private fun InsnList.processThisAware(): Int {
        val calls = findGetThisCalls()
        for (call in calls) {
            val receiver = call.findReceiver()
            remove(receiver)
            set(call, VarInsnNode(Opcodes.ALOAD, 0))
        }
        return calls.size
    }

    override fun apply(context: ComponentContext): Boolean {
        val (targetClass, _, logger) = context
        logger.info { "Mixin is this-aware, processing references to getThis()" }
        val callCount = targetClass.methods.sumOf { method -> method.instructions.processThisAware() }
        if (callCount == 0) return false
        logger.info { "Processed $callCount getThis calls" }
        return true
    }
}