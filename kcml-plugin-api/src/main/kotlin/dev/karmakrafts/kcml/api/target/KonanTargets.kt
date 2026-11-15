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

package dev.karmakrafts.kcml.api.target

import dev.karmakrafts.kcml.api.DelicateKcmlApi
import dev.karmakrafts.kcml.api.util.EnumExtender
import dev.karmakrafts.kcml.api.util.ReflectionUtils
import org.jetbrains.kotlin.konan.target.Architecture
import org.jetbrains.kotlin.konan.target.Family
import org.jetbrains.kotlin.konan.target.KonanTarget

// TODO: add documentation
@Suppress("NOTHING_TO_INLINE")
@OptIn(DelicateKcmlApi::class)
public object KonanTargets {
    // TODO: add documentation
    public inline fun createFamily(
        name: String,
        exeSuffix: String,
        dynamicPrefix: String,
        dynamicSuffix: String,
        staticPrefix: String,
        staticSuffix: String
    ): Family = EnumExtender.define(name, exeSuffix, dynamicPrefix, dynamicSuffix, staticPrefix, staticSuffix)

    // TODO: add documentation
    public inline fun createArchitecture(
        name: String, bitness: Int
    ): Architecture = EnumExtender.define(name, bitness)

    // TODO: add documentation
    public inline fun create(
        name: String, family: Family, architecture: Architecture
    ): KonanTarget = ReflectionUtils.new(name, family, architecture)
}