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

package dev.karmakrafts.kcml.agent.asm

import org.objectweb.asm.Type
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TypeExtensionsTest {
    @Test
    fun `identifies method and nonmethod descriptors`() {
        assertTrue(Type.getMethodType("(Ljava/lang/String;)I").isMethodType)
        assertFalse(Type.getMethodType("(Ljava/lang/String;)I").isObjectType)
        assertFalse(Type.getType("Ljava/lang/String;").isMethodType)
        assertTrue(Type.getType("Ljava/lang/String;").isObjectType)
        assertTrue(Type.INT_TYPE.isObjectType)
    }

    @Test
    fun `creates types from method and field descriptors`() {
        val descriptors = mapOf(
            "(I)Ljava/lang/String;" to Type.METHOD,
            "()V" to Type.METHOD,
            "Ljava/lang/String;" to Type.OBJECT,
            "I" to Type.INT,
            "J" to Type.LONG,
            "[I" to Type.ARRAY,
            "[[Ljava/lang/String;" to Type.ARRAY
        )
        for ((descriptor, sort) in descriptors) {
            val type = Types.getMethodOrFieldType(descriptor)
            assertEquals(descriptor, type.descriptor)
            assertEquals(sort, type.sort, descriptor)
            assertEquals(Type.getType(descriptor), type)
            assertEquals(sort == Type.METHOD, type.isMethodType)
        }
    }

    @Test
    fun `rejects empty and unknown descriptors`() {
        assertFailsWith<IllegalArgumentException> { Types.getMethodOrFieldType("") }
        assertFailsWith<IllegalArgumentException> { Types.getMethodOrFieldType("Q") }
    }

    @Test
    fun `defines the runtime API type descriptors`() {
        assertEquals("java/lang/Object", Types.any.internalName)
        assertEquals("dev/karmakrafts/kcml/api/mixin/MixinBridge", Types.Mixin.bridge.internalName)
        assertEquals("dev.karmakrafts.kcml.api.mixin.MixinBridge", Types.Mixin.bridge.className)
        assertEquals("Ldev/karmakrafts/kcml/api/mixin/MixinBridgeABI;", Types.Mixin.bridgeABI.descriptor)
        assertEquals("dev/karmakrafts/kcml/api/mixin/DirectMixin", Types.Mixin.directMixin.internalName)
        assertEquals("dev/karmakrafts/kcml/api/mixin/IndirectMixin", Types.Mixin.indirectMixin.internalName)
        assertEquals("dev/karmakrafts/kcml/api/mixin/Inject", Types.Mixin.inject.internalName)
        assertEquals("dev/karmakrafts/kcml/api/mixin/Inject\$Order", Types.Mixin.order.internalName)
        assertEquals("dev/karmakrafts/kcml/api/mixin/ThisAware", Types.Mixin.thisAware.internalName)
        assertEquals("dev/karmakrafts/kcml/api/mixin/ReturnContext", Types.Mixin.returnContext.internalName)
    }

    @Test
    fun `defines erased runtime API method types`() {
        assertEquals("(Ljava/lang/Object;Ljava/lang/String;)V", Types.checkNotNullParameter.descriptor)
        assertEquals("()Ljava/lang/Object;", Types.Mixin.getThis.descriptor)
        assertEquals("(Ljava/lang/Object;)V", Types.Mixin.returnFromTarget.descriptor)
        val getters = mapOf(
            Types.Mixin.getStringConstant to "Ljava/lang/String;",
            Types.Mixin.getByteConstant to "B",
            Types.Mixin.getShortConstant to "S",
            Types.Mixin.getIntConstant to "I",
            Types.Mixin.getLongConstant to "J",
            Types.Mixin.getFloatConstant to "F",
            Types.Mixin.getDoubleConstant to "D",
            Types.Mixin.getBooleanConstant to "Z"
        )
        for ((type, returnDescriptor) in getters) {
            assertEquals(Type.METHOD, type.sort)
            assertEquals("(Ljava/lang/String;)$returnDescriptor", type.descriptor)
            assertEquals(listOf(Types.string), type.argumentTypes.toList())
            assertEquals(returnDescriptor, type.returnType.descriptor)
        }
    }
}