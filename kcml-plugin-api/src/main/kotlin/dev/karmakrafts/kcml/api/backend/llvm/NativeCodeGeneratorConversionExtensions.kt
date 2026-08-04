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

@file:OptIn(ExperimentalForeignApi::class)

package dev.karmakrafts.kcml.api.backend.llvm

import kotlinx.cinterop.ExperimentalForeignApi
import llvm.LLVMConstInt
import llvm.LLVMConstReal
import llvm.LLVMTypeRef
import llvm.LLVMValueRef
import org.jetbrains.kotlin.backend.konan.llvm.LlvmCallable
import org.jetbrains.kotlin.ir.declarations.IrSimpleFunction
import org.jetbrains.kotlin.ir.types.IrType

/**
 * Returns the LLVM callable corresponding to an IR function.
 *
 * @param function the IR function to materialize.
 * @return the corresponding LLVM callable.
 * @throws IllegalArgumentException if [function] cannot be materialized.
 */
@Suppress("NOTHING_TO_INLINE")
inline fun NativeCodeGenerator.materializeFunction(function: IrSimpleFunction): LlvmCallable =
    requireNotNull(tryMaterializeFunction(function)) {
        "Could not materialize function during code generation of plugin '${backend.pluginId}'"
    }

/**
 * Returns the LLVM type corresponding to a Kotlin IR type.
 *
 * @param type the Kotlin IR type to materialize.
 * @return the corresponding LLVM type.
 * @throws IllegalArgumentException if [type] cannot be materialized.
 */
@Suppress("NOTHING_TO_INLINE")
inline fun NativeCodeGenerator.materializeType(type: IrType): LLVMTypeRef = requireNotNull(tryMaterializeType(type)) {
    "Could not materialize type during code generation of plugin '${backend.pluginId}'"
}

// IR conversions

/**
 * Materializes this Kotlin IR type as an LLVM type using the context [NativeCodeGenerator].
 *
 * @return the LLVM type corresponding to this IR type.
 * @throws IllegalArgumentException if this IR type cannot be materialized.
 */
context(generator: NativeCodeGenerator)
inline val IrType.llvm: LLVMTypeRef get() = generator.materializeType(this)

/**
 * Materializes this Kotlin IR function as an LLVM callable using the context [NativeCodeGenerator].
 *
 * @return the LLVM callable corresponding to this IR function.
 * @throws IllegalArgumentException if this IR function cannot be materialized.
 */
context(generator: NativeCodeGenerator)
inline val IrSimpleFunction.llvm: LlvmCallable get() = generator.materializeFunction(this)

// Signed constants

/** Creates an LLVM `i1` constant representing this Boolean value. */
context(generator: NativeCodeGenerator)
inline val Boolean.llvm: LLVMValueRef
    get() = requireNotNull(LLVMConstInt(generator.types.int1, if (this) 1L else 0L, 0)) {
        "Could not create constant Byte value for LLVM during code generation of plugin '${generator.backend.pluginId}'"
    }

/** Creates an LLVM `i8` constant representing this Byte value. */
context(generator: NativeCodeGenerator)
inline val Byte.llvm: LLVMValueRef
    get() = requireNotNull(LLVMConstInt(generator.types.int8, toLong(), 0)) {
        "Could not create constant Byte value for LLVM during code generation of plugin '${generator.backend.pluginId}'"
    }

/** Creates an LLVM `i16` constant representing this Short value. */
context(generator: NativeCodeGenerator)
inline val Short.llvm: LLVMValueRef
    get() = requireNotNull(LLVMConstInt(generator.types.int16, toLong(), 0)) {
        "Could not create constant Short value for LLVM during code generation of plugin '${generator.backend.pluginId}'"
    }

/** Creates an LLVM `i32` constant representing this Int value. */
context(generator: NativeCodeGenerator)
inline val Int.llvm: LLVMValueRef
    get() = requireNotNull(LLVMConstInt(generator.types.int32, toLong(), 0)) {
        "Could not create constant Int value for LLVM during code generation of plugin '${generator.backend.pluginId}'"
    }

/** Creates an LLVM `i64` constant representing this Long value. */
context(generator: NativeCodeGenerator)
inline val Long.llvm: LLVMValueRef
    get() = requireNotNull(LLVMConstInt(generator.types.int64, this, 0)) {
        "Could not create constant Long value for LLVM during code generation of plugin '${generator.backend.pluginId}'"
    }

// Unsigned constants

/** Creates an LLVM `i8` constant representing this UByte value. */
context(generator: NativeCodeGenerator)
inline val UByte.llvm: LLVMValueRef
    get() = requireNotNull(LLVMConstInt(generator.types.int8, toLong(), 0)) {
        "Could not create constant UByte value for LLVM during code generation of plugin '${generator.backend.pluginId}'"
    }

/** Creates an LLVM `i16` constant representing this UShort value. */
context(generator: NativeCodeGenerator)
inline val UShort.llvm: LLVMValueRef
    get() = requireNotNull(LLVMConstInt(generator.types.int16, toLong(), 0)) {
        "Could not create constant UShort value for LLVM during code generation of plugin '${generator.backend.pluginId}'"
    }

/** Creates an LLVM `i32` constant representing this UInt value. */
context(generator: NativeCodeGenerator)
inline val UInt.llvm: LLVMValueRef
    get() = requireNotNull(LLVMConstInt(generator.types.int32, toLong(), 0)) {
        "Could not create constant UInt value for LLVM during code generation of plugin '${generator.backend.pluginId}'"
    }

/** Creates an LLVM `i64` constant representing this ULong value. */
context(generator: NativeCodeGenerator)
inline val ULong.llvm: LLVMValueRef
    get() = requireNotNull(LLVMConstInt(generator.types.int64, toLong(), 0)) {
        "Could not create constant ULong value for LLVM during code generation of plugin '${generator.backend.pluginId}'"
    }

// Float constants

/** Creates an LLVM `float` constant representing this Float value. */
context(generator: NativeCodeGenerator)
inline val Float.llvm: LLVMValueRef
    get() = requireNotNull(LLVMConstReal(generator.types.float32, toDouble())) {
        "Could not create constant Float value for LLVM during code generation of plugin '${generator.backend.pluginId}'"
    }

/** Creates an LLVM `double` constant representing this Double value. */
context(generator: NativeCodeGenerator)
inline val Double.llvm: LLVMValueRef
    get() = requireNotNull(LLVMConstReal(generator.types.float64, this)) {
        "Could not create constant Double value for LLVM during code generation of plugin '${generator.backend.pluginId}'"
    }