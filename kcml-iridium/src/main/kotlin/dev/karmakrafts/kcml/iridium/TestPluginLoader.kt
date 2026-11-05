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
import dev.karmakrafts.kcml.api.plugin.PluginStorage

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
    override val loadingPluginId: String? = null

    override fun findPlugin(id: String): CompilerPlugin? = null
    override fun getPlugin(id: String): CompilerPlugin = throw NotImplementedError()
    override fun findMetadata(pluginId: String): PluginMetadata? = null
    override fun getMetadata(pluginId: String): PluginMetadata = throw NotImplementedError()
    override fun findExtensionRegistry(pluginId: String): ExtensionRegistry? = null
    override fun getExtensionRegistry(pluginId: String): ExtensionRegistry = throw NotImplementedError()
    override fun findPluginStorage(pluginId: String): PluginStorage? = null
    override fun getPluginStorage(pluginId: String): PluginStorage = throw NotImplementedError()

    @InternalKcmlApi
    override fun findIpm(pluginId: String): IPM? = null

    @InternalKcmlApi
    override fun getIpm(pluginId: String): IPM = throw NotImplementedError()

    override fun allPlugins(): List<String> = emptyList()
    override fun allPluginsSorted(): List<String> = emptyList()
}