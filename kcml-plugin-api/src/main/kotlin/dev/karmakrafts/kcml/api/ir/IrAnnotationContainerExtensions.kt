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

import org.jetbrains.kotlin.ir.declarations.IrAnnotationContainer
import org.jetbrains.kotlin.ir.declarations.IrParameterKind
import org.jetbrains.kotlin.ir.expressions.IrConstructorCall
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.symbols.UnsafeDuringIrConstructionAPI
import org.jetbrains.kotlin.ir.types.classFqName
import org.jetbrains.kotlin.name.FqName

/**
 * Gets an annotation constructor call of a requested type from this IR declaration.
 *
 * @param type fully qualified name of the annotation class.
 * @param index zero-based occurrence when the annotation is repeated.
 * @return the matching annotation call, or `null` when that occurrence is absent.
 */
fun IrAnnotationContainer.getAnnotation( // @formatter:off
    type: FqName,
    index: Int = 0
): IrConstructorCall? { // @formatter:on
    return annotations.filter { it.type.classFqName == type }.getOrNull(index)
}

/**
 * Gets the raw IR expression supplied to a named annotation argument.
 *
 * @param type fully qualified name of the annotation class.
 * @param name name of a regular constructor parameter of the annotation.
 * @param index zero-based occurrence when the annotation is repeated.
 * @return the argument expression, or `null` when the annotation or parameter is absent.
 */
@OptIn(UnsafeDuringIrConstructionAPI::class)
fun IrAnnotationContainer.getRawAnnotationValue(
    type: FqName, name: String, index: Int = 0
): IrExpression? {
    val annotation = getAnnotation(type, index) ?: return null
    val constructor = annotation.symbol.owner
    // @formatter:off
    val parameter = constructor.parameters
        .filter { it.kind == IrParameterKind.Regular }
        .find { it.name.asString() == name }
        ?: return null
    // @formatter:on
    return annotation.arguments[parameter]
}

/**
 * Gets and converts a scalar annotation argument.
 *
 * @param T expected Kotlin value type.
 * @param type fully qualified name of the annotation class.
 * @param name name of a regular constructor parameter of the annotation.
 * @param index zero-based occurrence when the annotation is repeated.
 * @return the converted argument value, or `null` when it is unavailable or incompatible.
 */
inline fun <reified T> IrAnnotationContainer.getAnnotationValue( // @formatter:off
    type: FqName,
    name: String,
    index: Int = 0
): T? = getRawAnnotationValue(type, name, index).unwrapConstValue<T>() // @formatter:on

/**
 * Gets and converts an array or vararg annotation argument.
 *
 * @param T expected Kotlin value type for each argument element.
 * @param type fully qualified name of the annotation class.
 * @param name name of a regular constructor parameter of the annotation.
 * @param index zero-based occurrence when the annotation is repeated.
 * @return converted argument values, or an empty list when the argument is unavailable or not an array.
 */
inline fun <reified T> IrAnnotationContainer.getAnnotationValues( // @formatter:off
    type: FqName,
    name: String,
    index: Int = 0
): List<T?> = getRawAnnotationValue(type, name, index).unwrapConstValues<T>() // @formatter:on