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

@ConsistentCopyVisibility
data class IPMData @PublishedApi internal constructor(private val map: Map<String, Any>) {
    companion object {
        fun fromMap(map: Map<String, Any>): IPMData = IPMData(map)

        inline fun build(block: MutableMap<String, Any>.() -> Unit): IPMData = IPMData(buildMap(block))
    }

    @Suppress("UNCHECKED_CAST")
    operator fun <T> get(key: String): T = map[key] as T
}