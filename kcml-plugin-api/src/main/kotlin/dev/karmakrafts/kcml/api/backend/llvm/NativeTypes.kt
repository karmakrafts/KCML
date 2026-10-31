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
 */
@OptIn(ExperimentalForeignApi::class)
public interface NativeTypes {
    /** One-bit integer type. */
    public val int1: LLVMTypeRef

    /** Eight-bit integer type. */
    public val int8: LLVMTypeRef

    /** Sixteen-bit integer type. */
    public val int16: LLVMTypeRef

    /** Thirty-two-bit integer type. */
    public val int32: LLVMTypeRef

    /** Sixty-four-bit integer type. */
    public val int64: LLVMTypeRef

    /** Single-precision floating-point type. */
    public val float32: LLVMTypeRef

    /** Double-precision floating-point type. */
    public val float64: LLVMTypeRef

    /** Opaque pointer type in address space zero. */
    public val ptr: LLVMTypeRef
}