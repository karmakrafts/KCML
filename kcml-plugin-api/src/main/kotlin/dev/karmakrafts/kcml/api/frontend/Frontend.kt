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

package dev.karmakrafts.kcml.api.frontend

import dev.karmakrafts.kcml.api.log.Logger
import dev.karmakrafts.kcml.api.log.LoggerFactory
import dev.karmakrafts.kcml.api.plugin.PluginLoader
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.config.messageCollector
import org.jetbrains.kotlin.fir.FirSession

/**
 * Provides the compiler services available while KCML integrates with Kotlin's FIR frontend.
 *
 * FIR extensions receive this context to inspect the session and report diagnostics while the
 * compiler resolves and generates frontend declarations.
 */
interface Frontend {
    /** The ID of the plugin this frontend belongs to **/
    val pluginId: String

    /** FIR session for the current compilation, including symbol providers and type services. */
    val session: FirSession

    /** Compiler configuration for the active Kotlin compilation. */
    val config: CompilerConfiguration

    /**
     * Creates named KCML loggers that report through the current compiler's diagnostic collector.
     */
    val loggerFactory: LoggerFactory

    /** Logger for the KCML component dispatching this frontend extension. */
    val logger: Logger

    /**
     * Provides the plugins and extension registries discovered for this compiler invocation.
     * Registries have already been populated before FIR extensions are dispatched.
     */
    val loader: PluginLoader

    /** Collects compiler diagnostics emitted by KCML extensions. */
    val messageCollector: MessageCollector
        get() = config.messageCollector
}