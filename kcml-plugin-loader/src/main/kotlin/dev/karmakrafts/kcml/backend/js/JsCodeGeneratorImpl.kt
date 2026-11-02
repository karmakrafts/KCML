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

package dev.karmakrafts.kcml.backend.js

import dev.karmakrafts.kcml.api.backend.js.JsCodeGenerator
import dev.karmakrafts.kcml.api.backend.js.LateJsBackend
import org.jetbrains.kotlin.ir.IrStatement
import org.jetbrains.kotlin.ir.backend.js.transformers.irToJs.IrElementToJsExpressionTransformer
import org.jetbrains.kotlin.ir.backend.js.transformers.irToJs.IrElementToJsStatementTransformer
import org.jetbrains.kotlin.ir.backend.js.utils.JsGenerationContext
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.js.backend.ast.JsExpression
import org.jetbrains.kotlin.js.backend.ast.JsStatement

internal class JsCodeGeneratorImpl(
    override val backend: LateJsBackend, override val context: JsGenerationContext
) : JsCodeGenerator {
    override fun generateExpression(expression: IrExpression): JsExpression {
        return expression.accept(IrElementToJsExpressionTransformer(), context)
    }

    override fun generateStatement(statement: IrStatement): JsStatement {
        return statement.accept(IrElementToJsStatementTransformer(), context)
    }
}