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

import dev.karmakrafts.kcml.agent.log.NoopLogger
import dev.karmakrafts.kcml.agent.mixin.MixinLoader
import org.objectweb.asm.Opcodes
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.InsnNode
import org.objectweb.asm.tree.LabelNode
import org.objectweb.asm.tree.MethodInsnNode
import org.objectweb.asm.tree.MethodNode
import org.objectweb.asm.tree.VarInsnNode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ThisAwareComponentTest {
    private fun createContext(method: MethodNode): ComponentContext = ComponentContext(
        target = ClassNode().apply { methods = mutableListOf(method) },
        loader = MixinLoader(NoopLogger),
        logger = NoopLogger
    )

    @Test
    fun `replaces getThis calls with target receiver loads`() {
        val mixinClass = ClassNode().apply { name = "example/Mixin" }
        val targetMethod = MethodNode().apply {
            instructions.add(VarInsnNode(Opcodes.ALOAD, 7))
            instructions.add(LabelNode())
            instructions.add(
                MethodInsnNode(
                    Opcodes.INVOKEVIRTUAL, mixinClass.name, "getThis", "()Ljava/lang/Object;", false
                )
            )
            instructions.add(InsnNode(Opcodes.POP))
            instructions.add(VarInsnNode(Opcodes.ALOAD, 3))
            instructions.add(
                MethodInsnNode(
                    Opcodes.INVOKEVIRTUAL, "example/Other", "getThis", "()Ljava/lang/Object;", false
                )
            )
            instructions.add(InsnNode(Opcodes.POP))
            instructions.add(InsnNode(Opcodes.RETURN))
        }
        val component = ThisAwareComponent(mixinClass)

        assertTrue(component.apply(createContext(targetMethod)))

        assertEquals(
            listOf(
            Opcodes.ALOAD, Opcodes.POP, Opcodes.ALOAD, Opcodes.INVOKEVIRTUAL, Opcodes.POP, Opcodes.RETURN
        ), targetMethod.instructions.filter { it.opcode >= 0 }.map { it.opcode })
        assertEquals(listOf(0, 3), targetMethod.instructions.filterIsInstance<VarInsnNode>().map { it.`var` })
    }

    @Test
    fun `reports no change without getThis calls`() {
        val targetMethod = MethodNode().apply {
            instructions.add(InsnNode(Opcodes.RETURN))
        }

        assertFalse(ThisAwareComponent(ClassNode().apply { name = "example/Mixin" }).apply(createContext(targetMethod)))
    }

    @Test
    fun `rejects getThis calls without a receiver load`() {
        val mixinClass = ClassNode().apply { name = "example/Mixin" }
        val targetMethod = MethodNode().apply {
            instructions.add(InsnNode(Opcodes.ACONST_NULL))
            instructions.add(
                MethodInsnNode(
                    Opcodes.INVOKEVIRTUAL, mixinClass.name, "getThis", "()Ljava/lang/Object;", false
                )
            )
            instructions.add(InsnNode(Opcodes.RETURN))
        }

        val error = assertFailsWith<IllegalStateException> {
            ThisAwareComponent(mixinClass).apply(createContext(targetMethod))
        }

        assertTrue(error.message.orEmpty().contains("requires the mixin receiver"))
    }
}