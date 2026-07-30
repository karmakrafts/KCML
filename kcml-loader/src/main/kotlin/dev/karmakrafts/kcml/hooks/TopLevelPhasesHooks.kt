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

package dev.karmakrafts.kcml.hooks

import dev.karmakrafts.kcml.log.MessageCollectorLoggerFactory
import dev.karmakrafts.kcml.plugin.PluginLoaderImpl
import dev.karmakrafts.kcml.util.ReflectionUtils
import org.jetbrains.kotlin.backend.konan.NativeSecondStageCompilationConfig
import org.jetbrains.kotlin.backend.konan.driver.NativePhaseContext
import org.jetbrains.kotlin.backend.konan.ir.BackendNativeSymbols
import org.jetbrains.kotlin.builtins.konan.KonanBuiltIns
import org.jetbrains.kotlin.config.messageCollector

@Suppress("UNUSED") // This class is used from runtime generated ASM
object TopLevelPhasesHooks {
    @KCMLHookApi
    @JvmStatic
    fun onRunAfterLowerings(@ActualType("NativeGenerationState") state: Any) {
        try {
            // First retrieve NativePhaseConfig and configs non-reflectively
            val phaseContext = state as NativePhaseContext // NativeGenerationState implements NativePhaseContext
            val nativeConfig = phaseContext.config
            val config = nativeConfig.configuration
            val messageCollector = config.messageCollector
            val loggerFactory = MessageCollectorLoggerFactory(PluginLoaderImpl, messageCollector)
            val logger = loggerFactory("KCML")
            logger.info("Intercepted NativePhaseContext in runAfterLowerings")
            // Then reflect out the guts of the Konan Context object and related fields
            @ActualType("Context") val context = ReflectionUtils.getField<Any, Any>("context", state)
            val secondStageConfig = ReflectionUtils.getSuperField<Any, NativeSecondStageCompilationConfig>( // @formatter:off
                superClassName = "BasicNativeBackendPhaseContext",
                name = "config",
                instance = state
            ) // @formatter:on
            val builtIns = ReflectionUtils.getField<Any, KonanBuiltIns>("builtIns", context)
            val symbols = ReflectionUtils.getField<Any, BackendNativeSymbols>("symbols", context)
            logger.info("Extracted internals of NativeGenerationState")
        } catch (_: Throwable) {
            error("Failed to intercept native code generation state")
        }
    }
}