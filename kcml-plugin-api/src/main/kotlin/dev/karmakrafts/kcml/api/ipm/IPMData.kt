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

package dev.karmakrafts.kcml.api.ipm

/** Key-value payload carried by an inter-plugin message. */
@ConsistentCopyVisibility
data class IPMData @PublishedApi internal constructor(private val map: Map<String, Any>) {
    companion object {
        /**
         * Creates message data from a map.
         *
         * @param map payload values keyed by name.
         * @return message data backed by [map].
         */
        fun fromMap(map: Map<String, Any>): IPMData = IPMData(map)

        /**
         * Creates message data with a map builder.
         *
         * @param block adds payload values keyed by name.
         * @return message data containing the values added by [block].
         */
        inline fun build(block: MutableMap<String, Any>.() -> Unit): IPMData = IPMData(buildMap(block))
    }

    /**
     * Returns a payload value as the requested type.
     *
     * @param T expected value type.
     * @param key payload key.
     * @return the value for [key] cast to [T].
     */
    @Suppress("UNCHECKED_CAST")
    operator fun <T> get(key: String): T = map[key] as T
}