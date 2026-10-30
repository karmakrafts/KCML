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

package dev.karmakrafts.kcml.api.backend.wasm

import org.jetbrains.kotlin.backend.wasm.ir2wasm.WasmFunctionCodegenContext
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.wasm.ir.WasmExpressionBuilder

/**
 * Generates WebAssembly instructions while Kotlin IR expressions are translated to WebAssembly.
 *
 * Extensions can use the compiler contexts exposed by this interface together with the instruction
 * helpers in this package to contribute code to the current function body.
 */
interface WasmCodeGenerator {
    /** Late WebAssembly backend context for the current compilation. */
    val backend: LateWasmBackend

    /** Code-generation context for the current function; unavailable outside a body. */
    val context: WasmFunctionCodegenContext

    /** Instruction builder for the current expression, inside a body or constant field initializer. */
    val expressionBuilder: WasmExpressionBuilder

    /**
     * Generates WebAssembly instructions for a Kotlin IR [expression].
     * Nested expressions pass through code-generation extensions as usual. Only available
     * inside a body; generating the element currently being intercepted recurses.
     *
     * @param expression Kotlin IR expression to generate.
     */
    fun generateExpression(expression: IrExpression)
}