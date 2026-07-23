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

import dev.karmakrafts.kcml.api.plugin.PluginMetadata
import io.github.z4kn4fein.semver.Version
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal data class SerializablePluginMetadata(
    override val id: String,
    override val name: String?,
    override val version: Version?,
    @SerialName("issue_tracker_url") override val issueTrackerUrl: String?,
    override val dependencies: List<SerializablePluginDependency>
) : PluginMetadata {
    companion object {
        @PublishedApi
        internal val defaultVersion: Version = Version()
    }

    inline val nameOrId: String get() = name ?: id

    inline val versionOrDefault: Version get() = version ?: defaultVersion
}