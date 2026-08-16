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
        assertEquals("(I)Ljava/lang/String;", Types.getMethodOrFieldType("(I)Ljava/lang/String;").descriptor)
        assertEquals("Ljava/lang/String;", Types.getMethodOrFieldType("Ljava/lang/String;").descriptor)
        assertEquals("I", Types.getMethodOrFieldType("I").descriptor)
    }

    @Test
    fun `defines the runtime API type descriptors`() {
        assertEquals("java/lang/Object", Types.any.internalName)
        assertEquals("dev/karmakrafts/kcml/api/mixin/DirectMixin", Types.Mixin.directMixin.internalName)
        assertEquals("dev/karmakrafts/kcml/api/mixin/IndirectMixin", Types.Mixin.indirectMixin.internalName)
        assertEquals("dev/karmakrafts/kcml/api/mixin/Inject", Types.Mixin.inject.internalName)
        assertEquals("dev/karmakrafts/kcml/api/mixin/Inject\$Order", Types.Mixin.order.internalName)
        assertEquals("dev/karmakrafts/kcml/api/mixin/ThisAware", Types.Mixin.thisAware.internalName)
        assertEquals("dev/karmakrafts/kcml/api/mixin/ReturnContext", Types.Mixin.returnContext.internalName)
    }
}