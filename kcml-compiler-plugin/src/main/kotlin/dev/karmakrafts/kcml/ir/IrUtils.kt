/*
 * Copyright 2025 Karma Krafts & associates
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

package dev.karmakrafts.kcml.ir

import org.jetbrains.kotlin.backend.common.lower.DeclarationIrBuilder
import org.jetbrains.kotlin.ir.IrBuiltIns
import org.jetbrains.kotlin.ir.IrElement
import org.jetbrains.kotlin.ir.IrStatement
import org.jetbrains.kotlin.ir.builders.IrGeneratorContext
import org.jetbrains.kotlin.ir.declarations.IrConstructor
import org.jetbrains.kotlin.ir.declarations.IrDeclaration
import org.jetbrains.kotlin.ir.declarations.IrDeclarationContainer
import org.jetbrains.kotlin.ir.declarations.IrEnumEntry
import org.jetbrains.kotlin.ir.declarations.IrParameterKind
import org.jetbrains.kotlin.ir.declarations.IrSymbolOwner
import org.jetbrains.kotlin.ir.declarations.IrValueDeclaration
import org.jetbrains.kotlin.ir.expressions.IrBlock
import org.jetbrains.kotlin.ir.expressions.IrComposite
import org.jetbrains.kotlin.ir.expressions.IrConstructorCall
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.expressions.IrGetValue
import org.jetbrains.kotlin.ir.expressions.IrStatementOrigin
import org.jetbrains.kotlin.ir.expressions.IrVararg
import org.jetbrains.kotlin.ir.expressions.IrVarargElement
import org.jetbrains.kotlin.ir.expressions.impl.IrBlockImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrCompositeImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrConstructorCallImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrGetEnumValueImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrGetValueImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrVarargImpl
import org.jetbrains.kotlin.ir.symbols.IrClassSymbol
import org.jetbrains.kotlin.ir.symbols.IrConstructorSymbol
import org.jetbrains.kotlin.ir.symbols.IrEnumEntrySymbol
import org.jetbrains.kotlin.ir.symbols.IrValueSymbol
import org.jetbrains.kotlin.ir.symbols.UnsafeDuringIrConstructionAPI
import org.jetbrains.kotlin.ir.types.IrType
import org.jetbrains.kotlin.ir.types.classOrFail
import org.jetbrains.kotlin.ir.types.defaultType
import org.jetbrains.kotlin.ir.types.typeWith
import org.jetbrains.kotlin.ir.util.SYNTHETIC_OFFSET
import org.jetbrains.kotlin.ir.visitors.IrVisitorVoid
import org.jetbrains.kotlin.ir.visitors.acceptChildrenVoid
import org.jetbrains.kotlin.ir.visitors.acceptVoid

fun IrDeclaration.findContainingParent(): IrDeclarationContainer? {
    return when (val parent = parent) {
        is IrDeclarationContainer -> parent
        is IrDeclaration -> parent.findContainingParent()
        else -> null
    }
}

inline fun <reified E : IrElement> IrElement.findChild(crossinline predicate: (E) -> Boolean = { true }): E? {
    var result: E? = null
    acceptVoid(object : IrVisitorVoid() {
        override fun visitElement(element: IrElement) {
            if (result == null && element is E && predicate(element)) {
                result = element
                return
            }
            element.acceptChildrenVoid(this)
        }
    })
    return result
}

fun IrSymbolOwner.declarationBuilder( // @formatter:off
    context: IrGeneratorContext,
    startOffset: Int = SYNTHETIC_OFFSET,
    endOffset: Int = SYNTHETIC_OFFSET
): DeclarationIrBuilder = DeclarationIrBuilder(
    generatorContext = context,
    symbol = symbol,
    startOffset = startOffset,
    endOffset = endOffset
) // @formatter:on

@OptIn(UnsafeDuringIrConstructionAPI::class)
fun IrValueSymbol.load(): IrGetValue = IrGetValueImpl( // @formatter:off
    startOffset = SYNTHETIC_OFFSET,
    endOffset = SYNTHETIC_OFFSET,
    type = owner.type,
    symbol = this
) // @formatter:on

fun IrValueDeclaration.load(): IrGetValue = symbol.load()

fun List<IrStatement>.createComposite(type: IrType): IrComposite = IrCompositeImpl( // @formatter:off
    startOffset = SYNTHETIC_OFFSET,
    endOffset = SYNTHETIC_OFFSET,
    type = type,
    origin = null,
    statements = this
) // @formatter:on

fun List<IrStatement>.createBlock(type: IrType, origin: IrStatementOrigin? = null): IrBlock = IrBlockImpl( // @formatter:off
    startOffset = SYNTHETIC_OFFSET,
    endOffset = SYNTHETIC_OFFSET,
    type = type,
    origin = origin,
    statements = this
) // @formatter:on

fun List<IrVarargElement>.createVararg(irBuiltIns: IrBuiltIns, type: IrType): IrVararg = IrVarargImpl(
    startOffset = SYNTHETIC_OFFSET,
    endOffset = SYNTHETIC_OFFSET,
    type = irBuiltIns.arrayClass.typeWith(type),
    varargElementType = type,
    elements = this
)

@OptIn(UnsafeDuringIrConstructionAPI::class)
fun IrConstructorSymbol.new(
    startOffset: Int = SYNTHETIC_OFFSET,
    endOffset: Int = SYNTHETIC_OFFSET,
    typeArguments: Map<String, IrType> = emptyMap(),
    valueArguments: Map<String, IrExpression> = emptyMap()
): IrConstructorCall = IrConstructorCallImpl(
    startOffset = startOffset,
    endOffset = endOffset,
    type = owner.returnType,
    symbol = this,
    typeArgumentsCount = typeArguments.size,
    constructorTypeArgumentsCount = typeArguments.size
).apply {
    putArguments(typeArguments, valueArguments)
}

fun IrConstructor.new(
    startOffset: Int = SYNTHETIC_OFFSET,
    endOffset: Int = SYNTHETIC_OFFSET,
    typeArguments: Map<String, IrType> = emptyMap(),
    valueArguments: Map<String, IrExpression> = emptyMap()
): IrConstructorCall = symbol.new(startOffset, endOffset, typeArguments, valueArguments)

@OptIn(UnsafeDuringIrConstructionAPI::class)
fun IrConstructorCall.getAnnotationValues(): Map<String, Any?> {
    val constructor = symbol.owner
    val parameters = constructor.parameters.filter { it.kind == IrParameterKind.Regular }
    if (parameters.isEmpty()) return emptyMap()
    val parameterNames = parameters.map { it.name.asString() }
    val values = HashMap<String, Any?>()
    val firstParamIndex = parameters.first().indexInParameters
    val lastParamIndex = firstParamIndex + parameters.size
    var paramIndex = 0
    for (index in firstParamIndex..<lastParamIndex) {
        val value = arguments[index]
        values[parameterNames[paramIndex]] = value.unwrapRawConstValue()
        paramIndex++
    }
    return values
}

@OptIn(UnsafeDuringIrConstructionAPI::class)
fun IrClassSymbol.getEnumConstant(name: String): IrEnumEntrySymbol {
    return requireNotNull(
        defaultType.classOrFail.owner.declarations.filterIsInstance<IrEnumEntry>()
            .find { it.name.asString() == name }) { "No entry $name in $this" }.symbol
}

inline fun <T> T.getEnumValue(
    type: IrClassSymbol, mapper: T.() -> String
): IrGetEnumValueImpl = IrGetEnumValueImpl(
    startOffset = SYNTHETIC_OFFSET,
    endOffset = SYNTHETIC_OFFSET,
    type = type.defaultType,
    symbol = type.getEnumConstant(this.mapper())
)