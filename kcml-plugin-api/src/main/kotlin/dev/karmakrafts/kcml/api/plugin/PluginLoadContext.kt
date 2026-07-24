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

package dev.karmakrafts.kcml.api.plugin

import dev.karmakrafts.kcml.api.extension.ExtensionRegistry
import dev.karmakrafts.kcml.api.log.Logger
import dev.karmakrafts.kcml.api.log.LoggerFactory
import org.jetbrains.kotlin.config.CompilerConfiguration

/**
 * Services supplied while KCML loads one [CompilerPlugin] for a Kotlin compiler invocation.
 *
 * The context combines the plugin-specific extension registry with the compiler configuration and
 * KCML services shared by extensions. It is supplied only to [CompilerPlugin.load]; extensions
 * receive the corresponding frontend or backend context when Kotlin reaches their compiler phase.
 *
 * @property extensionRegistry registry owned by the plugin being loaded; register its extensions
 *   here so KCML can dispatch them to Kotlin compiler phases.
 * @property config configuration of the active Kotlin compiler invocation.
 * @property loggerFactory factory for creating KCML loggers that report through the compiler's
 *   diagnostic infrastructure.
 * @property logger logger named for the plugin being loaded.
 * @property loader loader that exposes the other discovered KCML plugins, metadata, and available
 *   extension registries for this invocation.
 */
data class PluginLoadContext( // @formatter:off
    val extensionRegistry: ExtensionRegistry,
    val config: CompilerConfiguration,
    val loggerFactory: LoggerFactory,
    val logger: Logger,
    val loader: PluginLoader
) // @formatter:on