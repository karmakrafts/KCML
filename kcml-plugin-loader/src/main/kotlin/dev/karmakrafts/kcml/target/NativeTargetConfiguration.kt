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

import dev.karmakrafts.kcml.api.target.KonanTargets
import dev.karmakrafts.kcml.api.target.NativeArchitectureConfiguration
import dev.karmakrafts.kcml.api.target.NativeCompileTarget
import dev.karmakrafts.kcml.target.proto.NativeTargetDescriptor
import dev.karmakrafts.kcml.util.kcmlNativeTarget
import kotlinx.serialization.decodeFromString
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.config.CompilerConfigurationKey
import org.jetbrains.kotlin.konan.config.NativeConfigurationKeys
import org.jetbrains.kotlin.konan.config.konanTarget
import org.jetbrains.kotlin.konan.target.Architecture
import org.jetbrains.kotlin.konan.target.BinaryFormat
import org.jetbrains.kotlin.konan.target.Family
import org.jetbrains.kotlin.konan.target.KonanTarget
import kotlin.io.path.Path
import kotlin.io.path.readText

internal class NativeTargetConfiguration(
    val target: KonanTarget, val architecture: NativeArchitectureConfiguration, val explicitTarget: Boolean
) {
    companion object {
        private val key: CompilerConfigurationKey<NativeTargetConfiguration> =
            CompilerConfigurationKey.create("kcmlResolvedNativeTarget")
        private val architectureKey: CompilerConfigurationKey<NativeArchitectureConfiguration> =
            CompilerConfigurationKey.create("kcmlResolvedNativeArchitecture")

        fun architecture(configuration: CompilerConfiguration): NativeArchitectureConfiguration? =
            configuration[architectureKey]

        fun get(configuration: CompilerConfiguration): NativeTargetConfiguration? {
            var nativeConfig = configuration[key]
            if (nativeConfig != null) return nativeConfig

            val descriptorPath = configuration.kcmlNativeTarget ?: return null
            val payload = Path(descriptorPath).readText(Charsets.UTF_8)
            val descriptor = NativeTargetDescriptor.json.decodeFromString<NativeTargetDescriptor>(payload)
            val name = descriptor.name.also { require(it.isNotBlank()) { "Native target name must not be blank" } }
            // Set up target family
            val (familyName, exeSuffix, dynamicPrefix, dynamicSuffix, staticPrefix, staticSuffix) = descriptor.family
            require(familyName.isNotBlank())
            val family = synchronized(Family::class.java) {
                Family.entries.firstOrNull { it.name == familyName }?.also {
                    require(
                        it.exeSuffix == exeSuffix && it.dynamicPrefix == dynamicPrefix && it.dynamicSuffix == dynamicSuffix && it.staticPrefix == staticPrefix && it.staticSuffix == staticSuffix
                    ) { "Conflicting definition of Native family '$familyName'" }
                } ?: KonanTargets.createFamily(
                    familyName, exeSuffix, dynamicPrefix, dynamicSuffix, staticPrefix, staticSuffix
                )
            }
            // Set up target architecture
            val architectureName = descriptor.architecture.name.also { require(it.isNotBlank()) }
            val bitness = descriptor.architecture.bitness.also {
                require(it > 0) { "Native architecture bitness must be positive" }
            }
            val architecture = synchronized(Architecture::class.java) {
                Architecture.entries.firstOrNull { it.name == architectureName }?.also {
                    @Suppress("DEPRECATION") require(it.bitness == bitness) { "Conflicting definition of Native architecture '$architectureName'" }
                } ?: KonanTargets.createArchitecture(architectureName, bitness)
            }
            // Set up concrete target instance
            val target = KonanTarget.predefinedTargets[name]?.takeIf {
                it.family == family && it.architecture == architecture
            } ?: KonanTargets.create(name, family, architecture)

            val llvm = descriptor.llvm
            val settings = NativeArchitectureConfiguration().apply {
                targetTriple = llvm.targetTriple
                cpu = llvm.cpu
                features = llvm.features
                dataLayout = llvm.dataLayout
                binaryFormat = llvm.binaryFormat?.let(BinaryFormat::valueOf)
                supports64BitAtomics = llvm.supports64BitAtomics
                supportsUnalignedAccess = llvm.supportsUnalignedAccess
                properties.putAll(llvm.properties)
                validate()
            }

            nativeConfig = NativeTargetConfiguration(target, settings, descriptor.explicitTarget)
            configuration.put(key, nativeConfig)
            return nativeConfig
        }

        fun configure(target: NativeCompileTarget, configuration: CompilerConfiguration): NativeCompileTarget {
            val explicit = get(configuration)
            val settings = if (explicit == null) target.architecture
            else NativeArchitectureConfiguration().apply {
                target.architecture?.let { arch ->
                    targetTriple = arch.targetTriple
                    cpu = arch.cpu
                    features = arch.features
                    dataLayout = arch.dataLayout
                    binaryFormat = arch.binaryFormat
                    supports64BitAtomics = arch.supports64BitAtomics
                    supportsUnalignedAccess = arch.supportsUnalignedAccess
                    properties.putAll(arch.properties)
                }
                explicit.architecture.let { arch ->
                    arch.targetTriple?.let { targetTriple = it }
                    arch.cpu?.let { cpu = it }
                    arch.features?.let { features = it }
                    arch.dataLayout?.let { dataLayout = it }
                    arch.binaryFormat?.let { binaryFormat = it }
                    arch.supports64BitAtomics?.let { supports64BitAtomics = it }
                    arch.supportsUnalignedAccess?.let { supportsUnalignedAccess = it }
                    properties.putAll(arch.properties)
                }
            }

            val resolved =
                if (explicit == null || !explicit.explicitTarget || target.konanTarget === explicit.target) target
                else object : NativeCompileTarget by target {
                    override val konanTarget: KonanTarget = explicit.target
                    override val architecture: NativeArchitectureConfiguration? = settings
                }

            configuration.konanTarget = resolved.konanTarget.name
            settings?.let { settings ->
                settings.validate()
                val properties =
                    configuration[NativeConfigurationKeys.OVERRIDE_KONAN_PROPERTIES].orEmpty().toMutableMap()
                properties.putAll(settings.properties)
                val name = resolved.konanTarget.name
                settings.targetTriple?.let { properties["targetTriple.$name"] = it }
                settings.cpu?.let { properties["targetCpu.$name"] = it }
                settings.features?.let { properties["targetCpuFeatures.$name"] = it }
                configuration.put(NativeConfigurationKeys.OVERRIDE_KONAN_PROPERTIES, properties)
                configuration.put(architectureKey, settings)
            }
            return resolved
        }
    }
}