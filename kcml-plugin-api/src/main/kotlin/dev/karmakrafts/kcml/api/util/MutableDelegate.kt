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

package dev.karmakrafts.kcml.api.util

import kotlin.reflect.KProperty

/**
 * Mutable property delegate backed by getter and setter callbacks.
 *
 * Each property read invokes [getter] and each write invokes [setter]; no value is cached by the delegate.
 *
 * @param T non-null property value type.
 * @property getter callback used to read the property value.
 * @property setter callback used to write the property value.
 */
public data class MutableDelegate<T : Any>( // @formatter:off
    @PublishedApi internal val getter: () -> T,
    @PublishedApi internal val setter: (T) -> Unit
) { // @formatter:on
    /**
     * Reads the property value using [getter].
     *
     * @param thisRef instance containing the delegated property, or `null` for a top-level property; unused.
     * @param property metadata for the delegated property; unused.
     * @return the value returned by [getter].
     */
    @Suppress("NOTHING_TO_INLINE")
    public inline operator fun getValue(thisRef: Any?, property: KProperty<*>): T = getter()

    /**
     * Writes the property value using [setter].
     *
     * @param thisRef instance containing the delegated property, or `null` for a top-level property; unused.
     * @param property metadata for the delegated property; unused.
     * @param value new property value.
     */
    @Suppress("NOTHING_TO_INLINE")
    public inline operator fun setValue(thisRef: Any?, property: KProperty<*>, value: T) {
        setter(value)
    }
}