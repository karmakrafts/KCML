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

import org.jetbrains.kotlin.ir.declarations.IrField
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.expressions.IrGetField
import org.jetbrains.kotlin.ir.expressions.IrSetField
import org.jetbrains.kotlin.ir.expressions.impl.IrGetFieldImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrSetFieldImpl
import org.jetbrains.kotlin.ir.symbols.IrFieldSymbol
import org.jetbrains.kotlin.ir.symbols.UnsafeDuringIrConstructionAPI
import org.jetbrains.kotlin.ir.util.SYNTHETIC_OFFSET

/**
 * Creates an IR expression that reads this field symbol.
 *
 * @param receiver dispatch receiver for an instance field, or `null` for a static/top-level field.
 * @return a synthetic-offset field-read expression.
 */
@OptIn(UnsafeDuringIrConstructionAPI::class)
fun IrFieldSymbol.load(receiver: IrExpression? = null): IrGetField = IrGetFieldImpl( // @formatter:off
    startOffset = SYNTHETIC_OFFSET,
    endOffset = SYNTHETIC_OFFSET,
    symbol = this,
    type = owner.type,
    receiver = receiver
) // @formatter:on

/**
 * Creates an IR expression that reads this field.
 *
 * @param receiver dispatch receiver for an instance field, or `null` for a static/top-level field.
 * @return a synthetic-offset field-read expression.
 */
fun IrField.load(receiver: IrExpression? = null): IrGetField = symbol.load(receiver)

/**
 * Creates an IR expression that assigns a value to this field symbol.
 *
 * @param value expression whose result is assigned to the field.
 * @param receiver dispatch receiver for an instance field, or `null` for a static/top-level field.
 * @return a synthetic-offset field-write expression.
 */
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

/**
 * Creates an IR expression that assigns a value to this field.
 *
 * @param value expression whose result is assigned to the field.
 * @param receiver dispatch receiver for an instance field, or `null` for a static/top-level field.
 * @return a synthetic-offset field-write expression.
 */
fun IrField.store(
    value: IrExpression, receiver: IrExpression? = null
): IrSetField = symbol.store(value, receiver)