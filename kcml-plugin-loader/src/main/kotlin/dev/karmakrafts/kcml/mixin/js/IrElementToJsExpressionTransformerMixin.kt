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
import org.jetbrains.kotlin.ir.backend.js.transformers.irToJs.IrElementToJsExpressionTransformer
import org.jetbrains.kotlin.ir.backend.js.utils.JsGenerationContext
import org.jetbrains.kotlin.js.backend.ast.JsExpression

@OptIn(KCMLHookApi::class)
@Suppress("UNUSED")
@DirectMixin(IrElementToJsExpressionTransformer::class, -999)
internal class IrElementToJsExpressionTransformerMixin {
    @Inject(name = "visitComposite", order = Inject.Order.BEFORE)
    fun visitComposite(
        @Capture(index = 1) element: IrElement,
        @Capture(index = 2) context: JsGenerationContext,
        result: ReturnContext<JsExpression>
    ) {
        JSHooks.onGenerateExpression(element, context)?.let { result.returnFromTarget(it) }
    }

    @Inject(name = "visitVararg", order = Inject.Order.BEFORE)
    fun visitVararg(
        @Capture(index = 1) element: IrElement,
        @Capture(index = 2) context: JsGenerationContext,
        result: ReturnContext<JsExpression>
    ) {
        JSHooks.onGenerateExpression(element, context)?.let { result.returnFromTarget(it) }
    }

    @Inject(name = "visitExpressionBody", order = Inject.Order.BEFORE)
    fun visitExpressionBody(
        @Capture(index = 1) element: IrElement,
        @Capture(index = 2) context: JsGenerationContext,
        result: ReturnContext<JsExpression>
    ) {
        JSHooks.onGenerateExpression(element, context)?.let { result.returnFromTarget(it) }
    }

    @Inject(name = "visitFunctionExpression", order = Inject.Order.BEFORE)
    fun visitFunctionExpression(
        @Capture(index = 1) element: IrElement,
        @Capture(index = 2) context: JsGenerationContext,
        result: ReturnContext<JsExpression>
    ) {
        JSHooks.onGenerateExpression(element, context)?.let { result.returnFromTarget(it) }
    }

    @Inject(name = "visitConst", order = Inject.Order.BEFORE)
    fun visitConst(
        @Capture(index = 1) element: IrElement,
        @Capture(index = 2) context: JsGenerationContext,
        result: ReturnContext<JsExpression>
    ) {
        JSHooks.onGenerateExpression(element, context)?.let { result.returnFromTarget(it) }
    }

    @Inject(name = "visitStringConcatenation", order = Inject.Order.BEFORE)
    fun visitStringConcatenation(
        @Capture(index = 1) element: IrElement,
        @Capture(index = 2) context: JsGenerationContext,
        result: ReturnContext<JsExpression>
    ) {
        JSHooks.onGenerateExpression(element, context)?.let { result.returnFromTarget(it) }
    }

    @Inject(name = "visitGetField", order = Inject.Order.BEFORE)
    fun visitGetField(
        @Capture(index = 1) element: IrElement,
        @Capture(index = 2) context: JsGenerationContext,
        result: ReturnContext<JsExpression>
    ) {
        JSHooks.onGenerateExpression(element, context)?.let { result.returnFromTarget(it) }
    }

    @Inject(name = "visitGetValue", order = Inject.Order.BEFORE)
    fun visitGetValue(
        @Capture(index = 1) element: IrElement,
        @Capture(index = 2) context: JsGenerationContext,
        result: ReturnContext<JsExpression>
    ) {
        JSHooks.onGenerateExpression(element, context)?.let { result.returnFromTarget(it) }
    }

    @Inject(name = "visitGetObjectValue", order = Inject.Order.BEFORE)
    fun visitGetObjectValue(
        @Capture(index = 1) element: IrElement,
        @Capture(index = 2) context: JsGenerationContext,
        result: ReturnContext<JsExpression>
    ) {
        JSHooks.onGenerateExpression(element, context)?.let { result.returnFromTarget(it) }
    }

