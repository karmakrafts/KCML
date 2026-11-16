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
import org.jetbrains.kotlin.gradle.plugin.KotlinPlatformType
import org.jetbrains.kotlin.gradle.plugin.KotlinTarget
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinWithJavaTarget
import org.jetbrains.kotlin.gradle.targets.js.dsl.KotlinJsTargetDsl
import org.jetbrains.kotlin.gradle.targets.js.dsl.KotlinWasmJsTargetDsl
import org.jetbrains.kotlin.gradle.targets.js.dsl.KotlinWasmTargetDsl
import org.jetbrains.kotlin.gradle.targets.jvm.KotlinJvmTarget
import org.jetbrains.kotlin.konan.target.HostManager
import org.jetbrains.kotlin.konan.target.KonanTarget

private fun KotlinTarget.selectCustomCompileTarget(name: String) {
    require(name.isNotBlank()) { "Custom compile target alias must not be blank" }
    project.extensions.getByType(KCMLExtension::class.java).customCompileTargets.put(this.name, name)
}

/**
 * Selects the plugin-provided compile target alias [name] for this Kotlin/JVM project's target.
 */
fun KotlinJvmProjectExtension.customJvmTarget(
    name: String,
    configure: KotlinWithJavaTarget<*, *>.() -> Unit = {}
): KotlinWithJavaTarget<*, *> = target.apply {
    selectCustomCompileTarget(name)
    configure()
}

/**
 * Creates a JVM target named [name] using the plugin-provided compile target alias [name].
 */
fun KotlinMultiplatformExtension.customJvmTarget(
    name: String,
    configure: KotlinJvmTarget.() -> Unit = {}
): KotlinJvmTarget = customJvmTarget(name, jvm(name), configure)

/**
 * Selects the plugin-provided alias [name] for an existing JVM or Android [target].
 */
fun <T : KotlinTarget> KotlinMultiplatformExtension.customJvmTarget(
    name: String,
    target: T,
    configure: T.() -> Unit = {}
): T = target.apply {
    require(platformType == KotlinPlatformType.jvm || platformType == KotlinPlatformType.androidJvm)
    selectCustomCompileTarget(name)
    configure()
}

/**
 * Creates a JavaScript target named [name] using the plugin-provided compile target alias [name].
 */
fun KotlinMultiplatformExtension.customJsTarget(
    name: String,
    configure: KotlinJsTargetDsl.() -> Unit = {}
): KotlinJsTargetDsl = customJsTarget(name, js(name), configure)

/**
 * Selects the plugin-provided alias [name] for an existing JavaScript [target].
 */
fun <T : KotlinJsTargetDsl> KotlinMultiplatformExtension.customJsTarget(
    name: String,
    target: T,
    configure: T.() -> Unit = {}
): T = target.apply {
    require(platformType == KotlinPlatformType.js)
    selectCustomCompileTarget(name)
    configure()
}

/**
 * Creates a Wasm JS target named [name] using the plugin-provided compile target alias [name].
 * To select Wasm WASI, pass a target created with `wasmWasi` to the other overload.
 */
fun KotlinMultiplatformExtension.customWasmTarget(
    name: String,
    configure: KotlinWasmJsTargetDsl.() -> Unit = {}
): KotlinWasmJsTargetDsl = customWasmTarget(name, wasmJs(name), configure)

/**
 * Selects the plugin-provided alias [name] for an existing Wasm JS or Wasm WASI [target].
 */
fun <T : KotlinWasmTargetDsl> KotlinMultiplatformExtension.customWasmTarget(
    name: String,
    target: T,
    configure: T.() -> Unit = {}
): T = target.apply {
    selectCustomCompileTarget(name)
    configure()
}

/**
 * Creates a host Native target named [name] using the plugin-provided compile target alias [name].
 * To choose another platform, pass a target created with Kotlin's Native target DSL to the other overload.
 */
fun KotlinMultiplatformExtension.customNativeTarget(
    name: String,
    configure: KotlinNativeTarget.() -> Unit = {}
): KotlinNativeTarget = customNativeTarget(name, when (HostManager.host) {
    KonanTarget.LINUX_X64 -> linuxX64(name)
    KonanTarget.MINGW_X64 -> mingwX64(name)
    KonanTarget.MACOS_ARM64 -> macosArm64(name)
    else -> error("Unsupported Kotlin/Native host: ${HostManager.host}")
}, configure)

/**
 * Selects the plugin-provided alias [name] for an existing Native [target], preserving its platform.
 */
fun <T : KotlinNativeTarget> KotlinMultiplatformExtension.customNativeTarget(
    name: String,
    target: T,
    configure: T.() -> Unit = {}
): T = target.apply {
    selectCustomCompileTarget(name)
    configure()
}