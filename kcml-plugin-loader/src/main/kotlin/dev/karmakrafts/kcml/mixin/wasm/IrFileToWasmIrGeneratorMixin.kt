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
import org.jetbrains.kotlin.backend.wasm.ir2wasm.IrFileToWasmIrGenerator
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrField
import org.jetbrains.kotlin.ir.declarations.IrFunction
import org.jetbrains.kotlin.ir.declarations.IrProperty
import org.jetbrains.kotlin.ir.declarations.IrTypeAlias

@OptIn(KCMLHookApi::class)
@Suppress("UNUSED")
@DirectMixin(IrFileToWasmIrGenerator::class, -999)
internal class IrFileToWasmIrGeneratorMixin : ThisAware<IrFileToWasmIrGenerator> {
    @Inject(name = "visitClass", order = Inject.Order.BEFORE)
    fun visitClass(@Capture declaration: IrClass, returnContext: ReturnContext<Unit>) {
        if (WASMHooks.onGenerateDeclaration(declaration, getThis())) returnContext.returnFromTarget()
    }

    @Inject(name = "visitFunction", order = Inject.Order.BEFORE)
    fun visitFunction(@Capture declaration: IrFunction, returnContext: ReturnContext<Unit>) {
        if (WASMHooks.onGenerateDeclaration(declaration, getThis())) returnContext.returnFromTarget()
    }

    @Inject(name = "visitField", order = Inject.Order.BEFORE)
    fun visitField(@Capture declaration: IrField, returnContext: ReturnContext<Unit>) {
        if (WASMHooks.onGenerateDeclaration(declaration, getThis())) returnContext.returnFromTarget()
    }

    @Inject(name = "visitProperty", order = Inject.Order.BEFORE)
    fun visitProperty(@Capture declaration: IrProperty, returnContext: ReturnContext<Unit>) {
        if (WASMHooks.onGenerateDeclaration(declaration, getThis())) returnContext.returnFromTarget()
    }

    @Inject(name = "visitTypeAlias", order = Inject.Order.BEFORE)
    fun visitTypeAlias(@Capture declaration: IrTypeAlias, returnContext: ReturnContext<Unit>) {
        if (WASMHooks.onGenerateDeclaration(declaration, getThis())) returnContext.returnFromTarget()
    }
}