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

package dev.karmakrafts.kcml.example.wasm

import dev.karmakrafts.kcml.api.backend.wasm.LateWasmBackend
import dev.karmakrafts.kcml.api.backend.wasm.WasmCodeGenerator
import dev.karmakrafts.kcml.api.backend.wasm.const
import dev.karmakrafts.kcml.api.extension.AbstractExtension
import dev.karmakrafts.kcml.api.extension.ExtensionId
import dev.karmakrafts.kcml.api.extension.wasm.WasmCodeGenerationExtension
import org.jetbrains.kotlin.ir.IrElement
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrField
import org.jetbrains.kotlin.ir.declarations.IrFile
import org.jetbrains.kotlin.ir.declarations.IrSimpleFunction
import org.jetbrains.kotlin.ir.declarations.IrVariable
import org.jetbrains.kotlin.ir.expressions.IrBlockBody
import org.jetbrains.kotlin.ir.expressions.IrConst
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.expressions.IrFunctionAccessExpression
import org.jetbrains.kotlin.ir.expressions.IrReturn
import org.jetbrains.kotlin.ir.expressions.impl.IrConstImpl
import org.jetbrains.kotlin.ir.symbols.UnsafeDuringIrConstructionAPI
import org.jetbrains.kotlin.ir.visitors.IrElementTransformerVoid
import org.jetbrains.kotlin.ir.visitors.transformChildrenVoid

@OptIn(UnsafeDuringIrConstructionAPI::class)
@ExtensionId("example_wasm_code_generation_extension")
internal class ExampleWasmCodeGenerationExtension : AbstractExtension(), WasmCodeGenerationExtension {
    override fun shouldProcess(element: IrElement, backend: LateWasmBackend): Boolean {
        return when (element) {
            is IrConst -> element.value == 0x4B434D4C
            is IrReturn -> (element.value as? IrConst)?.value == 0x4B434D4D
            is IrSimpleFunction -> element.name.asString() == "wasmDeclarationTransform"
            is IrVariable -> element.name.asString() == "wasmLocalTransform"
            is IrClass -> element.name.asString() == "WasmDeclarationTransformClass"
            is IrField -> element.name.asString() == "wasmFieldTransform"
            is IrFile -> element.fileEntry.name.endsWith("WasmCodeGenerationTest.kt")
            is IrBlockBody -> true
            is IrFunctionAccessExpression -> element.symbol.owner.name.asString() == "wasmTransformedIntrinsic"
            else -> false
        }
    }

    override fun WasmCodeGenerator.process(element: IrElement): Boolean {
        when (element) {
            is IrConst -> {
                const(45)
                return true
            }

            is IrReturn -> element.value = IrConstImpl.int(
                element.value.startOffset, element.value.endOffset, element.value.type, 46
            )

            is IrSimpleFunction -> element.replaceConstants(47)
            is IrVariable -> element.initializer = IrConstImpl.int(
                element.startOffset, element.endOffset, element.type, 48
            )

            is IrFile -> element.declarations.filterIsInstance<IrSimpleFunction>()
                .filter { function -> function.name.asString() == "wasmFileTransform" }
                .forEach { function -> function.replaceConstants(49) }

            is IrClass -> element.replaceConstants(50)
            is IrField -> element.initializer?.expression = IrConstImpl.int(
                element.startOffset, element.endOffset, element.type, 51
            )

            is IrBlockBody -> if (context.irFunction?.name?.asString() == "wasmBodyTransform") {
                element.replaceConstants(52)
            }

            is IrFunctionAccessExpression -> {
                val argument = requireNotNull(element.arguments[2])
                element.arguments[2] = IrConstImpl.int(argument.startOffset, argument.endOffset, argument.type, 53)
            }
        }
        return false
    }

    private fun IrElement.replaceConstants(value: Int) {
        transformChildrenVoid(object : IrElementTransformerVoid() {
            override fun visitConst(expression: IrConst): IrExpression {
                if (expression.value != 43) return expression
                return IrConstImpl.int(expression.startOffset, expression.endOffset, expression.type, value)
            }
        })
    }
}