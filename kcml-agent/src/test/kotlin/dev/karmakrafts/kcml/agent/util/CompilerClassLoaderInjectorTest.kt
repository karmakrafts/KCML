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

package dev.karmakrafts.kcml.agent.util

import java.io.IOException
import java.lang.instrument.Instrumentation
import java.lang.reflect.Proxy
import java.nio.file.Path
import java.util.jar.JarFile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class CompilerClassLoaderInjectorTest {
    private fun instrumentation(append: (JarFile) -> Unit): Instrumentation = Proxy.newProxyInstance(
        javaClass.classLoader, arrayOf(Instrumentation::class.java)
    ) { _, method, args ->
        when (method.name) {
            "appendToSystemClassLoaderSearch" -> (args[0] as JarFile).use(append)
            else -> error("Unexpected instrumentation call: ${method.name}")
        }
        null
    } as Instrumentation

    @Test
    fun `injects plugin loader into application loader only once`() {
        val path = Path.of(checkNotNull(System.getProperty("kcml.loader.jar")))
        var injections = 0
        val injector = CompilerClassLoaderInjector(path, instrumentation { jar ->
            assertEquals(path.toString(), jar.name)
            injections++
        })

        repeat(2) { injector.accept(ClassLoader.getSystemClassLoader()) }

        assertEquals(1, injections)
    }

    @Test
    fun `retries failed application loader injection`() {
        val path = Path.of(checkNotNull(System.getProperty("kcml.loader.jar")))
        var attempts = 0
        val injector = CompilerClassLoaderInjector(path, instrumentation {
            if (++attempts == 1) throw IllegalStateException("Injection failed")
        })

        assertFailsWith<IllegalStateException> { injector.accept(ClassLoader.getSystemClassLoader()) }
        injector.accept(ClassLoader.getSystemClassLoader())

        assertEquals(2, attempts)
    }

    @Test
    fun `rejects missing plugin loader jar`() {
        val injector = CompilerClassLoaderInjector(Path.of("build", "missing-loader.jar"), instrumentation {
            error("Must not inject a missing JAR")
        })

        assertFailsWith<IOException> { injector.accept(ClassLoader.getSystemClassLoader()) }
    }

    @Test
    fun `reports unsupported compiler class loader`() {
        val loader = object : ClassLoader(getPlatformClassLoader()) {}
        val injector = CompilerClassLoaderInjector(Path.of("unused.jar"), instrumentation {
            error("Must not inject into the application class loader")
        })

        val error = assertFailsWith<IllegalStateException> { injector.accept(loader) }

        assertTrue(error.message.orEmpty().contains(loader.javaClass.name))
    }
}