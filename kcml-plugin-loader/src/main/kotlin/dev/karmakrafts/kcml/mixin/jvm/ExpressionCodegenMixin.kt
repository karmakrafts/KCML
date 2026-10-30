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

package dev.karmakrafts.kcml.mixin.jvm

import dev.karmakrafts.kcml.api.mixin.Capture
import dev.karmakrafts.kcml.api.mixin.DirectMixin
import dev.karmakrafts.kcml.api.mixin.Inject
import dev.karmakrafts.kcml.api.mixin.ReturnContext
import dev.karmakrafts.kcml.api.mixin.ThisAware
import dev.karmakrafts.kcml.api.mixin.returnFromTarget
import dev.karmakrafts.kcml.hooks.KCMLHookApi
import dev.karmakrafts.kcml.hooks.jvm.JVMHooks
import org.jetbrains.kotlin.backend.jvm.codegen.BlockInfo
import org.jetbrains.kotlin.backend.jvm.codegen.ExpressionCodegen
import org.jetbrains.kotlin.backend.jvm.codegen.PromisedValue
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrVariable
import org.jetbrains.kotlin.ir.expressions.IrBlockBody
import org.jetbrains.kotlin.ir.expressions.IrBreakContinue
import org.jetbrains.kotlin.ir.expressions.IrCall
import org.jetbrains.kotlin.ir.expressions.IrClassReference
import org.jetbrains.kotlin.ir.expressions.IrConst
import org.jetbrains.kotlin.ir.expressions.IrConstructorCall
import org.jetbrains.kotlin.ir.expressions.IrContainerExpression
import org.jetbrains.kotlin.ir.expressions.IrDelegatingConstructorCall
import org.jetbrains.kotlin.ir.expressions.IrDoWhileLoop
import org.jetbrains.kotlin.ir.expressions.IrExpressionBody
import org.jetbrains.kotlin.ir.expressions.IrFieldAccessExpression
import org.jetbrains.kotlin.ir.expressions.IrGetClass
import org.jetbrains.kotlin.ir.expressions.IrGetValue
import org.jetbrains.kotlin.ir.expressions.IrReturn
import org.jetbrains.kotlin.ir.expressions.IrSetField
import org.jetbrains.kotlin.ir.expressions.IrSetValue
import org.jetbrains.kotlin.ir.expressions.IrStringConcatenation
import org.jetbrains.kotlin.ir.expressions.IrThrow
import org.jetbrains.kotlin.ir.expressions.IrTry
import org.jetbrains.kotlin.ir.expressions.IrTypeOperatorCall
import org.jetbrains.kotlin.ir.expressions.IrWhen
import org.jetbrains.kotlin.ir.expressions.IrWhileLoop

// Explicit descriptors exclude erased bridges, whose order may change during retransformation.
private const val VISITOR_RESULT: String =
    "Lorg/jetbrains/kotlin/backend/jvm/codegen/BlockInfo;)Lorg/jetbrains/kotlin/backend/jvm/codegen/PromisedValue;"

@OptIn(KCMLHookApi::class)
@Suppress("UNUSED")
@DirectMixin(ExpressionCodegen::class, -999)
internal class ExpressionCodegenMixin : ThisAware<ExpressionCodegen> {
    @Inject(
        name = "visitContainerExpression",
        descriptor = "(Lorg/jetbrains/kotlin/ir/expressions/IrContainerExpression;$VISITOR_RESULT",
        order = Inject.Order.BEFORE
    )
    fun visitContainerExpression(
        @Capture expression: IrContainerExpression, @Capture data: BlockInfo, returnContext: ReturnContext<PromisedValue>
    ) {
        JVMHooks.onGenerateElement(expression, getThis(), data)?.let { returnContext.returnFromTarget(it) }
    }

