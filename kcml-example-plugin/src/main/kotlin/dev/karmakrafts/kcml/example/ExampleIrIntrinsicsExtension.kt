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

package dev.karmakrafts.kcml.example

import dev.karmakrafts.kcml.api.backend.IrBackend
import dev.karmakrafts.kcml.api.extension.AbstractExtension
import dev.karmakrafts.kcml.api.extension.ExtensionId
import dev.karmakrafts.kcml.api.extension.IrIntrinsicsExtension
import org.jetbrains.kotlin.ir.expressions.IrCall
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.symbols.UnsafeDuringIrConstructionAPI
import org.jetbrains.kotlin.ir.util.hasAnnotation
import org.jetbrains.kotlin.ir.util.toIrConst
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name

@OptIn(UnsafeDuringIrConstructionAPI::class)
@ExtensionId("example_ir_intrinsics_extension")
internal class ExampleIrIntrinsicsExtension : AbstractExtension(), IrIntrinsicsExtension {
    companion object {
        private val packageFqName: FqName = FqName("dev.karmakrafts.example")
        private val annotationId: ClassId = ClassId(packageFqName, Name.identifier("CustomIrIntrinsic"))
    }

    override fun shouldProcess(call: IrCall, backend: IrBackend): Boolean {
        return call.symbol.owner.hasAnnotation(annotationId)
    }

    override fun process(call: IrCall, backend: IrBackend): IrExpression {
        return 44.toIrConst(backend.irBuiltIns.intType)
    }
}