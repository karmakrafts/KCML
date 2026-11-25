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
import java.lang.instrument.Instrumentation
import java.lang.reflect.Proxy
import java.nio.file.Files
import java.nio.file.Path
import java.util.jar.JarFile
import java.util.jar.JarOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull

class KCMLAgentTest {
    @Test
    fun `injects mixin bridge into bootstrap classpath`() {
        val bridgePath = Files.createTempFile(Path.of("build"), "kcml-mixin-bridge", ".jar")
        val appendedJars = ArrayList<JarFile>()
        try {
            JarOutputStream(Files.newOutputStream(bridgePath)).use { }
            val instrumentation = Proxy.newProxyInstance(
                javaClass.classLoader, arrayOf(Instrumentation::class.java)
            ) { _, method, args ->
                check(method.name == "appendToBootstrapClassLoaderSearch") { "Unexpected call: ${method.name}" }
                appendedJars += args[0] as JarFile
                null
            } as Instrumentation

            KCMLAgent.injectMixinBridge(bridgePath.toString(), NoopLogger, instrumentation)

            assertEquals(bridgePath.toString(), appendedJars.single().name)
            assertNotNull(appendedJars.single().entries())
        }
        finally {
            appendedJars.forEach(JarFile::close)
            Files.delete(bridgePath)
        }
    }

    @Test
    fun `rejects missing startup options before accessing instrumentation`() {
        val instrumentation = Proxy.newProxyInstance(
            javaClass.classLoader, arrayOf(Instrumentation::class.java)
        ) { _, method, _ -> error("Unexpected call: ${method.name}") } as Instrumentation

        val error = assertFailsWith<IllegalArgumentException> { KCMLAgent.agentmain(null, instrumentation) }

        assertEquals("KCML compiler agent requires initial startup options", error.message)
    }
}