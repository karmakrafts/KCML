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

@Suppress("UNCHECKED_CAST")
inline fun <reified T> IrElement?.unwrapConstValue(): T? {
    val value = unwrapRawConstValue()
    val javaType = T::class.java
    return (if (javaType.isEnum) (javaType.enumConstants as Array<Enum<*>>).find { it.name == value as? String }
    else value) as? T
}

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