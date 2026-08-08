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

import dev.karmakrafts.kcml.api.backend.Backend
import dev.karmakrafts.kcml.api.backend.BackendType
import org.jetbrains.kotlin.backend.wasm.WasmBackendContext
import org.jetbrains.kotlin.backend.wasm.ir2wasm.WasmDeclarationCodegenContext
import org.jetbrains.kotlin.backend.wasm.ir2wasm.WasmTypeCodegenContext
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.ir.IrBuiltIns

/**
 * Provides access to Kotlin's WebAssembly code-generation state after IR lowering has completed.
 *
 * This backend context is available to extensions that participate in the late WebAssembly
 * compilation phase and need to inspect or generate WebAssembly declarations.
 */
interface LateWasmBackend : Backend {
    /** Identifies this context as the late WebAssembly backend. */
    override val type: BackendType
        get() = BackendType.LateWasm

    /** Compiler backend context for the current WebAssembly compilation. */
    val context: WasmBackendContext

    /** Context used to translate Kotlin IR types to WebAssembly types. */
    val typeContext: WasmTypeCodegenContext

    /** Context for the declaration currently being generated, or `null` outside a declaration. */
    val declarationContext: WasmDeclarationCodegenContext?

    /** Compiler configuration associated with the current WebAssembly compilation. */
    override val config: CompilerConfiguration
        get() = context.configuration

    /** Kotlin IR built-ins available to the WebAssembly backend. */
    val irBuiltIns: IrBuiltIns
        get() = context.irBuiltIns
}