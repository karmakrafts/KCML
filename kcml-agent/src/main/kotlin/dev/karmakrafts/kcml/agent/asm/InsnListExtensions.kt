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

import org.objectweb.asm.tree.AbstractInsnNode
import org.objectweb.asm.tree.InsnList
import org.objectweb.asm.tree.LabelNode
import org.objectweb.asm.tree.VarInsnNode

internal fun InsnList.copy(): InsnList {
    val labels = filterIsInstance<LabelNode>().associateWith { LabelNode() }
    val list = InsnList()
    forEach { instruction -> list.add(instruction.clone(labels)) }
    return list
}

internal fun List<AbstractInsnNode>.toInsnList(): InsnList {
    val list = InsnList()
    forEach(list::add)
    return list
}

private fun InsnList.getLowestStackIndex(): Int {
    var index = Int.MAX_VALUE
    for (insn in this) when (insn) {
        is VarInsnNode -> {
            if (index > insn.`var`) index = insn.`var`
        }
    }
    return if (index == Int.MAX_VALUE) -1 else index
}

internal fun InsnList.relocateStack(startIndex: Int): InsnList {
    val currentBaseIndex = getLowestStackIndex()
    if (currentBaseIndex == -1) return this // Nothing to relocate
    for (insn in this) when (insn) {
        is VarInsnNode -> {
            insn.`var` -= currentBaseIndex
            insn.`var` += startIndex
        }
    }
    return this
}