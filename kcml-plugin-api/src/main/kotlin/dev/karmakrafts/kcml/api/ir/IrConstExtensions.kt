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

import org.jetbrains.kotlin.ir.IrElement
import org.jetbrains.kotlin.ir.declarations.IrParameterKind
import org.jetbrains.kotlin.ir.expressions.IrCall
import org.jetbrains.kotlin.ir.expressions.IrClassReference
import org.jetbrains.kotlin.ir.expressions.IrConst
import org.jetbrains.kotlin.ir.expressions.IrConstantArray
import org.jetbrains.kotlin.ir.expressions.IrConstantPrimitive
import org.jetbrains.kotlin.ir.expressions.IrErrorExpression
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.expressions.IrExpressionBody
import org.jetbrains.kotlin.ir.expressions.IrGetEnumValue
import org.jetbrains.kotlin.ir.expressions.IrGetField
import org.jetbrains.kotlin.ir.expressions.IrVararg
import org.jetbrains.kotlin.ir.symbols.UnsafeDuringIrConstructionAPI
import org.jetbrains.kotlin.ir.util.target

/**
 * Unwraps an IR representation of a Kotlin compile-time constant into its runtime value.
 *
 * This supports annotation argument forms emitted by Kotlin IR, including fields, enum entries,
 * class literals, arrays, varargs, and conversion calls around primitive constants.
 *
 * @return the raw value, or `null` when this element is not a supported constant representation.
 * @throws IllegalStateException if an error expression or a non-expression annotation vararg element is encountered.
 */
@OptIn(UnsafeDuringIrConstructionAPI::class)
fun IrElement?.unwrapRawConstValue(): Any? {
    return when (this) {
        is IrErrorExpression -> error("Got IrErrorExpression in getConstType: $description")
        is IrExpressionBody -> expression.unwrapRawConstValue()
        is IrGetField -> symbol.owner.initializer.unwrapRawConstValue()
        is IrGetEnumValue -> symbol.owner.name.asString() // Enum values are unwrapped to their constant names
        is IrClassReference -> classType
        is IrConst -> value
        is IrConstantPrimitive -> value.unwrapRawConstValue()
        is IrConstantArray -> elements.map { it.unwrapRawConstValue() }.toList()

        is IrVararg -> elements.map { element ->
            check(element is IrExpression) { "Annotation vararg element must be an expression" }
            element.unwrapRawConstValue()
        }.toList()

        // Edge case for handling constants wrapped by conversion functions such as toNInt, toNUInt, toNFloat etc.
        is IrCall -> {
            var receiver = dispatchReceiver
            if (receiver != null && receiver is IrConst) {
                return receiver.unwrapRawConstValue()
            }
            receiver = arguments[target.parameters.single { it.kind == IrParameterKind.ExtensionReceiver }]
            if (receiver != null && receiver is IrConst) {
                return receiver.unwrapRawConstValue()
            }
            null
        }

        else -> null
    }
}

/**
 * Unwraps an IR constant and converts it to a requested Kotlin type.
 *
 * Enum constants are resolved from the enum-entry name represented in IR.
 *
 * @param T expected Kotlin value type.
 * @return the converted constant value, or `null` when it is absent or has a different type.
 */
@Suppress("UNCHECKED_CAST")
inline fun <reified T> IrElement?.unwrapConstValue(): T? {
    val value = unwrapRawConstValue()
    val javaType = T::class.java
    return (if (javaType.isEnum) (javaType.enumConstants as Array<Enum<*>>).find { it.name == value as? String }
    else value) as? T
}

/**
 * Unwraps an IR array or vararg constant and converts each element to a requested Kotlin type.
 *
 * Enum constants are resolved from their IR entry names.
 *
 * @param T expected Kotlin value type for each array element.
 * @return converted values, or an empty list when this element does not represent a constant array.
 */
@Suppress("UNCHECKED_CAST")
inline fun <reified T> IrElement?.unwrapConstValues(): List<T?> {
    val values = unwrapRawConstValue() as List<Any?>? ?: return emptyList()
    val javaType = T::class.java
    val isEnum = javaType.isEnum
    return values.map { value ->
        (if (isEnum) (javaType.enumConstants as Array<Enum<*>>).find { it.name == value as? String }
        else value) as? T
    }
}