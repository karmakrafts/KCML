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

import dev.karmakrafts.kcml.agent.asm.Types
import dev.karmakrafts.kcml.agent.asm.findInvisibleAnnotation
import dev.karmakrafts.kcml.agent.asm.findVisibleAnnotation
import dev.karmakrafts.kcml.agent.asm.getValue
import org.objectweb.asm.Opcodes
import org.objectweb.asm.tree.ClassNode

internal class RewriteAccessComponent(
    private val mixinClass: ClassNode
) : AbstractMixinComponent() {
    override val retransformable: Boolean = false

    // TODO: extend RewriteAccess support to fields, properties and functions
    override fun apply(context: ComponentContext): Boolean {
        // @formatter:off
        val annotation = mixinClass.findVisibleAnnotation(Types.Mixin.rewriteAccess)
            ?: mixinClass.findInvisibleAnnotation(Types.Mixin.rewriteAccess)
            ?: return false
        // @formatter:on
        val newAccess = annotation.getValue<Int>("value") ?: return false
        val targetClass = context.targetClass
        // If the super access (sealed class) is cleared, also clear the permitted subclass table
        if (targetClass.access and Opcodes.ACC_SUPER != 0 && newAccess and Opcodes.ACC_SUPER == 0) {
            targetClass.permittedSubclasses?.clear()
        }
        targetClass.access = newAccess // Update to the new access flags
        return true
    }
}