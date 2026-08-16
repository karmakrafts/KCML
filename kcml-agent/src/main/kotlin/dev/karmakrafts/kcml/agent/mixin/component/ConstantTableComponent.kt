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
import dev.karmakrafts.kcml.api.mixin.MixinBridge
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.InsnList
import org.objectweb.asm.tree.IntInsnNode
import org.objectweb.asm.tree.LdcInsnNode
import org.objectweb.asm.tree.MethodInsnNode

@DependsOn(InjectComponent::class)
internal class ConstantTableComponent(
    private val mixinClass: ClassNode
) : AbstractMixinComponent() {
    companion object {
        private val getterDescriptors: Map<String, String> = mapOf(
            "getString" to Type.getMethodDescriptor(Types.string, Types.string),
            "getByte" to Type.getMethodDescriptor(Type.BYTE_TYPE, Types.string),
            "getShort" to Type.getMethodDescriptor(Type.SHORT_TYPE, Types.string),
            "getInt" to Type.getMethodDescriptor(Type.INT_TYPE, Types.string),
            "getLong" to Type.getMethodDescriptor(Type.LONG_TYPE, Types.string),
            "getFloat" to Type.getMethodDescriptor(Type.FLOAT_TYPE, Types.string),
            "getDouble" to Type.getMethodDescriptor(Type.DOUBLE_TYPE, Types.string),
            "getBoolean" to Type.getMethodDescriptor(Type.BOOLEAN_TYPE, Types.string)
        )
    }

    private fun MethodInsnNode.isConstantTableCall(): Boolean =
        opcode == Opcodes.INVOKESTATIC && owner == Types.Mixin.constantTable.internalName && getterDescriptors[name] == desc

    private fun MethodInsnNode.findConstantName(): LdcInsnNode {
        var instruction = previous
        while (instruction != null && instruction.opcode == -1) instruction = instruction.previous
        return checkNotNull(instruction as? LdcInsnNode) {
            "ConstantTable.$name() requires a literal string constant name"
        }.also { constant ->
            check(constant.cst is String) { "ConstantTable.$name() requires a literal string constant name" }
        }
    }

    private fun MethodInsnNode.resolveConstant(name: String): Any {
        val value = checkNotNull(MixinBridge.INSTANCE.constantTable[name]) {
            "Mixin constant $name is not defined"
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
        } ?: error("Mixin constant $name is incompatible with ConstantTable.${this.name}()")
    }

    private fun InsnList.processConstantTable(): Int {
        val calls = filterIsInstance<MethodInsnNode>().filter { instruction -> instruction.isConstantTableCall() }
        for (call in calls) {
            val constantName = call.findConstantName()
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
        val (_, _, logger) = context
        logger.info { "Processing references to ConstantTable" }
        val referenceCount = mixinClass.methods.sumOf { method -> method.instructions.processConstantTable() }
        if (referenceCount == 0) return false
        logger.info { "Replaced $referenceCount ConstantTable references" }
        return true
    }
}