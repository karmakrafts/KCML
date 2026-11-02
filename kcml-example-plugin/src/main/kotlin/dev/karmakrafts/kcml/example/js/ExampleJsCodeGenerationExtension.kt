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

package dev.karmakrafts.kcml.example.js

import dev.karmakrafts.kcml.api.backend.js.JsCodeGenerator
import dev.karmakrafts.kcml.api.backend.js.LateJsBackend
import dev.karmakrafts.kcml.api.extension.AbstractExtension
import dev.karmakrafts.kcml.api.extension.ExtensionId
import dev.karmakrafts.kcml.api.extension.js.JsCodeGenerationExtension
import org.jetbrains.kotlin.ir.IrElement
import org.jetbrains.kotlin.ir.declarations.IrVariable
import org.jetbrains.kotlin.ir.expressions.IrBlockBody
import org.jetbrains.kotlin.ir.expressions.IrConst
import org.jetbrains.kotlin.ir.expressions.IrFunctionAccessExpression
import org.jetbrains.kotlin.ir.expressions.IrReturn
import org.jetbrains.kotlin.ir.expressions.impl.IrConstImpl
import org.jetbrains.kotlin.ir.symbols.UnsafeDuringIrConstructionAPI
import org.jetbrains.kotlin.js.backend.ast.JsBlock
import org.jetbrains.kotlin.js.backend.ast.JsIntLiteral
import org.jetbrains.kotlin.js.backend.ast.JsNode
import org.jetbrains.kotlin.js.backend.ast.JsReturn

@OptIn(UnsafeDuringIrConstructionAPI::class)
@ExtensionId("example_js_code_generation_extension")
internal class ExampleJsCodeGenerationExtension : AbstractExtension(), JsCodeGenerationExtension {
    override fun shouldProcess(element: IrElement, backend: LateJsBackend): Boolean {
        return when (element) {
            is IrConst -> element.value == 0x4B434D4C
            is IrReturn -> (element.value as? IrConst)?.value in listOf(0x4B434D4D, 0x4B434D4E)
            is IrVariable -> element.name.asString() == "jsLocalTransform"
            is IrBlockBody -> true
            is IrFunctionAccessExpression -> element.symbol.owner.name.asString() == "jsTransformedIntrinsic"
            else -> false
        }
    }

    override fun JsCodeGenerator.process(element: IrElement): JsNode? {
        when (element) {
            is IrConst -> return JsIntLiteral(45)
            is IrReturn -> {
                if ((element.value as? IrConst)?.value == 0x4B434D4E) return JsReturn(JsIntLiteral(47))
                element.value = IrConstImpl.int(element.value.startOffset, element.value.endOffset, element.value.type, 46)
            }

            is IrVariable -> element.initializer = IrConstImpl.int(
                element.startOffset, element.endOffset, element.type, 48
            )

            is IrBlockBody -> if (context.currentFunction?.name?.asString() == "jsBodyTransform") {
                val statement = element.statements.single() as IrReturn
                statement.value = IrConstImpl.int(
                    statement.value.startOffset, statement.value.endOffset, statement.value.type, 52
                )
                return JsBlock(element.statements.map { generateStatement(it) })
            }

            is IrFunctionAccessExpression -> {
                val argument = requireNotNull(element.arguments[2])
                element.arguments[2] = IrConstImpl.int(argument.startOffset, argument.endOffset, argument.type, 53)
            }
        }
        return null
    }
}