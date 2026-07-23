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

import dev.karmakrafts.kcml.hooks.InternalHooksApi
import dev.karmakrafts.kcml.hooks.UsedAtRuntime
import kotlinx.cinterop.ExperimentalForeignApi
import llvm.LLVMContextRef
import llvm.LLVMModuleRef
import org.jetbrains.kotlin.backend.konan.NativeSecondStageCompilationConfig
import org.jetbrains.kotlin.backend.konan.driver.NativePhaseContext
import org.jetbrains.kotlin.backend.konan.ir.BackendNativeSymbols
import org.jetbrains.kotlin.builtins.konan.KonanBuiltIns
import kotlin.concurrent.atomics.AtomicReference

/**
 * Exposes public hooks for injected code in Kotlin/Native TopLevelPhases.kt
 * to capture internal compiler state.
 */
@OptIn(ExperimentalForeignApi::class)
@InternalHooksApi
object TopLevelPhasesHooks {
    internal val _generationState: AtomicReference<NativeGenerationState?> = AtomicReference(null)
    internal inline val generationState: NativeGenerationState get() = _generationState.load()!!

    @Suppress("UNUSED")
    @UsedAtRuntime
    @JvmStatic
    fun onRunAllLowerings( // @formatter:off
        builtIns: KonanBuiltIns,
        symbols: BackendNativeSymbols,
        phaseContext: NativePhaseContext,
        secondStageConfig: NativeSecondStageCompilationConfig,
        llvmContext: LLVMContextRef,
        llvmModule: LLVMModuleRef
    ) { // @formatter:on
        _generationState.store(
            NativeGenerationState(
                builtIns = builtIns,
                symbols = symbols,
                phaseContext = phaseContext,
                secondStageConfig = secondStageConfig,
                llvmContext = llvmContext,
                llvmModule = llvmModule
            )
        )
    }
}