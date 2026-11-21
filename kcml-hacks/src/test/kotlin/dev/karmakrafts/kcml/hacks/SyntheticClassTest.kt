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

package dev.karmakrafts.kcml.hacks

import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type
import java.lang.reflect.Modifier
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

@OptIn(DiscouragedKcmlApi::class)
class SyntheticClassTest {
    abstract class Parent {
        abstract fun message(): String
    }

    @Test
    fun `builder controls class header constructors and methods`() {
        var calls = 0
        val synthetic = SyntheticClass("test.synthetic.Custom", Parent::class.java) {
            calls++
            val superName = Type.getInternalName(Parent::class.java)
            visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC, "test/synthetic/Custom", null, superName, null)
            visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null).apply {
                visitCode()
                visitVarInsn(Opcodes.ALOAD, 0)
                visitMethodInsn(Opcodes.INVOKESPECIAL, superName, "<init>", "()V", false)
                visitInsn(Opcodes.RETURN)
                visitMaxs(0, 0)
                visitEnd()
            }
            visitMethod(Opcodes.ACC_PUBLIC, "message", "()Ljava/lang/String;", null, null).apply {
                visitCode()
                visitLdcInsn("custom")
                visitInsn(Opcodes.ARETURN)
                visitMaxs(0, 0)
                visitEnd()
            }
        }
        val value = synthetic.type.getConstructor().newInstance()

        assertEquals(1, calls)
        assertEquals("custom", value.message())
        assertEquals("test.synthetic.Custom", synthetic.type.name)
        assertSame(synthetic.type, value.javaClass)
        assertFalse(Modifier.isFinal(synthetic.type.modifiers))
    }

    @Test
    fun `does not generate a forwarding constructor implicitly`() {
        val synthetic = SyntheticClass("test.synthetic.Empty", Any::class.java) {
            visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC, "test/synthetic/Empty", null, "java/lang/Object", null)
        }

        assertTrue(synthetic.type.declaredConstructors.isEmpty())
    }

    @Test
    fun `rejects generated classes not assignable to the requested type`() {
        assertFailsWith<ClassCastException> {
            SyntheticClass("test.synthetic.Unrelated", Parent::class.java) {
                visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC, "test/synthetic/Unrelated", null, "java/lang/Object", null)
            }
        }
    }
}