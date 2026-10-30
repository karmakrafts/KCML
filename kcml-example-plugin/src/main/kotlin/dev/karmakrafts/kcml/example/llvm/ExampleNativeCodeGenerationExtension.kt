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
import dev.karmakrafts.kcml.api.backend.llvm.llvm
import dev.karmakrafts.kcml.api.extension.AbstractExtension
import dev.karmakrafts.kcml.api.extension.ExtensionId
import dev.karmakrafts.kcml.api.extension.llvm.NativeCodeGenerationExtension
import kotlinx.cinterop.ExperimentalForeignApi
import llvm.LLVMValueRef
import org.jetbrains.kotlin.ir.IrElement
import org.jetbrains.kotlin.ir.declarations.IrSimpleFunction
import org.jetbrains.kotlin.ir.declarations.IrVariable
import org.jetbrains.kotlin.ir.expressions.IrConst
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.expressions.IrReturn
import org.jetbrains.kotlin.ir.expressions.impl.IrConstImpl
import org.jetbrains.kotlin.ir.visitors.IrElementTransformerVoid
import org.jetbrains.kotlin.ir.visitors.transformChildrenVoid

@OptIn(ExperimentalForeignApi::class)
@ExtensionId("example_native_code_generation_extension")
internal class ExampleNativeCodeGenerationExtension : AbstractExtension(), NativeCodeGenerationExtension {
    override fun shouldProcess(element: IrElement, backend: LateNativeBackend): Boolean {
        return when (element) {
            is IrConst -> element.value == 0x4B434D4C
            is IrReturn -> (element.value as? IrConst)?.value == 0x4B434D4D
            is IrSimpleFunction -> element.name.asString() == "nativeDeclarationTransform"
            is IrVariable -> element.name.asString() == "nativeLocalTransform"
            else -> false
        }
    }

    override fun NativeCodeGenerator.process(element: IrElement): LLVMValueRef? {
        when (element) {
            is IrConst -> return 45.llvm
            is IrReturn -> element.value = IrConstImpl.int(
                element.value.startOffset, element.value.endOffset, element.value.type, 46
            )

            is IrSimpleFunction -> element.body?.transformChildrenVoid(object : IrElementTransformerVoid() {
                override fun visitConst(expression: IrConst): IrExpression {
                    if (expression.value != 43) return expression
                    return IrConstImpl.int(expression.startOffset, expression.endOffset, expression.type, 47)
                }
            })

            is IrVariable -> element.initializer = IrConstImpl.int(
                element.startOffset, element.endOffset, element.type, 48
            )
        }
        return null
    }
}