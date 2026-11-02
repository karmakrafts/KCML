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

package dev.karmakrafts.kcml.mixin.js

import dev.karmakrafts.kcml.api.mixin.Capture
import dev.karmakrafts.kcml.api.mixin.DirectMixin
import dev.karmakrafts.kcml.api.mixin.Inject
import dev.karmakrafts.kcml.api.mixin.ReturnContext
import dev.karmakrafts.kcml.hooks.KCMLHookApi
import dev.karmakrafts.kcml.hooks.js.JSHooks
import org.jetbrains.kotlin.ir.IrElement
import org.jetbrains.kotlin.ir.backend.js.transformers.irToJs.IrElementToJsStatementTransformer
import org.jetbrains.kotlin.ir.backend.js.utils.JsGenerationContext
import org.jetbrains.kotlin.js.backend.ast.JsStatement

@OptIn(KCMLHookApi::class)
@Suppress("UNUSED")
@DirectMixin(IrElementToJsStatementTransformer::class, -999)
internal class IrElementToJsStatementTransformerMixin {
    // The generic expression and delegating-constructor visitors already delegate to the expression transformer.
    @Inject(name = "visitBlockBody", order = Inject.Order.BEFORE)
    fun visitBlockBody(
        @Capture(index = 1) element: IrElement,
        @Capture(index = 2) context: JsGenerationContext,
        result: ReturnContext<JsStatement>
    ) {
        JSHooks.onGenerateStatement(element, context)?.let { result.returnFromTarget(it) }
    }

    @Inject(name = "visitReturnableBlock", order = Inject.Order.BEFORE)
    fun visitReturnableBlock(
        @Capture(index = 1) element: IrElement,
        @Capture(index = 2) context: JsGenerationContext,
        result: ReturnContext<JsStatement>
    ) {
        JSHooks.onGenerateStatement(element, context)?.let { result.returnFromTarget(it) }
    }

    @Inject(name = "visitBlock", order = Inject.Order.BEFORE)
    fun visitBlock(
        @Capture(index = 1) element: IrElement,
        @Capture(index = 2) context: JsGenerationContext,
        result: ReturnContext<JsStatement>
    ) {
        JSHooks.onGenerateStatement(element, context)?.let { result.returnFromTarget(it) }
    }

    @Inject(name = "visitComposite", order = Inject.Order.BEFORE)
    fun visitComposite(
        @Capture(index = 1) element: IrElement,
        @Capture(index = 2) context: JsGenerationContext,
        result: ReturnContext<JsStatement>
    ) {
        JSHooks.onGenerateStatement(element, context)?.let { result.returnFromTarget(it) }
    }

    @Inject(name = "visitFunctionExpression", order = Inject.Order.BEFORE)
    fun visitFunctionExpression(
        @Capture(index = 1) element: IrElement,
        @Capture(index = 2) context: JsGenerationContext,
        result: ReturnContext<JsStatement>
    ) {
        JSHooks.onGenerateStatement(element, context)?.let { result.returnFromTarget(it) }
    }

    @Inject(name = "visitBreak", order = Inject.Order.BEFORE)
    fun visitBreak(
        @Capture(index = 1) element: IrElement,
        @Capture(index = 2) context: JsGenerationContext,
        result: ReturnContext<JsStatement>
    ) {
        JSHooks.onGenerateStatement(element, context)?.let { result.returnFromTarget(it) }
    }

    @Inject(name = "visitContinue", order = Inject.Order.BEFORE)
    fun visitContinue(
        @Capture(index = 1) element: IrElement,
        @Capture(index = 2) context: JsGenerationContext,
        result: ReturnContext<JsStatement>
    ) {
        JSHooks.onGenerateStatement(element, context)?.let { result.returnFromTarget(it) }
    }

