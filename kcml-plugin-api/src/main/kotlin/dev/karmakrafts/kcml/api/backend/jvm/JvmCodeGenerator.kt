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

package dev.karmakrafts.kcml.api.backend.jvm

import org.jetbrains.kotlin.backend.jvm.codegen.BlockInfo
import org.jetbrains.kotlin.backend.jvm.codegen.ExpressionCodegen
import org.jetbrains.kotlin.backend.jvm.codegen.PromisedValue
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.org.objectweb.asm.commons.InstructionAdapter

/** Generates JVM bytecode within the currently intercepted function body. */
public interface JvmCodeGenerator {
    /** Late JVM backend available during bytecode generation. */
    public val backend: LateJvmBackend

    /** Compiler expression generator for the active function. */
    public val context: ExpressionCodegen

    /** Lexical block state used for locals and control flow. */
    public val blockInfo: BlockInfo

    /** The compiler's shaded ASM adapter, not KCML's unshaded ASM API. */
    public val instructionAdapter: InstructionAdapter
        get() = context.mv

    /**
     * Generates an expression, passing nested expressions through extensions as usual.
     * Generating the element currently being intercepted recurses.
     *
     * @param expression the IR expression to generate.
     * @return the generated value, which may defer emitting bytecode until consumed.
     */
    public fun generateExpression(expression: IrExpression): PromisedValue
}