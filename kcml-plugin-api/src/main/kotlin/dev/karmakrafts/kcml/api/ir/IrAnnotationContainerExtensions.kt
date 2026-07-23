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

fun IrAnnotationContainer.getAnnotation( // @formatter:off
    type: FqName,
    index: Int = 0
): IrConstructorCall? { // @formatter:on
    return annotations.filter { it.type.classFqName == type }.getOrNull(index)
}

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

inline fun <reified T> IrAnnotationContainer.getAnnotationValue( // @formatter:off
    type: FqName,
    name: String,
    index: Int = 0
): T? = getRawAnnotationValue(type, name, index).unwrapConstValue<T>() // @formatter:on

inline fun <reified T> IrAnnotationContainer.getAnnotationValues( // @formatter:off
    type: FqName,
    name: String,
    index: Int = 0
): List<T?> = getRawAnnotationValue(type, name, index).unwrapConstValues<T>() // @formatter:on