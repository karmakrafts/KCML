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

package dev.karmakrafts.kcml.hacks

import sun.misc.Unsafe

/**
 * Provides access to the JVM's internal [Unsafe] instance.
 *
 * Operations performed through [unsafe] can bypass normal JVM safety guarantees and should be used with care.
 */
@DiscouragedKcmlApi
public object UnsafeUtils {
    /**
     * The lazily initialized JVM [Unsafe] instance, obtained through reflective access to `theUnsafe`.
     *
     * Access may be denied by JVM security or module restrictions. Initialization failures are reported as
     * [IllegalStateException].
     */
    @Suppress("DiscouragedPrivateApi")
    public val unsafe: Unsafe by lazy {
        try {
            val clazz = Unsafe::class.java
            val field = clazz.getDeclaredField("theUnsafe")
            field.isAccessible = true
            val instance = field.get(null)
            field.isAccessible = false
            instance as Unsafe
        } catch (error: Throwable) {
            error(error.message ?: "Unknown error")
        }
    }
}