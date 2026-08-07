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
    override val context: WasmFunctionCodegenContext,
    override val expressionBuilder: WasmExpressionBuilder,
    private val generateExpressionCallback: (IrExpression) -> Unit
) : WasmCodeGenerator {
    // @formatter:on
    companion object {
        fun fromView( // @formatter:off
            backend: LateWasmBackend,
            view: BodyGeneratorView
        ): WasmCodeGeneratorImpl = WasmCodeGeneratorImpl( // @formatter:on
            backend = backend,
            context = view.functionContext,
            expressionBuilder = view.body,
            generateExpressionCallback = view.generateExpressionCallback
        )
    }

    override fun generateExpression(expression: IrExpression) {
        generateExpressionCallback(expression)
    }
}