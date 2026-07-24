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
 * Stores the KCML extensions participating in a compiler invocation.
 *
 * The registry is responsible for extension identity and exposes both registration order and the
 * dependency-respecting order used by the compiler-plugin dispatcher.
 */
interface ExtensionRegistry {
    /**
     * Stable identifier of the KCML plugin that owns this registry.
     *
     * Each loaded plugin receives a separate registry, allowing extensions with the same ID to be
     * managed independently until KCML dispatches them to the Kotlin compiler.
     */
    val pluginId: String

    /**
     * Registers an extension for later compiler-phase dispatch.
     *
     * @param extension the KCML extension to register.
     * @throws IllegalStateException if KCML has frozen this registry for compiler-phase dispatch.
     * @throws IllegalArgumentException if an extension with the same ID is already registered.
     */
    fun register(extension: Extension)

    /**
     * Removes a previously registered extension.
     *
     * @param extension the extension to remove.
     * @throws IllegalStateException if KCML has frozen this registry for compiler-phase dispatch.
     */
    fun unregister(extension: Extension)

    /**
     * Finds the registered extension with an identifier.
     *
     * @param id the extension identifier.
     * @return the registered extension, or `null` when it is absent.
     */
    fun find(id: String): Extension?

    /**
     * Gets the registered extension with an identifier.
     *
     * @param id the extension identifier.
     * @return the registered extension.
     * @throws IllegalArgumentException if no extension has [id].
     */
    operator fun get(id: String): Extension

    /**
     * Checks whether this registry contains an extension ID.
     *
     * @param id extension identifier to look up.
     * @return `true` if an extension with [id] is registered.
     */
    operator fun contains(id: String): Boolean

    /**
     * Checks whether this registry contains an extension instance.
     *
     * @param extension extension instance to look up.
     * @return `true` if [extension] is registered.
     */
    operator fun contains(extension: Extension): Boolean

    /** Returns all registered extensions in registration order. */
    fun all(): List<Extension>

    /**
     * Returns all registered extensions ordered according to their dependency constraints.
     *
     * @return the extensions in the order KCML uses for Kotlin compiler-phase dispatch.
     * @throws IllegalStateException if KCML has not yet frozen this registry after plugin loading.
     */
    fun allSorted(): List<Extension>
}