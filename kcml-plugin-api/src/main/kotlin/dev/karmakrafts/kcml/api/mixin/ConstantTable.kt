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
 * Allows materializing runtime values from the mixin as constants in the transformed code.
 * To add values to a mixin's constant table, define a class which implements [ConstantTablePopulator]
 * and is marked with the [MixinPlugin] annotation.
 */
interface ConstantTable {
    fun getString(key: String): String?
    fun getInt(key: String): Int?
    fun getLong(key: String): Long?
    fun getFloat(key: String): Float?
    fun getDouble(key: String): Double?
}