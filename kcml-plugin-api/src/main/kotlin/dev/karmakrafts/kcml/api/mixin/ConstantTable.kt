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

/**
 * Provides typed placeholders for constants registered through the mixin bridge.
 *
 * The runtime mixin compiler replaces these calls with constants from the current mixin source's
 * table. Names must be literal strings, and the registered value must match the accessor's type.
 * These functions must not be invoked directly without mixin rewriting.
 */
public object ConstantTable {
    /**
     * Reads a registered string constant after mixin rewriting.
     *
     * @param name literal name of the constant in the current mixin source's table.
     * @return the registered string value.
     * @throws NotImplementedError if invoked directly without mixin rewriting.
     */
    @JvmStatic
    public fun getString(name: String): String = throw NotImplementedError("This should not be called directly")

    /**
     * Reads a registered byte constant after mixin rewriting.
     *
     * @param name literal name of the constant in the current mixin source's table.
     * @return the registered byte value.
     * @throws NotImplementedError if invoked directly without mixin rewriting.
     */
    @JvmStatic
    public fun getByte(name: String): Byte = throw NotImplementedError("This should not be called directly")

    /**
     * Reads a registered short constant after mixin rewriting.
     *
     * @param name literal name of the constant in the current mixin source's table.
     * @return the registered short value.
     * @throws NotImplementedError if invoked directly without mixin rewriting.
     */
    @JvmStatic
    public fun getShort(name: String): Short = throw NotImplementedError("This should not be called directly")

    /**
     * Reads a registered integer constant after mixin rewriting.
     *
     * @param name literal name of the constant in the current mixin source's table.
     * @return the registered integer value.
     * @throws NotImplementedError if invoked directly without mixin rewriting.
     */
    @JvmStatic
    public fun getInt(name: String): Int = throw NotImplementedError("This should not be called directly")

    /**
     * Reads a registered long constant after mixin rewriting.
     *
     * @param name literal name of the constant in the current mixin source's table.
     * @return the registered long value.
     * @throws NotImplementedError if invoked directly without mixin rewriting.
     */
    @JvmStatic
    public fun getLong(name: String): Long = throw NotImplementedError("This should not be called directly")

    /**
     * Reads a registered single-precision floating-point constant after mixin rewriting.
     *
     * @param name literal name of the constant in the current mixin source's table.
     * @return the registered float value.
     * @throws NotImplementedError if invoked directly without mixin rewriting.
     */
    @JvmStatic
    public fun getFloat(name: String): Float = throw NotImplementedError("This should not be called directly")

    /**
     * Reads a registered double-precision floating-point constant after mixin rewriting.
     *
     * @param name literal name of the constant in the current mixin source's table.
     * @return the registered double value.
     * @throws NotImplementedError if invoked directly without mixin rewriting.
     */
    @JvmStatic
    public fun getDouble(name: String): Double = throw NotImplementedError("This should not be called directly")

    /**
     * Reads a registered Boolean constant after mixin rewriting.
     *
     * @param name literal name of the constant in the current mixin source's table.
     * @return the registered Boolean value.
     * @throws NotImplementedError if invoked directly without mixin rewriting.
     */
    @JvmStatic
    public fun getBoolean(name: String): Boolean = throw NotImplementedError("This should not be called directly")
}