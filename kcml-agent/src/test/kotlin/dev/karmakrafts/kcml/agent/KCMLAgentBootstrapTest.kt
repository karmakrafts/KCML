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

package dev.karmakrafts.kcml.agent

import dev.karmakrafts.kcml.agent.proto.A2SHandshake
import dev.karmakrafts.kcml.agent.proto.S2AHandshake
import dev.karmakrafts.kcml.agent.proto.io.AgentPacketHandler
import dev.karmakrafts.kcml.agent.proto.io.AgentPacketReader
import dev.karmakrafts.kcml.agent.proto.io.AgentPacketWriter
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.Opcodes
import java.io.File
import java.lang.instrument.Instrumentation
import java.lang.reflect.Proxy
import java.net.InetAddress
import java.net.ServerSocket
import java.net.URLClassLoader
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import java.util.jar.JarEntry
import java.util.jar.JarOutputStream
import java.util.jar.Manifest
import java.util.zip.ZipFile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class KCMLAgentBootstrapTest {
    @Test
    fun `starts isolated agent with bridge injected into bootstrap class loader`() {
        assertAgentStartup(false)
    }

    @Test
    fun `reports incompatible bridge retained by previous build`() {
        assertAgentStartup(true)
    }

    @Test
    fun `reports incompatible bridge even when new bridge has a different path`() {
        assertAgentStartup(true, false)
    }

    private fun assertAgentStartup(legacyBridge: Boolean, replaceBridge: Boolean = true) {
        val directory = Files.createTempDirectory(Path.of("build"), "agent-startup-test-").toAbsolutePath()
        try {
            val harness = directory.resolve("harness.jar")
            val manifest = Manifest().apply {
                mainAttributes.putValue("Manifest-Version", "1.0")
                mainAttributes.putValue("Premain-Class", AgentStartupProcess::class.java.name)
                mainAttributes.putValue("Can-Retransform-Classes", "true")
            }
            JarOutputStream(Files.newOutputStream(harness), manifest).use { output ->
                val entry = AgentStartupProcess::class.java.name.replace('.', '/') + ".class"
                output.putNextEntry(JarEntry(entry))
                checkNotNull(javaClass.getResourceAsStream("/$entry")).use { it.copyTo(output) }
                output.closeEntry()
            }
            val loader = directory.resolve("loader.jar")
            JarOutputStream(Files.newOutputStream(loader)).use { }
            val currentBridge = checkNotNull(System.getProperty("kcml.bridge.jar"))
            val bridge = if (legacyBridge) {
                val writer = ClassWriter(0)
                writer.visit(
                    Opcodes.V17,
                    Opcodes.ACC_PUBLIC,
                    "dev/karmakrafts/kcml/api/mixin/MixinBridge",
                    null,
                    "java/lang/Object",
                    null
                )
                writer.visitField(
                    Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC,
                    "INSTANCE",
                    "Ldev/karmakrafts/kcml/api/mixin/MixinBridge;",
                    null,
                    null
                ).visitEnd()
                writer.visitEnd()
                directory.resolve("bridge.jar").also { path ->
                    JarOutputStream(Files.newOutputStream(path)).use { output ->
                        output.putNextEntry(JarEntry("dev/karmakrafts/kcml/api/mixin/MixinBridge.class"))
                        output.write(writer.toByteArray())
                        output.closeEntry()
                    }
                }.toString()
            }
            else currentBridge
            ServerSocket(0, 1, InetAddress.getLoopbackAddress()).use { server ->
                server.soTimeout = 30_000
                val handshake = CompletableFuture.runAsync {
                    server.accept().use { socket ->
                        socket.soTimeout = 30_000
                        val reader = AgentPacketReader(socket.getInputStream())
                        val writer = AgentPacketWriter(socket.getOutputStream())
                        val handler = AgentPacketHandler(A2SHandshake.serializer()) { request ->
                            writer.write(
                                S2AHandshake.serializer(), S2AHandshake(
                                    request.signature,
                                    loader.toString(),
                                    if (replaceBridge) bridge else currentBridge,
                                    emptyList()
                                )
                            )
                        }
                        reader.read { type -> handler.takeIf { it.type == type } }
                    }
                }
                val agent = Path.of(checkNotNull(System.getProperty("kcml.agent.jar")))
                val log = directory.resolve("startup.log")
                val command = mutableListOf(
                    Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                    "-javaagent:$harness",
                    "-cp",
                    "$harness${File.pathSeparator}$agent",
                    AgentStartupProcess::class.java.name,
                    agent.toUri().toString(),
                    server.localPort.toString()
                )
                if (legacyBridge) command += listOf(bridge, currentBridge)
                if (!replaceBridge) command += "separate"
                val process = ProcessBuilder(command).redirectErrorStream(true).redirectOutput(log.toFile()).start()
                try {
                    assertTrue(process.waitFor(30, TimeUnit.SECONDS), "Agent startup timed out")
                    assertEquals(0, process.exitValue(), Files.readString(log))
                    handshake.get(10, TimeUnit.SECONDS)
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

    private fun createAgent(path: Path, version: String, property: String) {
        val writer = ClassWriter(0)
        writer.visit(
            Opcodes.V17, Opcodes.ACC_PUBLIC, "dev/karmakrafts/kcml/agent/KCMLAgent", null, "java/lang/Object", null
        )
        writer.visitMethod(
            Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC,
            "agentmain",
            "(Ljava/lang/String;Ljava/lang/instrument/Instrumentation;)V",
            null,
            null
        ).apply {
            visitCode()
            visitLdcInsn(property)
            visitLdcInsn(version)
            visitMethodInsn(
                Opcodes.INVOKESTATIC,
                "java/lang/System",
                "setProperty",
                "(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;",
                false
            )
            visitInsn(Opcodes.POP)
            visitLdcInsn("$property.options")
            visitVarInsn(Opcodes.ALOAD, 0)
            visitMethodInsn(
                Opcodes.INVOKESTATIC,
                "java/lang/System",
                "setProperty",
                "(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;",
                false
            )
            visitInsn(Opcodes.POP)
            visitInsn(Opcodes.RETURN)
            visitMaxs(2, 2)
            visitEnd()
        }
        writer.visitEnd()
        ZipFile(checkNotNull(System.getProperty("kcml.agent.jar"))).use { agent ->
            val manifest = agent.getInputStream(agent.getEntry("META-INF/MANIFEST.MF")).use(::Manifest)
            val entrypoint = manifest.mainAttributes.getValue("Agent-Class").replace('.', '/') + ".class"
            JarOutputStream(Files.newOutputStream(path), manifest).use { output ->
                output.putNextEntry(JarEntry("dev/karmakrafts/kcml/agent/KCMLAgent.class"))
                output.write(writer.toByteArray())
                output.closeEntry()
                if (entrypoint != "dev/karmakrafts/kcml/agent/KCMLAgent.class") {
                    output.putNextEntry(JarEntry(entrypoint))
                    agent.getInputStream(agent.getEntry(entrypoint)).use { it.copyTo(output) }
                    output.closeEntry()
                }
            }
        }
    }

    @Test
    fun `loads current agent even when an older implementation is already loaded`() {
        val directory = Files.createTempDirectory(Path.of("build"), "agent-bootstrap-test-").toAbsolutePath()
        val property = "kcml.agent.bootstrap.test.${directory.fileName}"
        try {
            val oldAgent = directory.resolve("old.jar")
            val currentAgent = directory.resolve("current agent.jar")
            val nextAgent = directory.resolve("next.jar")
            createAgent(oldAgent, "old", property)
            createAgent(currentAgent, "current", property)
            createAgent(nextAgent, "next", property)
            URLClassLoader(arrayOf(oldAgent.toUri().toURL()), ClassLoader.getPlatformClassLoader()).use { parent ->
                parent.loadClass("dev.karmakrafts.kcml.agent.KCMLAgent")
                    .getMethod("agentmain", String::class.java, Instrumentation::class.java)
                    .invoke(null, "", null)
                assertEquals("old", System.getProperty(property))
                URLClassLoader(arrayOf(currentAgent.toUri().toURL()), parent).use { loader ->
                    val entrypoint = ZipFile(currentAgent.toFile()).use { archive ->
                        archive.getInputStream(archive.getEntry("META-INF/MANIFEST.MF"))
                            .use(::Manifest).mainAttributes.getValue("Agent-Class")
                    }
                    val main = loader.loadClass(entrypoint)
                        .getMethod("agentmain", String::class.java, Instrumentation::class.java)
                    main.invoke(null, "${currentAgent.toUri()}\ncomm_port=1234", null)
                    assertEquals("current", System.getProperty(property))
                    assertEquals("comm_port=1234", System.getProperty("$property.options"))
                    main.invoke(null, "${nextAgent.toUri()}\ncomm_port=1234", null)
                    assertEquals("next", System.getProperty(property))
                }
            }
        }
        finally {
            System.clearProperty(property)
            System.clearProperty("$property.options")
            directory.toFile().deleteRecursively()
        }
    }

    @Test
    fun `rejects missing agent URI`() {
        for (options in listOf(null, "", "comm_port=1234", "\ncomm_port=1234")) {
            assertFailsWith<IllegalArgumentException> { KCMLAgentBootstrap.agentmain(options, null) }
        }
    }

    @Test
    fun `propagates implementation startup errors`() {
        val agent = Path.of(checkNotNull(System.getProperty("kcml.agent.jar")))
        val instrumentation = Proxy.newProxyInstance(
            javaClass.classLoader, arrayOf(Instrumentation::class.java)
        ) { _, _, _ -> error("No instrumentation calls expected") } as Instrumentation
        val error = assertFailsWith<IllegalArgumentException> {
            KCMLAgentBootstrap.agentmain("${agent.toUri()}\nlogging=false", instrumentation)
        }
        assertEquals("Could not get agent argument 'comm_port'", error.message)
    }
}