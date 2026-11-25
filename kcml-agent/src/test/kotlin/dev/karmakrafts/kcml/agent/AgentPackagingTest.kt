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

import org.objectweb.asm.ClassReader
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.LdcInsnNode
import java.lang.instrument.Instrumentation
import java.net.URLClassLoader
import java.nio.file.Files
import java.nio.file.Path
import java.util.jar.JarFile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class AgentPackagingTest {
    @Test
    fun `plain and shaded agents are distinct readable archives`() {
        val shaded = Path.of(checkNotNull(System.getProperty("kcml.agent.jar")))
        val plain = Path.of(checkNotNull(System.getProperty("kcml.agent.plain.jar")))

        assertFalse(Files.isSameFile(shaded, plain), "Plain JAR must not overwrite the self-contained agent")
        for (path in listOf(shaded, plain)) {
            JarFile(path.toFile()).use { jar ->
                assertNotNull(jar.getJarEntry("dev/karmakrafts/kcml/agent/KCMLAgent.class"))
                assertEquals(
                    path == shaded,
                    jar.getJarEntry("dev/karmakrafts/kcml/agent/internal/kotlin/Unit.class") != null
                )
            }
        }
    }

    @Test
    fun `shaded agent declares an available dynamic attach entrypoint`() {
        JarFile(checkNotNull(System.getProperty("kcml.agent.jar"))).use { jar ->
            val attributes = jar.manifest.mainAttributes
            val entrypoint = assertNotNull(attributes.getValue("Agent-Class"))
            assertNotNull(jar.getJarEntry("${entrypoint.replace('.', '/')}.class"))
            assertEquals("true", attributes.getValue("Can-Redefine-Classes"))
            assertEquals("true", attributes.getValue("Can-Retransform-Classes"))
        }
    }

    @Test
    fun `shaded agent loads independently of the compiler classpath`() {
        val shaded = Path.of(checkNotNull(System.getProperty("kcml.agent.jar")))
        URLClassLoader(arrayOf(shaded.toUri().toURL()), ClassLoader.getPlatformClassLoader()).use { loader ->
            val intrinsics = loader.loadClass("dev.karmakrafts.kcml.agent.internal.kotlin.jvm.internal.Intrinsics")
            assertEquals(loader, intrinsics.classLoader)
            val agent = Class.forName("dev.karmakrafts.kcml.agent.KCMLAgent", true, loader)
            assertEquals(loader, agent.classLoader)
            assertNotNull(agent.getMethod("agentmain", String::class.java, Instrumentation::class.java))
        }
    }

    @Test
    fun `preserves external type names in packaged agent constants`() {
        val classNode = ClassNode()
        JarFile(checkNotNull(System.getProperty("kcml.agent.jar"))).use { jar ->
            val entry = assertNotNull(jar.getJarEntry("dev/karmakrafts/kcml/agent/asm/Types.class"))
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