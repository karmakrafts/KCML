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

import dev.karmakrafts.kcml.hacks.DiscouragedKcmlApi
import dev.karmakrafts.kcml.hacks.EnumExtender
import dev.karmakrafts.kcml.target.proto.NativeArchitectureDescriptor
import dev.karmakrafts.kcml.target.proto.NativeFamilyDescriptor
import dev.karmakrafts.kcml.target.proto.NativeLlvmDescriptor
import dev.karmakrafts.kcml.target.proto.NativeTargetDescriptor
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget
import org.jetbrains.kotlin.konan.target.Architecture
import org.jetbrains.kotlin.konan.target.BinaryFormat
import org.jetbrains.kotlin.konan.target.Family
import java.io.File

/**
 * Optional LLVM settings. Unspecified fields retain the compiler plugin's defaults.
 */
class NativeArchitectureConfiguration {
    var targetTriple: String? = null
        set(value) {
            require(value == null || value.isNotBlank()) { "targetTriple must not be blank" }
            field = value
        }

    var cpu: String? = null
        set(value) {
            require(value == null || value.isNotBlank()) { "cpu must not be blank" }
            field = value
        }

    var features: String? = null
        set(value) {
            require(value.isNullOrEmpty() || value.isNotBlank()) { "features must not contain only whitespace" }
            field = value
        }

    var dataLayout: String? = null
        set(value) {
            require(value == null || value.isNotBlank()) { "dataLayout must not be blank" }
            field = value
        }

    var binaryFormat: BinaryFormat? = null
    var supports64BitAtomics: Boolean? = null
    var supportsUnalignedAccess: Boolean? = null

    /** Exact konan.properties keys; include a target suffix when the property requires one. */
    val properties: MutableMap<String, String> = linkedMapOf()
}

/** Configures LLVM settings for either a regular or a custom Native target. */
fun KotlinNativeTarget.architecture(configure: NativeArchitectureConfiguration.() -> Unit) {
    val settings =
        project.extensions.getByType(KCMLExtension::class.java).nativeTargets.getOrPut(this) { NativeTargetConfiguration() }
    settings.llvm.apply(configure)
    settings.configured = true
}

/** Defines a new Native family, including its binary naming conventions. */
@OptIn(DiscouragedKcmlApi::class)
fun nativeFamily(
    name: String,
    exeSuffix: String = Family.LINUX.exeSuffix,
    dynamicPrefix: String = "lib",
    dynamicSuffix: String = Family.LINUX.dynamicSuffix,
    staticPrefix: String = "lib",
    staticSuffix: String = Family.LINUX.staticSuffix
): Family {
    require(name.isNotBlank()) { "Native family name must not be blank" }
    return synchronized(Family::class.java) {
        val existing = Family.entries.firstOrNull { it.name == name }
        if (existing == null) {
            EnumExtender.define<Family>(name, exeSuffix, dynamicPrefix, dynamicSuffix, staticPrefix, staticSuffix)
        }
        else {
            require(
                existing.exeSuffix == exeSuffix && existing.dynamicPrefix == dynamicPrefix && existing.dynamicSuffix == dynamicSuffix && existing.staticPrefix == staticPrefix && existing.staticSuffix == staticSuffix
            ) { "Native family '$name' already exists with different binary naming conventions" }
            existing
        }
    }
}

/** Defines a new Native architecture with the given positive [bitness]. */
@OptIn(DiscouragedKcmlApi::class)
fun nativeArchitecture(name: String, bitness: Int): Architecture {
    require(name.isNotBlank()) { "Native architecture name must not be blank" }
    require(bitness > 0) { "Native architecture bitness must be positive" }
    return synchronized(Architecture::class.java) {
        val existing = Architecture.entries.firstOrNull { it.name == name }
        if (existing == null) {
            EnumExtender.define<Architecture>(name, bitness)
        }
        else {
            require(existing.bitness == bitness) { "Native architecture '$name' already exists with different bitness" }
            existing
        }
    }
}

internal class NativeTargetConfiguration {
    var explicit: Boolean = false
    var configured: Boolean = false
    val llvm: NativeArchitectureConfiguration = NativeArchitectureConfiguration()
}

internal fun KotlinNativeTarget.nativeTargetFile(): File? = synchronized(this) {
    val json = nativeTargetJson() ?: return@synchronized null
    val file = project.layout.buildDirectory.file("kcml_native_targets/$name.json").get().asFile
    if (!file.isFile || file.readText() != json) {
        file.parentFile.mkdirs()
        file.writeText(json)
    }
    file
}

@Suppress("DEPRECATION")
internal fun KotlinNativeTarget.nativeTargetJson(): String? {
    val configuration = project.extensions.getByType(KCMLExtension::class.java).nativeTargets[this] ?: return null
    if (!configuration.explicit && !configuration.configured) return null
    val target = konanTarget
    val descriptor = NativeTargetDescriptor(
        explicitTarget = configuration.explicit,
        name = target.name,
        family = target.family.let {
            NativeFamilyDescriptor(
                it.name, it.exeSuffix, it.dynamicPrefix, it.dynamicSuffix, it.staticPrefix, it.staticSuffix
            )
        },
        architecture = NativeArchitectureDescriptor(target.architecture.name, target.architecture.bitness),
        llvm = configuration.llvm.let {
            require(it.properties.keys.all(String::isNotBlank)) { "Native property keys must not be blank" }
            NativeLlvmDescriptor(
                targetTriple = it.targetTriple,
                cpu = it.cpu,
                features = it.features,
                dataLayout = it.dataLayout,
                binaryFormat = it.binaryFormat?.name,
                supports64BitAtomics = it.supports64BitAtomics,
                supportsUnalignedAccess = it.supportsUnalignedAccess,
                properties = it.properties.toMap()
            )
        }
    )
    return NativeTargetDescriptor.json.encodeToString(NativeTargetDescriptor.serializer(), descriptor)
}