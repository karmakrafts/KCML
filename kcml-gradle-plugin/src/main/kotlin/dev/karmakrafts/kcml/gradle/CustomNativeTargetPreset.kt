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

@file:Suppress("INVISIBLE_REFERENCE", "INVISIBLE_MEMBER")

package dev.karmakrafts.kcml.gradle

import dev.karmakrafts.kcml.hacks.DiscouragedKcmlApi
import dev.karmakrafts.kcml.hacks.SyntheticDelegateClass
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTargetPreset
import org.jetbrains.kotlin.konan.target.Architecture
import org.jetbrains.kotlin.konan.target.Family
import org.jetbrains.kotlin.konan.target.KonanTarget
import java.lang.reflect.Constructor
import kotlin.jvm.internal.DefaultConstructorMarker

internal fun KotlinMultiplatformExtension.createCustomNativeTarget(
    name: String, konanTarget: KonanTarget
): KotlinNativeTarget {
    val adapter = project.extensions.getByType(KCMLExtension::class.java).nativeTargetAdapter
    adapter.register(konanTarget)
    return KotlinNativeTargetPreset(name, project, konanTarget).createTargetInternal(name).also {
        it.compilations.all(adapter::configure)
        adapter.configure(it)
        targets.add(it)
    }
}

@OptIn(DiscouragedKcmlApi::class)
private object CustomKonanTargetFactory {
    val constructor: Constructor<out KonanTarget> by lazy {
        SyntheticDelegateClass(
            "dev.karmakrafts.kcml.gradle.internal.CustomKonanTarget",
            KonanTarget::class.java.getDeclaredConstructor(
                String::class.java, Family::class.java, Architecture::class.java, DefaultConstructorMarker::class.java
            ),
            trailingNullParameters = 1
        ).constructor
    }
}

internal fun customKonanTarget(name: String, family: Family, architecture: Architecture): KonanTarget {
    require(name.isNotBlank()) { "Konan target name must not be blank" }
    return CustomKonanTargetFactory.constructor.newInstance(name, family, architecture)
}