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

package dev.karmakrafts.kcml.target

import dev.karmakrafts.kcml.hacks.DiscouragedKcmlApi
import dev.karmakrafts.kcml.hacks.EnumExtender
import dev.karmakrafts.kcml.hacks.SyntheticDelegateClass
import org.jetbrains.kotlin.konan.target.Architecture
import org.jetbrains.kotlin.konan.target.Family
import org.jetbrains.kotlin.konan.target.KonanTarget
import java.lang.reflect.Constructor
import kotlin.jvm.internal.DefaultConstructorMarker

@PublishedApi
@OptIn(DiscouragedKcmlApi::class)
internal object CustomKonanTargetFactory {
    val constructor: Constructor<out KonanTarget> by lazy {
        SyntheticDelegateClass(
            "dev.karmakrafts.kcml.target.internal.CustomKonanTarget", KonanTarget::class.java.getDeclaredConstructor(
                String::class.java, Family::class.java, Architecture::class.java, DefaultConstructorMarker::class.java
            ), trailingNullParameters = 1
        ).constructor
    }
}

/** Utilities for defining custom Kotlin/Native families, architectures, and targets. */
@OptIn(DiscouragedKcmlApi::class)
@Suppress("NOTHING_TO_INLINE")
object KonanTargets {
    /**
     * Defines and registers a new Kotlin/Native [Family] enum value.
     *
     * @param name non-empty, unique name for the family enum value.
     * @param exeSuffix suffix used for executable files in this family.
     * @param dynamicPrefix prefix used for dynamic library files in this family.
     * @param dynamicSuffix suffix used for dynamic library files in this family.
     * @param staticPrefix prefix used for static library files in this family.
     * @param staticSuffix suffix used for static library files in this family.
     * @return the newly registered family value.
     * @throws IllegalArgumentException if [name] is empty or already used by a family value.
     */
    inline fun createFamily(
        name: String,
        exeSuffix: String,
        dynamicPrefix: String,
        dynamicSuffix: String,
        staticPrefix: String,
        staticSuffix: String
    ): Family = EnumExtender.define(name, exeSuffix, dynamicPrefix, dynamicSuffix, staticPrefix, staticSuffix)

    /**
     * Defines and registers a new Kotlin/Native [Architecture] enum value.
     *
     * @param name non-empty, unique name for the architecture enum value.
     * @param bitness number of bits in the architecture.
     * @return the newly registered architecture value.
     * @throws IllegalArgumentException if [name] is empty or already used by an architecture value.
     */
    inline fun createArchitecture(
        name: String, bitness: Int
    ): Architecture = EnumExtender.define(name, bitness)

    /**
     * Creates a [KonanTarget] instance from its name, family, and architecture. Unlike [createFamily] and
     * [createArchitecture], this creates a target instance rather than registering a new enum value.
     *
     * @param name name identifying the target.
     * @param family platform family for the target.
     * @param architecture architecture for the target.
     * @return the constructed target.
     */
    inline fun create(
        name: String, family: Family, architecture: Architecture
    ): KonanTarget = CustomKonanTargetFactory.constructor.newInstance(name, family, architecture)
}