    @Inject(
        name = "visitBlockBody",
        descriptor = "(Lorg/jetbrains/kotlin/ir/expressions/IrBlockBody;$VISITOR_RESULT",
        order = Inject.Order.BEFORE
    )
    fun visitBlockBody(
        @Capture body: IrBlockBody, @Capture data: BlockInfo, returnContext: ReturnContext<PromisedValue>
    ) {
        JVMHooks.onGenerateElement(body, getThis(), data)?.let { returnContext.returnFromTarget(it) }
    }

    @Inject(
        name = "visitExpressionBody",
        descriptor = "(Lorg/jetbrains/kotlin/ir/expressions/IrExpressionBody;$VISITOR_RESULT",
        order = Inject.Order.BEFORE
    )
    fun visitExpressionBody(
        @Capture body: IrExpressionBody, @Capture data: BlockInfo, returnContext: ReturnContext<PromisedValue>
    ) {
        JVMHooks.onGenerateElement(body, getThis(), data)?.let { returnContext.returnFromTarget(it) }
    }

    @Inject(
        name = "visitCall",
        descriptor = "(Lorg/jetbrains/kotlin/ir/expressions/IrCall;$VISITOR_RESULT",
        order = Inject.Order.BEFORE
    )
    fun visitCall(
        @Capture expression: IrCall, @Capture data: BlockInfo, returnContext: ReturnContext<PromisedValue>
    ) {
        JVMHooks.onGenerateElement(expression, getThis(), data)?.let { returnContext.returnFromTarget(it) }
    }

    @Inject(
        name = "visitConstructorCall",
        descriptor = "(Lorg/jetbrains/kotlin/ir/expressions/IrConstructorCall;$VISITOR_RESULT",
        order = Inject.Order.BEFORE
    )
    fun visitConstructorCall(
        @Capture expression: IrConstructorCall, @Capture data: BlockInfo, returnContext: ReturnContext<PromisedValue>
    ) {
        JVMHooks.onGenerateElement(expression, getThis(), data)?.let { returnContext.returnFromTarget(it) }
    }

    @Inject(
        name = "visitDelegatingConstructorCall",
        descriptor = "(Lorg/jetbrains/kotlin/ir/expressions/IrDelegatingConstructorCall;$VISITOR_RESULT",
        order = Inject.Order.BEFORE
    )
    fun visitDelegatingConstructorCall(
        @Capture expression: IrDelegatingConstructorCall, @Capture data: BlockInfo, returnContext: ReturnContext<PromisedValue>
    ) {
        JVMHooks.onGenerateElement(expression, getThis(), data)?.let { returnContext.returnFromTarget(it) }
    }

    @Inject(
        name = "visitVariable",
        descriptor = "(Lorg/jetbrains/kotlin/ir/declarations/IrVariable;$VISITOR_RESULT",
        order = Inject.Order.BEFORE
    )
    fun visitVariable(
        @Capture declaration: IrVariable, @Capture data: BlockInfo, returnContext: ReturnContext<PromisedValue>
    ) {
        JVMHooks.onGenerateElement(declaration, getThis(), data)?.let { returnContext.returnFromTarget(it) }
    }

    @Inject(
        name = "visitClass",
        descriptor = "(Lorg/jetbrains/kotlin/ir/declarations/IrClass;$VISITOR_RESULT",
        order = Inject.Order.BEFORE
    )
    fun visitClass(
        @Capture declaration: IrClass, @Capture data: BlockInfo, returnContext: ReturnContext<PromisedValue>
    ) {
        JVMHooks.onGenerateElement(declaration, getThis(), data)?.let { returnContext.returnFromTarget(it) }
    }

    @Inject(
        name = "visitGetValue",
        descriptor = "(Lorg/jetbrains/kotlin/ir/expressions/IrGetValue;$VISITOR_RESULT",
        order = Inject.Order.BEFORE
    )
    fun visitGetValue(
        @Capture expression: IrGetValue, @Capture data: BlockInfo, returnContext: ReturnContext<PromisedValue>
    ) {
        JVMHooks.onGenerateElement(expression, getThis(), data)?.let { returnContext.returnFromTarget(it) }
    }

