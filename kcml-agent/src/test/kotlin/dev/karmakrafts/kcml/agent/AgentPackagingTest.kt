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

import java.net.URLClassLoader
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class AgentPackagingTest {
    @Test
    fun `plain and shaded agents have distinct outputs`() {
        val shaded = Path.of(checkNotNull(System.getProperty("kcml.agent.jar")))
        val plain = Path.of(checkNotNull(System.getProperty("kcml.agent.plain.jar")))

        assertNotEquals(shaded, plain, "Plain JAR must not overwrite the self-contained agent")
    }

    @Test
    fun `shaded agent loads independently of the compiler classpath`() {
        val shaded = Path.of(checkNotNull(System.getProperty("kcml.agent.jar")))
        URLClassLoader(arrayOf(shaded.toUri().toURL()), ClassLoader.getPlatformClassLoader()).use { loader ->
            assertNotNull(loader.loadClass("dev.karmakrafts.kcml.agent.internal.kotlin.jvm.internal.Intrinsics"))
            val agent = Class.forName("dev.karmakrafts.kcml.agent.KCMLAgent", true, loader)
            assertTrue(agent.declaredMethods.any { method -> method.name == "agentmain" })
        }
    }
}