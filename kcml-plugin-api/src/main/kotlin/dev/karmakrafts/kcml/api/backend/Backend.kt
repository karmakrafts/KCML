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

package dev.karmakrafts.kcml.api.backend

import dev.karmakrafts.kcml.api.log.Logger
import dev.karmakrafts.kcml.api.log.LoggerFactory
import dev.karmakrafts.kcml.api.plugin.PluginLoader
import dev.karmakrafts.kcml.api.target.CompileTarget
import org.jetbrains.kotlin.config.CompilerConfiguration

/**
 * Provides the compiler services available while KCML integrates with a Kotlin backend.
 *
 * A backend implementation exposes the target-independent IR services used by KCML extensions
 * after FIR analysis has completed.
 */
sealed interface Backend {
    /** Namespace for utilities shared by all backend contexts. */
    companion object

    /** Kind of Kotlin backend represented by this context. */
    val type: BackendType

    /** Compilation target processed by this backend invocation. */
    val compileTarget: CompileTarget

    /** Compiler configuration for the active Kotlin compilation. */
    val config: CompilerConfiguration

    /**
     * Creates named KCML loggers that report through the current compiler's diagnostic collector.
     */
    val loggerFactory: LoggerFactory

    /** Logger for the KCML component dispatching this backend extension. */
    val logger: Logger

    /**
     * Provides the plugins and extension registries discovered for this compiler invocation.
     * Registries have already been populated before IR extensions are dispatched.
     */
    val loader: PluginLoader
}