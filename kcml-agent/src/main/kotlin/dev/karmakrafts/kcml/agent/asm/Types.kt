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
    val unit: Type = Type.getObjectType("kotlin/Unit")

    object Mixin {
        val mixin: Type = Type.getObjectType("dev/karmakrafts/kcml/api/mixin/Mixin")
        val directMixin: Type = Type.getObjectType("dev/karmakrafts/kcml/api/mixin/DirectMixin")
        val indirectMixin: Type = Type.getObjectType("dev/karmakrafts/kcml/api/mixin/IndirectMixin")
        val inject: Type = Type.getObjectType("dev/karmakrafts/kcml/api/mixin/Inject")
        val order: Type = Type.getObjectType($$"dev/karmakrafts/kcml/api/mixin/Inject$Order")
        val thisAware: Type = Type.getObjectType("dev/karmakrafts/kcml/api/mixin/ThisAware")
        val returnContext: Type = Type.getObjectType("dev/karmakrafts/kcml/api/mixin/ReturnContext")
        val capture: Type = Type.getObjectType("dev/karmakrafts/kcml/api/mixin/Capture")
    }

    fun getMethodOrFieldType(descriptor: String): Type = when {
        '(' in descriptor -> Type.getMethodType(descriptor)
        else -> Type.getMethodType(descriptor)
    }
}