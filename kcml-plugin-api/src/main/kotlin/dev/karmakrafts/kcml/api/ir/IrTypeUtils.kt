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
import org.jetbrains.kotlin.ir.declarations.IrFunction
import org.jetbrains.kotlin.ir.declarations.IrParameterKind
import org.jetbrains.kotlin.ir.types.IrType
import org.jetbrains.kotlin.ir.types.typeWith

/**
 * Builds the Kotlin function type represented by this IR function's regular parameters and return type.
 *
 * @param irBuiltIns IR built-ins that provide the appropriate `FunctionN` class.
 * @return the corresponding non-reflective Kotlin function type.
 */
fun IrFunction.getFunctionType(irBuiltIns: IrBuiltIns): IrType {
    val paramTypes = parameters.filter { it.kind == IrParameterKind.Regular }.map { it.type }
    return irBuiltIns.functionN(paramTypes.size).typeWith(paramTypes + returnType)
}

/**
 * Builds the reflective Kotlin function type represented by this IR function's regular parameters and return type.
 *
 * @param irBuiltIns IR built-ins that provide the appropriate `KFunctionN` class.
 * @return the corresponding reflective Kotlin function type.
 */
fun IrFunction.getKFunctionType(irBuiltIns: IrBuiltIns): IrType {
    val paramTypes = parameters.filter { it.kind == IrParameterKind.Regular }.map { it.type }
    return irBuiltIns.kFunctionN(paramTypes.size).typeWith(paramTypes + returnType)
}