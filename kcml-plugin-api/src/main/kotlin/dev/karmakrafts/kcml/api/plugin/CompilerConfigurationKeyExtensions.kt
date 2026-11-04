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

import org.jetbrains.kotlin.config.CompilerConfigurationKey

/**
 * Resolve a compiler option for the given KCML plugin by its ID and key.
 * Works the same way as [CompilerConfigurationKey.Companion.create], except
 * that it automatically handles formatting of the key.
 *
 * @param pluginId The ID of the associated compiler plugin. (The value in the `Plugin` annotation)
 * @param key The key of the value to query.
 * @return The value associated with the given key.
 */
@Suppress("NOTHING_TO_INLINE")
public inline fun <T : Any> CompilerConfigurationKey.Companion.createForPlugin(
    pluginId: String, key: String
): CompilerConfigurationKey<T> {
    return CompilerConfigurationKey.create("$pluginId${key.replaceFirstChar { it.uppercase() }}")
}