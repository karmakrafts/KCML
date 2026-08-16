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

import dev.karmakrafts.kcml.agent.log.NoopLogger
import org.objectweb.asm.ClassReader
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.LdcInsnNode
import java.lang.instrument.Instrumentation
import java.lang.reflect.Proxy
import java.nio.file.Files
import java.util.jar.JarOutputStream
import java.util.zip.ZipFile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class KCMLAgentTest {
    @Test
    fun `injects mixin bridge into bootstrap classpath`() {
        val bridgePath = Files.createTempFile("kcml-mixin-bridge", ".jar")
        JarOutputStream(Files.newOutputStream(bridgePath)).use { }
        val calls = ArrayList<String>()
        val instrumentation = Proxy.newProxyInstance(
            javaClass.classLoader, arrayOf(Instrumentation::class.java)
        ) { _, method, _ ->
            calls += method.name
            null
        } as Instrumentation

        KCMLAgent.injectMixinBridge(bridgePath.toString(), NoopLogger, instrumentation)

        assertEquals(listOf("appendToBootstrapClassLoaderSearch"), calls)
    }

    @Test
    fun `preserves external type names in packaged agent constants`() {
        val agentPath = checkNotNull(System.getProperty("kcml.agent.jar"))
        val classNode = ClassNode()
        ZipFile(agentPath).use { jar ->
            val entry = checkNotNull(jar.getEntry("dev/karmakrafts/kcml/agent/asm/Types.class"))
            jar.getInputStream(entry).use { stream -> ClassReader(stream).accept(classNode, 0) }
        }
        val constants = classNode.methods.asSequence()
            .flatMap { method -> method.instructions.asSequence() }
            .filterIsInstance<LdcInsnNode>()
            .mapNotNull { instruction -> instruction.cst as? String }
            .toSet()

        assertTrue("kotlin/Unit" in constants)
        assertTrue("kotlin/jvm/internal/Intrinsics" in constants)
        assertFalse(constants.any { constant -> constant.contains("agent/internal/kotlin") })
    }
}