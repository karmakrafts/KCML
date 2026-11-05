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

package dev.karmakrafts.kcml.plugin

import dev.karmakrafts.kcml.api.plugin.PluginStorage
import dev.karmakrafts.kcml.api.plugin.PluginStorage.Key
import java.util.concurrent.ConcurrentHashMap

internal data class PluginStorageImpl( // @formatter:off
    override val ownerPluginId: String,
    val values: ConcurrentHashMap<Key<*>, Any> = ConcurrentHashMap()
) : PluginStorage { // @formatter:on
    @Suppress("UNCHECKED_CAST")
    override operator fun <T : Any> get(key: Key<T>): T {
        val value = requireNotNull(values[key]) {
            "No plugin storage value with key '${key.name}' for plugin '$ownerPluginId'"
        }
        require(key.type.isInstance(value)) {
            "Plugin storage value with key '${key.name}' for plugin '$ownerPluginId' is not of type ${key.type}"
        }
        return value as T // This cast is safe at runtime
    }

    override operator fun <T : Any> set(key: Key<T>, value: T) {
        if (key.isFinal) require(!values.containsKey(key)) {
            "Plugin storage value with key '${key.name}' for plugin '$ownerPluginId' is final and cannot be set again"
        }
        values.filter { [currentKey, _] -> key.name == currentKey.name }
            .firstNotNullOfOrNull { [currentKey, _] -> currentKey }
            ?.let { currentKey ->
                require(currentKey.type === key.type) {
                    "Plugin storage value with key '${key.name}' for plugin '$ownerPluginId' expected type ${currentKey.type} but got ${key.type}"
                }
            }
        values[key] = value
    }
}