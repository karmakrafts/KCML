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
import dev.karmakrafts.kcml.agent.log.NoopLogger
import org.objectweb.asm.Opcodes
import org.objectweb.asm.tree.AnnotationNode
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.FieldInsnNode
import org.objectweb.asm.tree.InsnNode
import org.objectweb.asm.tree.JumpInsnNode
import org.objectweb.asm.tree.LabelNode
import org.objectweb.asm.tree.LdcInsnNode
import org.objectweb.asm.tree.LocalVariableNode
import org.objectweb.asm.tree.MethodInsnNode
import org.objectweb.asm.tree.MethodNode
import org.objectweb.asm.tree.ParameterNode
import org.objectweb.asm.tree.VarInsnNode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotSame
import kotlin.test.assertSame
import kotlin.test.assertTrue

class InjectComponentTest {
    private fun createMixinMethod(): MethodNode = MethodNode(0, "inject", "(JLjava/lang/String;)V", null, null).apply {
        parameters = mutableListOf(ParameterNode("wide", 0), ParameterNode("captured", 0))
        invisibleParameterAnnotations = arrayOf(
            mutableListOf(AnnotationNode(Types.Mixin.capture.descriptor)),
            mutableListOf(AnnotationNode(Types.Mixin.capture.descriptor))
        )
        instructions.add(VarInsnNode(Opcodes.ALOAD, 0))
        instructions.add(VarInsnNode(Opcodes.LLOAD, 1))
        instructions.add(VarInsnNode(Opcodes.LSTORE, 1))
        instructions.add(VarInsnNode(Opcodes.ALOAD, 3))
        instructions.add(VarInsnNode(Opcodes.ASTORE, 3))
        instructions.add(InsnNode(Opcodes.RETURN))
    }

    private fun createTargetMethod(includeCaptured: Boolean = true): MethodNode =
        MethodNode(Opcodes.ACC_STATIC, "target", "()V", null, null).apply {
            val start = LabelNode()
            val end = LabelNode()
            instructions.add(start)
            instructions.add(InsnNode(Opcodes.RETURN))
            instructions.add(end)
            localVariables = mutableListOf(LocalVariableNode("wide", "J", null, start, end, 5))
            if (includeCaptured) {
                localVariables.add(LocalVariableNode("captured", "Ljava/lang/String;", null, start, end, 8))
            }
        }

    private fun createComponent(
        mixinMethod: MethodNode,
        targetOpcode: Int = Opcodes.RETURN
    ): InjectComponent = InjectComponent(
        name = "target",
        descriptor = null,
        slice = Slice(),
        target = Target(opcode = targetOpcode),
        order = Order.BEFORE,
        mixinClass = ClassNode(),
        mixinMethod = mixinMethod
    )

    private fun createContext(targetMethod: MethodNode): ComponentContext {
        val targetClass = ClassNode().apply {
            name = "example/Target"
            methods = mutableListOf(targetMethod)
        }
        return ComponentContext(targetClass, MixinLoader(NoopLogger), NoopLogger)
    }

    @Test
    fun `inserts remapped copy while preserving original mixin instructions`() {
        val mixinMethod = createMixinMethod()
        val originalInstructions = mixinMethod.instructions.toArray()
        val targetMethod = createTargetMethod()
        val component = createComponent(mixinMethod)

        assertTrue(component.apply(createContext(targetMethod)))

        assertEquals(listOf(0, 1, 1, 3, 3), mixinMethod.instructions.filterIsInstance<VarInsnNode>().map { it.`var` })
        assertEquals(originalInstructions.size, mixinMethod.instructions.size())
        originalInstructions.forEachIndexed { index, instruction ->
            assertSame(instruction, mixinMethod.instructions[index])
        }

        val injectedVariables = targetMethod.instructions.filterIsInstance<VarInsnNode>()
        assertEquals(listOf(0, 5, 5, 8, 8), injectedVariables.map { it.`var` })
        injectedVariables.forEach { injectedInstruction ->
            originalInstructions.forEach { originalInstruction ->
                assertNotSame(originalInstruction, injectedInstruction)
            }
        }
        assertEquals(
            listOf(
                Opcodes.ALOAD,
                Opcodes.LLOAD,
                Opcodes.LSTORE,
                Opcodes.ALOAD,
                Opcodes.ASTORE,
                Opcodes.GOTO,
                Opcodes.RETURN
            ),
            targetMethod.instructions.filter { it.opcode >= 0 }.map { it.opcode }
        )
    }

