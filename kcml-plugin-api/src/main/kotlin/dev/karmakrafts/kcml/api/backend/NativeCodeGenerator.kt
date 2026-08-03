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

@OptIn(ExperimentalForeignApi::class)
interface NativeCodeGenerator {
    val functionBuilder: LLVMBuilderRef

    fun tryMaterializeFunction(function: IrSimpleFunction): LlvmCallable?
}

@Suppress("NOTHING_TO_INLINE")
inline fun NativeCodeGenerator.materializeFunction(function: IrSimpleFunction): LlvmCallable =
    requireNotNull(tryMaterializeFunction(function))