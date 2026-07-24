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

/**
 * Discovers and provides KCML compiler plugins and their metadata.
 *
 * Implementations coordinate plugin loading with dependency resolution before the loaded plugins
 * register compiler extensions.
 */
interface PluginLoader {
    /**
     * The ID of the plugin currently being loaded.
     * After the load has completed, this value will always be null.
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
     * @throws NoSuchElementException if no plugin has [id].
     */
    fun getPlugin(id: String): CompilerPlugin

    /**
     * Finds metadata for a discoverable compiler plugin.
     *
     * @param id stable plugin identifier.
     * @return plugin metadata, or `null` when it is unavailable.
     */
    fun findMetadata(id: String): PluginMetadata?

    /**
     * Gets metadata for a discoverable compiler plugin.
     *
     * @param id stable plugin identifier.
     * @return plugin metadata.
     * @throws NoSuchElementException if no metadata has [id].
     */
    fun getMetadata(id: String): PluginMetadata

    /** @return identifiers of all discoverable plugins in loader order. */
    fun allPlugins(): List<String>

    /** @return identifiers of all discoverable plugins ordered by dependency constraints. */
    fun allPluginsSorted(): List<String>
}