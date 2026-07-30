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

import dev.karmakrafts.kcml.api.backend.LateNativeBackend
import dev.karmakrafts.kcml.api.log.Logger
import dev.karmakrafts.kcml.api.log.LoggerFactory
import dev.karmakrafts.kcml.api.plugin.PluginLoader
import kotlinx.cinterop.ExperimentalForeignApi
import llvm.LLVMContextRef
import llvm.LLVMModuleRef
import org.jetbrains.kotlin.backend.konan.NativeSecondStageCompilationConfig
import org.jetbrains.kotlin.backend.konan.driver.NativePhaseContext
import org.jetbrains.kotlin.backend.konan.ir.BackendNativeSymbols
import org.jetbrains.kotlin.builtins.konan.KonanBuiltIns

@OptIn(ExperimentalForeignApi::class)
internal data class LateNativeBackendImpl(
    override val builtIns: KonanBuiltIns,
    override val symbols: BackendNativeSymbols,
    override val phaseContext: NativePhaseContext,
    override val secondStageConfig: NativeSecondStageCompilationConfig,
    override val llvmContext: LLVMContextRef,
    override val llvmModule: LLVMModuleRef,
    override val loggerFactory: LoggerFactory,
    override val logger: Logger,
    override val loader: PluginLoader
) : LateNativeBackend