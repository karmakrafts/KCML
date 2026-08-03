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

package dev.karmakrafts.kcml.api.backend

import kotlinx.cinterop.ExperimentalForeignApi
import llvm.LLVMBuilderRef
import org.jetbrains.kotlin.backend.konan.llvm.LlvmCallable
import org.jetbrains.kotlin.ir.declarations.IrSimpleFunction

/**
 * Materializes Kotlin/Native IR functions as LLVM callables during late native code generation.
 *
 * Implementations bridge KCML's late-native extensions to the active Kotlin/Native LLVM function
 * generator and use [functionBuilder] to emit instructions into the current function body.
 */
@OptIn(ExperimentalForeignApi::class)
interface NativeCodeGenerator {
    /** LLVM instruction builder positioned for the function currently being generated. */
    val functionBuilder: LLVMBuilderRef

    /**
     * Materializes an IR function as an LLVM callable when the native backend can generate it.
     *
     * @param function IR function to materialize.
     * @return the generated LLVM callable, or `null` when [function] cannot be materialized.
     */
    fun tryMaterializeFunction(function: IrSimpleFunction): LlvmCallable?
}

/**
 * Materializes an IR function as an LLVM callable.
 *
 * @param function IR function to materialize.
 * @return the generated LLVM callable.
 * @throws IllegalArgumentException when [function] cannot be materialized by this generator.
 */
@Suppress("NOTHING_TO_INLINE")
inline fun NativeCodeGenerator.materializeFunction(function: IrSimpleFunction): LlvmCallable =
    requireNotNull(tryMaterializeFunction(function))