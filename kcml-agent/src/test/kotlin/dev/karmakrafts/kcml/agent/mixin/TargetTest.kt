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

import org.objectweb.asm.Opcodes.ALOAD
import org.objectweb.asm.Opcodes.GETFIELD
import org.objectweb.asm.Opcodes.INVOKEVIRTUAL
import org.objectweb.asm.Type
import org.objectweb.asm.tree.AnnotationNode
import org.objectweb.asm.tree.FieldInsnNode
import org.objectweb.asm.tree.InsnList
import org.objectweb.asm.tree.MethodInsnNode
import org.objectweb.asm.tree.VarInsnNode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame

class TargetTest {
    @Test
    fun `reads annotation values and defaults`() {
        val defaultTarget = Target.fromAnnotation(AnnotationNode("Lexample/Target;").apply {
            values = mutableListOf()
        })
        val configuredTarget = Target.fromAnnotation(AnnotationNode("Lexample/Target;").apply {
            values = mutableListOf(
                "owner",
                "example/Owner",
                "name",
                "call",
                "descriptor",
                "(I)Ljava/lang/String;",
                "opcode",
                INVOKEVIRTUAL,
                "index",
                2,
                "ordinal",
                1,
                "offset",
                -1
            )
        })

        assertEquals(Target(), defaultTarget)
        assertEquals(Type.getObjectType("example/Owner"), configuredTarget.owner)
        assertEquals("call", configuredTarget.name)
        assertEquals(Type.getMethodType("(I)Ljava/lang/String;"), configuredTarget.methodOrFieldType)
        assertEquals(INVOKEVIRTUAL, configuredTarget.opcode)
        assertEquals(2, configuredTarget.index)
        assertEquals(1, configuredTarget.ordinal)
        assertEquals(-1, configuredTarget.offset)
    }

    @Test
    fun `find applies every method filter combination`() {
        val anyMethod = MethodInsnNode(INVOKEVIRTUAL, "example/Method0", "method0", "()V", false)
        val ownerMethod = MethodInsnNode(INVOKEVIRTUAL, "example/MethodOwner", "method1", "(I)V", false)
        val nameMethod = MethodInsnNode(INVOKEVIRTUAL, "example/Method2", "methodName", "(J)V", false)
        val descriptorMethod =
            MethodInsnNode(INVOKEVIRTUAL, "example/Method3", "method3", "(Ljava/lang/String;)V", false)
        val ownerNameMethod = MethodInsnNode(INVOKEVIRTUAL, "example/MethodOwnerName", "methodOwnerName", "(Z)V", false)
        val ownerDescriptorMethod =
            MethodInsnNode(INVOKEVIRTUAL, "example/MethodOwnerDescriptor", "method5", "(D)V", false)
        val nameDescriptorMethod =
            MethodInsnNode(INVOKEVIRTUAL, "example/Method6", "methodNameDescriptor", "(F)V", false)
        val allFiltersMethod =
            MethodInsnNode(INVOKEVIRTUAL, "example/MethodAll", "methodAll", "(Ljava/lang/Object;)V", false)
        val instructions = InsnList().apply {
            add(anyMethod)
            add(ownerMethod)
            add(nameMethod)
            add(descriptorMethod)
            add(ownerNameMethod)
            add(ownerDescriptorMethod)
            add(nameDescriptorMethod)
            add(allFiltersMethod)
        }

        val targetCases = listOf(
            Target(opcode = INVOKEVIRTUAL) to anyMethod,
            Target(owner = Type.getObjectType("example/MethodOwner"), opcode = INVOKEVIRTUAL) to ownerMethod,
            Target(name = "methodName", opcode = INVOKEVIRTUAL) to nameMethod,
            Target(
                methodOrFieldType = Type.getMethodType("(Ljava/lang/String;)V"),
                opcode = INVOKEVIRTUAL
            ) to descriptorMethod,
            Target(
                owner = Type.getObjectType("example/MethodOwnerName"), name = "methodOwnerName", opcode = INVOKEVIRTUAL
            ) to ownerNameMethod,
            Target(
                owner = Type.getObjectType("example/MethodOwnerDescriptor"),
                methodOrFieldType = Type.getMethodType("(D)V"),
                opcode = INVOKEVIRTUAL
            ) to ownerDescriptorMethod,
            Target(
                name = "methodNameDescriptor", methodOrFieldType = Type.getMethodType("(F)V"), opcode = INVOKEVIRTUAL
            ) to nameDescriptorMethod,
            Target(
                owner = Type.getObjectType("example/MethodAll"),
                name = "methodAll",
                methodOrFieldType = Type.getMethodType("(Ljava/lang/Object;)V"),
                opcode = INVOKEVIRTUAL
            ) to allFiltersMethod
        )

        for ((target, expected) in targetCases) assertSame(expected, target.find(instructions))
        assertNull(Target(owner = Type.getObjectType("example/Missing"), opcode = INVOKEVIRTUAL).find(instructions))
        assertNull(Target(name = "missing", opcode = INVOKEVIRTUAL).find(instructions))
        assertNull(Target(methodOrFieldType = Type.getMethodType("()I"), opcode = INVOKEVIRTUAL).find(instructions))
    }

