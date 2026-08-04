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

package dev.karmakrafts.kcml.backend.llvm

import dev.karmakrafts.kcml.api.backend.llvm.LateNativeBackend
import dev.karmakrafts.kcml.api.log.Logger
import dev.karmakrafts.kcml.api.log.LoggerFactory
import dev.karmakrafts.kcml.api.plugin.PluginLoader
import dev.karmakrafts.kcml.api.target.NativeCompileTarget
import dev.karmakrafts.kcml.hooks.NativeGenerationStateView
import dev.karmakrafts.kcml.target.NativeCompileTargetImpl
import kotlinx.cinterop.ExperimentalForeignApi
import llvm.LLVMContextRef
import llvm.LLVMModuleRef
import org.jetbrains.kotlin.backend.konan.NativeSecondStageCompilationConfig
import org.jetbrains.kotlin.backend.konan.driver.NativePhaseContext
import org.jetbrains.kotlin.backend.konan.ir.BackendNativeSymbols
import org.jetbrains.kotlin.builtins.konan.KonanBuiltIns
import org.jetbrains.kotlin.ir.IrBuiltIns

@OptIn(ExperimentalForeignApi::class)
internal data class LateNativeBackendImpl(
    override val pluginId: String,
    override val irBuiltIns: IrBuiltIns,
    override val builtIns: KonanBuiltIns,
    override val symbols: BackendNativeSymbols,
    override val phaseContext: NativePhaseContext,
    override val secondStageConfig: NativeSecondStageCompilationConfig,
    override val llvmContext: LLVMContextRef,
    override val llvmModule: LLVMModuleRef,
    override val loggerFactory: LoggerFactory,
    override val logger: Logger,
    override val loader: PluginLoader
) : LateNativeBackend {
    companion object {
        fun fromView( // @formatter:off
            stateView: NativeGenerationStateView,
            pluginId: String,
            loader: PluginLoader
        ): LateNativeBackendImpl = LateNativeBackendImpl( // @formatter:on
            pluginId = pluginId,
            irBuiltIns = stateView.irBuiltIns,
            builtIns = stateView.builtIns,
            symbols = stateView.symbols,
            phaseContext = stateView.phaseContext,
            secondStageConfig = stateView.secondStageConfig,
            llvmContext = stateView.llvmContext,
            llvmModule = stateView.llvmModule,
            loggerFactory = stateView.loggerFactory,
            logger = stateView.loggerFactory.getForPlugin(pluginId),
            loader = loader
        )
    }

    override val compileTarget: NativeCompileTarget by lazy {
        NativeCompileTargetImpl(secondStageConfig.target)
    }
}