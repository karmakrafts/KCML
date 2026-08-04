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

package dev.karmakrafts.kcml.api.backend.llvm

import kotlinx.cinterop.ExperimentalForeignApi
import llvm.LLVMTypeRef

/**
 * Provides LLVM types used during native code generation.
 *
 * @property int1 The one-bit integer type.
 * @property int8 The eight-bit integer type.
 * @property int16 The sixteen-bit integer type.
 * @property int32 The thirty-two-bit integer type.
 * @property int64 The sixty-four-bit integer type.
 * @property float32 The single-precision floating-point type.
 * @property float64 The double-precision floating-point type.
 * @property ptr The opaque pointer type in address space zero.
 */
@OptIn(ExperimentalForeignApi::class)
interface NativeTypes {
    val int1: LLVMTypeRef
    val int8: LLVMTypeRef
    val int16: LLVMTypeRef
    val int32: LLVMTypeRef
    val int64: LLVMTypeRef
    val float32: LLVMTypeRef
    val float64: LLVMTypeRef
    val ptr: LLVMTypeRef
}