    @Inject(name = "visitSetField", order = Inject.Order.BEFORE)
    fun visitSetField(
        @Capture(index = 1) element: IrElement,
        @Capture(index = 2) context: JsGenerationContext,
        result: ReturnContext<JsExpression>
    ) {
        JSHooks.onGenerateExpression(element, context)?.let { result.returnFromTarget(it) }
    }

    @Inject(name = "visitSetValue", order = Inject.Order.BEFORE)
    fun visitSetValue(
        @Capture(index = 1) element: IrElement,
        @Capture(index = 2) context: JsGenerationContext,
        result: ReturnContext<JsExpression>
    ) {
        JSHooks.onGenerateExpression(element, context)?.let { result.returnFromTarget(it) }
    }

    @Inject(name = "visitDelegatingConstructorCall", order = Inject.Order.BEFORE)
    fun visitDelegatingConstructorCall(
        @Capture(index = 1) element: IrElement,
        @Capture(index = 2) context: JsGenerationContext,
        result: ReturnContext<JsExpression>
    ) {
        JSHooks.onGenerateExpression(element, context)?.let { result.returnFromTarget(it) }
    }

    @Inject(name = "visitConstructorCall", order = Inject.Order.BEFORE)
    fun visitConstructorCall(
        @Capture(index = 1) element: IrElement,
        @Capture(index = 2) context: JsGenerationContext,
        result: ReturnContext<JsExpression>
    ) {
        JSHooks.onGenerateExpression(element, context)?.let { result.returnFromTarget(it) }
    }

    @Inject(name = "visitCall", order = Inject.Order.BEFORE)
    fun visitCall(
        @Capture(index = 1) element: IrElement,
        @Capture(index = 2) context: JsGenerationContext,
        result: ReturnContext<JsExpression>
    ) {
        JSHooks.onGenerateExpression(element, context)?.let { result.returnFromTarget(it) }
    }

    @Inject(name = "visitWhen", order = Inject.Order.BEFORE)
    fun visitWhen(
        @Capture(index = 1) element: IrElement,
        @Capture(index = 2) context: JsGenerationContext,
        result: ReturnContext<JsExpression>
    ) {
        JSHooks.onGenerateExpression(element, context)?.let { result.returnFromTarget(it) }
    }

    @Inject(name = "visitTypeOperator", order = Inject.Order.BEFORE)
    fun visitTypeOperator(
        @Capture(index = 1) element: IrElement,
        @Capture(index = 2) context: JsGenerationContext,
        result: ReturnContext<JsExpression>
    ) {
        JSHooks.onGenerateExpression(element, context)?.let { result.returnFromTarget(it) }
    }

    @Inject(name = "visitDynamicMemberExpression", order = Inject.Order.BEFORE)
    fun visitDynamicMemberExpression(
        @Capture(index = 1) element: IrElement,
        @Capture(index = 2) context: JsGenerationContext,
        result: ReturnContext<JsExpression>
    ) {
        JSHooks.onGenerateExpression(element, context)?.let { result.returnFromTarget(it) }
    }

    @Inject(name = "visitDynamicOperatorExpression", order = Inject.Order.BEFORE)
    fun visitDynamicOperatorExpression(
        @Capture(index = 1) element: IrElement,
        @Capture(index = 2) context: JsGenerationContext,
        result: ReturnContext<JsExpression>
    ) {
        JSHooks.onGenerateExpression(element, context)?.let { result.returnFromTarget(it) }
    }

    @Inject(name = "visitRawFunctionReference", order = Inject.Order.BEFORE)
    fun visitRawFunctionReference(
        @Capture(index = 1) element: IrElement,
        @Capture(index = 2) context: JsGenerationContext,
        result: ReturnContext<JsExpression>
    ) {
        JSHooks.onGenerateExpression(element, context)?.let { result.returnFromTarget(it) }
    }
}