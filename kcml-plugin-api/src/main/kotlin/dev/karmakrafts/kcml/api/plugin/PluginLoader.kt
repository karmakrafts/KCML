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

/**
 * Discovers and provides KCML compiler plugins and their metadata.
 *
 * KCML discovers plugins before Kotlin's FIR and IR extension points run, orders them by declared
 * dependencies, and calls each plugin's [CompilerPlugin.load] function. The loader then exposes
 * the resulting plugin instances, metadata, and per-plugin extension registries to KCML code.
 */
interface PluginLoader {
    val logger: Logger

    /**
     * The ID of the plugin currently being loaded.
     * It is `null` before loading starts and after loading completes.
     */
    val loadingPluginId: String?

    /**
     * Finds a loaded compiler plugin.
     *
     * @param id stable plugin identifier.
     * @return the loaded plugin, or `null` when it is unavailable.
     */
    fun findPlugin(id: String): CompilerPlugin?

    /**
     * Gets a loaded compiler plugin.
     *
     * @param id stable plugin identifier.
     * @return the loaded plugin.
     * @throws IllegalArgumentException if no plugin has [id].
     */
    fun getPlugin(id: String): CompilerPlugin

    /**
     * Finds metadata for a discoverable compiler plugin.
     *
     * @param pluginId stable plugin identifier.
     * @return plugin metadata, or `null` when it is unavailable.
     */
    fun findMetadata(pluginId: String): PluginMetadata?

    /**
     * Gets metadata for a discoverable compiler plugin.
     *
     * @param pluginId stable plugin identifier.
     * @return plugin metadata.
     * @throws IllegalArgumentException if no metadata has [pluginId].
     */
    fun getMetadata(pluginId: String): PluginMetadata

    /**
     * Finds the extension registry created while a plugin was loaded.
     *
     * @param pluginId stable plugin identifier.
     * @return the plugin's registry, or `null` when that plugin has not been loaded or has no
     *   registry.
     */
    fun findExtensionRegistry(pluginId: String): ExtensionRegistry?

    /**
     * Gets the extension registry created while a plugin was loaded.
     *
     * @param pluginId stable plugin identifier.
     * @return the plugin's extension registry.
     * @throws IllegalArgumentException if no registry exists for [pluginId].
     */
    fun getExtensionRegistry(pluginId: String): ExtensionRegistry

    /** @return identifiers of all discoverable plugins in loader order. */
    fun allPlugins(): List<String>

    /** @return identifiers of all discoverable plugins ordered by dependency constraints. */
    fun allPluginsSorted(): List<String>
}