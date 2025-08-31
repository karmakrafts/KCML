/*
 * Copyright 2025 Karma Krafts & associates
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

package dev.karmakrafts.kcml.ir

import dev.karmakrafts.kcml.util.UsedByAgent
import kotlinx.cinterop.ExperimentalForeignApi
import llvm.LLVMValueRef
import org.jetbrains.kotlin.ir.declarations.IrDeclarationOrigin
import org.jetbrains.kotlin.ir.declarations.IrFunction
import org.jetbrains.kotlin.ir.expressions.IrCall

@OptIn(ExperimentalForeignApi::class)
typealias NativeIntrinsicHandler = (callee: IrCall, args: List<LLVMValueRef>, resultSlot: LLVMValueRef?) -> LLVMValueRef

@UsedByAgent
@OptIn(ExperimentalForeignApi::class)
abstract class NativeIrIntrinsicOrigin(
    override val name: String
) : IrDeclarationOrigin {
    companion object {
        inline fun create(name: String, crossinline handler: NativeIntrinsicHandler): NativeIrIntrinsicOrigin {
            return object : NativeIrIntrinsicOrigin(name) {
                override fun evaluateCall(
                    callee: IrCall, args: List<LLVMValueRef>, resultSlot: LLVMValueRef?
                ): LLVMValueRef = handler(callee, args, resultSlot)
            }
        }

        inline fun create(
            origin: IrDeclarationOrigin, crossinline handler: NativeIntrinsicHandler
        ): NativeIrIntrinsicOrigin = create(origin.name, handler)
    }

    abstract fun evaluateCall(
        callee: IrCall, args: List<LLVMValueRef>, resultSlot: LLVMValueRef?
    ): LLVMValueRef
}

@OptIn(ExperimentalForeignApi::class)
inline fun IrFunction.attachBitcode(crossinline handler: NativeIntrinsicHandler) {
    origin = NativeIrIntrinsicOrigin.create(origin, handler)
}