    @Test
    fun `find applies every field filter combination`() {
        val anyField = FieldInsnNode(GETFIELD, "example/Field0", "field0", "I")
        val ownerField = FieldInsnNode(GETFIELD, "example/FieldOwner", "field1", "J")
        val nameField = FieldInsnNode(GETFIELD, "example/Field2", "fieldName", "Z")
        val descriptorField = FieldInsnNode(GETFIELD, "example/Field3", "field3", "D")
        val ownerNameField = FieldInsnNode(GETFIELD, "example/FieldOwnerName", "fieldOwnerName", "F")
        val ownerDescriptorField =
            FieldInsnNode(GETFIELD, "example/FieldOwnerDescriptor", "field5", "Ljava/lang/String;")
        val nameDescriptorField = FieldInsnNode(GETFIELD, "example/Field6", "fieldNameDescriptor", "[I")
        val allFiltersField = FieldInsnNode(GETFIELD, "example/FieldAll", "fieldAll", "Ljava/lang/Object;")
        val instructions = InsnList().apply {
            add(anyField)
            add(ownerField)
            add(nameField)
            add(descriptorField)
            add(ownerNameField)
            add(ownerDescriptorField)
            add(nameDescriptorField)
            add(allFiltersField)
        }

        val targetCases = listOf(
            Target(opcode = GETFIELD) to anyField,
            Target(owner = Type.getObjectType("example/FieldOwner"), opcode = GETFIELD) to ownerField,
            Target(name = "fieldName", opcode = GETFIELD) to nameField,
            Target(methodOrFieldType = Type.DOUBLE_TYPE, opcode = GETFIELD) to descriptorField,
            Target(
                owner = Type.getObjectType("example/FieldOwnerName"), name = "fieldOwnerName", opcode = GETFIELD
            ) to ownerNameField,
            Target(
                owner = Type.getObjectType("example/FieldOwnerDescriptor"),
                methodOrFieldType = Type.getType(String::class.java),
                opcode = GETFIELD
            ) to ownerDescriptorField,
            Target(
                name = "fieldNameDescriptor", methodOrFieldType = Type.getType("[I"), opcode = GETFIELD
            ) to nameDescriptorField,
            Target(
                owner = Type.getObjectType("example/FieldAll"),
                name = "fieldAll",
                methodOrFieldType = Type.getType(Any::class.java),
                opcode = GETFIELD
            ) to allFiltersField
        )

        for ((target, expected) in targetCases) assertSame(expected, target.find(instructions))
        assertNull(Target(owner = Type.getObjectType("example/Missing"), opcode = GETFIELD).find(instructions))
        assertNull(Target(name = "missing", opcode = GETFIELD).find(instructions))
        assertNull(Target(methodOrFieldType = Type.SHORT_TYPE, opcode = GETFIELD).find(instructions))
    }

    @Test
    fun `filters variable instructions and returns null when no valid target exists`() {
        val firstLoad = VarInsnNode(ALOAD, 1)
        val secondLoad = VarInsnNode(ALOAD, 2)
        val instructions = InsnList().apply {
            add(firstLoad)
            add(secondLoad)
        }

        assertSame(secondLoad, Target(opcode = ALOAD, index = 2).find(instructions))
        assertNull(Target(opcode = ALOAD, index = 3).find(instructions))
        assertNull(Target(opcode = ALOAD, index = 2, offset = 1).find(instructions))
    }
}