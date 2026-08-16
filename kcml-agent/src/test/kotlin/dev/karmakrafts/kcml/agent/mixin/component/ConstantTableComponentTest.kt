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
import dev.karmakrafts.kcml.agent.mixin.MixinLoader
import dev.karmakrafts.kcml.api.mixin.MixinBridge
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.InsnNode
import org.objectweb.asm.tree.IntInsnNode
import org.objectweb.asm.tree.LdcInsnNode
import org.objectweb.asm.tree.MethodInsnNode
import org.objectweb.asm.tree.MethodNode
import org.objectweb.asm.tree.VarInsnNode
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ConstantTableComponentTest {
    private val constantTable: MutableMap<String, Any> = MixinBridge.INSTANCE.constantTable

    @BeforeTest
    @AfterTest
    fun clearConstantTable() {
        constantTable.clear()
    }

    private fun createContext(): ComponentContext = ComponentContext(
        target = ClassNode(), loader = MixinLoader(NoopLogger), logger = NoopLogger
    )

    private fun MethodNode.addConstantCall(name: String, getterName: String, returnType: Type) {
        instructions.add(LdcInsnNode(name))
        instructions.add(
            MethodInsnNode(
                Opcodes.INVOKESTATIC,
                Types.Mixin.constantTable.internalName,
                getterName,
                Type.getMethodDescriptor(returnType, Types.string),
                false
            )
        )
    }

    @Test
    fun `replaces all supported constant table calls with optimized instructions`() {
        constantTable += mapOf(
            "string" to "value",
            "byte" to 1.toByte(),
            "short" to 2.toShort(),
            "int" to 3,
            "long" to 4L,
            "float" to 5.5F,
            "double" to 6.5,
            "boolean" to true
        )
        val method = MethodNode().apply {
            addConstantCall("string", "getString", Types.string)
            addConstantCall("byte", "getByte", Type.BYTE_TYPE)
            addConstantCall("short", "getShort", Type.SHORT_TYPE)
            addConstantCall("int", "getInt", Type.INT_TYPE)
            addConstantCall("long", "getLong", Type.LONG_TYPE)
            addConstantCall("float", "getFloat", Type.FLOAT_TYPE)
            addConstantCall("double", "getDouble", Type.DOUBLE_TYPE)
            addConstantCall("boolean", "getBoolean", Type.BOOLEAN_TYPE)
            instructions.add(InsnNode(Opcodes.RETURN))
        }
        val mixinClass = ClassNode().apply { methods = mutableListOf(method) }

        assertTrue(ConstantTableComponent(mixinClass).apply(createContext()))

        assertEquals(
            listOf("value", 3, 4L, 5.5F, 6.5, 1),
            method.instructions.filterIsInstance<LdcInsnNode>().map { instruction -> instruction.cst })
        assertEquals(
            listOf(1, 2),
            method.instructions.filterIsInstance<IntInsnNode>().map { instruction -> instruction.operand })
        assertEquals(
            listOf(
                Opcodes.LDC,
                Opcodes.BIPUSH,
                Opcodes.SIPUSH,
                Opcodes.LDC,
                Opcodes.LDC,
                Opcodes.LDC,
                Opcodes.LDC,
                Opcodes.LDC,
                Opcodes.RETURN
            ), method.instructions.map { instruction -> instruction.opcode })
    }

    @Test
    fun `reports no change without constant table calls`() {
        val method = MethodNode().apply {
            instructions.add(LdcInsnNode("name"))
            instructions.add(
                MethodInsnNode(
                    Opcodes.INVOKESTATIC, "example/Other", "getString", "(Ljava/lang/String;)Ljava/lang/String;", false
                )
            )
            instructions.add(InsnNode(Opcodes.RETURN))
        }
        val mixinClass = ClassNode().apply { methods = mutableListOf(method) }

        assertFalse(ConstantTableComponent(mixinClass).apply(createContext()))
        assertEquals(3, method.instructions.size())
    }

    @Test
    fun `rejects non-literal constant names`() {
        val method = MethodNode().apply {
            instructions.add(VarInsnNode(Opcodes.ALOAD, 0))
            instructions.add(
                MethodInsnNode(
                    Opcodes.INVOKESTATIC,
                    Types.Mixin.constantTable.internalName,
                    "getString",
                    "(Ljava/lang/String;)Ljava/lang/String;",
                    false
                )
            )
        }
        val mixinClass = ClassNode().apply { methods = mutableListOf(method) }

        val error = assertFailsWith<IllegalStateException> {
            ConstantTableComponent(mixinClass).apply(createContext())
        }

        assertTrue(error.message.orEmpty().contains("literal string"))
    }

    @Test
    fun `rejects missing constants`() {
        val method = MethodNode().apply { addConstantCall("missing", "getInt", Type.INT_TYPE) }
        val mixinClass = ClassNode().apply { methods = mutableListOf(method) }

        val error = assertFailsWith<IllegalStateException> {
            ConstantTableComponent(mixinClass).apply(createContext())
        }

        assertTrue(error.message.orEmpty().contains("missing"))
    }

    @Test
    fun `rejects constants whose type does not match the getter`() {
        constantTable["value"] = "not an integer"
        val method = MethodNode().apply { addConstantCall("value", "getInt", Type.INT_TYPE) }
        val mixinClass = ClassNode().apply { methods = mutableListOf(method) }

        val error = assertFailsWith<IllegalStateException> {
            ConstantTableComponent(mixinClass).apply(createContext())
        }

        assertTrue(error.message.orEmpty().contains("getInt"))
    }
}