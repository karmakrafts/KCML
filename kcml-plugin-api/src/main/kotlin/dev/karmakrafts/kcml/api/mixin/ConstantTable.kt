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

package dev.karmakrafts.kcml.api.mixin

object ConstantTable {
    @JvmStatic
    fun getString(name: String): String = throw NotImplementedError("This should not be called directly")

    @JvmStatic
    fun getByte(name: String): Byte = throw NotImplementedError("This should not be called directly")

    @JvmStatic
    fun getShort(name: String): Short = throw NotImplementedError("This should not be called directly")

    @JvmStatic
    fun getInt(name: String): Int = throw NotImplementedError("This should not be called directly")

    @JvmStatic
    fun getLong(name: String): Long = throw NotImplementedError("This should not be called directly")

    @JvmStatic
    fun getFloat(name: String): Float = throw NotImplementedError("This should not be called directly")

    @JvmStatic
    fun getDouble(name: String): Double = throw NotImplementedError("This should not be called directly")

    @JvmStatic
    fun getBoolean(name: String): Boolean = throw NotImplementedError("This should not be called directly")
}