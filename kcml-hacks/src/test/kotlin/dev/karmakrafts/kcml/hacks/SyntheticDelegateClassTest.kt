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

import java.lang.reflect.Modifier
import kotlin.jvm.internal.DefaultConstructorMarker
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotSame
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

@OptIn(DiscouragedKcmlApi::class)
class SyntheticDelegateClassTest {
    open class Arguments(
        val flag: Boolean,
        val byte: Byte,
        val char: Char,
        val short: Short,
        val int: Int,
        val long: Long,
        val float: Float,
        val double: Double,
        val text: String?,
        val array: IntArray
    )

    open class ProtectedConstructor protected constructor(val text: String?, val marker: Any?)

    sealed class SealedParent(val text: String)

    open class PrivateConstructor private constructor()

    @Test
    fun `forwards references and all primitive types with correct local slots`() {
        val synthetic = SyntheticDelegateClass("test.synthetic.Arguments", Arguments::class.java.constructors.single())
        val array = intArrayOf(1, 2)
        val value = synthetic.constructor.newInstance(true, 2.toByte(), 'x', 3.toShort(), 4, 5L, 6F, 7.0, null, array)
            as Arguments

        assertTrue(value.flag)
        assertEquals(2.toByte(), value.byte)
        assertEquals('x', value.char)
        assertEquals(3.toShort(), value.short)
        assertEquals(4, value.int)
        assertEquals(5L, value.long)
        assertEquals(6F, value.float)
        assertEquals(7.0, value.double)
        assertNull(value.text)
        assertSame(array, value.array)
        assertSame(synthetic.type, value.javaClass)
        assertEquals("test.synthetic.Arguments", synthetic.type.name)
        assertTrue(Modifier.isPublic(synthetic.type.modifiers))
        assertTrue(Modifier.isFinal(synthetic.type.modifiers))
    }

    @Test
    fun `exposes protected constructor and supplies trailing nulls`() {
        val synthetic = SyntheticDelegateClass(
            "test.synthetic.Protected",
            ProtectedConstructor::class.java.getDeclaredConstructor(String::class.java, Any::class.java),
            trailingNullParameters = 1
        )
        val value = synthetic.constructor.newInstance("forwarded")

        assertContentEquals(arrayOf(String::class.java), synthetic.constructor.parameterTypes)
        assertEquals("forwarded", value.text)
        assertNull(value.marker)
    }

    @Test
    fun `extends Kotlin sealed class using its synthetic marker constructor`() {
        val synthetic = SyntheticDelegateClass(
            "test.synthetic.Sealed",
            SealedParent::class.java.getDeclaredConstructor(String::class.java, DefaultConstructorMarker::class.java),
            trailingNullParameters = 1
        )

        assertEquals("sealed", synthetic.constructor.newInstance("sealed").text)
    }

    @Test
    fun `supports bootstrap superclass and isolates repeated class names`() {
        val first = SyntheticDelegateClass("test.synthetic.Object", Any::class.java.getConstructor())
        val second = SyntheticDelegateClass("test.synthetic.Object", Any::class.java.getConstructor())

        assertNotSame(first.type, second.type)
        assertSame(first.type, first.constructor.newInstance().javaClass)
        assertSame(second.type, second.constructor.newInstance().javaClass)
    }

    @Test
    fun `can supply null for every constructor parameter`() {
        val synthetic = SyntheticDelegateClass(
            "test.synthetic.AllNull",
            ProtectedConstructor::class.java.getDeclaredConstructor(String::class.java, Any::class.java),
            trailingNullParameters = 2
        )
        val value = synthetic.constructor.newInstance()

        assertNull(value.text)
        assertNull(value.marker)
    }

    @Test
    fun `rejects invalid trailing null counts and primitive parameters`() {
        val constructor = Arguments::class.java.declaredConstructors.single()
        for (count in listOf(-1, constructor.parameterCount + 1, 3)) {
            assertFailsWith<IllegalArgumentException> {
                SyntheticDelegateClass("test.synthetic.Invalid", constructor, trailingNullParameters = count)
            }
        }
    }

    @Test
    fun `rejects final superclasses and private constructors`() {
        assertFailsWith<IllegalArgumentException> {
            SyntheticDelegateClass("test.synthetic.Final", String::class.java.getConstructor())
        }
        assertFailsWith<IllegalArgumentException> {
            SyntheticDelegateClass("test.synthetic.Private", PrivateConstructor::class.java.getDeclaredConstructor())
        }
    }
}