    @Inject(
        name = "visitSetValue",
        descriptor = "(Lorg/jetbrains/kotlin/ir/expressions/IrSetValue;$VISITOR_RESULT",
        order = Inject.Order.BEFORE
    )
    fun visitSetValue(
        @Capture expression: IrSetValue, @Capture data: BlockInfo, returnContext: ReturnContext<PromisedValue>
    ) {
        JVMHooks.onGenerateElement(expression, getThis(), data)?.let { returnContext.returnFromTarget(it) }
    }

    @Inject(
        name = "visitFieldAccess",
        descriptor = "(Lorg/jetbrains/kotlin/ir/expressions/IrFieldAccessExpression;$VISITOR_RESULT",
        order = Inject.Order.BEFORE
    )
    fun visitFieldAccess(
        @Capture expression: IrFieldAccessExpression, @Capture data: BlockInfo, returnContext: ReturnContext<PromisedValue>
    ) {
        JVMHooks.onGenerateElement(expression, getThis(), data)?.let { returnContext.returnFromTarget(it) }
    }

    @Inject(
        name = "visitSetField",
        descriptor = "(Lorg/jetbrains/kotlin/ir/expressions/IrSetField;$VISITOR_RESULT",
        order = Inject.Order.BEFORE
    )
    fun visitSetField(
        @Capture expression: IrSetField, @Capture data: BlockInfo, returnContext: ReturnContext<PromisedValue>
    ) {
        JVMHooks.onGenerateElement(expression, getThis(), data)?.let { returnContext.returnFromTarget(it) }
    }

    @Inject(
        name = "visitConst",
        descriptor = "(Lorg/jetbrains/kotlin/ir/expressions/IrConst;$VISITOR_RESULT",
        order = Inject.Order.BEFORE
    )
    fun visitConst(
        @Capture expression: IrConst, @Capture data: BlockInfo, returnContext: ReturnContext<PromisedValue>
    ) {
        JVMHooks.onGenerateElement(expression, getThis(), data)?.let { returnContext.returnFromTarget(it) }
    }

    @Inject(
        name = "visitReturn",
        descriptor = "(Lorg/jetbrains/kotlin/ir/expressions/IrReturn;$VISITOR_RESULT",
        order = Inject.Order.BEFORE
    )
    fun visitReturn(
        @Capture expression: IrReturn, @Capture data: BlockInfo, returnContext: ReturnContext<PromisedValue>
    ) {
        JVMHooks.onGenerateElement(expression, getThis(), data)?.let { returnContext.returnFromTarget(it) }
    }

    @Inject(
        name = "visitWhen",
        descriptor = "(Lorg/jetbrains/kotlin/ir/expressions/IrWhen;$VISITOR_RESULT",
        order = Inject.Order.BEFORE
    )
    fun visitWhen(
        @Capture expression: IrWhen, @Capture data: BlockInfo, returnContext: ReturnContext<PromisedValue>
    ) {
        JVMHooks.onGenerateElement(expression, getThis(), data)?.let { returnContext.returnFromTarget(it) }
    }

    @Inject(
        name = "visitTypeOperator",
        descriptor = "(Lorg/jetbrains/kotlin/ir/expressions/IrTypeOperatorCall;$VISITOR_RESULT",
        order = Inject.Order.BEFORE
    )
    fun visitTypeOperator(
        @Capture expression: IrTypeOperatorCall, @Capture data: BlockInfo, returnContext: ReturnContext<PromisedValue>
    ) {
        JVMHooks.onGenerateElement(expression, getThis(), data)?.let { returnContext.returnFromTarget(it) }
    }

