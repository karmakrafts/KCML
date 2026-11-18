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

package dev.karmakrafts.kcml.mixin.llvm

import dev.karmakrafts.kcml.api.mixin.IndirectMixin
import dev.karmakrafts.kcml.api.mixin.Inject
import dev.karmakrafts.kcml.api.mixin.ThisAware
import dev.karmakrafts.kcml.hooks.KCMLHookApi
import dev.karmakrafts.kcml.hooks.llvm.NativeTargetHooks
import org.objectweb.asm.Opcodes

@OptIn(KCMLHookApi::class)
@IndirectMixin("org.jetbrains.kotlin.backend.konan.llvm.Runtime", -999)
internal class RuntimeMixin : ThisAware<Any> {
    @Inject(
        name = "<init>",
        target = Inject.Target(name = "llvmModule", opcode = Opcodes.PUTFIELD),
        order = Inject.Order.AFTER
    )
    fun initializeModule() {
        NativeTargetHooks.onRuntimeModule(getThis())
    }
}