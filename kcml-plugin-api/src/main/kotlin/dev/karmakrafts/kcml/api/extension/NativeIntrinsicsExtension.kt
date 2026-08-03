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

package dev.karmakrafts.kcml.api.extension

import dev.karmakrafts.kcml.api.backend.LateNativeBackend
import dev.karmakrafts.kcml.api.backend.NativeCodeGenerator
import kotlinx.cinterop.ExperimentalForeignApi
import llvm.LLVMValueRef
import org.jetbrains.kotlin.ir.expressions.IrCall

/**
 * Contributes custom intrinsic handling to Kotlin/Native code generation.
 *
 * KCML consults implementations while lowering IR calls to LLVM so an extension can replace a
 * recognized call with its own LLVM value.
 */
@OptIn(ExperimentalForeignApi::class)
interface NativeIntrinsicsExtension : LateNativeExtension {
    /**
     * Determines whether an IR call should be lowered as an intrinsic by this extension.
     *
     * @param call IR call being considered by Kotlin/Native code generation.
     * @param backend KCML context exposing the active native and LLVM backend state.
     * @return `true` when [process] should lower [call].
     */
    fun shouldProcess(call: IrCall, backend: LateNativeBackend): Boolean

    /**
     * Lowers a recognized IR call to an LLVM value.
     *
     * @param call IR call selected by [shouldProcess].
     * @param args materialized LLVM values for the call arguments.
     * @param backend KCML context exposing the active native and LLVM backend state.
     * @return LLVM value that replaces [call] in the generated native code.
     */
    fun process( // @formatter:off
        call: IrCall,
        args: List<LLVMValueRef>,
        backend: LateNativeBackend,
        codeGenerator: NativeCodeGenerator
    ): LLVMValueRef // @formatter:on
}