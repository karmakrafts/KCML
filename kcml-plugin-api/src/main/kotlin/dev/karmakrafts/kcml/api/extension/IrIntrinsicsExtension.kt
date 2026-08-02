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

package dev.karmakrafts.kcml.api.extension

import dev.karmakrafts.kcml.api.backend.IrBackend
import org.jetbrains.kotlin.ir.declarations.IrModuleFragment
import org.jetbrains.kotlin.ir.expressions.IrCall
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.util.render
import org.jetbrains.kotlin.ir.visitors.IrElementTransformerVoid
import org.jetbrains.kotlin.ir.visitors.transformChildrenVoid

/**
 * Contributes custom intrinsic handling during Kotlin IR processing.
 *
 * KCML visits every call in a module and delegates recognized calls to this extension, allowing it
 * to replace the call with a custom IR expression before backend code generation.
 */
interface IrIntrinsicsExtension : IrExtension {
    /**
     * Determines whether an IR call should be processed as an intrinsic by this extension.
     *
     * @param call IR call being considered during module processing.
     * @param backend KCML context exposing the active target backend.
     * @return `true` when [process] should replace [call].
     */
    fun shouldProcess(call: IrCall, backend: IrBackend): Boolean

    /**
     * Replaces a recognized IR call with a custom expression.
     *
     * @param call IR call selected by [shouldProcess].
     * @param backend KCML context exposing the active target backend.
     * @return expression that replaces [call] in the processed module.
     */
    fun process(call: IrCall, backend: IrBackend): IrExpression

    override fun process(module: IrModuleFragment, backend: IrBackend) {
        module.transformChildrenVoid(object : IrElementTransformerVoid() {
            override fun visitCall(expression: IrCall): IrExpression {
                val call = super.visitCall(expression)
                if (call is IrCall && shouldProcess(call, backend)) {
                    backend.logger.info("Processing IR intrinsic call ${call.render()}")
                    return process(call, backend)
                }
                return call
            }
        })
    }
}