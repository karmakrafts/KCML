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

package dev.karmakrafts.kcml.hooks.native

import kotlinx.cinterop.ExperimentalForeignApi
import llvm.LLVMContextRef
import llvm.LLVMModuleRef
import org.jetbrains.kotlin.backend.konan.NativeSecondStageCompilationConfig
import org.jetbrains.kotlin.backend.konan.driver.NativePhaseContext
import org.jetbrains.kotlin.backend.konan.ir.BackendNativeSymbols
import org.jetbrains.kotlin.builtins.konan.KonanBuiltIns

/**
 * Kotlin/Native compiler does not expose this type to the outside world,
 * so we have to build it ourselves.
 */
@OptIn(ExperimentalForeignApi::class)
internal data class NativeGenerationState(
    val builtIns: KonanBuiltIns,
    val symbols: BackendNativeSymbols,
    val phaseContext: NativePhaseContext,
    val secondStageConfig: NativeSecondStageCompilationConfig,
    val llvmContext: LLVMContextRef,
    val llvmModule: LLVMModuleRef
)