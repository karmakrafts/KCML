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

package dev.karmakrafts.kcml

import dev.karmakrafts.kcml.agent.proto.A2SHandshake
import dev.karmakrafts.kcml.agent.proto.S2AHandshake
import dev.karmakrafts.kcml.agent.proto.io.AgentPacketHandler
import dev.karmakrafts.kcml.agent.proto.io.AgentPacketReader
import dev.karmakrafts.kcml.agent.proto.io.AgentPacketWriter
import dev.karmakrafts.kcml.api.backend.IrBackend
import dev.karmakrafts.kcml.api.backend.jvm.JvmCodeGenerator
import dev.karmakrafts.kcml.api.backend.jvm.LateJvmBackend
import dev.karmakrafts.kcml.api.extension.AbstractExtension
import dev.karmakrafts.kcml.api.extension.ExtensionId
import dev.karmakrafts.kcml.api.extension.IrExtension
import dev.karmakrafts.kcml.api.extension.jvm.JvmCodeGenerationExtension
import dev.karmakrafts.kcml.api.plugin.CompilerPlugin
import dev.karmakrafts.kcml.api.plugin.InitializationContext
import dev.karmakrafts.kcml.api.plugin.Plugin
import dev.karmakrafts.kcml.api.plugin.PreInitializationContext
import dev.karmakrafts.kcml.api.target.CompileTarget
import dev.karmakrafts.kcml.api.target.CompileTargetFactory
import dev.karmakrafts.kcml.api.target.CompileTargetRegistry
import dev.karmakrafts.kcml.api.target.CompileTargetType
import dev.karmakrafts.kcml.api.target.JvmCompileTarget
import dev.karmakrafts.kcml.api.target.NativeCompileTarget
import dev.karmakrafts.kcml.plugin.PluginLoaderImpl
import dev.karmakrafts.kcml.target.KonanTargets
import dev.karmakrafts.kcml.util.kcmlCompileTarget
import org.jetbrains.kotlin.backend.jvm.codegen.PromisedValue
import org.jetbrains.kotlin.cli.common.ExitCode
import org.jetbrains.kotlin.cli.common.arguments.K2JVMCompilerArguments
import org.jetbrains.kotlin.cli.jvm.K2JVMCompiler
import org.jetbrains.kotlin.cli.plugins.processCompilerPluginsOptions
import org.jetbrains.kotlin.compiler.plugin.CommandLineProcessor
import org.jetbrains.kotlin.compiler.plugin.CompilerPluginRegistrar
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.config.CompilerConfigurationKey
import org.jetbrains.kotlin.ir.IrElement
import org.jetbrains.kotlin.ir.declarations.IrModuleFragment
import org.jetbrains.kotlin.konan.config.NativeConfigurationKeys
import org.jetbrains.kotlin.konan.config.konanTarget
import org.jetbrains.kotlin.konan.target.Architecture
import org.jetbrains.kotlin.konan.target.BinaryFormat
import org.jetbrains.kotlin.konan.target.ClangFlags
import org.jetbrains.kotlin.konan.target.Family
import org.jetbrains.kotlin.konan.target.HostManager
import org.jetbrains.kotlin.konan.target.KonanTarget
import org.jetbrains.kotlin.konan.target.binaryFormat
import org.jetbrains.kotlin.konan.target.loadConfigurables
import org.jetbrains.kotlin.konan.target.pointerBits
import org.jetbrains.kotlin.konan.target.supports64BitAtomics
import org.jetbrains.kotlin.konan.target.supportsUnalignedAccess
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.PrintStream
import java.lang.reflect.InvocationTargetException
import java.net.InetAddress
import java.net.ServerSocket
import java.nio.file.Files
import java.nio.file.Path
import java.util.*
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import java.util.jar.JarEntry
import java.util.jar.JarOutputStream
import java.util.zip.ZipFile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNotSame
import kotlin.test.assertSame
import kotlin.test.assertTrue

@Plugin("early_options_test")
class EarlyOptionsTestPlugin : CompilerPlugin {
    companion object {
        val key: CompilerConfigurationKey<Int> = CompilerConfigurationKey.create("early_options_testValue")
    }

    var creationCount: Int = 0
    var preInitCount: Int = 0
    lateinit var factoryTarget: CompileTarget
    lateinit var preInitTarget: CompileTarget
    lateinit var initTarget: CompileTarget
    var initializedValue: Int? = null
    var irCount: Int = 0
    var lateInitCount: Int = 0
    var generatedElementCount: Int = 0

