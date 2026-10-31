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

package dev.karmakrafts.kcml.api.extension.wasm

import dev.karmakrafts.kcml.api.backend.wasm.LateWasmBackend
import dev.karmakrafts.kcml.api.backend.wasm.WasmCodeGenerator
import org.jetbrains.kotlin.ir.IrElement
import org.jetbrains.kotlin.ir.expressions.IrFunctionAccessExpression

/** Replaces selected function calls with custom WebAssembly instructions. */
public interface WasmIntrinsicsExtension : WasmCodeGenerationExtension {
    /**
     * Delegates function-access expressions to the call-specific predicate.
     *
     * @param element IR element about to be generated.
     * @param backend KCML context exposing the active WebAssembly compilation state.
     * @return `false` for other elements, otherwise the call-specific predicate result.
     */
    override fun shouldProcess(element: IrElement, backend: LateWasmBackend): Boolean {
        return element is IrFunctionAccessExpression && shouldProcess(element, backend)
    }

    /**
     * Delegates function-access expressions to the call-specific processor.
     *
     * @param element IR element selected for processing.
     * @return `true` after processing a function access, or `false` for other elements.
     */
    override fun WasmCodeGenerator.process(element: IrElement): Boolean {
        if (element !is IrFunctionAccessExpression) return false
        process(element)
        return true
    }

    /**
     * Selects calls whose generation is replaced by this extension.
     *
     * @param call function access being considered for replacement.
     * @param backend KCML context exposing the active WebAssembly compilation state.
     * @return `true` when this extension should generate [call].
     */
    public fun shouldProcess(call: IrFunctionAccessExpression, backend: LateWasmBackend): Boolean

    /**
     * Emits the replacement, including any required argument evaluation.
     *
     * @receiver generator used to emit the replacement instructions and result.
     * @param call function access selected by [shouldProcess].
     */
    public fun WasmCodeGenerator.process(call: IrFunctionAccessExpression)
}