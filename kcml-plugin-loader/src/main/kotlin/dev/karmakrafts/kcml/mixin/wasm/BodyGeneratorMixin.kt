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

package dev.karmakrafts.kcml.mixin.wasm

import dev.karmakrafts.kcml.api.mixin.Capture
import dev.karmakrafts.kcml.api.mixin.DirectMixin
import dev.karmakrafts.kcml.api.mixin.Inject
import dev.karmakrafts.kcml.api.mixin.ReturnContext
import dev.karmakrafts.kcml.api.mixin.ThisAware
import dev.karmakrafts.kcml.api.mixin.returnFromTarget
import dev.karmakrafts.kcml.hooks.KCMLHookApi
import dev.karmakrafts.kcml.hooks.wasm.WASMHooks
import org.jetbrains.kotlin.backend.wasm.ir2wasm.BodyGenerator
import org.jetbrains.kotlin.ir.declarations.IrVariable
import org.jetbrains.kotlin.ir.expressions.IrBlockBody
import org.jetbrains.kotlin.ir.expressions.IrExpression

@OptIn(KCMLHookApi::class)
@Suppress("UNUSED")
@DirectMixin(BodyGenerator::class, -999)
internal class BodyGeneratorMixin : ThisAware<BodyGenerator> {
    @Inject(name = $$"generateExpression$org_jetbrains_kotlin_backend_wasm", order = Inject.Order.BEFORE)
    fun generateExpression(@Capture expression: IrExpression, returnContext: ReturnContext<Unit>) {
        if (WASMHooks.onGenerateElement(expression, getThis())) returnContext.returnFromTarget()
    }

    @Inject(name = "visitVariable", order = Inject.Order.BEFORE)
    fun visitVariable(@Capture declaration: IrVariable, returnContext: ReturnContext<Unit>) {
        if (WASMHooks.onGenerateElement(declaration, getThis())) returnContext.returnFromTarget()
    }

    @Inject(name = "visitBlockBody", order = Inject.Order.BEFORE)
    fun visitBlockBody(@Capture body: IrBlockBody, returnContext: ReturnContext<Unit>) {
        if (WASMHooks.onGenerateElement(body, getThis())) returnContext.returnFromTarget()
    }
}