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

package dev.karmakrafts.kcml.gradle

import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.plugin.KotlinTarget
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget
import org.jetbrains.kotlin.gradle.targets.js.dsl.KotlinJsTargetDsl
import org.jetbrains.kotlin.gradle.targets.js.dsl.KotlinWasmJsTargetDsl
import org.jetbrains.kotlin.gradle.targets.jvm.KotlinJvmTarget
import org.jetbrains.kotlin.konan.target.Architecture
import org.jetbrains.kotlin.konan.target.Family
import org.jetbrains.kotlin.konan.target.HostManager
import org.jetbrains.kotlin.konan.target.KonanTarget

private fun KotlinTarget.selectCustomCompileTarget(name: String) {
    require(name.isNotBlank()) { "Custom compile target alias must not be blank" }
    project.extensions.getByType(KCMLExtension::class.java).customCompileTargets.put(this.name, name)
}

private fun KotlinMultiplatformExtension.checkCustomTargetName(name: String) {
    require(name.isNotBlank()) { "Custom compile target alias must not be blank" }
    require(name !in targets.names) { "Kotlin target '$name' already exists" }
}

/**
 * Creates an independent JVM target named [name] using the plugin-provided alias [name].
 */
fun KotlinJvmProjectExtension.customJvmTarget(
    name: String, configure: KotlinJvmTarget.() -> Unit = {}
): KotlinJvmTarget {
    require(name.isNotBlank()) { "Custom compile target alias must not be blank" }
    require(name != target.name && "${name}Main" !in sourceSets.names && "${name}Test" !in sourceSets.names) {
        "Kotlin target '$name' already exists"
    }
    return CustomJvmTargetPreset(target.project, target).create(name).apply {
        selectCustomCompileTarget(name)
        configure()
    }
}

/**
 * Creates a JVM target named [name] using the plugin-provided compile target alias [name].
 */
fun KotlinMultiplatformExtension.customJvmTarget(
    name: String, configure: KotlinJvmTarget.() -> Unit = {}
): KotlinJvmTarget {
    checkCustomTargetName(name)
    return jvm(name) {
        selectCustomCompileTarget(name)
        configure()
    }
}

/**
 * Creates a JavaScript target named [name] using the plugin-provided compile target alias [name].
 */
fun KotlinMultiplatformExtension.customJsTarget(
    name: String, configure: KotlinJsTargetDsl.() -> Unit = {}
): KotlinJsTargetDsl {
    checkCustomTargetName(name)
    return js(name) {
        selectCustomCompileTarget(name)
        configure()
    }
}

/**
 * Creates a Wasm JS target named [name] using the plugin-provided compile target alias [name].
 */
fun KotlinMultiplatformExtension.customWasmTarget(
    name: String, configure: KotlinWasmJsTargetDsl.() -> Unit = {}
): KotlinWasmJsTargetDsl {
    checkCustomTargetName(name)
    return wasmJs(name) {
        selectCustomCompileTarget(name)
        configure()
    }
}

/**
 * Creates a host Native target named [name] using the plugin-provided compile target alias [name].
 */
fun KotlinMultiplatformExtension.customNativeTarget(
    name: String, configure: KotlinNativeTarget.() -> Unit = {}
): KotlinNativeTarget {
    checkCustomTargetName(name)
    return when (HostManager.host) {
        KonanTarget.LINUX_X64 -> linuxX64(name)
        KonanTarget.MINGW_X64 -> mingwX64(name)
        KonanTarget.MACOS_ARM64 -> macosArm64(name)
        else -> error("Unsupported Kotlin/Native host: ${HostManager.host}")
    }.apply {
        selectCustomCompileTarget(name)
        configure()
    }
}

/**
 * Creates an independent Native target preserving the supplied [konanTarget] instance.
 */
fun KotlinMultiplatformExtension.customNativeTarget(
    name: String, konanTarget: KonanTarget, configure: KotlinNativeTarget.() -> Unit = {}
): KotlinNativeTarget {
    checkCustomTargetName(name)
    require(konanTarget.name.isNotBlank()) { "Konan target name must not be blank" }
    return createCustomNativeTarget(name, konanTarget).apply {
        selectCustomCompileTarget(name)
        project.extensions.getByType(KCMLExtension::class.java).nativeTargets.getOrPut(this) { NativeTargetConfiguration() }.explicit =
            true
        configure()
    }
}

/**
 * Creates a Native target using [konanTarget]'s real name by default.
 */
fun KotlinMultiplatformExtension.customNativeTarget(
    konanTarget: KonanTarget, configure: KotlinNativeTarget.() -> Unit = {}
): KotlinNativeTarget = customNativeTarget(konanTarget.name, konanTarget, configure)

/**
 * Creates a Native target with an independent Konan identity, including runtime enum constants.
 */
fun KotlinMultiplatformExtension.customNativeTarget(
    name: String, family: Family, architecture: Architecture, configure: KotlinNativeTarget.() -> Unit = {}
): KotlinNativeTarget {
    checkCustomTargetName(name)
    return customNativeTarget(name, customKonanTarget(name, family, architecture), configure)
}