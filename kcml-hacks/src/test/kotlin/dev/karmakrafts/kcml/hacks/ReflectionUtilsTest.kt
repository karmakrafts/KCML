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

import java.lang.reflect.InvocationTargetException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

@OptIn(DiscouragedKcmlApi::class)
class ReflectionUtilsTest {
    open class FieldFixture {
        @JvmField
        val publicField = "public"
        private val privateField = "private"
        private val nullableField: String? = null

        companion object {
            @JvmField
            val staticField = "static"
        }
    }

    class RuntimeFieldFixture : FieldFixture() {
        @JvmField
        val runtimeField = "runtime"
    }

    class ConstructorFixture(val text: String, val suffix: String)

    class AssignableConstructorFixture(val value: CharSequence)

    class NullableConstructorFixture(val value: String?)

    class NoArgumentConstructorFixture

    class ThrowingConstructorFixture(value: String) {
        init {
            check(value != "fail") { "constructor failed" }
        }
    }

    interface FieldContract {
        companion object {
            const val contractField = "interface field"
        }
    }

    open class RootFixture {
        @JvmField
        val rootField = "root field"
        private val privateRootField = "private root field"
        private val rootProperty = "root property"

        private fun getPrivateRootProperty(): String = "private root property"

        fun getRootProperty(): String = rootProperty

        companion object {
            @JvmField
            val staticRootField = "static root field"

            @JvmStatic
            fun getStaticRootProperty(): String = "static root property"
        }
    }

    open class MiddleFixture : RootFixture()

    class LeafFixture : MiddleFixture(), FieldContract

    interface PropertyContract {
        val contractProperty: String
    }

    class InterfacePropertyFixture : PropertyContract {
        override val contractProperty = "interface property"
    }

    class PropertyFixture {
        val publicProperty = "public property"

        private fun getPrivateProperty(): String = "private property"

        fun getArgumentProperty(unused: String): String = unused

        fun getNumberProperty(): Int = 42

        companion object {
            @JvmStatic
            fun getStaticProperty(): String = "static property"
        }
    }

    @Test
    fun `constructs instances with zero multiple assignable and nullable arguments`() {
        assertEquals(
            NoArgumentConstructorFixture::class.java, ReflectionUtils.new<NoArgumentConstructorFixture>().javaClass
        )
        assertEquals("value", ReflectionUtils.new<ConstructorFixture>("value", "suffix").text)
        assertEquals("suffix", ReflectionUtils.new<ConstructorFixture>("value", "suffix").suffix)
        assertEquals("assignable", ReflectionUtils.new<AssignableConstructorFixture>("assignable").value)
        assertEquals(null, ReflectionUtils.new<NullableConstructorFixture>(null).value)
    }

    @Test
    fun `rejects missing constructors and propagates constructor invocation failures`() {
        assertFailsWith<IllegalArgumentException> {
            ReflectionUtils.new<ConstructorFixture>("only one argument")
        }
        val error = assertFailsWith<InvocationTargetException> {
            ReflectionUtils.new<ThrowingConstructorFixture>("fail")
        }
        assertEquals("constructor failed", error.cause?.message)
    }

    @Test
    fun `reads declared public private static nullable and runtime class fields`() {
        val fixture = FieldFixture()

        assertEquals("public", ReflectionUtils.getField<FieldFixture, String>("publicField", fixture))
        assertEquals("private", ReflectionUtils.getField<FieldFixture, String>("privateField", fixture))
        assertEquals(null, ReflectionUtils.getField<FieldFixture, String?>("nullableField", fixture))
        assertEquals("static", ReflectionUtils.getField<FieldFixture, String>("staticField"))
        val runtimeFixture = RuntimeFieldFixture()
        assertEquals("runtime", ReflectionUtils.getField<FieldFixture, String>("runtimeField", runtimeFixture))
        assertFailsWith<NoSuchFieldException> {
            ReflectionUtils.getField<FieldFixture, String>("publicField", runtimeFixture)
        }
    }

