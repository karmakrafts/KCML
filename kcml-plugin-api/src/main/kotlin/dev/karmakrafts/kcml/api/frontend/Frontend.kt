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

import dev.karmakrafts.kcml.api.InternalKcmlApi
import dev.karmakrafts.kcml.api.extension.ExtensionRegistry
import dev.karmakrafts.kcml.api.ipm.IPM
import dev.karmakrafts.kcml.api.log.Logger
import dev.karmakrafts.kcml.api.log.LoggerFactory
import dev.karmakrafts.kcml.api.plugin.CompilerPlugin
import dev.karmakrafts.kcml.api.plugin.PluginLoader
import dev.karmakrafts.kcml.api.plugin.PluginMetadata
import dev.karmakrafts.kcml.api.plugin.PluginStorage

/**
 * Provides the shared plugin services available to KCML frontend extensions.
 *
 * [FirFrontend] provides an active FIR session, while [SyntaxFrontend] supports syntax extensions
 * before FIR and can also be supplied by an IDE adapter.
 */
public sealed interface Frontend {
    /** ID of the plugin to which this frontend context belongs. */
    public val pluginId: String

    /**
     * Creates named KCML loggers that report through the current compiler's diagnostic collector.
     */
    public val loggerFactory: LoggerFactory

    /** Default logger for this frontend's owning plugin. */
    public val logger: Logger
        get() = loggerFactory.getForPlugin(pluginId)

    /**
     * Provides the plugins and extension registries discovered for this compiler invocation.
     * Registries have already been populated before frontend extensions are dispatched.
     */
    public val loader: PluginLoader
}

/** Storage for [Frontend.pluginId], scoped to the current compiler invocation. */
public inline val Frontend.storage: PluginStorage
    get() = loader.getPluginStorage(pluginId)

/** Extension registry containing the extensions contributed by [Frontend.pluginId]. */
public inline val Frontend.extensionRegistry: ExtensionRegistry
    get() = loader.getExtensionRegistry(pluginId)

/** Metadata describing the plugin identified by [Frontend.pluginId]. */
public inline val Frontend.metadata: PluginMetadata
    get() = loader.getMetadata(pluginId)

/** Loaded compiler plugin identified by [Frontend.pluginId]. */
public inline val Frontend.plugin: CompilerPlugin
    get() = loader.getPlugin(pluginId)

/** Inter-plugin messaging service associated with [Frontend.pluginId]. */
@OptIn(InternalKcmlApi::class)
public inline val Frontend.ipm: IPM
    get() = loader.getIpm(pluginId)