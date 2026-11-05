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
 * Plugin instances and available metadata can be queried once discovery completes. Storage becomes
 * available as each plugin is pre-initialized; extension registries and message services become
 * available as each plugin is initialized. Lookups do not create missing services.
 */
public interface PluginLoader {
    /** Logger for plugin discovery and loading. */
    public val logger: Logger

    /** Stable identifier of the plugin currently in [CompilerPlugin.preInit] or [CompilerPlugin.init], or `null` otherwise. */
    public val loadingPluginId: String?

    /**
     * Finds a loaded plugin.
     *
     * @param id plugin ID.
     * @return the plugin, or `null` if unavailable.
     */
    public fun findPlugin(id: String): CompilerPlugin?

    /**
     * Returns a loaded plugin.
     *
     * @param id plugin ID.
     * @return the plugin.
     * @throws IllegalArgumentException if no plugin has [id].
     */
    public fun getPlugin(id: String): CompilerPlugin

    /**
     * Finds plugin metadata.
     *
     * @param pluginId plugin ID.
     * @return metadata, or `null` if unavailable.
     */
    public fun findMetadata(pluginId: String): PluginMetadata?

    /**
     * Returns plugin metadata.
     *
     * @param pluginId plugin ID.
     * @return metadata.
     * @throws IllegalArgumentException if no metadata has [pluginId].
     */
    public fun getMetadata(pluginId: String): PluginMetadata

    /**
     * Finds a plugin's extension registry.
     *
     * @param pluginId plugin ID.
     * @return the registry, or `null` if unavailable.
     */
    public fun findExtensionRegistry(pluginId: String): ExtensionRegistry?

    /**
     * Returns a plugin's extension registry.
     *
     * @param pluginId plugin ID.
     * @return the registry.
     * @throws IllegalArgumentException if no registry exists for [pluginId].
     */
    public fun getExtensionRegistry(pluginId: String): ExtensionRegistry

    /**
     * Finds a plugin's invocation-scoped storage without creating it.
     *
     * The same storage is supplied to the plugin during pre-initialization and initialization.
     *
     * @param pluginId ID of the plugin that owns the storage.
     * @return the storage, or `null` if it is not yet available or the plugin is unknown.
     */
    public fun findPluginStorage(pluginId: String): PluginStorage?

    /**
     * Returns a plugin's invocation-scoped storage without creating it.
     *
     * @param pluginId ID of the plugin that owns the storage.
     * @return the storage shared by the plugin's pre-initialization and initialization contexts.
     * @throws IllegalArgumentException if no storage exists for [pluginId].
     */
    public fun getPluginStorage(pluginId: String): PluginStorage

    /**
     * Finds a plugin's message service.
     *
     * @param pluginId plugin ID.
     * @return the message service, or `null` if unavailable.
     */
    @InternalKcmlApi
    public fun findIpm(pluginId: String): IPM?

    /**
     * Returns a plugin's message service.
     *
     * @param pluginId plugin ID.
     * @return the message service.
     * @throws IllegalArgumentException if no message service exists for [pluginId].
     */
    @InternalKcmlApi
    public fun getIpm(pluginId: String): IPM

    /**
     * Lists all discovered plugin IDs in loader insertion order.
     *
     * @return plugin IDs in loader order.
     */
    public fun allPlugins(): List<String>

    /**
     * Lists all discovered plugin IDs in resolved dependency order.
     *
     * @return plugin IDs in dependency order.
     * @throws IllegalStateException if plugin loading has not completed.
     */
    public fun allPluginsSorted(): List<String>
}