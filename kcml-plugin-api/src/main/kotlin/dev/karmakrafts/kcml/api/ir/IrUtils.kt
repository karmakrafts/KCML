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

/**
 * Finds the nearest declaration container that encloses this declaration in the Kotlin IR tree.
 *
 * @return the containing declaration container, or `null` when none exists.
 */
fun IrDeclaration.findContainingParent(): IrDeclarationContainer? {
    return when (val parent = parent) {
        is IrDeclarationContainer -> parent
        is IrDeclaration -> parent.findContainingParent()
        else -> null
    }
}

/**
 * Finds the first descendant of a requested IR element type that satisfies a predicate.
 *
 * @param E concrete IR element type to locate.
 * @param predicate additional condition evaluated for candidate descendants.
 * @return the first matching descendant in visitor traversal order, or `null`.
 */
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

/**
 * Creates a declaration-aware IR builder for this symbol owner.
 *
 * @param context generator context supplied by the Kotlin backend.
 * @param startOffset source offset for subsequently generated IR, or [SYNTHETIC_OFFSET].
 * @param endOffset source offset for subsequently generated IR, or [SYNTHETIC_OFFSET].
 * @return a builder associated with this declaration's symbol.
 */
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

/** Creates an IR expression that reads the value represented by this symbol. */
@OptIn(UnsafeDuringIrConstructionAPI::class)
fun IrValueSymbol.load(): IrGetValue = IrGetValueImpl( // @formatter:off
    startOffset = SYNTHETIC_OFFSET,
    endOffset = SYNTHETIC_OFFSET,
    type = owner.type,
    symbol = this
) // @formatter:on

/** Creates an IR expression that reads this value declaration. */
fun IrValueDeclaration.load(): IrGetValue = symbol.load()

/**
 * Creates a composite IR expression that evaluates these statements in order.
 *
 * @param type result type assigned to the composite expression.
 * @return a synthetic-offset IR composite containing these statements.
 */
fun List<IrStatement>.createComposite(type: IrType): IrComposite = IrCompositeImpl( // @formatter:off
    startOffset = SYNTHETIC_OFFSET,
    endOffset = SYNTHETIC_OFFSET,
    type = type,
    origin = null,
    statements = this
) // @formatter:on

/**
 * Creates an IR block that evaluates these statements in order.
 *
 * @param type result type assigned to the block.
 * @param origin optional Kotlin IR origin identifying how the block was produced.
 * @return a synthetic-offset IR block containing these statements.
 */
fun List<IrStatement>.createBlock(type: IrType, origin: IrStatementOrigin? = null): IrBlock = IrBlockImpl( // @formatter:off
    startOffset = SYNTHETIC_OFFSET,
    endOffset = SYNTHETIC_OFFSET,
    type = type,
    origin = origin,
    statements = this
) // @formatter:on

/**
 * Creates an IR vararg expression from these elements.
 *
 * @param irBuiltIns IR built-ins used to construct the array result type.
 * @param type element type of the vararg.
 * @return a synthetic-offset vararg whose type is `Array<type>`.
 */
fun List<IrVarargElement>.createVararg(irBuiltIns: IrBuiltIns, type: IrType): IrVararg = IrVarargImpl(
    startOffset = SYNTHETIC_OFFSET,
    endOffset = SYNTHETIC_OFFSET,
    type = irBuiltIns.arrayClass.typeWith(type),
    varargElementType = type,
    elements = this
)

/**
 * Creates an IR constructor call targeting this constructor symbol.
 *
 * @param startOffset source offset for the generated call, or [SYNTHETIC_OFFSET].
 * @param endOffset source offset for the generated call, or [SYNTHETIC_OFFSET].
 * @param typeArguments mappings from constructor or class type-parameter names to IR types.
 * @param valueArguments mappings from constructor value-parameter names to argument expressions.
 * @return a constructor call with the supplied arguments.
 */
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

/**
 * Creates an IR constructor call targeting this constructor.
 *
 * @param startOffset source offset for the generated call, or [SYNTHETIC_OFFSET].
 * @param endOffset source offset for the generated call, or [SYNTHETIC_OFFSET].
 * @param typeArguments mappings from constructor or class type-parameter names to IR types.
 * @param valueArguments mappings from constructor value-parameter names to argument expressions.
 * @return a constructor call with the supplied arguments.
 */
fun IrConstructor.new(
    startOffset: Int = SYNTHETIC_OFFSET,
    endOffset: Int = SYNTHETIC_OFFSET,
    typeArguments: Map<String, IrType> = emptyMap(),
    valueArguments: Map<String, IrExpression> = emptyMap()
): IrConstructorCall = symbol.new(startOffset, endOffset, typeArguments, valueArguments)

/**
 * Extracts raw constant values from the regular parameters of an annotation constructor call.
 *
 * @return a map from annotation parameter names to their unwrapped IR constant values.
 */
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

/**
 * Resolves an enum entry by name from this enum class symbol.
 *
 * @param name simple name of the requested enum entry.
 * @return the matching enum-entry symbol.
 * @throws IllegalArgumentException if this class has no entry named [name].
 */
@OptIn(UnsafeDuringIrConstructionAPI::class)
fun IrClassSymbol.getEnumConstant(name: String): IrEnumEntrySymbol {
    return requireNotNull(
        defaultType.classOrFail.owner.declarations.filterIsInstance<IrEnumEntry>()
            .find { it.name.asString() == name }) { "No entry $name in $this" }.symbol
}

/**
 * Creates an IR expression for the enum entry selected from a receiver value.
 *
 * @param T type from which the entry name is derived.
 * @param type symbol of the enum class that owns the entry.
 * @param mapper maps the receiver to the requested enum-entry name.
 * @return a synthetic-offset IR enum-value expression.
 */
inline fun <T> T.getEnumValue(
    type: IrClassSymbol, mapper: T.() -> String
): IrGetEnumValueImpl = IrGetEnumValueImpl(
    startOffset = SYNTHETIC_OFFSET,
    endOffset = SYNTHETIC_OFFSET,
    type = type.defaultType,
    symbol = type.getEnumConstant(this.mapper())
)