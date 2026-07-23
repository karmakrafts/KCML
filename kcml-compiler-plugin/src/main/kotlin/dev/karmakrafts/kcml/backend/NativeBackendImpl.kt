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

package dev.karmakrafts.kcml.backend

import dev.karmakrafts.kcml.api.backend.NativeBackend
import dev.karmakrafts.kcml.hooks.InternalHooksApi
import dev.karmakrafts.kcml.hooks.native.TopLevelPhasesHooks
import kotlinx.cinterop.ExperimentalForeignApi
import llvm.LLVMContextRef
import llvm.LLVMModuleRef
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.backend.konan.NativeSecondStageCompilationConfig
import org.jetbrains.kotlin.backend.konan.driver.NativePhaseContext
import org.jetbrains.kotlin.backend.konan.ir.BackendNativeSymbols
import org.jetbrains.kotlin.builtins.konan.KonanBuiltIns
import org.jetbrains.kotlin.config.CompilerConfiguration

@OptIn(ExperimentalForeignApi::class, InternalHooksApi::class)
internal class NativeBackendImpl( // @formatter:off
    context: IrPluginContext,
    override val config: CompilerConfiguration
) : AbstractBackend(context, config), NativeBackend { // @formatter:on
    override val builtIns: KonanBuiltIns
        get() = TopLevelPhasesHooks.generationState.builtIns
    override val symbols: BackendNativeSymbols
        get() = TopLevelPhasesHooks.generationState.symbols
    override val phaseContext: NativePhaseContext
        get() = TopLevelPhasesHooks.generationState.phaseContext
    override val secondStageConfig: NativeSecondStageCompilationConfig
        get() = TopLevelPhasesHooks.generationState.secondStageConfig
    override val llvmContext: LLVMContextRef
        get() = TopLevelPhasesHooks.generationState.llvmContext
    override val llvmModule: LLVMModuleRef
        get() = TopLevelPhasesHooks.generationState.llvmModule
}