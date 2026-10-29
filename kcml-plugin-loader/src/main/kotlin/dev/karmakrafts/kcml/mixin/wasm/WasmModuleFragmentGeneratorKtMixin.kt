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

package dev.karmakrafts.kcml.mixin.wasm

import dev.karmakrafts.kcml.api.mixin.Capture
import dev.karmakrafts.kcml.api.mixin.IndirectMixin
import dev.karmakrafts.kcml.api.mixin.Inject
import dev.karmakrafts.kcml.api.mixin.Inject.Order
import dev.karmakrafts.kcml.api.mixin.Inject.Target
import dev.karmakrafts.kcml.hooks.KCMLHookApi
import dev.karmakrafts.kcml.hooks.wasm.WASMHooks
import org.jetbrains.kotlin.backend.wasm.WasmBackendContext
import org.jetbrains.kotlin.backend.wasm.ir2wasm.WasmDeclarationCodegenContext
import org.jetbrains.kotlin.backend.wasm.ir2wasm.WasmTypeCodegenContext
import org.jetbrains.kotlin.ir.declarations.IrFile
import org.objectweb.asm.Opcodes

@Suppress("UNUSED")
@OptIn(KCMLHookApi::class)
@IndirectMixin("org.jetbrains.kotlin.backend.wasm.ir2wasm.WasmModuleFragmentGeneratorKt", -999)
internal object WasmModuleFragmentGeneratorKtMixin {
    @Inject( // @formatter:off
        name = "compileIrFile",
        target = Target(opcode = Opcodes.NEW),
        order = Order.BEFORE
    ) // @formatter:on
    @JvmStatic
    fun compileIrFile(
        @Capture irFile: IrFile,
        @Capture backendContext: WasmBackendContext,
        @Capture typeContext: WasmTypeCodegenContext,
        @Capture declarationContext: WasmDeclarationCodegenContext?
    ) {
        WASMHooks.onCompileIrFiles(irFile, backendContext, typeContext, declarationContext)
    }
}