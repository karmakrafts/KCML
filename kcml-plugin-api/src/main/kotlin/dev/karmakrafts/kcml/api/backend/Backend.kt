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

import dev.karmakrafts.kcml.api.InternalKcmlApi
import dev.karmakrafts.kcml.api.extension.ExtensionRegistry
import dev.karmakrafts.kcml.api.ipm.IPM
import dev.karmakrafts.kcml.api.log.Logger
import dev.karmakrafts.kcml.api.log.LoggerFactory
import dev.karmakrafts.kcml.api.plugin.CompilerPlugin
import dev.karmakrafts.kcml.api.plugin.PluginLoader
import dev.karmakrafts.kcml.api.plugin.PluginMetadata
import dev.karmakrafts.kcml.api.plugin.PluginStorage
import dev.karmakrafts.kcml.api.target.CompileTarget
import org.jetbrains.kotlin.config.CompilerConfiguration

/**
 * Provides common compiler services to KCML extensions running during Kotlin backend processing.
 *
 * Backend contexts identify the plugin, backend phase, and compilation target, and expose compiler
 * configuration, logging, and plugin-loading services. They may represent an IR phase or a later
 * target-specific code-generation phase. [IrBackend] adds services specific to IR processing.
 */
public interface Backend {
    /** Namespace for utilities shared by all backend contexts. */
    public companion object; // Semicolon needed because of parser bug..

    /** Stable KCML ID of the plugin whose backend extension is using this context. */
    public val pluginId: String

    /** Backend phase represented by this context, such as IR processing or later code generation. */
    public val type: BackendType

    /** Platform target of the active compilation; this is distinct from the backend phase in [type]. */
    public val compileTarget: CompileTarget

    /** Kotlin compiler configuration for the active compilation. */
    public val config: CompilerConfiguration

    /**
     * Factory for creating named KCML loggers associated with this backend context.
     *
     * Use [LoggerFactory.invoke] for a named component or [LoggerFactory.getForPlugin] for a plugin.
     */
    public val loggerFactory: LoggerFactory

    /** Logger for the KCML component dispatching the current backend extension. */
    public val logger: Logger

    /**
     * Plugin loader for the current compiler invocation, exposing discovered plugins and their
     * available extension registries.
     */
    public val loader: PluginLoader
}

/** Storage for [Backend.pluginId], scoped to the current compiler invocation. */
public inline val Backend.storage: PluginStorage
    get() = loader.getPluginStorage(pluginId)

/** Extension registry containing the extensions contributed by [Backend.pluginId]. */
public inline val Backend.extensionRegistry: ExtensionRegistry
    get() = loader.getExtensionRegistry(pluginId)

/** Metadata describing the plugin identified by [Backend.pluginId]. */
public inline val Backend.metadata: PluginMetadata
    get() = loader.getMetadata(pluginId)

/** Loaded compiler plugin identified by [Backend.pluginId]. */
public inline val Backend.plugin: CompilerPlugin
    get() = loader.getPlugin(pluginId)

/** Inter-plugin messaging service associated with [Backend.pluginId]. */
@OptIn(InternalKcmlApi::class)
public inline val Backend.ipm: IPM
    get() = loader.getIpm(pluginId)
