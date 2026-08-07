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

package dev.karmakrafts.kcml.hooks.llvm

import dev.karmakrafts.kcml.api.plugin.PluginLoader
import dev.karmakrafts.kcml.hooks.ActualType
import dev.karmakrafts.kcml.util.ReflectionUtils
import kotlinx.cinterop.ExperimentalForeignApi
import llvm.LLVMTypeRef
import llvm.LLVMValueRef
import org.jetbrains.kotlin.backend.konan.llvm.LlvmCallable
import org.jetbrains.kotlin.ir.declarations.IrSimpleFunction
import org.jetbrains.kotlin.ir.types.IrType

@OptIn(ExperimentalForeignApi::class)
internal data class CodeGeneratorView( // @formatter:off
    val tryMaterializeFunctionCallback: (IrSimpleFunction) -> LlvmCallable?,
    val tryMaterializeTypeCallback: (IrType) -> LLVMTypeRef?,
    val unitInstance: LLVMValueRef
) {
    // @formatter:on
    companion object {
        @Suppress("UNCHECKED_CAST")
        fun fromImpl( // @formatter:off
            @ActualType("CodeGenerator") impl: Any,
            loader: PluginLoader
        ): Result<CodeGeneratorView> = runCatching { // @formatter:on
            loader.logger.info("Creating CodeGeneratorView")
            val type = impl::class.java
            val llvmFunctionOrNull = type.declaredMethods.first { method -> method.name == "llvmFunctionOrNull" }
            loader.logger.info("Got llvmFunctionOrNull method reference")
            val llvm = ReflectionUtils.getSuperDelegateProperty<Any, Any>( // @formatter:off
                superClassName = "ContextUtils",
                name = "llvm",
                instance = impl
            ) // @formatter:on
            val toLLVMType = Class.forName(
                "org.jetbrains.kotlin.backend.konan.llvm.DataLayoutKt"
            ).declaredMethods.first { method -> method.name == "toLLVMType" }
            loader.logger.info("Got toLLVMType method reference")
            val getUnitInstance = Class.forName(
                "org.jetbrains.kotlin.backend.konan.llvm.KotlinStaticDataKt"
            ).declaredMethods.first { method -> method.name == "getTheUnitInstanceRef" }
            val unitInstanceRef = getUnitInstance.invoke(null, impl)
            val unitInstance = unitInstanceRef::class.java.methods.first { method -> method.name == "getLlvm" }
                .invoke(unitInstanceRef) as LLVMValueRef
            loader.logger.info("Got the Unit instance at 0x${unitInstance.rawValue.toHexString()}")
            CodeGeneratorView( // @formatter:off
                tryMaterializeFunctionCallback = { function ->
                    llvmFunctionOrNull.invoke(impl, function) as? LlvmCallable
                },
                tryMaterializeTypeCallback = { irType ->
                    toLLVMType.invoke(null, irType, llvm) as? LLVMTypeRef
                },
                unitInstance = unitInstance
            ) // @formatter:on
        }
    }
}