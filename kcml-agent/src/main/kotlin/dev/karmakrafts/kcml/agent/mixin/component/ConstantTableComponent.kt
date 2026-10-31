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
import dev.karmakrafts.kcml.agent.asm.previousInstruction
import dev.karmakrafts.kcml.api.mixin.MixinBridge
import org.objectweb.asm.Opcodes
import org.objectweb.asm.tree.InsnList
import org.objectweb.asm.tree.IntInsnNode
import org.objectweb.asm.tree.LdcInsnNode
import org.objectweb.asm.tree.MethodInsnNode

@DependsOn(InjectComponent::class)
internal class ConstantTableComponent : AbstractMixinComponent() {
    companion object {
        private val getterDescriptors: Map<String, String> = mapOf(
            "getString" to Types.Mixin.getStringConstant.descriptor,
            "getByte" to Types.Mixin.getByteConstant.descriptor,
            "getShort" to Types.Mixin.getShortConstant.descriptor,
            "getInt" to Types.Mixin.getIntConstant.descriptor,
            "getLong" to Types.Mixin.getLongConstant.descriptor,
            "getFloat" to Types.Mixin.getFloatConstant.descriptor,
            "getDouble" to Types.Mixin.getDoubleConstant.descriptor,
            "getBoolean" to Types.Mixin.getBooleanConstant.descriptor
        )
    }

    private val mixinSource = MixinBridge.INSTANCE.mixinSource

    private fun MethodInsnNode.isConstantTableCall(): Boolean =
        opcode == Opcodes.INVOKESTATIC && owner == Types.Mixin.constantTable.internalName && getterDescriptors[name] == desc

    private fun MethodInsnNode.findConstantArgument(): LdcInsnNode {
        val instruction = previousInstruction
        return checkNotNull(instruction as? LdcInsnNode) {
            "ConstantTable.$name() requires a literal string constant name"
        }.also { constant ->
            check(constant.cst is String) { "ConstantTable.$name() requires a literal string constant name" }
        }
    }

    private fun MethodInsnNode.resolveConstant(name: String): Any {
        val source =
            checkNotNull(mixinSource) { "No mixin source was loading when the constant table component was created" }
        val value = checkNotNull(MixinBridge.INSTANCE.constantTable[source]?.get(name)) {
            "Mixin constant ${source.sourceName}:$name is not defined"
        }
        return when (this.name) {
            "getString" -> value as? String
            "getByte" -> value as? Byte
            "getShort" -> value as? Short
            "getInt" -> value as? Int
            "getLong" -> value as? Long
            "getFloat" -> value as? Float
            "getDouble" -> value as? Double
            "getBoolean" -> (value as? Boolean)?.let { constant -> if (constant) 1 else 0 }
            else -> null
        } ?: error("Mixin constant ${source.sourceName}:$name is incompatible with ConstantTable.${this.name}()")
    }

    private fun InsnList.processConstantTable(): Int {
        val calls = filterIsInstance<MethodInsnNode>().filter { instruction -> instruction.isConstantTableCall() }
        for (call in calls) {
            val constantName = call.findConstantArgument()
            val value = call.resolveConstant(constantName.cst as String)
            remove(constantName)
            set(
                call, when (value) {
                    is Byte -> IntInsnNode(Opcodes.BIPUSH, value.toInt())
                    is Short -> IntInsnNode(Opcodes.SIPUSH, value.toInt())
                    else -> LdcInsnNode(value)
                }
            )
        }
        return calls.size
    }

    override fun apply(context: ComponentContext): Boolean {
        val [targetClass, _, logger] = context
        logger.info { "Processing references to ConstantTable" }
        val referenceCount = targetClass.methods.sumOf { method -> method.instructions.processConstantTable() }
        if (referenceCount == 0) return false
        logger.info { "Replaced $referenceCount ConstantTable references" }
        return true
    }
}