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

package dev.karmakrafts.kcml.backend.wasm

import dev.karmakrafts.kcml.api.backend.wasm.LateWasmBackend
import dev.karmakrafts.kcml.api.backend.wasm.WasmCodeGenerator
import dev.karmakrafts.kcml.hooks.wasm.BodyGeneratorView
import org.jetbrains.kotlin.backend.wasm.ir2wasm.WasmFunctionCodegenContext
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.wasm.ir.WasmExpressionBuilder

internal class WasmCodeGeneratorImpl( // @formatter:off
    override val backend: LateWasmBackend,
    private val functionContext: WasmFunctionCodegenContext? = null,
    private val body: WasmExpressionBuilder? = null,
    private val generateExpressionCallback: ((IrExpression) -> Unit)? = null
) : WasmCodeGenerator {
    // @formatter:on
    companion object {
        fun fromView( // @formatter:off
            backend: LateWasmBackend,
            view: BodyGeneratorView
        ): WasmCodeGeneratorImpl = WasmCodeGeneratorImpl( // @formatter:on
            backend = backend,
            functionContext = view.functionContext,
            body = view.body,
            generateExpressionCallback = view.generateExpressionCallback
        )
    }

    override val context: WasmFunctionCodegenContext
        get() = checkNotNull(functionContext) { "No active WebAssembly function body" }

    override val expressionBuilder: WasmExpressionBuilder
        get() = checkNotNull(body) { "No active WebAssembly function body" }

    override fun generateExpression(expression: IrExpression) {
        checkNotNull(generateExpressionCallback) { "No active WebAssembly function body" }(expression)
    }
}