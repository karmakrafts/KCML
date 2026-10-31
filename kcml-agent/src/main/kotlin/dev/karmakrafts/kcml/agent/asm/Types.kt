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

internal object Types {
    val any: Type = Type.getObjectType("java/lang/Object")
    val string: Type = Type.getObjectType("java/lang/String")
    val unit: Type = Type.getObjectType("kotlin/Unit")
    val intrinsics: Type = Type.getObjectType("kotlin/jvm/internal/Intrinsics")
    val checkNotNullParameter: Type = Type.getMethodType(Type.VOID_TYPE, any, string)

    object Mixin {
        val bridge: Type = Type.getObjectType("dev/karmakrafts/kcml/api/mixin/MixinBridge")
        val bridgeABI: Type = Type.getObjectType("dev/karmakrafts/kcml/api/mixin/MixinBridgeABI")
        val intrinsicInterface: Type = Type.getObjectType("dev/karmakrafts/kcml/api/mixin/IntrinsicInterface")
        val componentMarker: Type = Type.getObjectType("dev/karmakrafts/kcml/api/mixin/ComponentMarker")
        val directMixin: Type = Type.getObjectType("dev/karmakrafts/kcml/api/mixin/DirectMixin")
        val indirectMixin: Type = Type.getObjectType("dev/karmakrafts/kcml/api/mixin/IndirectMixin")
        val inject: Type = Type.getObjectType("dev/karmakrafts/kcml/api/mixin/Inject")
        val order: Type = Type.getObjectType($$"dev/karmakrafts/kcml/api/mixin/Inject$Order")
        val thisAware: Type = Type.getObjectType("dev/karmakrafts/kcml/api/mixin/ThisAware")
        val returnContext: Type = Type.getObjectType("dev/karmakrafts/kcml/api/mixin/ReturnContext")
        val capture: Type = Type.getObjectType("dev/karmakrafts/kcml/api/mixin/Capture")
        val constantTable: Type = Type.getObjectType("dev/karmakrafts/kcml/api/mixin/ConstantTable")

        val getThis: Type = Type.getMethodType(any)
        val returnFromTarget: Type = Type.getMethodType(Type.VOID_TYPE, any)
        val getStringConstant: Type = Type.getMethodType(string, string)
        val getByteConstant: Type = Type.getMethodType(Type.BYTE_TYPE, string)
        val getShortConstant: Type = Type.getMethodType(Type.SHORT_TYPE, string)
        val getIntConstant: Type = Type.getMethodType(Type.INT_TYPE, string)
        val getLongConstant: Type = Type.getMethodType(Type.LONG_TYPE, string)
        val getFloatConstant: Type = Type.getMethodType(Type.FLOAT_TYPE, string)
        val getDoubleConstant: Type = Type.getMethodType(Type.DOUBLE_TYPE, string)
        val getBooleanConstant: Type = Type.getMethodType(Type.BOOLEAN_TYPE, string)
    }

    fun getMethodOrFieldType(descriptor: String): Type {
        require(descriptor.isNotEmpty()) { "Type descriptor must not be empty" }
        return Type.getType(descriptor)
    }
}