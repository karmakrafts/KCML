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

/**
 * Assigns type and value arguments to an IR function-access expression by parameter name.
 *
 * For constructor calls, type parameters declared by the constructed class are also considered.
 *
 * @param typeArguments mappings from declared type-parameter names to IR types.
 * @param valueArguments mappings from declared value-parameter names to argument expressions.
 * @throws IllegalStateException if an argument name does not resolve on the target function or constructor class.
 */
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

/**
 * Creates an IR call to this simple function symbol.
 *
 * The helper configures the result type, receiver slots, and named arguments required by Kotlin's
 * IR call representation.
 *
 * @param startOffset source offset for the generated call, or [SYNTHETIC_OFFSET].
 * @param endOffset source offset for the generated call, or [SYNTHETIC_OFFSET].
 * @param typeArguments mappings from the function's type-parameter names to IR types.
 * @param valueArguments mappings from the function's value-parameter names to argument expressions.
 * @param dispatchReceiver receiver expression for an instance member call, if required.
 * @param extensionReceiver receiver expression for an extension function call, if required.
 * @return a call expression targeting this function symbol.
 */
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

/**
 * Creates an IR call to this function.
 *
 * This is the declaration counterpart of [IrSimpleFunctionSymbol.call].
 *
 * @param startOffset source offset for the generated call, or [SYNTHETIC_OFFSET].
 * @param endOffset source offset for the generated call, or [SYNTHETIC_OFFSET].
 * @param typeArguments mappings from the function's type-parameter names to IR types.
 * @param valueArguments mappings from the function's value-parameter names to argument expressions.
 * @param dispatchReceiver receiver expression for an instance member call, if required.
 * @param extensionReceiver receiver expression for an extension function call, if required.
 * @return a call expression targeting this function.
 */
fun IrSimpleFunction.call(
    startOffset: Int = SYNTHETIC_OFFSET,
    endOffset: Int = SYNTHETIC_OFFSET,
    typeArguments: Map<String, IrType> = emptyMap(),
    valueArguments: Map<String, IrExpression> = emptyMap(),
    dispatchReceiver: IrExpression? = null,
    extensionReceiver: IrExpression? = null
): IrCall = symbol.call(startOffset, endOffset, typeArguments, valueArguments, dispatchReceiver, extensionReceiver)

/**
 * Wraps this simple function in a lambda-shaped IR function expression.
 *
 * @param irBuiltIns IR built-ins used to derive the function expression's Kotlin function type.
 * @param startOffset source offset for the generated expression, or [SYNTHETIC_OFFSET].
 * @param endOffset source offset for the generated expression, or [SYNTHETIC_OFFSET].
 * @return an IR function expression with the lambda statement origin.
 */
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