    override fun registerTargets(registry: CompileTargetRegistry) {
        for (alias in listOf("custom_jvm", "custom_native")) {
            val targetType = object : CompileTargetType {
                override val aliases: Set<String> = setOf(alias)
            }
            registry.register(targetType, CompileTargetFactory {
                ++creationCount
                when (alias) {
                    "custom_jvm" -> object : JvmCompileTarget {
                        override val name: String = "Custom Android"
                        override val isAndroid: Boolean = true
                    }
                    else -> object : NativeCompileTarget {
                        override val name: String = "Custom Native"
                        override val type: CompileTargetType = targetType
                        override val konanTarget: KonanTarget =
                            KonanTargets.create("kcml_custom_linux_x64", Family.LINUX, Architecture.X64)
                    }
                }.also { factoryTarget = it }
            })
        }
    }

    override fun preInit(context: PreInitializationContext) {
        ++preInitCount
        preInitTarget = context.compileTarget
        context.addOption("value", "<int>", "A contributed option") { value, config -> config.put(key, value.toInt()) }
    }

    override fun init(context: InitializationContext) {
        initTarget = context.compileTarget
        initializedValue = context.config[key]
        context.extensionRegistry.register(EarlyJvmTestExtension(this))
    }
}

@ExtensionId("early_jvm_test")
private class EarlyJvmTestExtension(private val plugin: EarlyOptionsTestPlugin) : AbstractExtension(), JvmCodeGenerationExtension,
    IrExtension {
    private fun assertCompileTarget(target: CompileTarget) {
        assertSame(plugin.initTarget, target)
        assertEquals("Custom Android", target.name)
        assertTrue((target as JvmCompileTarget).isAndroid)
        assertEquals(1, plugin.creationCount)
    }

    override fun process(module: IrModuleFragment, backend: IrBackend) {
        assertCompileTarget(backend.compileTarget)
        ++plugin.irCount
    }

    override fun init(backend: LateJvmBackend) {
        assertCompileTarget(backend.compileTarget)
        ++plugin.lateInitCount
    }

    override fun JvmCodeGenerator.process(element: IrElement): PromisedValue? {
        ++plugin.generatedElementCount
        return null
    }
}

class EarlyBootstrapTest {
    companion object {
        // All repeated agent attachments and compiler invocations share this budget on CI.
        private val timeoutMillis: Long = TimeUnit.MINUTES.toMillis(3)
    }

    @Test
    fun `fresh and repeated compiler invocations bootstrap before contributed option validation`() {
        val directory = Files.createTempDirectory(Path.of("build"), "early-bootstrap-").toAbsolutePath()
        try {
            val compilerPlugin = checkNotNull(System.getProperty("kcml.compiler.plugin.jar"))
            val runtimeLoader = directory.resolve("loader.jar")
            val bridge = directory.resolve("bridge.jar")
            ZipFile(compilerPlugin).use { archive ->
                for ([entry, path] in listOf("kcml-loader.jar" to runtimeLoader, "kcml-mixin-bridge.jar" to bridge)) {
                    archive.getInputStream(archive.getEntry(entry)).use { input -> Files.copy(input, path) }
                }
            }
            val plugin = directory.resolve("plugin.jar")
            JarOutputStream(Files.newOutputStream(plugin)).use { archive ->
                val classEntry = EarlyOptionsTestPlugin::class.java.name.replace('.', '/') + ".class"
                archive.putNextEntry(JarEntry(classEntry))
                EarlyOptionsTestPlugin::class.java.classLoader.getResourceAsStream(classEntry)!!.use { input ->
                    input.copyTo(archive)
                }
                archive.closeEntry()
            }
            ServerSocket(0, 2, InetAddress.getLoopbackAddress()).use { server ->
                server.soTimeout = timeoutMillis.toInt()
                val handshakes = CompletableFuture.runAsync {
                    repeat(11) {
                        server.accept().use { socket ->
                            socket.soTimeout = timeoutMillis.toInt()
                            val writer = AgentPacketWriter(socket.getOutputStream())
                            val handler = AgentPacketHandler(A2SHandshake.serializer()) { request ->
                                writer.write(
                                    S2AHandshake.serializer(), S2AHandshake(
                                        request.signature,
                                        runtimeLoader.toString(),
                                        bridge.toString(),
                                        listOf(plugin.toString())
                                    )
                                )
                            }
                            AgentPacketReader(socket.getInputStream()).read { type -> handler.takeIf { it.type == type } }
                        }
                    }
                }
                val log = directory.resolve("compiler.log")
                val classpath =
                    compilerPlugin + File.pathSeparator + checkNotNull(System.getProperty("kcml.test.classpath"))
                val process = ProcessBuilder(
                    Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                    "-Duser.home=${directory.resolve("home")}",
                    "-cp",
                    classpath,
                    EarlyBootstrapProcess::class.java.name,
                    server.localPort.toString(),
                    plugin.toString(),
                    directory.toString()
                ).redirectErrorStream(true).redirectOutput(log.toFile()).start()
                try {
                    assertTrue(
                        process.waitFor(timeoutMillis, TimeUnit.MILLISECONDS),
                        "Early bootstrap timed out after ${timeoutMillis}ms: ${Files.readString(log)}"
                    )
                    assertEquals(0, process.exitValue(), Files.readString(log))
                    handshakes.get(10, TimeUnit.SECONDS)
                }
                finally {
                    process.destroyForcibly().waitFor()
                }
            }
        }
        finally {
            directory.toFile().deleteRecursively()
        }
    }
}

