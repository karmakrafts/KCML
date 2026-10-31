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

/**
 * Intercepts Kotlin IR immediately before WebAssembly code generation.
 *
 * Extensions are consulted in dispatch order for files, declarations, local variables, bodies,
 * and expressions, including nested expressions. Children are intercepted as they are generated,
 * not in a separate traversal before their parent is generated.
 */
public interface WasmCodeGenerationExtension : LateWasmExtension {
    /** Selects elements to transform or replace using [process]. */
    public fun shouldProcess(element: IrElement, backend: LateWasmBackend): Boolean = true

    /**
     * Transforms [element] in place or replaces its code generation.
     *
     * Return `false` to continue with subsequent extensions and normal generation using any
     * changes made to [element]. Return `true` to stop dispatch and suppress normal generation.
     * Expression replacements must emit their result through [WasmCodeGenerator.expressionBuilder]
     * and preserve argument evaluation, stack types, and other WebAssembly invariants.
     *
     * Instruction emission is available inside bodies and constant field initializers. Function
     * context and expression generation require an active function body. Generating the same
     * element recursively will intercept it again.
     */
    public fun WasmCodeGenerator.process(element: IrElement): Boolean
}