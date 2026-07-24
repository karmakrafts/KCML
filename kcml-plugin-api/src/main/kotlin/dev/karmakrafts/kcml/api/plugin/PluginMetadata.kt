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

import io.github.z4kn4fein.semver.Version

@PublishedApi
internal val defaultPluginVersion: Version = Version()

/**
 * Describes a KCML compiler plugin discovered by a [PluginLoader].
 *
 * The loader uses this metadata to expose plugin identity, display information, compatibility, and
 * dependency ordering without requiring consumers to instantiate the plugin.
 */
interface PluginMetadata {
    /** Stable plugin identifier used by the loader and dependency declarations. */
    val id: String

    /** Optional human-readable plugin name. */
    val name: String?

    /** Optional semantic version of this plugin distribution. */
    val version: Version?

    /** Optional URL where users can report problems with this plugin. */
    val issueTrackerUrl: String?

    /** Plugins that must be available before this plugin is loaded. */
    val dependencies: List<PluginDependency>
}

inline val PluginMetadata.nameOrId: String get() = name ?: id

inline val PluginMetadata.versionOrDefault: Version get() = version ?: defaultPluginVersion