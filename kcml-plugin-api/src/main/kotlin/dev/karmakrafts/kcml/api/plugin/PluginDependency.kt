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

import dev.karmakrafts.kcml.api.util.Order
import io.github.z4kn4fein.semver.constraints.Constraint

/**
 * Declares a dependency required to load a KCML compiler plugin.
 *
 * Plugin loaders use these constraints to validate availability, version compatibility, and load
 * order before a plugin can register its extensions.
 */
public interface PluginDependency {
    /** Identifier of the plugin on which the declaring plugin depends. */
    public val id: String

    /** Whether the declaring plugin must be rejected when this dependency is unavailable. */
    public val required: Boolean

    /** Load order of the declaring plugin relative to this dependency. */
    public val order: Order

    /** Optional version requirement used to validate the resolved dependency. */
    public val version: Constraint?
}