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

import org.jetbrains.kotlin.ir.declarations.IrField
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.expressions.IrGetField
import org.jetbrains.kotlin.ir.expressions.IrSetField
import org.jetbrains.kotlin.ir.expressions.impl.IrGetFieldImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrSetFieldImpl
import org.jetbrains.kotlin.ir.symbols.IrFieldSymbol
import org.jetbrains.kotlin.ir.symbols.UnsafeDuringIrConstructionAPI
import org.jetbrains.kotlin.ir.util.SYNTHETIC_OFFSET

@OptIn(UnsafeDuringIrConstructionAPI::class)
fun IrFieldSymbol.load(receiver: IrExpression? = null): IrGetField = IrGetFieldImpl( // @formatter:off
    startOffset = SYNTHETIC_OFFSET,
    endOffset = SYNTHETIC_OFFSET,
    symbol = this,
    type = owner.type,
    receiver = receiver
) // @formatter:on

fun IrField.load(receiver: IrExpression? = null): IrGetField = symbol.load(receiver)

@OptIn(UnsafeDuringIrConstructionAPI::class)
fun IrFieldSymbol.store(
    value: IrExpression, receiver: IrExpression? = null
): IrSetField = IrSetFieldImpl( // @formatter:off
    startOffset = SYNTHETIC_OFFSET,
    endOffset = SYNTHETIC_OFFSET,
    symbol = this,
    type = owner.type,
    receiver = receiver,
    value = value
) // @formatter:on

fun IrField.store(
    value: IrExpression, receiver: IrExpression? = null
): IrSetField = symbol.store(value, receiver)