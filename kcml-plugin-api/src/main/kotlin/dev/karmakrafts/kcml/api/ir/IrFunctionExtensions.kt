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

package dev.karmakrafts.kcml.api.ir

import org.jetbrains.kotlin.ir.IrBuiltIns
import org.jetbrains.kotlin.ir.declarations.IrParameterKind
import org.jetbrains.kotlin.ir.declarations.IrSimpleFunction
import org.jetbrains.kotlin.ir.expressions.IrCall
import org.jetbrains.kotlin.ir.expressions.IrConstructorCall
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.expressions.IrFunctionAccessExpression
import org.jetbrains.kotlin.ir.expressions.IrFunctionExpression
import org.jetbrains.kotlin.ir.expressions.IrStatementOrigin
import org.jetbrains.kotlin.ir.expressions.impl.IrCallImplWithShape
import org.jetbrains.kotlin.ir.expressions.impl.IrFunctionExpressionImpl
import org.jetbrains.kotlin.ir.symbols.IrSimpleFunctionSymbol
import org.jetbrains.kotlin.ir.symbols.UnsafeDuringIrConstructionAPI
import org.jetbrains.kotlin.ir.types.IrType
import org.jetbrains.kotlin.ir.types.getClass
import org.jetbrains.kotlin.ir.util.SYNTHETIC_OFFSET
import org.jetbrains.kotlin.ir.util.dump

@OptIn(UnsafeDuringIrConstructionAPI::class)
fun IrFunctionAccessExpression.putArguments( // @formatter:off
    typeArguments: Map<String, IrType>,
    valueArguments: Map<String, IrExpression>
) { // @formatter:on
    val function = symbol.owner
    for ((name, type) in typeArguments) {
        var parameter = function.typeParameters.find { it.name.asString() == name }
        // For constructor calls, alternatively attempt to resolve type parameter from class
        if (parameter == null && this is IrConstructorCall) {
            parameter = this.type.getClass()?.typeParameters?.find { it.name.asString() == name }
        }
        check(parameter != null) { "No type parameter named $name found in ${function.dump()}" }
        this.typeArguments[parameter.index] = type
    }
    for ((name, value) in valueArguments) {
        val parameter = function.parameters.find { it.name.asString() == name }
        check(parameter != null) { "No value parameter named $name found in ${function.dump()}" }
        arguments[parameter] = value
    }
}

@OptIn(UnsafeDuringIrConstructionAPI::class)
fun IrSimpleFunctionSymbol.call(
    startOffset: Int = SYNTHETIC_OFFSET,
    endOffset: Int = SYNTHETIC_OFFSET,
    typeArguments: Map<String, IrType> = emptyMap(),
    valueArguments: Map<String, IrExpression> = emptyMap(),
    dispatchReceiver: IrExpression? = null,
    extensionReceiver: IrExpression? = null
): IrCall = IrCallImplWithShape(
    startOffset = startOffset,
    endOffset = endOffset,
    type = owner.returnType,
    symbol = this,
    typeArgumentsCount = typeArguments.size,
    valueArgumentsCount = valueArguments.size,
    contextParameterCount = 0,
    hasDispatchReceiver = dispatchReceiver != null,
    hasExtensionReceiver = extensionReceiver != null
).apply {
    this.dispatchReceiver = dispatchReceiver
    owner.parameters.find { it.kind == IrParameterKind.ExtensionReceiver }?.let {
        arguments[it] = extensionReceiver
    }
    putArguments(typeArguments, valueArguments)
}

fun IrSimpleFunction.call(
    startOffset: Int = SYNTHETIC_OFFSET,
    endOffset: Int = SYNTHETIC_OFFSET,
    typeArguments: Map<String, IrType> = emptyMap(),
    valueArguments: Map<String, IrExpression> = emptyMap(),
    dispatchReceiver: IrExpression? = null,
    extensionReceiver: IrExpression? = null
): IrCall = symbol.call(startOffset, endOffset, typeArguments, valueArguments, dispatchReceiver, extensionReceiver)

fun IrSimpleFunction.createExpression( // @formatter:off
    irBuiltIns: IrBuiltIns,
    startOffset: Int = SYNTHETIC_OFFSET,
    endOffset: Int = SYNTHETIC_OFFSET
): IrFunctionExpression = IrFunctionExpressionImpl( // @formatter:on
    startOffset = startOffset,
    endOffset = endOffset,
    type = getFunctionType(irBuiltIns),
    function = this,
    origin = IrStatementOrigin.LAMBDA
)