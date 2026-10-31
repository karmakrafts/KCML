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

package dev.karmakrafts.kcml.api.extension

/**
 * Stores the extensions contributed by one plugin for a compiler invocation.
 *
 * Registries accept changes while their plugin is loading. KCML freezes each registry before
 * dispatching compiler callbacks, at which point dependency-sorted access becomes available.
 */
public interface ExtensionRegistry {
    /** Stable identifier of the plugin that owns this registry. */
    public val pluginId: String

    /**
     * Registers an extension under its stable [Extension.id].
     *
     * @param extension extension to register.
     * @throws IllegalStateException if this registry is frozen.
     * @throws IllegalArgumentException if an extension with the same ID is already registered.
     */
    public fun register(extension: Extension)

    /**
     * Unregisters a previously registered extension.
     *
     * @param extension extension to unregister.
     * @throws IllegalStateException if this registry is frozen.
     */
    public fun unregister(extension: Extension)

    /**
     * Finds a registered extension by its stable ID.
     *
     * @param id extension ID.
     * @return the extension, or `null` if absent.
     */
    public fun find(id: String): Extension?

    /**
     * Returns a registered extension by its stable ID.
     *
     * @param id extension ID.
     * @return the extension.
     * @throws IllegalArgumentException if no extension has [id].
     */
    public operator fun get(id: String): Extension

    /**
     * Checks whether an extension ID is registered.
     *
     * @param id extension ID.
     * @return `true` if [id] is registered.
     */
    public operator fun contains(id: String): Boolean

    /**
     * Checks whether an extension is registered.
     *
     * @param extension extension to check.
     * @return `true` if [extension] is registered.
     */
    public operator fun contains(extension: Extension): Boolean

    /**
     * Returns all extensions in registration order.
     *
     * @return registered extensions.
     */
    public fun all(): List<Extension>

    /**
     * Returns all extensions in resolved dependency order.
     *
     * @return registered extensions in dependency order.
     * @throws IllegalStateException if this registry is not frozen.
     */
    public fun allSorted(): List<Extension>
}