    @Inject(name = "visitSetField", order = Inject.Order.BEFORE)
    fun visitSetField(
        @Capture(index = 1) element: IrElement,
        @Capture(index = 2) context: JsGenerationContext,
        result: ReturnContext<JsStatement>
    ) {
        JSHooks.onGenerateStatement(element, context)?.let { result.returnFromTarget(it) }
    }

    @Inject(name = "visitSetValue", order = Inject.Order.BEFORE)
    fun visitSetValue(
        @Capture(index = 1) element: IrElement,
        @Capture(index = 2) context: JsGenerationContext,
        result: ReturnContext<JsStatement>
    ) {
        JSHooks.onGenerateStatement(element, context)?.let { result.returnFromTarget(it) }
    }

    @Inject(name = "visitReturn", order = Inject.Order.BEFORE)
    fun visitReturn(
        @Capture(index = 1) element: IrElement,
        @Capture(index = 2) context: JsGenerationContext,
        result: ReturnContext<JsStatement>
    ) {
        JSHooks.onGenerateStatement(element, context)?.let { result.returnFromTarget(it) }
    }

    @Inject(name = "visitThrow", order = Inject.Order.BEFORE)
    fun visitThrow(
        @Capture(index = 1) element: IrElement,
        @Capture(index = 2) context: JsGenerationContext,
        result: ReturnContext<JsStatement>
    ) {
        JSHooks.onGenerateStatement(element, context)?.let { result.returnFromTarget(it) }
    }

    @Inject(name = "visitVariable", order = Inject.Order.BEFORE)
    fun visitVariable(
        @Capture(index = 1) element: IrElement,
        @Capture(index = 2) context: JsGenerationContext,
        result: ReturnContext<JsStatement>
    ) {
        JSHooks.onGenerateStatement(element, context)?.let { result.returnFromTarget(it) }
    }

    @Inject(name = "visitCall", order = Inject.Order.BEFORE)
    fun visitCall(
        @Capture(index = 1) element: IrElement,
        @Capture(index = 2) context: JsGenerationContext,
        result: ReturnContext<JsStatement>
    ) {
        JSHooks.onGenerateStatement(element, context)?.let { result.returnFromTarget(it) }
    }

    @Inject(name = "visitInstanceInitializerCall", order = Inject.Order.BEFORE)
    fun visitInstanceInitializerCall(
        @Capture(index = 1) element: IrElement,
        @Capture(index = 2) context: JsGenerationContext,
        result: ReturnContext<JsStatement>
    ) {
        JSHooks.onGenerateStatement(element, context)?.let { result.returnFromTarget(it) }
    }

    @Inject(name = "visitTry", order = Inject.Order.BEFORE)
    fun visitTry(
        @Capture(index = 1) element: IrElement,
        @Capture(index = 2) context: JsGenerationContext,
        result: ReturnContext<JsStatement>
    ) {
        JSHooks.onGenerateStatement(element, context)?.let { result.returnFromTarget(it) }
    }

    @Inject(name = "visitWhen", order = Inject.Order.BEFORE)
    fun visitWhen(
        @Capture(index = 1) element: IrElement,
        @Capture(index = 2) context: JsGenerationContext,
        result: ReturnContext<JsStatement>
    ) {
        JSHooks.onGenerateStatement(element, context)?.let { result.returnFromTarget(it) }
    }

    @Inject(name = "visitWhileLoop", order = Inject.Order.BEFORE)
    fun visitWhileLoop(
        @Capture(index = 1) element: IrElement,
        @Capture(index = 2) context: JsGenerationContext,
        result: ReturnContext<JsStatement>
    ) {
        JSHooks.onGenerateStatement(element, context)?.let { result.returnFromTarget(it) }
    }

    @Inject(name = "visitDoWhileLoop", order = Inject.Order.BEFORE)
    fun visitDoWhileLoop(
        @Capture(index = 1) element: IrElement,
        @Capture(index = 2) context: JsGenerationContext,
        result: ReturnContext<JsStatement>
    ) {
        JSHooks.onGenerateStatement(element, context)?.let { result.returnFromTarget(it) }
    }
}