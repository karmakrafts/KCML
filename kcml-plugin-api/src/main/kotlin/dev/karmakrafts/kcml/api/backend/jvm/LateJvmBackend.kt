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

package dev.karmakrafts.kcml.api.backend.jvm

import dev.karmakrafts.kcml.api.backend.Backend
import dev.karmakrafts.kcml.api.backend.BackendType
import dev.karmakrafts.kcml.api.target.JvmCompileTarget
import org.jetbrains.kotlin.backend.jvm.JvmBackendContext
import org.jetbrains.kotlin.backend.jvm.JvmSymbols
import org.jetbrains.kotlin.codegen.state.GenerationState
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.ir.IrBuiltIns

/** Exposes the JVM compiler state during bytecode generation. */
interface LateJvmBackend : Backend {
    override val type: BackendType
        get() = BackendType.LateJvm

    override val compileTarget: JvmCompileTarget

    /** Compiler context for the active JVM compilation. */
    val context: JvmBackendContext

    /** State receiving generated classes. */
    val state: GenerationState
        get() = context.state

    /** Built-in IR definitions for the active compilation. */
    val irBuiltIns: IrBuiltIns
        get() = context.irBuiltIns

    /** JVM backend symbols resolved by the compiler. */
    val symbols: JvmSymbols
        get() = context.symbols

    override val config: CompilerConfiguration
        get() = context.configuration
}