@OptIn(ExperimentalCompilerApi::class, CompilerConfiguration.Internals::class)
object EarlyBootstrapProcess {
    private const val NATIVE_TARGET = "kcml_bootstrap_native64"
    private const val NATIVE_FAMILY = "KCML_BOOTSTRAP_FAMILY"
    private const val NATIVE_ARCHITECTURE = "KCML_BOOTSTRAP_ARCH64"
    private const val TARGET_TRIPLE = "x86_64-unknown-linux-gnu"
    private const val DATA_LAYOUT = "e-m:e-p270:32:32-p271:32:32-p272:64:64-i64:64-f80:128-n8:16:32:64-S128"
    private lateinit var descriptorDirectory: Path

    private fun nativeTargetMetadata(bitness: Int = 64, dynamicSuffix: String = ".so", cpu: String = "x86-64"): String = """
        {
            "name": "$NATIVE_TARGET",
            "family": {
                "name": "$NATIVE_FAMILY",
                "exeSuffix": ".bin",
                "dynamicPrefix": "lib",
                "dynamicSuffix": "$dynamicSuffix",
                "staticPrefix": "lib",
                "staticSuffix": ".a"
            },
            "architecture": {"name": "$NATIVE_ARCHITECTURE", "bitness": $bitness},
            "llvm": {
                "targetTriple": "$TARGET_TRIPLE",
                "cpu": "$cpu",
                "features": "+sse2",
                "dataLayout": "$DATA_LAYOUT",
                "properties": {"clangFlags.$NATIVE_TARGET": "-DKCML_BOOTSTRAP_NATIVE=1"}
            }
        }
    """.trimIndent().let {
        val path = Files.createTempFile(descriptorDirectory, "native target ü-", ".json")
        Files.writeString(path, it, Charsets.UTF_8)
        path.toString()
    }

    private fun assertNativeMetadata(target: CompileTarget) {
        assertEquals("Custom Native", target.name)
        assertEquals(setOf("custom_native"), target.type.aliases)
        val native = target as NativeCompileTarget
        assertEquals(NATIVE_TARGET, native.konanTarget.name)
        with(native.konanTarget.family) {
            assertEquals(NATIVE_FAMILY, name)
            assertEquals(".bin", exeSuffix)
            assertEquals("lib", dynamicPrefix)
            assertEquals(".so", dynamicSuffix)
            assertEquals("lib", staticPrefix)
            assertEquals(".a", staticSuffix)
        }
        assertEquals(NATIVE_ARCHITECTURE, native.konanTarget.architecture.name)
        assertEquals(64, native.konanTarget.architecture.bitness)
        assertEquals(BinaryFormat.ELF, native.konanTarget.binaryFormat())
        assertEquals(64, native.konanTarget.pointerBits())
        assertFalse(native.konanTarget.supports64BitAtomics())
        assertFalse(native.konanTarget.supportsUnalignedAccess())
        with(assertNotNull(native.architecture)) {
            assertEquals(TARGET_TRIPLE, targetTriple)
            assertEquals("x86-64", cpu)
            assertEquals("+sse2", features)
            assertEquals(DATA_LAYOUT, dataLayout)
            assertEquals(mapOf("clangFlags.$NATIVE_TARGET" to "-DKCML_BOOTSTRAP_NATIVE=1"), properties)
        }
    }

