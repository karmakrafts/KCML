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

package dev.karmakrafts.kcml.example.llvm

import dev.karmakrafts.kcml.api.backend.llvm.LateNativeBackend
import dev.karmakrafts.kcml.api.backend.llvm.NativeCodeGenerator
import dev.karmakrafts.kcml.api.extension.AbstractExtension
import dev.karmakrafts.kcml.api.extension.ExtensionId
import dev.karmakrafts.kcml.api.extension.NativeIntrinsicsExtension
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.toLong
import llvm.LLVMConstInt
import llvm.LLVMInt32TypeInContext
import llvm.LLVMValueRef
import org.jetbrains.kotlin.ir.expressions.IrCall
import org.jetbrains.kotlin.ir.symbols.UnsafeDuringIrConstructionAPI
import org.jetbrains.kotlin.ir.util.hasAnnotation
import org.jetbrains.kotlin.ir.util.render
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name

@OptIn(UnsafeDuringIrConstructionAPI::class, ExperimentalForeignApi::class)
@ExtensionId("example_native_intrinsics_extension")
internal class ExampleNativeIntrinsicsExtension : AbstractExtension(), NativeIntrinsicsExtension {
    companion object {
        private val packageFqName: FqName = FqName("dev.karmakrafts.example")
        private val annotationId: ClassId = ClassId(packageFqName, Name.identifier("CustomIntrinsic"))
    }

    override fun shouldProcess(call: IrCall, backend: LateNativeBackend): Boolean {
        val target = call.symbol.owner
        return target.hasAnnotation(annotationId)
    }

    override fun NativeCodeGenerator.process( // @formatter:off
        call: IrCall,
        args: List<LLVMValueRef>
    ): LLVMValueRef { // @formatter:on
        backend.logger.info("We are processing an intrinsic call: ${call.render()}")
        backend.logger.info("Function builder at 0x${functionBuilder.toLong().toHexString()}")
        return requireNotNull(LLVMConstInt(LLVMInt32TypeInContext(backend.llvmContext), 44, 0)) {
            "Could not emit constant value in LLVM IR from native intrinsics extension"
        }
    }
}