    @Test
    fun `field lookup reports missing and incorrectly typed fields`() {
        assertFailsWith<NoSuchFieldException> {
            ReflectionUtils.getField<FieldFixture, String>("missingField")
        }
        assertFailsWith<ClassCastException> {
            ReflectionUtils.getField<FieldFixture, Int>("publicField", FieldFixture())
        }
    }

    @Test
    fun `reads fields declared by recursively selected superclasses and direct interfaces`() {
        val fixture = LeafFixture()

        assertEquals(
            "root field", ReflectionUtils.getSuperField<LeafFixture, String>("RootFixture", "rootField", fixture)
        )
        assertEquals(
            "private root field",
            ReflectionUtils.getSuperField<LeafFixture, String>("RootFixture", "privateRootField", fixture)
        )
        assertEquals(
            "static root field", ReflectionUtils.getSuperField<LeafFixture, String>("RootFixture", "staticRootField")
        )
        assertEquals(
            "interface field",
            ReflectionUtils.getSuperField<LeafFixture, String>("FieldContract", "contractField", fixture)
        )
    }

    @Test
    fun `super field lookup reports missing types and fields`() {
        assertFailsWith<NoSuchElementException> {
            ReflectionUtils.getSuperField<LeafFixture, String>("MissingType", "rootField", LeafFixture())
        }
        assertFailsWith<NoSuchFieldException> {
            ReflectionUtils.getSuperField<LeafFixture, String>("RootFixture", "missingField", LeafFixture())
        }
    }

    @Test
    fun `invokes declared public private and static getters`() {
        val fixture = PropertyFixture()

        assertEquals("public property", ReflectionUtils.getProperty<PropertyFixture, String>("publicProperty", fixture))
        assertEquals(
            "private property", ReflectionUtils.getProperty<PropertyFixture, String>("privateProperty", fixture)
        )
        assertEquals("static property", ReflectionUtils.getProperty<PropertyFixture, String>("staticProperty"))
    }

    @Test
    fun `property lookup is declared only and reports missing invalid and incorrectly typed getters`() {
        assertFailsWith<NoSuchElementException> {
            ReflectionUtils.getProperty<LeafFixture, String>("rootProperty", LeafFixture())
        }
        assertFailsWith<NoSuchElementException> {
            ReflectionUtils.getProperty<PropertyFixture, String>("missingProperty", PropertyFixture())
        }
        assertFailsWith<IllegalArgumentException> {
            ReflectionUtils.getProperty<PropertyFixture, String>("argumentProperty", PropertyFixture())
        }
        assertFailsWith<ClassCastException> {
            ReflectionUtils.getProperty<PropertyFixture, String>("numberProperty", PropertyFixture())
        }
    }

    @Test
    fun `invokes getters from recursively selected superclasses and direct interfaces`() {
        val fixture = LeafFixture()

        assertEquals(
            "root property",
            ReflectionUtils.getSuperProperty<LeafFixture, String>("RootFixture", "rootProperty", fixture)
        )
        assertEquals(
            "private root property",
            ReflectionUtils.getSuperProperty<LeafFixture, String>("RootFixture", "privateRootProperty", fixture)
        )
        assertEquals(
            "static root property",
            ReflectionUtils.getSuperProperty<LeafFixture, String>("RootFixture", "staticRootProperty")
        )
        assertEquals(
            "interface property", ReflectionUtils.getSuperProperty<InterfacePropertyFixture, String>(
                "PropertyContract", "contractProperty", InterfacePropertyFixture()
            )
        )
    }

    @Test
    fun `super property lookup reports missing types and getters`() {
        assertFailsWith<NoSuchElementException> {
            ReflectionUtils.getSuperProperty<LeafFixture, String>("MissingType", "rootProperty", LeafFixture())
        }
        assertFailsWith<NoSuchElementException> {
            ReflectionUtils.getSuperProperty<LeafFixture, String>("RootFixture", "missingProperty", LeafFixture())
        }
    }
}