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

import dev.karmakrafts.kcml.api.backend.NativeBackend
import kotlinx.cinterop.ExperimentalForeignApi
import llvm.LLVMValueRef
import org.jetbrains.kotlin.ir.expressions.IrCall

/**
 * Allows augmenting the Kotlin/Native backend with custom intrinsic functions.
 */
@OptIn(ExperimentalForeignApi::class)
interface NativeIntrinsicsExtension : Extension {
    /**
     * Determines whether the given call should be evaluated as a native intrinsic.
     * The called function may be derived from the given call target.
     *
     * @param call The call being checked.
     * @return True if the call should be evaluated as an intrinsic.
     */
    fun shouldProcess(call: IrCall, backend: NativeBackend): Boolean

    /**
     * Evaluates the given call as a native intrinsic and replaces its occurrence
     * in the final LLVM bitcode with the returned value reference.
     *
     * @param call The call being evaluated.
     * @param args A list of pointers to the materialized LLVM function arguments.
     * @return A value reference with which the given call is to be replaced
     *  in the final LLVM bitcode.
     */
    fun process(call: IrCall, args: List<LLVMValueRef>, backend: NativeBackend): LLVMValueRef
}