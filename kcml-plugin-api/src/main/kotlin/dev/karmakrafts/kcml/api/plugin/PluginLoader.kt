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

import dev.karmakrafts.kcml.api.InternalKcmlApi
import dev.karmakrafts.kcml.api.extension.ExtensionRegistry
import dev.karmakrafts.kcml.api.ipm.IPM
import dev.karmakrafts.kcml.api.log.Logger

/**
 * Discovers, validates, and loads compiler plugins for one Kotlin compiler invocation.
 *
 * The loader exposes plugin instances and metadata together with each plugin's extension registry
 * after dependency ordering and plugin initialization have completed.
 */
interface PluginLoader {
    /** Logger for plugin discovery and loading. */
    val logger: Logger

    /** Stable identifier of the plugin currently loading, or `null` outside plugin initialization. */
    val loadingPluginId: String?

    /**
     * Finds a loaded plugin.
     *
     * @param id plugin ID.
     * @return the plugin, or `null` if unavailable.
     */
    fun findPlugin(id: String): CompilerPlugin?

    /**
     * Returns a loaded plugin.
     *
     * @param id plugin ID.
     * @return the plugin.
     * @throws IllegalArgumentException if no plugin has [id].
     */
    fun getPlugin(id: String): CompilerPlugin

    /**
     * Finds plugin metadata.
     *
     * @param pluginId plugin ID.
     * @return metadata, or `null` if unavailable.
     */
    fun findMetadata(pluginId: String): PluginMetadata?

    /**
     * Returns plugin metadata.
     *
     * @param pluginId plugin ID.
     * @return metadata.
     * @throws IllegalArgumentException if no metadata has [pluginId].
     */
    fun getMetadata(pluginId: String): PluginMetadata

    /**
     * Finds a plugin's extension registry.
     *
     * @param pluginId plugin ID.
     * @return the registry, or `null` if unavailable.
     */
    fun findExtensionRegistry(pluginId: String): ExtensionRegistry?

    /**
     * Returns a plugin's extension registry.
     *
     * @param pluginId plugin ID.
     * @return the registry.
     * @throws IllegalArgumentException if no registry exists for [pluginId].
     */
    fun getExtensionRegistry(pluginId: String): ExtensionRegistry

    /**
     * Finds a plugin's message service.
     *
     * @param pluginId plugin ID.
     * @return the message service, or `null` if unavailable.
     */
    @InternalKcmlApi
    fun findIpm(pluginId: String): IPM?

    /**
     * Returns a plugin's message service.
     *
     * @param pluginId plugin ID.
     * @return the message service.
     * @throws IllegalArgumentException if no message service exists for [pluginId].
     */
    @InternalKcmlApi
    fun getIpm(pluginId: String): IPM

    /**
     * Lists all discovered plugin IDs in loader insertion order.
     *
     * @return plugin IDs in loader order.
     */
    fun allPlugins(): List<String>

    /**
     * Lists all discovered plugin IDs in resolved dependency order.
     *
     * @return plugin IDs in dependency order.
     */
    fun allPluginsSorted(): List<String>
}