    @JvmStatic
    fun main(arguments: Array<String>) {
        descriptorDirectory = Path.of(arguments[2])
        val processors = ServiceLoader.load(CommandLineProcessor::class.java).toList()
        assertTrue(processors.indexOfFirst { it.pluginId == "kcml.bootstrap" } < processors.indexOfFirst { it.pluginId == "kcml" })
        var host: KonanTarget? = null
        var predefinedTargets: Map<String, KonanTarget>? = null
        var familyCount = 0
        var architectureCount = 0
        fun assertHostUnchanged() {
            assertSame(host, HostManager.host)
            assertTrue(HostManager().enabled.any { it === host })
            assertEquals(predefinedTargets, KonanTarget.predefinedTargets)
            for ((name, target) in assertNotNull(predefinedTargets)) assertSame(target, KonanTarget.predefinedTargets[name])
        }

        fun assertNoCustomNativeTarget() {
            val hostManager = HostManager()
            for (name in listOf("kcml_custom_linux_x64", NATIVE_TARGET)) {
                assertFalse(name in hostManager.targets)
                assertTrue(hostManager.targetValues.none { it.name == name })
                assertTrue(hostManager.enabled.none { it.name == name })
            }
            assertHostUnchanged()
        }

        var previous: EarlyOptionsTestPlugin? = null
        var reconstructedFamily: Family? = null
        var reconstructedArchitecture: Architecture? = null
        for ((alias, metadata) in listOf(
            "custom_native" to null, "custom_native" to nativeTargetMetadata(), "custom_jvm" to null
        )) {
            for (value in 1..2) {
                val config = CompilerConfiguration().apply {
                    if (metadata != null) put(
                        NativeConfigurationKeys.OVERRIDE_KONAN_PROPERTIES, mapOf(
                            "kcml.bootstrap.retained" to "present", "targetCpu.$NATIVE_TARGET" to "stale-cpu"
                        )
                    )
                }
                processCompilerPluginsOptions(
                    config, buildList {
                        add("plugin:kcml:early_options_testValue=$value")
                        add("plugin:kcml.bootstrap:compileTarget=$alias")
                        metadata?.let { add("plugin:kcml.bootstrap:nativeTarget=$it") }
                        add("plugin:kcml.bootstrap:agentCommPort=${arguments[0]}")
                        add("plugin:kcml.bootstrap:pluginClasspaths=${arguments[1]}")
                    }, processors
                )
                val plugin = PluginLoaderImpl.getPlugin("early_options_test") as EarlyOptionsTestPlugin
                if (host == null) {
                    host = HostManager.host
                    predefinedTargets = KonanTarget.predefinedTargets.toMap()
                    familyCount = Family.entries.size
                    architectureCount = Architecture.entries.size
                    assertTrue(Family.entries.none { it.name == NATIVE_FAMILY })
                    assertTrue(Architecture.entries.none { it.name == NATIVE_ARCHITECTURE })
                }
                assertNotSame(previous, plugin)
                assertNotSame(previous?.preInitTarget, plugin.preInitTarget)
                assertEquals(1, plugin.preInitCount)
                assertEquals(1, plugin.creationCount)
                assertEquals(value, config[EarlyOptionsTestPlugin.key])
                assertEquals(null, plugin.initializedValue)
                assertSame(plugin.factoryTarget.type, plugin.preInitTarget.type)
                if (metadata != null) {
                    assertNativeMetadata(plugin.preInitTarget)
                    assertNotSame(plugin.factoryTarget, plugin.preInitTarget)
                    assertEquals("kcml_custom_linux_x64", (plugin.factoryTarget as NativeCompileTarget).konanTarget.name)
                    val overrides = assertNotNull(config[NativeConfigurationKeys.OVERRIDE_KONAN_PROPERTIES])
                    assertEquals(
                        mapOf(
                            "kcml.bootstrap.retained" to "present",
                            "targetTriple.$NATIVE_TARGET" to TARGET_TRIPLE,
                            "targetCpu.$NATIVE_TARGET" to "x86-64",
                            "targetCpuFeatures.$NATIVE_TARGET" to "+sse2",
                            "clangFlags.$NATIVE_TARGET" to "-DKCML_BOOTSTRAP_NATIVE=1"
                        ), overrides
                    )
                    val target = (plugin.preInitTarget as NativeCompileTarget).konanTarget
                    val configurables = loadConfigurables(target, Properties().apply { putAll(overrides) }, null)
                    assertSame(target, configurables.target)
                    assertEquals(TARGET_TRIPLE, configurables.targetTriple.toString())
                    assertEquals("x86-64", configurables.targetCpu)
                    assertEquals("+sse2", configurables.targetCpuFeatures)
                    assertTrue(configurables is ClangFlags)
                    assertEquals(listOf("-DKCML_BOOTSTRAP_NATIVE=1"), configurables.clangFlags)
                    Class.forName("org.jetbrains.kotlin.backend.konan.llvm.Runtime")
                    if (reconstructedFamily != null) {
                        assertSame(reconstructedFamily, target.family)
                        assertSame(reconstructedArchitecture, target.architecture)
                    }
                    reconstructedFamily = target.family
                    reconstructedArchitecture = target.architecture
                    assertSame(target.family, Family.entries.single { it.name == NATIVE_FAMILY })
                    assertSame(target.architecture, Architecture.entries.single { it.name == NATIVE_ARCHITECTURE })
                    assertEquals(familyCount + 1, Family.entries.size)
                    assertEquals(architectureCount + 1, Architecture.entries.size)
                }
                else {
                    assertSame(plugin.factoryTarget, plugin.preInitTarget)
                    assertTrue(config[NativeConfigurationKeys.OVERRIDE_KONAN_PROPERTIES].isNullOrEmpty())
                }
                with(KCMLCompilerPluginRegistrar()) {
                    CompilerPluginRegistrar.ExtensionStorage().registerExtensions(config)
                }
                assertEquals(value, plugin.initializedValue)
                assertSame(plugin.preInitTarget, plugin.initTarget)
                assertEquals(1, plugin.creationCount)
                assertHostUnchanged()
                if (alias == "custom_native") {
                    val target = (plugin.initTarget as NativeCompileTarget).konanTarget
                    if (metadata != null) assertNativeMetadata(plugin.initTarget)
                    else {
                        assertEquals("kcml_custom_linux_x64", target.name)
                        assertSame(Family.LINUX, target.family)
                        assertSame(Architecture.X64, target.architecture)
                        assertEquals(null, (plugin.initTarget as NativeCompileTarget).architecture)
                        assertFalse(NATIVE_TARGET in HostManager().targets)
                    }
                    assertEquals(target.name, config.konanTarget)
                    val hostManager = HostManager()
                    assertSame(target, hostManager.targetManager(target.name).target)
                    assertSame(target, hostManager.targetByName(target.name))
                    assertEquals(target.name, hostManager.known(target.name))
                    assertSame(target, hostManager.targetValues.single { it.name == target.name })
                    assertTrue(hostManager.isEnabled(target))
                    assertSame(target, hostManager.enabled.single { it.name == target.name })
                    assertFalse(target.name in KonanTarget.predefinedTargets)
                    assertFalse(target in KonanTarget.predefinedTargets.values)
                    if (metadata != null) {
                        assertFalse("kcml_custom_linux_x64" in hostManager.targets)
                        previous = plugin
                        continue
                    }
                    val constructor = Class.forName("org.jetbrains.kotlin.backend.konan.NativeSecondStageCompilationConfig")
                        .constructors.single()
                    Class.forName("dev.karmakrafts.kcml.hooks.CommonHooks").methods.single { it.name == "onExecImpl" }
                        .invoke(null, K2JVMCompiler(), K2JVMCompilerArguments().apply {
                            pluginOptions = arrayOf("plugin:kcml:early_options_testValue=$value")
                        })
                    val standaloneConfig = CompilerConfiguration().apply { kcmlCompileTarget = "custom_native" }
                    // Stop after the entry hook without requiring a native distribution.
                    val failure = assertFailsWith<InvocationTargetException> {
                        constructor.newInstance(null, standaloneConfig)
                    }
                    assertTrue(failure.cause is NullPointerException, failure.cause?.stackTraceToString())
                    val standalonePlugin = PluginLoaderImpl.getPlugin("early_options_test") as EarlyOptionsTestPlugin
                    val standaloneTarget = standalonePlugin.initTarget as NativeCompileTarget
                    assertEquals(1, standalonePlugin.creationCount)
                    assertEquals(value, standalonePlugin.initializedValue)
                    assertSame(standalonePlugin.preInitTarget, standaloneTarget)
                    assertEquals(standaloneTarget.konanTarget.name, standaloneConfig.konanTarget)
                    assertSame(standaloneTarget.konanTarget, HostManager().targetManager(standaloneConfig.konanTarget).target)
                }
                else {
                    assertEquals("Custom Android", plugin.initTarget.name)
                    assertTrue((plugin.initTarget as JvmCompileTarget).isAndroid)
                    assertNoCustomNativeTarget()
                }
                previous = plugin
            }
            if (metadata != null) {
                for ((invalidMetadata, message) in listOf(
                    nativeTargetMetadata(dynamicSuffix = ".conflict") to "Conflicting definition of Native family",
                    nativeTargetMetadata(bitness = 32) to "Conflicting definition of Native architecture",
                    nativeTargetMetadata(cpu = " ") to "Native architecture cpu must not be blank"
                )) {
                    val processor = KCMLBootstrapCommandLineProcessor()
                    val config = CompilerConfiguration()
                    fun option(name: String, value: String) = processor.processOption(
                        processor.pluginOptions.single { it.optionName == name }, value, config
                    )
                    option("compileTarget", "custom_native")
                    option("nativeTarget", invalidMetadata)
                    option("pluginClasspaths", arguments[1])
                    val failure = assertFailsWith<IllegalArgumentException> { option("agentCommPort", arguments[0]) }
                    assertTrue(failure.message.orEmpty().contains(message), failure.stackTraceToString())
                    assertEquals(familyCount + 1, Family.entries.size)
                    assertEquals(architectureCount + 1, Architecture.entries.size)
                    assertSame(reconstructedFamily, Family.entries.single { it.name == NATIVE_FAMILY })
                    assertSame(reconstructedArchitecture, Architecture.entries.single { it.name == NATIVE_ARCHITECTURE })
                    assertHostUnchanged()
                }
            }
        }
        val directory = Files.createTempDirectory(Path.of("build"), "unconfigured-compiler-")
        try {
            val source = directory.resolve("Plain.kt")
            Files.writeString(source, "class Plain { fun value(): Int = 42 }")
            val compiler = K2JVMCompiler()
            fun compile(options: List<String> = emptyList()) {
                val output = ByteArrayOutputStream()
                val result = PrintStream(output).use { stream ->
                    compiler.exec(
                        stream, *(listOf(
                            "-no-stdlib",
                            "-no-reflect",
                            "-classpath",
                            Path.of(Unit::class.java.protectionDomain.codeSource.location.toURI()).toString(),
                            "-d",
                            directory.resolve("classes").toString(),
                            source.toString()
                        ) + options).toTypedArray()
                    )
                }
                assertEquals(ExitCode.OK, result, output.toString())
                assertTrue(Files.exists(directory.resolve("classes/Plain.class")))
            }

            val compilerPlugin =
                Path.of(KCMLCompilerPluginRegistrar::class.java.protectionDomain.codeSource.location.toURI())
            for (value in 3..4) {
                compile(
                    listOf(
                        "-Xplugin=$compilerPlugin",
                        "-P",
                        "plugin:kcml:early_options_testValue=$value",
                        "-P",
                        "plugin:kcml.bootstrap:compileTarget=custom_jvm",
                        "-P",
                        "plugin:kcml.bootstrap:agentCommPort=${arguments[0]}",
                        "-P",
                        "plugin:kcml.bootstrap:pluginClasspaths=${arguments[1]}"
                    )
                )
                val plugin = PluginLoaderImpl.getPlugin("early_options_test") as EarlyOptionsTestPlugin
                assertNotSame(previous, plugin)
                assertNotSame(previous?.preInitTarget, plugin.preInitTarget)
                assertSame(plugin.preInitTarget, plugin.initTarget)
                assertEquals(1, plugin.preInitCount)
                assertEquals(1, plugin.creationCount)
                assertEquals(value, plugin.initializedValue)
                assertEquals(1, plugin.irCount)
                assertEquals(1, plugin.lateInitCount)
                assertTrue(plugin.generatedElementCount > 0)
                assertNoCustomNativeTarget()
                val generatedElementCount = plugin.generatedElementCount
                previous = plugin
                compile()
                assertTrue(PluginLoaderImpl.allPlugins().isEmpty())
                assertNoCustomNativeTarget()
                assertEquals(1, plugin.irCount)
                assertEquals(1, plugin.lateInitCount)
                assertEquals(generatedElementCount, plugin.generatedElementCount)
            }
        }
        finally {
            directory.toFile().deleteRecursively()
        }
    }
}