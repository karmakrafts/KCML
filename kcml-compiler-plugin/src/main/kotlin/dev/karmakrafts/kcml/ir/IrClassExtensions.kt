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

import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.ir.builders.declarations.addConstructor
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrConstructor
import org.jetbrains.kotlin.ir.expressions.impl.IrDelegatingConstructorCallImplWithShape
import org.jetbrains.kotlin.ir.expressions.impl.IrGetObjectValueImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrInstanceInitializerCallImpl
import org.jetbrains.kotlin.ir.symbols.IrClassSymbol
import org.jetbrains.kotlin.ir.symbols.UnsafeDuringIrConstructionAPI
import org.jetbrains.kotlin.ir.types.defaultType
import org.jetbrains.kotlin.ir.util.SYNTHETIC_OFFSET
import org.jetbrains.kotlin.ir.util.constructors

@OptIn(UnsafeDuringIrConstructionAPI::class)
fun IrClass.addDefaultObjectConstructor( // @formatter:off
    pluginContext: IrPluginContext,
    startOffset: Int = SYNTHETIC_OFFSET,
    endOffset: Int = SYNTHETIC_OFFSET
): IrConstructor = addConstructor { // @formatter:on
    this.startOffset = startOffset
    this.endOffset = endOffset
}.apply {
    val builtIns = pluginContext.irBuiltIns
    body = pluginContext.irFactory.createBlockBody( // @formatter:off
        startOffset = SYNTHETIC_OFFSET,
        endOffset = SYNTHETIC_OFFSET
    ).apply { // @formatter:on
        statements += IrDelegatingConstructorCallImplWithShape(
            startOffset = SYNTHETIC_OFFSET,
            endOffset = SYNTHETIC_OFFSET,
            type = pluginContext.irBuiltIns.unitType,
            symbol = builtIns.anyClass.owner.constructors.first().symbol,
            typeArgumentsCount = 0,
            valueArgumentsCount = 0,
            contextParameterCount = 0,
            hasDispatchReceiver = false,
            hasExtensionReceiver = false
        )
        statements += IrInstanceInitializerCallImpl(
            startOffset = SYNTHETIC_OFFSET,
            endOffset = SYNTHETIC_OFFSET,
            classSymbol = this@addDefaultObjectConstructor.symbol,
            type = builtIns.unitType
        )
    }
}

fun IrClassSymbol.getObjectInstance(): IrGetObjectValueImpl = IrGetObjectValueImpl(
    startOffset = SYNTHETIC_OFFSET, endOffset = SYNTHETIC_OFFSET, type = defaultType, symbol = this@getObjectInstance
)