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
import dev.karmakrafts.kcml.api.mixin.ReturnContext
import dev.karmakrafts.kcml.api.mixin.returnFromTarget
import dev.karmakrafts.kcml.hooks.KCMLHookApi
import dev.karmakrafts.kcml.hooks.wasm.WASMHooks
import org.jetbrains.kotlin.backend.wasm.WasmBackendContext
import org.jetbrains.kotlin.backend.wasm.ir2wasm.WasmDeclarationCodegenContext
import org.jetbrains.kotlin.ir.expressions.IrConst
import org.jetbrains.kotlin.wasm.ir.WasmExpressionBuilder

@OptIn(KCMLHookApi::class)
@Suppress("UNUSED")
@IndirectMixin("org.jetbrains.kotlin.backend.wasm.ir2wasm.DeclarationGeneratorKt", -999)
internal object DeclarationGeneratorKtMixin {
    @Inject(name = "generateConstExpression", order = Inject.Order.BEFORE)
    @JvmStatic
    fun generateConstExpression(
        @Capture expression: IrConst,
        @Capture body: WasmExpressionBuilder,
        @Capture backendContext: WasmBackendContext,
        @Capture declarationCodegenContext: WasmDeclarationCodegenContext,
        returnContext: ReturnContext<Unit>
    ) {
        if (WASMHooks.onGenerateConstant(expression, body, backendContext, declarationCodegenContext)) {
            returnContext.returnFromTarget()
        }
    }
}