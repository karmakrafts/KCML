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

package dev.karmakrafts.kcml.api.backend

import kotlinx.cinterop.ExperimentalForeignApi
import llvm.LLVMBuilderRef
import llvm.LLVMTypeRef
import llvm.LLVMValueRef
import org.jetbrains.kotlin.backend.konan.llvm.LlvmCallable
import org.jetbrains.kotlin.ir.IrBuiltIns
import org.jetbrains.kotlin.ir.declarations.IrSimpleFunction
import org.jetbrains.kotlin.ir.types.IrType

/**
 * Provides access to Kotlin/Native code-generation facilities during late native code generation.
 *
 * Use [functionBuilder] to emit LLVM instructions into the function currently being generated and
 * the materialization functions to obtain LLVM representations of Kotlin IR declarations and types.
 */
interface NativeCodeGenerator {
    /** The Kotlin IR built-in declarations and types available to the native backend. */
    val irBuiltIns: IrBuiltIns

    /** The LLVM instruction builder for the function currently being generated. */
    val functionBuilder: LLVMBuilderRef

    /** The LLVM value that references the global Kotlin [Unit] instance. */
    val unitInstance: LLVMValueRef

    /**
     * Returns the LLVM callable corresponding to an IR function, if one is available.
     *
     * @param function the IR function to materialize.
     * @return the corresponding LLVM callable, or `null` if [function] cannot be materialized.
     */
    fun tryMaterializeFunction(function: IrSimpleFunction): LlvmCallable?

    /**
     * Returns the LLVM type corresponding to a Kotlin IR type, if one is available.
     *
     * @param type the Kotlin IR type to materialize.
     * @return the corresponding LLVM type, or `null` if [type] cannot be materialized.
     */
    fun tryMaterializeType(type: IrType): LLVMTypeRef?
}

/**
 * Returns the LLVM callable corresponding to an IR function.
 *
 * @param function the IR function to materialize.
 * @return the corresponding LLVM callable.
 * @throws IllegalArgumentException if [function] cannot be materialized.
 */
@Suppress("NOTHING_TO_INLINE")
inline fun NativeCodeGenerator.materializeFunction(function: IrSimpleFunction): LlvmCallable =
    requireNotNull(tryMaterializeFunction(function))

/**
 * Returns the LLVM type corresponding to a Kotlin IR type.
 *
 * @param type the Kotlin IR type to materialize.
 * @return the corresponding LLVM type.
 * @throws IllegalArgumentException if [type] cannot be materialized.
 */
@Suppress("NOTHING_TO_INLINE")
inline fun NativeCodeGenerator.materializeType(type: IrType): LLVMTypeRef = requireNotNull(tryMaterializeType(type))