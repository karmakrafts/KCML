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

import dev.karmakrafts.kcml.api.target.NativeCompileTarget
import dev.karmakrafts.kcml.api.target.nativeArchitecture
import dev.karmakrafts.kcml.hooks.CommonHooks
import dev.karmakrafts.kcml.hooks.KCMLHookApi
import dev.karmakrafts.kcml.plugin.PluginLoaderImpl
import dev.karmakrafts.kcml.util.kcmlCompileTarget
import dev.karmakrafts.kcml.util.kcmlNativeTarget
import kotlinx.serialization.SerializationException
import org.jetbrains.kotlin.cli.common.arguments.K2JVMCompilerArguments
import org.jetbrains.kotlin.cli.jvm.K2JVMCompiler
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.konan.config.NativeConfigurationKeys
import org.jetbrains.kotlin.konan.config.konanTarget
import org.jetbrains.kotlin.konan.target.Family
import org.jetbrains.kotlin.konan.target.KonanTarget
import java.nio.file.Files
import java.nio.file.NoSuchFileException
import java.nio.file.Path
import java.util.*
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

@OptIn(CompilerConfiguration.Internals::class, KCMLHookApi::class)
class NativeTargetConfigurationTest {
    private val descriptorFiles = mutableListOf<Path>()

    private fun descriptorFile(payload: String = descriptor()): String {
        val path = Files.createTempFile(Path.of("build"), "native target ü-", ".json").toAbsolutePath()
        descriptorFiles.add(path)
        Files.writeString(path, payload, Charsets.UTF_8)
        return path.toString()
    }

    @AfterTest
    fun cleanupDescriptorFiles() {
        descriptorFiles.forEach { Files.deleteIfExists(it) }
    }

    private val target = object : NativeCompileTarget {
        override val name = "configured_native"
        override val konanTarget = KonanTarget.LINUX_X64
        override val architecture = nativeArchitecture {
            targetTriple = "x86_64-unknown-linux-gnu"
            cpu = "x86-64"
            features = "+sse2"
            properties["llvmHome.linux_x64"] = "/custom/llvm"
        }
    }

    private fun descriptor(llvm: String = "{}", bitness: Int = 64): String = """
        {"name":"linux_x64",
         "family":{"name":"LINUX","exeSuffix":"${Family.LINUX.exeSuffix}","dynamicPrefix":"${Family.LINUX.dynamicPrefix}","dynamicSuffix":"${Family.LINUX.dynamicSuffix}","staticPrefix":"${Family.LINUX.staticPrefix}","staticSuffix":"${Family.LINUX.staticSuffix}"},
         "architecture":{"name":"X64","bitness":$bitness},"llvm":$llvm}
    """.trimIndent()

    @Test
    fun `Native descriptors default absent LLVM settings and explicit target flag`() {
        val config = CompilerConfiguration().apply {
            kcmlNativeTarget = descriptorFile(descriptor().replace(",\"llvm\":{}", ""))
        }
        val resolved = checkNotNull(NativeTargetConfiguration.get(config))
        assertSame(KonanTarget.LINUX_X64, resolved.target)
        assertTrue(resolved.explicitTarget)
        with(resolved.architecture) {
            assertNull(targetTriple)
            assertNull(cpu)
            assertNull(features)
            assertNull(dataLayout)
            assertNull(binaryFormat)
            assertNull(supports64BitAtomics)
            assertNull(supportsUnalignedAccess)
            assertEquals(emptyMap(), properties)
        }
    }

    @Test
    fun `Native descriptors ignore unknown fields at every level`() {
        val payload = descriptor("""{"cpu":"haswell","futureLlvm":{"value":true}}""")
            .replace("\"name\":\"linux_x64\"", "\"name\":\"linux_x64\",\"futureTarget\":[1,2]")
            .replace("\"name\":\"LINUX\"", "\"name\":\"LINUX\",\"futureFamily\":true")
            .replace("\"name\":\"X64\"", "\"name\":\"X64\",\"futureArchitecture\":null")
        val config = CompilerConfiguration().apply { kcmlNativeTarget = descriptorFile(payload) }
        val resolved = checkNotNull(NativeTargetConfiguration.get(config))
        assertSame(KonanTarget.LINUX_X64, resolved.target)
        assertEquals("haswell", resolved.architecture.cpu)
    }

