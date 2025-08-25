/*
 * Copyright 2025 Karma Krafts & associates
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

import dev.karmakrafts.kcml.plugin.PluginMetadata.Companion.defaultVersion
import io.github.z4kn4fein.semver.Version
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Represents metadata for a KCML compiler plugin.
 */
@Serializable
data class PluginMetadata(
    /**
     * The unique identifier of the plugin.
     * This ID is used to reference the plugin in dependency declarations.
     */
    val id: String,

    /**
     * The human-readable name of the plugin.
     * Can be null, in which case [id] is used as the display name.
     */
    val name: String?,

    /**
     * The semantic version of the plugin.
     * Can be null, in which case [defaultVersion] is used.
     */
    val version: Version?,

    /**
     * URL to the plugin's issue tracker.
     * This can be used to report bugs or request features for the plugin.
     */
    @SerialName("issue_tracker_url") val issueTrackerUrl: String?,

    /**
     * List of plugin dependencies.
     * These are other plugins that this plugin depends on.
     * @see PluginDependency
     */
    val dependencies: List<PluginDependency>
) {
    companion object {
        /**
         * The default version used when a plugin doesn't specify a version.
         * This is an empty version (0.0.0).
         */
        @PublishedApi
        internal val defaultVersion: Version = Version()
    }

    /**
     * Returns the name of the plugin if available, otherwise returns the plugin ID.
     * This property ensures there's always a human-readable identifier for the plugin.
     */
    inline val nameOrId: String get() = name ?: id

    /**
     * Returns the version of the plugin if available, otherwise returns the default version.
     * This property ensures there's always a valid version for the plugin.
     */
    inline val versionOrDefault: Version get() = version ?: defaultVersion
}