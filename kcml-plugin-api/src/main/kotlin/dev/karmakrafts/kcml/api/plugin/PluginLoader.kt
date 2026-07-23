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

interface PluginLoader {
    /**
     * The ID of the plugin currently being loaded.
     * After the load has completed, this value will always be null.
     */
    val loadingPluginId: String?

    fun findPlugin(id: String): CompilerPlugin?

    fun getPlugin(id: String): CompilerPlugin

    fun findMetadata(id: String): PluginMetadata?

    fun getMetadata(id: String): PluginMetadata

    fun allPlugins(): List<String>

    fun allPluginsSorted(): List<String>
}