    @Test
    fun `plugin architecture settings merge with compiler property overrides`() {
        val config = CompilerConfiguration().apply {
            put(NativeConfigurationKeys.OVERRIDE_KONAN_PROPERTIES, mapOf("existing" to "kept"))
        }
        assertSame(target, NativeTargetConfiguration.configure(target, config))
        assertEquals("linux_x64", config.konanTarget)
        assertEquals(
            mapOf(
                "existing" to "kept",
                "llvmHome.linux_x64" to "/custom/llvm",
                "targetTriple.linux_x64" to "x86_64-unknown-linux-gnu",
                "targetCpu.linux_x64" to "x86-64",
                "targetCpuFeatures.linux_x64" to "+sse2"
            ), config[NativeConfigurationKeys.OVERRIDE_KONAN_PROPERTIES]
        )
    }

    @Test
    fun `explicit Gradle architecture overrides preserve unspecified plugin settings`() {
        val config = CompilerConfiguration().apply {
            kcmlNativeTarget = descriptorFile(descriptor("""{"cpu":"haswell","properties":{"flag":"a;b=c"}}"""))
        }
        val configured = NativeTargetConfiguration.configure(target, config)
        assertSame(target, configured)
        assertSame(target.type, configured.type)
        assertSame(KonanTarget.LINUX_X64, configured.konanTarget)
        assertEquals("haswell", NativeTargetConfiguration.architecture(config)?.cpu)
        assertEquals("+sse2", NativeTargetConfiguration.architecture(config)?.features)
        assertEquals("a;b=c", config[NativeConfigurationKeys.OVERRIDE_KONAN_PROPERTIES]?.get("flag"))
        assertEquals(
            "/custom/llvm", config[NativeConfigurationKeys.OVERRIDE_KONAN_PROPERTIES]?.get("llvmHome.linux_x64")
        )
        assertSame(NativeTargetConfiguration.get(config), NativeTargetConfiguration.get(config))
    }

    @Test
    fun `Native families are reused across configurations only when their properties match`() {
        val familyName = "KCML_CONFIGURATION_${UUID.randomUUID().toString().replace('-', '_')}"
        val customDescriptor = descriptor().replace("\"name\":\"LINUX\"", "\"name\":\"$familyName\"")
        for (payload in listOf(descriptor(), customDescriptor)) {
            fun decode(value: String = payload): NativeTargetConfiguration = checkNotNull(
                NativeTargetConfiguration.get(CompilerConfiguration().apply { kcmlNativeTarget = descriptorFile(value) })
            )

            val first = decode()
            val second = decode()
            assertSame(first.target.family, second.target.family)
            assertSame(first.target.architecture, second.target.architecture)
            assertEquals(1, Family.entries.count { it.name == first.target.family.name })

            val family = first.target.family
            for ((property, value) in mapOf(
                "exeSuffix" to family.exeSuffix,
                "dynamicPrefix" to family.dynamicPrefix,
                "dynamicSuffix" to family.dynamicSuffix,
                "staticPrefix" to family.staticPrefix,
                "staticSuffix" to family.staticSuffix
            )) {
                val conflicting = payload.replace("\"$property\":\"$value\"", "\"$property\":\"wrong\"")
                val error = assertFailsWith<IllegalArgumentException> { decode(conflicting) }
                assertEquals("Conflicting definition of Native family '${family.name}'", error.message)
                assertSame(family, decode().target.family)
            }
        }
    }

    @Test
    fun `Native descriptor files preserve arbitrary property punctuation and UTF-8`() {
        val json = descriptor("""{"features":"","properties":{"flag":"a,b;c=d:e","unicode":"Grüße 世界"}}""")
        val config = CompilerConfiguration().apply {
            kcmlNativeTarget = descriptorFile(json)
        }
        val configured = NativeTargetConfiguration.configure(target, config)
        assertSame(target, configured)
        assertEquals("", NativeTargetConfiguration.architecture(config)?.features)
        assertEquals("a,b;c=d:e", config[NativeConfigurationKeys.OVERRIDE_KONAN_PROPERTIES]?.get("flag"))
        assertEquals("Grüße 世界", config[NativeConfigurationKeys.OVERRIDE_KONAN_PROPERTIES]?.get("unicode"))
    }

