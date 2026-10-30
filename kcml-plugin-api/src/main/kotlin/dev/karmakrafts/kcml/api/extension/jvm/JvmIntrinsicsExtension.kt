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

package dev.karmakrafts.kcml.api.extension.jvm

import dev.karmakrafts.kcml.api.backend.jvm.JvmCodeGenerator
import dev.karmakrafts.kcml.api.backend.jvm.LateJvmBackend
import org.jetbrains.kotlin.backend.jvm.codegen.PromisedValue
import org.jetbrains.kotlin.ir.IrElement
import org.jetbrains.kotlin.ir.expressions.IrFunctionAccessExpression

/** Replaces selected calls with custom JVM bytecode. */
interface JvmIntrinsicsExtension : JvmCodeGenerationExtension {
    override fun shouldProcess(element: IrElement, backend: LateJvmBackend): Boolean {
        return element is IrFunctionAccessExpression && shouldProcess(element, backend)
    }

    override fun JvmCodeGenerator.process(element: IrElement): PromisedValue? {
        if (element !is IrFunctionAccessExpression) return null
        return process(element)
    }

    /** Selects calls whose generation is replaced by this extension. */
    fun shouldProcess(call: IrFunctionAccessExpression, backend: LateJvmBackend): Boolean

    /** Emits a replacement, including any required argument evaluation. */
    fun JvmCodeGenerator.process(call: IrFunctionAccessExpression): PromisedValue
}