    @Test
    fun `redirects all mixin returns to one local return frame`() {
        val mixinMethod = MethodNode(0, "inject", "()V", null, null).apply {
            parameters = mutableListOf()
            instructions.add(InsnNode(Opcodes.RETURN))
            instructions.add(InsnNode(Opcodes.RETURN))
        }
        val targetMethod = createTargetMethod()
        val component = createComponent(mixinMethod)

        assertTrue(component.apply(createContext(targetMethod)))

        val jumps = targetMethod.instructions.filterIsInstance<JumpInsnNode>()
        assertEquals(2, jumps.size)
        assertTrue(jumps.all { it.opcode == Opcodes.GOTO })
        assertSame(jumps.first().label, jumps.last().label)
        assertEquals(Opcodes.RETURN, jumps.first().label.next.opcode)
    }

    @Test
    fun `replaces return context call with typed target return`() {
        val mixinMethod = MethodNode(
            0,
            "inject",
            "(${Types.Mixin.returnContext.descriptor})V",
            "(${Types.Mixin.returnContext.descriptor.dropLast(1)}<Ljava/lang/String;>;)V",
            null
        ).apply {
            parameters = mutableListOf(ParameterNode("returnContext", 0))
            instructions.add(VarInsnNode(Opcodes.ALOAD, 1))
            instructions.add(LdcInsnNode("result"))
            instructions.add(
                MethodInsnNode(
                    Opcodes.INVOKEINTERFACE,
                    Types.Mixin.returnContext.internalName,
                    "returnFromTarget",
                    "(Ljava/lang/Object;)V",
                    true
                )
            )
            instructions.add(InsnNode(Opcodes.RETURN))
        }
        val targetMethod = MethodNode(
            Opcodes.ACC_STATIC,
            "target",
            "()Ljava/lang/String;",
            null,
            null
        ).apply {
            instructions.add(InsnNode(Opcodes.ACONST_NULL))
            instructions.add(InsnNode(Opcodes.ARETURN))
        }
        val component = createComponent(mixinMethod, Opcodes.ARETURN)

        assertTrue(component.apply(createContext(targetMethod)))

        assertEquals(
            listOf(Opcodes.ACONST_NULL, Opcodes.LDC, Opcodes.ARETURN, Opcodes.GOTO, Opcodes.ARETURN),
            targetMethod.instructions.filter { it.opcode >= 0 }.map { it.opcode }
        )
    }

    @Test
    fun `replaces unit return context call with void target return`() {
        val mixinMethod = MethodNode(
            0,
            "inject",
            "(${Types.Mixin.returnContext.descriptor})V",
            "(${Types.Mixin.returnContext.descriptor.dropLast(1)}<Lkotlin/Unit;>;)V",
            null
        ).apply {
            parameters = mutableListOf(ParameterNode("returnContext", 0))
            instructions.add(VarInsnNode(Opcodes.ALOAD, 1))
            instructions.add(VarInsnNode(Opcodes.ASTORE, 2))
            instructions.add(InsnNode(Opcodes.ICONST_0))
            instructions.add(VarInsnNode(Opcodes.ISTORE, 3))
            instructions.add(VarInsnNode(Opcodes.ALOAD, 2))
            instructions.add(FieldInsnNode(Opcodes.GETSTATIC, "kotlin/Unit", "INSTANCE", "Lkotlin/Unit;"))
            instructions.add(
                MethodInsnNode(
                    Opcodes.INVOKEINTERFACE,
                    Types.Mixin.returnContext.internalName,
                    "returnFromTarget",
                    "(Ljava/lang/Object;)V",
                    true
                )
            )
            instructions.add(InsnNode(Opcodes.RETURN))
        }
        val targetMethod = createTargetMethod()
        val component = createComponent(mixinMethod)

        assertTrue(component.apply(createContext(targetMethod)))

        assertEquals(
            listOf(Opcodes.ICONST_0, Opcodes.ISTORE, Opcodes.RETURN, Opcodes.GOTO, Opcodes.RETURN),
            targetMethod.instructions.filter { it.opcode >= 0 }.map { it.opcode }
        )
    }

    @Test
    fun `requires generic signature for return context`() {
        val mixinMethod = MethodNode(0, "inject", "(${Types.Mixin.returnContext.descriptor})V", null, null).apply {
            parameters = mutableListOf(ParameterNode("returnContext", 0))
            instructions.add(VarInsnNode(Opcodes.ALOAD, 1))
            instructions.add(LdcInsnNode("result"))
            instructions.add(
                MethodInsnNode(
                    Opcodes.INVOKEINTERFACE,
                    Types.Mixin.returnContext.internalName,
                    "returnFromTarget",
                    "(Ljava/lang/Object;)V",
                    true
                )
            )
            instructions.add(InsnNode(Opcodes.RETURN))
        }
        val component = createComponent(mixinMethod)

        assertFailsWith<IllegalStateException> {
            component.apply(createContext(createTargetMethod()))
        }
    }

    @Test
    fun `fails when a captured target local cannot be found`() {
        val component = createComponent(createMixinMethod())

        assertFailsWith<IllegalStateException> {
            component.apply(createContext(createTargetMethod(includeCaptured = false)))
        }
    }
}