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

import dev.karmakrafts.kcml.api.util.MutableDelegate
import kotlin.reflect.KClass

/**
 * Storage for a plugin's non-null values, accessed through typed keys.
 *
 * KCML creates one storage instance per plugin and compiler invocation, sharing it between
 * [PreInitializationContext.storage] and [InitializationContext.storage]. Values are kept in memory
 * for that invocation, not persisted between compilations. Other plugins can access existing
 * storage through [PluginLoader.findPluginStorage] or [PluginLoader.getPluginStorage].
 */
public interface PluginStorage {
    /**
     * Typed key describing a stored value and whether it can be reassigned.
     *
     * @param T non-null value type.
     */
    public interface Key<T : Any> {
        /** Factory functions for storage keys. */
        public companion object {
            @PublishedApi
            internal data class KeyImpl<T : Any>( // @formatter:off
                override val type: KClass<T>,
                override val name: String,
                override val isFinal: Boolean
            ) : Key<T> // @formatter:on

            /**
             * Creates a key for values of type [T].
             * Keys always use value equality.
             *
             * @param T non-null value type.
             * @param name name used in diagnostics and type consistency checks.
             * @param isFinal whether the value may only be assigned once; defaults to `false`.
             * @return a new storage key.
             */
            public inline fun <reified T : Any> create( // @formatter:off
                name: String,
                isFinal: Boolean = false
            ): Key<T> = KeyImpl(T::class, name, isFinal) // @formatter:on
        }

        /** Runtime type expected for values stored under this key. */
        public val type: KClass<T>

        /** Name used in diagnostics; keys with the same name must use the same [type]. */
        public val name: String

        /** Whether the value may only be assigned once, rather than replaced by later writes. */
        public val isFinal: Boolean
    }

    /** ID of the plugin that owns this storage. */
    public val ownerPluginId: String

    /**
     * Returns the value stored under [key].
     *
     * @param T non-null value type.
     * @param key key identifying the stored value.
     * @return the stored value.
     * @throws IllegalArgumentException if no value is stored under [key] or its runtime type does not match [Key.type].
     */
    public operator fun <T : Any> get(key: Key<T>): T

    /**
     * Stores [value] under [key], replacing any previous value unless the key is final.
     *
     * @param T non-null value type.
     * @param key key identifying the stored value.
     * @param value value to store.
     * @throws IllegalArgumentException if [key] is final and already has a value, or a stored key with the same name
     * has a different type.
     */
    public operator fun <T : Any> set(key: Key<T>, value: T)
}

/**
 * Creates a mutable property delegate that reads and writes this storage using [key].
 *
 * Creating the delegate does not access the storage. Property reads and writes use [PluginStorage.get] and
 * [PluginStorage.set], including their type checks, final-value restrictions, and exceptions.
 *
 * @param T non-null property value type.
 * @param key key identifying the delegated value.
 * @return a delegate backed by this storage.
 */
@Suppress("NOTHING_TO_INLINE")
public inline fun <T : Any> PluginStorage.delegateFor(key: PluginStorage.Key<T>): MutableDelegate<T> {
    return MutableDelegate({ this[key] }) { this[key] = it }
}