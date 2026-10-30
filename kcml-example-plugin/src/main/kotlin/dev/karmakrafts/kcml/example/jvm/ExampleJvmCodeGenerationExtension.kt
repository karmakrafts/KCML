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

package dev.karmakrafts.kcml.example.jvm

import dev.karmakrafts.kcml.api.backend.jvm.JvmCodeGenerator
import dev.karmakrafts.kcml.api.backend.jvm.LateJvmBackend
import dev.karmakrafts.kcml.api.extension.AbstractExtension
import dev.karmakrafts.kcml.api.extension.ExtensionId
import dev.karmakrafts.kcml.api.extension.jvm.JvmCodeGenerationExtension
import org.jetbrains.kotlin.backend.jvm.codegen.MaterialValue
import org.jetbrains.kotlin.backend.jvm.codegen.PromisedValue
import org.jetbrains.kotlin.ir.IrElement
import org.jetbrains.kotlin.ir.declarations.IrVariable
import org.jetbrains.kotlin.ir.expressions.IrConst
import org.jetbrains.kotlin.ir.expressions.IrReturn
import org.jetbrains.kotlin.ir.expressions.impl.IrConstImpl
import org.jetbrains.org.objectweb.asm.Type

@ExtensionId("example_jvm_code_generation_extension")
internal class ExampleJvmCodeGenerationExtension : AbstractExtension(), JvmCodeGenerationExtension {
    override fun shouldProcess(element: IrElement, backend: LateJvmBackend): Boolean {
        return when (element) {
            is IrConst -> element.value == 0x4B434D4C
            is IrReturn -> (element.value as? IrConst)?.value == 0x4B434D4D
            is IrVariable -> element.name.asString() == "jvmLocalTransform"
            else -> false
        }
    }

    override fun JvmCodeGenerator.process(element: IrElement): PromisedValue? {
        when (element) {
            is IrConst -> {
                instructionAdapter.iconst(45)
                return MaterialValue(context, Type.INT_TYPE, element.type)
            }

            is IrReturn -> element.value = IrConstImpl.int(
                element.value.startOffset, element.value.endOffset, element.value.type, 46
            )

            is IrVariable -> element.initializer = IrConstImpl.int(
                element.startOffset, element.endOffset, element.type, 48
            )
        }
        return null
    }
}