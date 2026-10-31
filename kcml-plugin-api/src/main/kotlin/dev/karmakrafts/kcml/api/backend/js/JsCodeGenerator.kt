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

package dev.karmakrafts.kcml.api.backend.js

import org.jetbrains.kotlin.ir.IrStatement
import org.jetbrains.kotlin.ir.backend.js.utils.JsGenerationContext
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.js.backend.ast.JsExpression
import org.jetbrains.kotlin.js.backend.ast.JsStatement

/** Generates JavaScript AST nodes while Kotlin IR is translated to JavaScript. */
public interface JsCodeGenerator {
    /** Late JavaScript backend for the current compilation. */
    public val backend: LateJsBackend

    /** Compiler generation context providing names and the active declaration state. */
    public val context: JsGenerationContext

    /**
     * Generates a JavaScript expression from Kotlin IR.
     * Nested expressions pass through code-generation extensions as usual.
     * Generating the element currently being intercepted recurses.
     *
     * @param expression lowered Kotlin IR expression to generate.
     * @return the generated JavaScript expression.
     */
    public fun generateExpression(expression: IrExpression): JsExpression

    /**
     * Generates a JavaScript statement from Kotlin IR, including expressions used as statements.
     * Nested elements pass through code-generation extensions as usual.
     * Generating the element currently being intercepted recurses.
     *
     * @param statement lowered Kotlin IR statement to generate.
     * @return the generated JavaScript statement.
     */
    public fun generateStatement(statement: IrStatement): JsStatement
}