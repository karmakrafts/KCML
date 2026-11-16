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
import dev.karmakrafts.kcml.api.target.KonanTargets
import dev.karmakrafts.kcml.api.target.NativeCompileTarget
import dev.karmakrafts.kcml.plugin.PluginLoaderImpl
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
import org.jetbrains.kotlin.konan.config.konanTarget
import org.jetbrains.kotlin.konan.target.Architecture
import org.jetbrains.kotlin.konan.target.Family
import org.jetbrains.kotlin.konan.target.HostManager
import org.jetbrains.kotlin.konan.target.KonanTarget
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
    lateinit var preInitTarget: CompileTarget
    lateinit var initTarget: CompileTarget
    var initializedValue: Int? = null
    var irCount: Int = 0
    var lateInitCount: Int = 0
    var generatedElementCount: Int = 0

    override fun registerTargets(registry: CompileTargetRegistry) {
        for (alias in listOf("custom_jvm", "custom_native")) {
            registry.register(object : CompileTargetType {
                override val aliases: Set<String> = setOf(alias)
            }, CompileTargetFactory {
                ++creationCount
                when (alias) {
                    "custom_jvm" -> object : JvmCompileTarget {
                        override val name: String = "Custom Android"
                        override val isAndroid: Boolean = true
                    }
                    else -> object : NativeCompileTarget {
                        override val name: String = "Custom Native"
                        override val konanTarget: KonanTarget =
                            KonanTargets.create("kcml_custom_linux_x64", Family.LINUX, Architecture.X64)
                    }
                }
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
                server.soTimeout = 30_000
                val handshakes = CompletableFuture.runAsync {
                    repeat(6) {
                        server.accept().use { socket ->
                            socket.soTimeout = 30_000
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
                    plugin.toString()
                ).redirectErrorStream(true).redirectOutput(log.toFile()).start()
                try {
                    assertTrue(
                        process.waitFor(45, TimeUnit.SECONDS), "Early bootstrap timed out: ${Files.readString(log)}"
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
    @JvmStatic
    fun main(arguments: Array<String>) {
        val processors = ServiceLoader.load(CommandLineProcessor::class.java).toList()
        assertTrue(processors.indexOfFirst { it.pluginId == "kcml.bootstrap" } < processors.indexOfFirst { it.pluginId == "kcml" })
        fun assertNoCustomNativeTarget() {
            val hostManager = HostManager()
            assertFalse("kcml_custom_linux_x64" in hostManager.targets)
            assertTrue(hostManager.targetValues.none { it.name == "kcml_custom_linux_x64" })
            assertTrue(hostManager.enabled.none { it.name == "kcml_custom_linux_x64" })
        }

        var previous: EarlyOptionsTestPlugin? = null
        for (alias in listOf("custom_native", "custom_jvm")) {
            for (value in 1..2) {
                val config = CompilerConfiguration()
                processCompilerPluginsOptions(
                    config, listOf(
                        "plugin:kcml:early_options_testValue=$value",
                        "plugin:kcml.bootstrap:compileTarget=$alias",
                        "plugin:kcml.bootstrap:agentCommPort=${arguments[0]}",
                        "plugin:kcml.bootstrap:pluginClasspaths=${arguments[1]}"
                    ), processors
                )
                val plugin = PluginLoaderImpl.getPlugin("early_options_test") as EarlyOptionsTestPlugin
                assertNotSame(previous, plugin)
                assertNotSame(previous?.preInitTarget, plugin.preInitTarget)
                assertEquals(1, plugin.preInitCount)
                assertEquals(1, plugin.creationCount)
                assertEquals(value, config[EarlyOptionsTestPlugin.key])
                assertEquals(null, plugin.initializedValue)
                with(KCMLCompilerPluginRegistrar()) {
                    CompilerPluginRegistrar.ExtensionStorage().registerExtensions(config)
                }
                assertEquals(value, plugin.initializedValue)
                assertSame(plugin.preInitTarget, plugin.initTarget)
                assertEquals(1, plugin.creationCount)
                if (alias == "custom_native") {
                    val target = (plugin.initTarget as NativeCompileTarget).konanTarget
                    assertEquals("kcml_custom_linux_x64", target.name)
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