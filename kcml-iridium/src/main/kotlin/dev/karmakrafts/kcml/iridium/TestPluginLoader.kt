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

package dev.karmakrafts.kcml.iridium

import dev.karmakrafts.kcml.api.InternalKcmlApi
import dev.karmakrafts.kcml.api.extension.ExtensionRegistry
import dev.karmakrafts.kcml.api.ipm.IPM
import dev.karmakrafts.kcml.api.log.Logger
import dev.karmakrafts.kcml.api.plugin.CompilerPlugin
import dev.karmakrafts.kcml.api.plugin.PluginLoader
import dev.karmakrafts.kcml.api.plugin.PluginMetadata

/**
 * Provides an empty plugin-loader implementation for Iridium test contexts.
 *
 * No plugins are loaded: optional lookups return `null`, plugin listings are empty, and required
 * lookups throw [NotImplementedError].
 *
 * @property logger logger exposed by this test loader.
 */
public class TestPluginLoader( // @formatter:off
    override val logger: Logger
) : PluginLoader { // @formatter:on
    /** Always `null` because this loader never loads a plugin. */
    override val loadingPluginId: String? = null

    /**
     * Reports that no plugin is available.
     *
     * @param id plugin ID to look up.
     * @return always `null`.
     */
    override fun findPlugin(id: String): CompilerPlugin? = null

    /**
     * Rejects a required plugin lookup, which this test loader does not support.
     *
     * @param id plugin ID to look up.
     * @throws NotImplementedError always, because plugins are not loaded.
     */
    override fun getPlugin(id: String): CompilerPlugin = throw NotImplementedError()

    /**
     * Reports that no plugin metadata is available.
     *
     * @param pluginId plugin ID whose metadata is requested.
     * @return always `null`.
     */
    override fun findMetadata(pluginId: String): PluginMetadata? = null

    /**
     * Rejects a required metadata lookup, which this test loader does not support.
     *
     * @param pluginId plugin ID whose metadata is requested.
     * @throws NotImplementedError always, because plugin metadata is not loaded.
     */
    override fun getMetadata(pluginId: String): PluginMetadata = throw NotImplementedError()

    /**
     * Reports that no extension registry is available.
     *
     * @param pluginId plugin ID whose extension registry is requested.
     * @return always `null`.
     */
    override fun findExtensionRegistry(pluginId: String): ExtensionRegistry? = null

    /**
     * Rejects a required extension-registry lookup, which this test loader does not support.
     *
     * @param pluginId plugin ID whose extension registry is requested.
     * @throws NotImplementedError always, because extension registries are not loaded.
     */
    override fun getExtensionRegistry(pluginId: String): ExtensionRegistry = throw NotImplementedError()

    /**
     * Reports that no inter-plugin messaging instance is available.
     *
     * @param pluginId plugin ID whose messaging instance is requested.
     * @return always `null`.
     */
    @InternalKcmlApi
    override fun findIpm(pluginId: String): IPM? = null

    /**
     * Rejects a required messaging-instance lookup, which this test loader does not support.
     *
     * @param pluginId plugin ID whose messaging instance is requested.
     * @throws NotImplementedError always, because messaging instances are not created.
     */
    @InternalKcmlApi
    override fun getIpm(pluginId: String): IPM = throw NotImplementedError()

    /**
     * Lists the plugins available from this test loader.
     *
     * @return always an empty list.
     */
    override fun allPlugins(): List<String> = emptyList()

    /**
     * Lists the plugins in dependency order.
     *
     * @return always an empty list because this test loader has no plugins.
     */
    override fun allPluginsSorted(): List<String> = emptyList()
}