    @Inject(
        name = "visitWhileLoop",
        descriptor = "(Lorg/jetbrains/kotlin/ir/expressions/IrWhileLoop;$VISITOR_RESULT",
        order = Inject.Order.BEFORE
    )
    fun visitWhileLoop(
        @Capture loop: IrWhileLoop, @Capture data: BlockInfo, returnContext: ReturnContext<PromisedValue>
    ) {
        JVMHooks.onGenerateElement(loop, getThis(), data)?.let { returnContext.returnFromTarget(it) }
    }

    @Inject(
        name = "visitDoWhileLoop",
        descriptor = "(Lorg/jetbrains/kotlin/ir/expressions/IrDoWhileLoop;$VISITOR_RESULT",
        order = Inject.Order.BEFORE
    )
    fun visitDoWhileLoop(
        @Capture loop: IrDoWhileLoop, @Capture data: BlockInfo, returnContext: ReturnContext<PromisedValue>
    ) {
        JVMHooks.onGenerateElement(loop, getThis(), data)?.let { returnContext.returnFromTarget(it) }
    }

    @Inject(
        name = "visitBreakContinue",
        descriptor = "(Lorg/jetbrains/kotlin/ir/expressions/IrBreakContinue;$VISITOR_RESULT",
        order = Inject.Order.BEFORE
    )
    fun visitBreakContinue(
        @Capture jump: IrBreakContinue, @Capture data: BlockInfo, returnContext: ReturnContext<PromisedValue>
    ) {
        JVMHooks.onGenerateElement(jump, getThis(), data)?.let { returnContext.returnFromTarget(it) }
    }

    @Inject(
        name = "visitTry",
        descriptor = "(Lorg/jetbrains/kotlin/ir/expressions/IrTry;$VISITOR_RESULT",
        order = Inject.Order.BEFORE
    )
    fun visitTry(
        @Capture aTry: IrTry, @Capture data: BlockInfo, returnContext: ReturnContext<PromisedValue>
    ) {
        JVMHooks.onGenerateElement(aTry, getThis(), data)?.let { returnContext.returnFromTarget(it) }
    }

    @Inject(
        name = "visitThrow",
        descriptor = "(Lorg/jetbrains/kotlin/ir/expressions/IrThrow;$VISITOR_RESULT",
        order = Inject.Order.BEFORE
    )
    fun visitThrow(
        @Capture expression: IrThrow, @Capture data: BlockInfo, returnContext: ReturnContext<PromisedValue>
    ) {
        JVMHooks.onGenerateElement(expression, getThis(), data)?.let { returnContext.returnFromTarget(it) }
    }

    @Inject(
        name = "visitStringConcatenation",
        descriptor = "(Lorg/jetbrains/kotlin/ir/expressions/IrStringConcatenation;$VISITOR_RESULT",
        order = Inject.Order.BEFORE
    )
    fun visitStringConcatenation(
        @Capture expression: IrStringConcatenation, @Capture data: BlockInfo, returnContext: ReturnContext<PromisedValue>
    ) {
        JVMHooks.onGenerateElement(expression, getThis(), data)?.let { returnContext.returnFromTarget(it) }
    }

    @Inject(
        name = "visitGetClass",
        descriptor = "(Lorg/jetbrains/kotlin/ir/expressions/IrGetClass;$VISITOR_RESULT",
        order = Inject.Order.BEFORE
    )
    fun visitGetClass(
        @Capture expression: IrGetClass, @Capture data: BlockInfo, returnContext: ReturnContext<PromisedValue>
    ) {
        JVMHooks.onGenerateElement(expression, getThis(), data)?.let { returnContext.returnFromTarget(it) }
    }

    @Inject(
        name = "visitClassReference",
        descriptor = "(Lorg/jetbrains/kotlin/ir/expressions/IrClassReference;$VISITOR_RESULT",
        order = Inject.Order.BEFORE
    )
    fun visitClassReference(
        @Capture expression: IrClassReference, @Capture data: BlockInfo, returnContext: ReturnContext<PromisedValue>
    ) {
        JVMHooks.onGenerateElement(expression, getThis(), data)?.let { returnContext.returnFromTarget(it) }
    }
}