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

package dev.karmakrafts.kcml.hooks.wasm

import dev.karmakrafts.kcml.util.ReflectionUtils
import org.jetbrains.kotlin.backend.wasm.WasmBackendContext
import org.jetbrains.kotlin.backend.wasm.ir2wasm.BodyGenerator
import org.jetbrains.kotlin.backend.wasm.ir2wasm.WasmDeclarationCodegenContext
import org.jetbrains.kotlin.backend.wasm.ir2wasm.WasmFunctionCodegenContext
import org.jetbrains.kotlin.backend.wasm.ir2wasm.WasmTypeCodegenContext
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.wasm.ir.WasmExpressionBuilder

internal class BodyGeneratorView(
    val backendContext: WasmBackendContext,
    val typeCodegenContext: WasmTypeCodegenContext,
    val declarationCodegenContext: WasmDeclarationCodegenContext,
    val functionContext: WasmFunctionCodegenContext,
    val body: WasmExpressionBuilder,
    val generateExpressionCallback: (IrExpression) -> Unit
) {
    companion object {
        fun fromImpl(generator: BodyGenerator): Result<BodyGeneratorView> = runCatching {
            val backendContext =
                ReflectionUtils.getField<BodyGenerator, WasmBackendContext>("backendContext", generator)
            val typeCodegenContext =
                ReflectionUtils.getField<BodyGenerator, WasmTypeCodegenContext>("typeCodegenContext", generator)
            val declarationCodegenContext = ReflectionUtils.getField<BodyGenerator, WasmDeclarationCodegenContext>(
                "declarationCodegenContext", generator
            )
            val functionContext =
                ReflectionUtils.getField<BodyGenerator, WasmFunctionCodegenContext>("functionContext", generator)
            val generateExpression = BodyGenerator::class.java.declaredMethods.first { method ->
                method.name == $$"generateExpression$org_jetbrains_kotlin_backend_wasm"
            }
            BodyGeneratorView(
                backendContext = backendContext,
                typeCodegenContext = typeCodegenContext,
                declarationCodegenContext = declarationCodegenContext,
                functionContext = functionContext,
                body = generator.body,
                generateExpressionCallback = { expression ->
                    generateExpression.isAccessible = true
                    generateExpression.invoke(generator, expression)
                    generateExpression.isAccessible = false
                })
        }
    }
}