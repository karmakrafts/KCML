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

import org.objectweb.asm.tree.AbstractInsnNode
import org.objectweb.asm.tree.InsnList

internal enum class Order(
    private val inserter: InsnList.(AbstractInsnNode, InsnList) -> Unit
) {
    // @formatter:off
    BEFORE(InsnList::insertBefore),
    AFTER (InsnList::insert);
    // @formatter:on

    fun insert(needle: AbstractInsnNode, injection: InsnList, target: InsnList) = with(target) {
        inserter(needle, injection)
    }
}