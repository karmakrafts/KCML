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

import dev.karmakrafts.kcml.api.log.LoggerFactory
import dev.karmakrafts.kcml.api.plugin.PluginLoader
import dev.karmakrafts.kcml.hooks.ActualType
import dev.karmakrafts.kcml.log.MessageCollectorLoggerFactory
import dev.karmakrafts.kcml.util.ReflectionUtils
import kotlinx.cinterop.ExperimentalForeignApi
import llvm.LLVMContextRef
import llvm.LLVMModuleRef
import org.jetbrains.kotlin.backend.konan.NativeSecondStageCompilationConfig
import org.jetbrains.kotlin.backend.konan.driver.NativePhaseContext
import org.jetbrains.kotlin.backend.konan.ir.BackendNativeSymbols
import org.jetbrains.kotlin.builtins.konan.KonanBuiltIns
import org.jetbrains.kotlin.config.messageCollector
import org.jetbrains.kotlin.ir.IrBuiltIns

@OptIn(ExperimentalForeignApi::class)
internal data class NativeGenerationStateView(
    val moduleId: String,
    val builtIns: KonanBuiltIns,
    val irBuiltIns: IrBuiltIns,
    val symbols: BackendNativeSymbols,
    val phaseContext: NativePhaseContext,
    val secondStageConfig: NativeSecondStageCompilationConfig,
    val loggerFactory: LoggerFactory,
    val llvmContext: LLVMContextRef,
    val llvmModule: LLVMModuleRef
) {
    companion object {
        fun fromImpl( // @formatter:off
            @ActualType("NativeGenerationState") impl: Any,
            loader: PluginLoader
        ): Result<NativeGenerationStateView> = runCatching { // @formatter:on
            // First retrieve NativePhaseConfig and configs non-reflectively
            val phaseContext = impl as NativePhaseContext // NativeGenerationState implements NativePhaseContext
            val nativeConfig = phaseContext.config
            val config = nativeConfig.configuration
            val messageCollector = config.messageCollector
            val loggerFactory = MessageCollectorLoggerFactory(loader, messageCollector)
            val logger = loggerFactory("KCML")
            // Then reflect out the guts of the Konan Context and related fields
            @ActualType("Context") val context = ReflectionUtils.getField<Any, Any>("context", impl)
            val secondStageConfig = ReflectionUtils.getSuperField<Any, NativeSecondStageCompilationConfig>( // @formatter:off
                superClassName = "BasicNativeBackendPhaseContext",
                name = "config",
                instance = impl
            ) // @formatter:on
            val moduleId = secondStageConfig.moduleId
            logger.info("Creating NativeGenerationStateView for module '$moduleId'")
            val builtIns = ReflectionUtils.getField<Any, KonanBuiltIns>("builtIns", context)
            val irBuiltIns = ReflectionUtils.getField<Any, IrBuiltIns>("irBuiltIns", context)
            val symbols = ReflectionUtils.getField<Any, BackendNativeSymbols>("symbols", context)
            val llvmContext = ReflectionUtils.getField<Any, LLVMContextRef>("llvmContext", impl)
            logger.info("LLVM context at 0x${llvmContext.rawValue.toHexString()}")
            val llvmCodegenHelpers by ReflectionUtils.getField<Any, Lazy<Any>>("llvmDelegate", impl)
            val llvmModule = ReflectionUtils.getSuperField<Any, LLVMModuleRef>( // @formatter:off
                superClassName = "BasicLlvmHelpers",
                name = "module",
                instance = llvmCodegenHelpers
            ) // @formatter:on
            logger.info("LLVM module at 0x${llvmModule.rawValue.toHexString()}")
            logger.info("Extracted internals of NativeGenerationState")
            NativeGenerationStateView(
                moduleId = moduleId,
                builtIns = builtIns,
                irBuiltIns = irBuiltIns,
                symbols = symbols,
                phaseContext = phaseContext,
                secondStageConfig = secondStageConfig,
                loggerFactory = loggerFactory,
                llvmContext = llvmContext,
                llvmModule = llvmModule
            )
        }
    }
}