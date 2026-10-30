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

import dev.karmakrafts.kcml.api.backend.BackendType
import dev.karmakrafts.kcml.api.backend.jvm.JvmCodeGenerator
import dev.karmakrafts.kcml.api.backend.jvm.LateJvmBackend
import dev.karmakrafts.kcml.api.extension.AbstractExtension
import dev.karmakrafts.kcml.api.extension.ExtensionId
import dev.karmakrafts.kcml.api.extension.jvm.JvmIntrinsicsExtension
import org.jetbrains.kotlin.backend.jvm.codegen.MaterialValue
import org.jetbrains.kotlin.backend.jvm.codegen.PromisedValue
import org.jetbrains.kotlin.ir.expressions.IrFunctionAccessExpression
import org.jetbrains.kotlin.ir.symbols.UnsafeDuringIrConstructionAPI
import org.jetbrains.org.objectweb.asm.Type

@OptIn(UnsafeDuringIrConstructionAPI::class)
@ExtensionId("example_jvm_intrinsics_extension")
internal class ExampleJvmIntrinsicsExtension : AbstractExtension(), JvmIntrinsicsExtension {
    private var initializationCount = 0

    override fun init(backend: LateJvmBackend) {
        check(backend.type == BackendType.LateJvm)
        check(backend.state === backend.context.state)
        check(backend.config === backend.context.configuration)
        check(backend.irBuiltIns === backend.context.irBuiltIns)
        check(backend.symbols === backend.context.symbols)
        check(backend.compileTarget.name == "JVM" || backend.compileTarget.isAndroid)
        initializationCount++
    }

    override fun shouldProcess(call: IrFunctionAccessExpression, backend: LateJvmBackend): Boolean {
        return call.symbol.owner.name.asString() in setOf("jvmIntrinsic", "jvmInitializationCount")
    }

    override fun JvmCodeGenerator.process(call: IrFunctionAccessExpression): PromisedValue {
        if (call.symbol.owner.name.asString() == "jvmInitializationCount") {
            instructionAdapter.iconst(initializationCount)
        }
        else {
            val argument = requireNotNull(call.arguments.single())
            generateExpression(argument).materializeAt(Type.INT_TYPE, argument.type, false)
            instructionAdapter.iconst(10)
            instructionAdapter.add(Type.INT_TYPE)
        }
        return MaterialValue(context, Type.INT_TYPE, call.type)
    }
}