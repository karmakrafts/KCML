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
     * Registers an extension for later compiler-phase dispatch.
     *
     * @param extension the KCML extension to register.
     */
    fun register(extension: Extension)

    /**
     * Removes a previously registered extension.
     *
     * @param extension the extension to remove.
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
     * @throws NoSuchElementException if no extension has [id].
     */
    operator fun get(id: String): Extension

    /** Returns all registered extensions in registration order. */
    fun all(): List<Extension>

    /** Returns all registered extensions ordered according to their dependency constraints. */
    fun allSorted(): List<Extension>
}