    @Test
    fun `architecture-only DSL preserves the plugin selected platform`() {
        val pluginTarget = object : NativeCompileTarget by target {
            override val konanTarget = KonanTarget.LINUX_ARM64
        }
        val config = CompilerConfiguration().apply {
            kcmlNativeTarget = descriptorFile(descriptor("""{"cpu":"generic"}""").replace(
                "\"name\":\"linux_x64\"", "\"explicitTarget\":false,\"name\":\"linux_x64\""
            ))
        }
        val configured = NativeTargetConfiguration.configure(pluginTarget, config)
        assertSame(KonanTarget.LINUX_ARM64, configured.konanTarget)
        assertEquals("generic", config[NativeConfigurationKeys.OVERRIDE_KONAN_PROPERTIES]?.get("targetCpu.linux_arm64"))
    }

    @Test
    fun `explicit Native descriptors do not require a plugin factory`() {
        val config = CompilerConfiguration().apply {
            kcmlCompileTarget = "standalone_custom_native"
            kcmlNativeTarget = descriptorFile()
        }
        val configured = CompileTargetRegistryImpl().createTarget(config) as NativeCompileTarget
        assertSame(KonanTarget.LINUX_X64, configured.konanTarget)
    }

    @Test
    fun `explicit Native settings are applied even without any compiler plugins`() {
        CommonHooks.onExecImpl(K2JVMCompiler(), K2JVMCompilerArguments())
        try {
            val config = CompilerConfiguration().apply {
                kcmlCompileTarget = "standalone_custom_native"
                kcmlNativeTarget = descriptorFile(descriptor("""{"cpu":"haswell"}"""))
            }
            PluginLoaderImpl.loadAndInvokeStandalone(config, emptyList())
            val target = PluginLoaderImpl.compileTargetOrNull as NativeCompileTarget
            assertSame(KonanTarget.LINUX_X64, target.konanTarget)
            assertEquals(
                "haswell", config[NativeConfigurationKeys.OVERRIDE_KONAN_PROPERTIES]?.get("targetCpu.linux_x64")
            )
        }
        finally {
            CommonHooks.onExecImpl(K2JVMCompiler(), K2JVMCompilerArguments())
        }
    }

    @Test
    fun `descriptor rejects conflicting enums and invalid settings`() {
        for (payload in listOf(
            descriptor(bitness = 32),
            descriptor(bitness = 0),
            descriptor().replace("\"exeSuffix\":\"${Family.LINUX.exeSuffix}\"", "\"exeSuffix\":\"wrong\""),
            descriptor("""{"targetTriple":" "}"""),
            descriptor("""{"cpu":" "}"""),
            descriptor("""{"properties":{" ":"value"}}""")
        )) {
            assertFailsWith<IllegalArgumentException>(payload) {
                NativeTargetConfiguration.get(CompilerConfiguration().apply { kcmlNativeTarget = descriptorFile(payload) })
            }
        }
    }

    @Test
    fun `missing Native descriptor file reports its path`() {
        val path = descriptorFile()
        Files.delete(Path.of(path))
        val config = CompilerConfiguration().apply { kcmlNativeTarget = path }
        val failure = assertFailsWith<NoSuchFileException> { NativeTargetConfiguration.get(config) }
        assertEquals(path, failure.file)
    }

    @Test
    fun `malformed Native descriptor file is rejected without caching the failure`() {
        val path = descriptorFile("{\"name\":")
        val config = CompilerConfiguration().apply { kcmlNativeTarget = path }
        assertFailsWith<SerializationException> { NativeTargetConfiguration.get(config) }
        Files.writeString(Path.of(path), descriptor(), Charsets.UTF_8)
        assertSame(KonanTarget.LINUX_X64, NativeTargetConfiguration.get(config)?.target)
    }

    @Test
    fun `architecture configuration rejects blank explicit LLVM settings`() {
        assertFailsWith<IllegalArgumentException> { nativeArchitecture { dataLayout = " " } }
        assertFailsWith<IllegalArgumentException> { nativeArchitecture { features = " " } }
        assertEquals("", nativeArchitecture { features = "" }.features)
    }
}