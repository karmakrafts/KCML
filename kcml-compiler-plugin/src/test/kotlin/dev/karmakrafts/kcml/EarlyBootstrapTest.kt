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
import dev.karmakrafts.kcml.api.plugin.CompilerPlugin
import dev.karmakrafts.kcml.api.plugin.InitializationContext
import dev.karmakrafts.kcml.api.plugin.Plugin
import dev.karmakrafts.kcml.api.plugin.PreInitializationContext
import dev.karmakrafts.kcml.plugin.PluginLoaderImpl
import org.jetbrains.kotlin.cli.plugins.processCompilerPluginsOptions
import org.jetbrains.kotlin.compiler.plugin.CommandLineProcessor
import org.jetbrains.kotlin.compiler.plugin.CompilerPluginRegistrar
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.config.CompilerConfigurationKey
import java.io.File
import java.net.InetAddress
import java.net.ServerSocket
import java.nio.file.Files
import java.nio.file.Path
import java.util.ServiceLoader
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import java.util.jar.JarEntry
import java.util.jar.JarOutputStream
import java.util.zip.ZipFile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotSame
import kotlin.test.assertTrue

@Plugin("early_options_test")
class EarlyOptionsTestPlugin : CompilerPlugin {
    companion object {
        val key: CompilerConfigurationKey<Int> = CompilerConfigurationKey.create("early_options_testValue")
    }

    var preInitCount: Int = 0
    var initializedValue: Int? = null

    override fun preInit(context: PreInitializationContext) {
        ++preInitCount
        context.addOption("value", "<int>", "A contributed option") { value, config -> config.put(key, value.toInt()) }
    }

    override fun init(context: InitializationContext) {
        initializedValue = context.config[key]
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
                for ((entry, path) in listOf("kcml-loader.jar" to runtimeLoader, "kcml-mixin-bridge.jar" to bridge)) {
                    archive.getInputStream(archive.getEntry(entry)).use { input -> Files.copy(input, path) }
                }
            }
            val plugin = directory.resolve("plugin.jar")
            JarOutputStream(Files.newOutputStream(plugin)).use { archive ->
                archive.putNextEntry(JarEntry("META-INF/services/${CompilerPlugin::class.java.name}"))
                archive.write(EarlyOptionsTestPlugin::class.java.name.toByteArray())
                archive.closeEntry()
            }
            ServerSocket(0, 2, InetAddress.getLoopbackAddress()).use { server ->
                server.soTimeout = 30_000
                val handshakes = CompletableFuture.runAsync {
                    repeat(2) {
                        server.accept().use { socket ->
                            socket.soTimeout = 30_000
                            val writer = AgentPacketWriter(socket.getOutputStream())
                            val handler = AgentPacketHandler(A2SHandshake.serializer()) { request ->
                                writer.write(S2AHandshake.serializer(), S2AHandshake(
                                    request.signature, runtimeLoader.toString(), bridge.toString(), listOf(plugin.toString())
                                ))
                            }
                            AgentPacketReader(socket.getInputStream()).read { type -> handler.takeIf { it.type == type } }
                        }
                    }
                }
                val log = directory.resolve("compiler.log")
                val classpath = compilerPlugin + File.pathSeparator + checkNotNull(System.getProperty("kcml.test.classpath"))
                val process = ProcessBuilder(
                    Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                    "-Duser.home=${directory.resolve("home")}", "-cp", classpath,
                    EarlyBootstrapProcess::class.java.name, server.localPort.toString(), plugin.toString()
                ).redirectErrorStream(true).redirectOutput(log.toFile()).start()
                try {
                    assertTrue(process.waitFor(45, TimeUnit.SECONDS), "Early bootstrap timed out: ${Files.readString(log)}")
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
        var previous: CompilerPlugin? = null
        for (value in 1..2) {
            val config = CompilerConfiguration()
            processCompilerPluginsOptions(config, listOf(
                "plugin:kcml:early_options_testValue=$value",
                "plugin:kcml.bootstrap:agentCommPort=${arguments[0]}",
                "plugin:kcml.bootstrap:pluginClasspaths=${arguments[1]}"
            ), processors)
            val plugin = PluginLoaderImpl.getPlugin("early_options_test") as EarlyOptionsTestPlugin
            assertNotSame(previous, plugin)
            assertEquals(1, plugin.preInitCount)
            assertEquals(value, config[EarlyOptionsTestPlugin.key])
            assertEquals(null, plugin.initializedValue)
            with(KCMLCompilerPluginRegistrar()) { CompilerPluginRegistrar.ExtensionStorage().registerExtensions(config) }
            assertEquals(value, plugin.initializedValue)
            previous = plugin
        }
    }
}