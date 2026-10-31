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

import dev.karmakrafts.kcml.agent.asm.Types
import dev.karmakrafts.kcml.agent.proto.A2SHandshake
import dev.karmakrafts.kcml.agent.proto.S2AHandshake
import dev.karmakrafts.kcml.agent.proto.io.AgentPacketHandler
import dev.karmakrafts.kcml.agent.proto.io.AgentPacketReader
import dev.karmakrafts.kcml.agent.proto.io.AgentPacketWriter
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type
import java.lang.instrument.ClassFileTransformer
import java.lang.instrument.Instrumentation
import java.lang.reflect.Proxy
import java.net.InetAddress
import java.net.ServerSocket
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import java.util.jar.JarEntry
import java.util.jar.JarFile
import java.util.jar.JarOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class KCMLAgentMixinTest {
    private fun createClass(name: String, body: ClassWriter.() -> Unit = {}): ByteArray = ClassWriter(0).apply {
        visit(Opcodes.V17, Opcodes.ACC_PUBLIC, name, null, "java/lang/Object", null)
        body()
        visitEnd()
    }.toByteArray()

    private fun createMixin(name: String, target: String, direct: Boolean): ByteArray = createClass(name) {
        val annotationType = if (direct) Types.Mixin.directMixin else Types.Mixin.indirectMixin
        visitAnnotation(annotationType.descriptor, false).apply {
            visit("target", if (direct) Type.getObjectType(target) else target.replace('/', '.'))
            visitEnd()
        }
        visitMethod(Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC, "inject", "()V", null, null).apply {
            visitAnnotation(Types.Mixin.inject.descriptor, false).apply {
                visit("name", "value")
                visitEnum("order", Types.Mixin.order.descriptor, "BEFORE")
                visitAnnotation("target", "Ldev/karmakrafts/kcml/api/mixin/Inject\$Target;").apply {
                    visit("opcode", Opcodes.GETSTATIC)
                    visitEnd()
                }
                visitEnd()
            }
            visitCode()
            visitIntInsn(Opcodes.BIPUSH, 42)
            visitFieldInsn(Opcodes.PUTSTATIC, target, "result", "I")
            visitInsn(Opcodes.RETURN)
            visitMaxs(1, 0)
            visitEnd()
        }
    }

    private fun Path.createJar(name: String, vararg classes: ByteArray): Path = resolve(name).also { path ->
        JarOutputStream(Files.newOutputStream(path)).use { output ->
            for (bytes in classes) {
                output.putNextEntry(JarEntry("${ClassReader(bytes).className}.class"))
                output.write(bytes)
                output.closeEntry()
            }
        }
    }

    private fun Path.createBridge(): Path = resolve("bridge.jar").also { path ->
        Files.copy(Path.of(checkNotNull(System.getProperty("kcml.bridge.jar"))), path)
    }

    private fun withDirectory(test: (Path) -> Unit) {
        val directory = Files.createTempDirectory(Path.of("build"), "agent-mixin-test-")
        try {
            test(directory)
        }
        finally {
            directory.toFile().deleteRecursively()
        }
    }

    private fun startAgent(
        loader: Path,
        bridge: Path,
        plugins: List<Path>,
        loadedClasses: Array<Class<*>> = emptyArray(),
        retransformed: MutableList<Class<*>> = ArrayList()
    ): ClassFileTransformer {
        var transformer: ClassFileTransformer? = null
        val instrumentation = Proxy.newProxyInstance(
            javaClass.classLoader, arrayOf(Instrumentation::class.java)
        ) { _, method, args ->
            when (method.name) {
                "appendToBootstrapClassLoaderSearch" -> (args[0] as JarFile).close()
                "addTransformer" -> transformer = args[0] as ClassFileTransformer
                "isRetransformClassesSupported", "isModifiableClass" -> return@newProxyInstance true
                "getAllLoadedClasses" -> return@newProxyInstance loadedClasses
                "retransformClasses" -> retransformed += (args[0] as Array<*>).filterIsInstance<Class<*>>()
            }
            null
        } as Instrumentation
        ServerSocket(0, 1, InetAddress.getLoopbackAddress()).use { server ->
            server.soTimeout = 10_000
            val startup = CompletableFuture.runAsync {
                KCMLAgent.agentmain("comm_port=${server.localPort}:logging=false", instrumentation)
            }
            server.accept().use { socket ->
                socket.soTimeout = 10_000
                val reader = AgentPacketReader(socket.getInputStream())
                val writer = AgentPacketWriter(socket.getOutputStream())
                val handler = AgentPacketHandler(A2SHandshake.serializer()) { request ->
                    writer.write(
                        S2AHandshake.serializer(), S2AHandshake(
                            request.signature, loader.toString(), bridge.toString(), plugins.map(Path::toString)
                        )
                    )
                }
                reader.read { type -> handler.takeIf { it.type == type } }
                startup.get(10, TimeUnit.SECONDS)
            }
        }
        return assertNotNull(transformer)
    }

    private fun assertMixinApplied(transformer: ClassFileTransformer, name: String) {
        val target = createClass(name) {
            visitField(Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC, "result", "I", null, null).visitEnd()
            visitMethod(Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC, "value", "()I", null, null).apply {
                visitCode()
                visitFieldInsn(Opcodes.GETSTATIC, name, "result", "I")
                visitInsn(Opcodes.IRETURN)
                visitMaxs(1, 0)
                visitEnd()
            }
        }
        val transformed = assertNotNull(transformer.transform(null, null, name, null, null, target))
        val targetClass = object : ClassLoader(javaClass.classLoader) {
            fun define(): Class<*> = defineClass(name.replace('/', '.'), transformed, 0, transformed.size)
        }.define()
        assertEquals(42, targetClass.getMethod("value").invoke(null), "Mixin was not applied to $name")
    }

    @Test
    fun `agent discovers and applies loader and plugin mixins`() = withDirectory { directory ->
        val loader = directory.createJar("loader.jar", createMixin("example/LoaderMixin", "example/LoaderTarget", true))
        val direct = directory.createJar("direct.jar", createMixin("example/DirectMixin", "example/DirectTarget", true))
        val indirect = directory.createJar(
            "indirect.jar", createMixin("example/IndirectMixin", "example/IndirectTarget", false)
        )
        val transformer = startAgent(loader, directory.createBridge(), listOf(direct, indirect))

        for (target in listOf("example/LoaderTarget", "example/DirectTarget", "example/IndirectTarget")) {
            assertMixinApplied(transformer, target)
        }
    }

    @Test
    fun `agent retains loader mixins without plugins or with unsupported plugin paths`() = withDirectory { directory ->
        val loader = directory.createJar("loader.jar", createMixin("example/LoaderMixin", "example/LoaderTarget", true))
        val bridge = directory.createBridge()
        val ordinary = directory.createJar("ordinary.jar", createClass("example/Ordinary"))

        for (plugins in listOf(emptyList(), listOf(directory, directory.resolve("missing.jar"), ordinary))) {
            assertMixinApplied(startAgent(loader, bridge, plugins), "example/LoaderTarget")
        }
    }

    @Test
    fun `agent patches mixin targets loaded before attachment`() = withDirectory { directory ->
        val target = KCMLAgentMixinTest::class.java
        val loader = directory.createJar(
            "loader.jar", createMixin("example/LoadedMixin", target.name.replace('.', '/'), true)
        )
        val retransformed = ArrayList<Class<*>>()
        startAgent(
            loader, directory.createBridge(), emptyList(), arrayOf(String::class.java, target), retransformed
        )
        assertEquals(listOf<Class<*>>(target), retransformed)
    }
}