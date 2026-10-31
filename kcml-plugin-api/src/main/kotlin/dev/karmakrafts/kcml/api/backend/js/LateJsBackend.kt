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

package dev.karmakrafts.kcml.api.backend.js

import dev.karmakrafts.kcml.api.backend.Backend
import dev.karmakrafts.kcml.api.backend.BackendType
import dev.karmakrafts.kcml.api.target.JsCompileTarget
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.ir.IrBuiltIns
import org.jetbrains.kotlin.ir.backend.js.BackendJsSymbols
import org.jetbrains.kotlin.ir.backend.js.JsIrBackendContext

/** Exposes the JavaScript compiler state during AST generation after IR lowering. */
public interface LateJsBackend : Backend {
    /** Identifies this context as the late JavaScript backend. */
    override val type: BackendType
        get() = BackendType.LateJs

    /** JavaScript compilation target processed during AST generation. */
    override val compileTarget: JsCompileTarget

    /** Compiler context for the active JavaScript compilation. */
    public val context: JsIrBackendContext

    /** Built-in IR definitions for the active compilation. */
    public val irBuiltIns: IrBuiltIns
        get() = context.irBuiltIns

    /** JavaScript backend symbols resolved by the compiler. */
    public val symbols: BackendJsSymbols
        get() = context.symbols

    /** Kotlin compiler configuration obtained from [context]. */
    override val config: CompilerConfiguration
        get() = context.configuration
}