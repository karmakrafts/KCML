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

package dev.karmakrafts.kcml.intellij.syntax.runtime

import com.intellij.openapi.project.Project
import dev.karmakrafts.kcml.internal.org.objectweb.asm.ClassWriter
import dev.karmakrafts.kcml.internal.org.objectweb.asm.Opcodes
import junit.framework.TestCase
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.jar.JarEntry
import java.util.jar.JarOutputStream

class SyntaxPluginClassLoaderTest : TestCase() {
    private lateinit var directory: Path

    override fun setUp() {
        super.setUp()
        directory = Files.createTempDirectory(Path.of("build"), "syntax-loader-")
    }

    override fun tearDown() {
        try {
            directory.toFile().deleteRecursively()
        }
        finally {
            super.tearDown()
        }
    }

    fun testParallelClassLoadingPreservesIdentityAndRemapsIdeTypes() {
        val jar = directory.resolve("plugin.jar")
        writeJar(jar, "first")
        SyntaxPluginClassLoader(arrayOf(jar.toUri().toURL()), javaClass.classLoader).use { loader ->
            assertTrue(
                "Instrumentation must allow independent classes to load concurrently",
                loader.isRegisteredAsParallelCapable
            )
            val executor = Executors.newFixedThreadPool(4)
            try {
                val classes = executor.invokeAll((0 until 16).map { index ->
                    Callable { loader.loadClass("fixture.Plugin${index % 2}") }
                }, 10, TimeUnit.SECONDS).map { it.get() }
                for ([index, type] in classes.withIndex()) {
                    assertSame(classes[index % 2], type)
                    assertSame(Project::class.java, type.getField("project").type)
                    assertEquals("first", type.getField("version").get(null))
                }
            }
            finally {
                executor.shutdownNow()
            }
        }
    }

    fun testNewLoaderSeesReplacedJarAndClosedLoaderCannotLoadNewClasses() {
        val jar = directory.resolve("plugin.jar")
        writeJar(jar, "first")
        val loader = SyntaxPluginClassLoader(arrayOf(jar.toUri().toURL()), javaClass.classLoader)
        loader.use {
            assertEquals("first", it.loadClass("fixture.Plugin0").getField("version").get(null))
        }
        try {
            loader.loadClass("fixture.Plugin1")
            fail("Closed loaders must not read plugin archives")
        } catch (_: ClassNotFoundException) {
        }
        writeJar(jar, "other")
        SyntaxPluginClassLoader(arrayOf(jar.toUri().toURL()), javaClass.classLoader).use {
            assertEquals("other", it.loadClass("fixture.Plugin0").getField("version").get(null))
        }
    }

    fun testClassDirectoryIsRemapped() {
        val file = directory.resolve("fixture/Plugin0.class")
        Files.createDirectories(file.parent)
        Files.write(file, classBytes("fixture/Plugin0", "directory"))
        SyntaxPluginClassLoader(arrayOf(directory.toUri().toURL()), javaClass.classLoader).use {
            val type = it.loadClass("fixture.Plugin0")
            assertSame(Project::class.java, type.getField("project").type)
            assertEquals("directory", type.getField("version").get(null))
        }
    }

    private fun writeJar(path: Path, version: String) {
        JarOutputStream(Files.newOutputStream(path)).use { output ->
            repeat(2) { index ->
                val name = "fixture/Plugin$index"
                output.putNextEntry(JarEntry("$name.class"))
                output.write(classBytes(name, version))
                output.closeEntry()
            }
        }
    }

    private fun classBytes(name: String, version: String): ByteArray = ClassWriter(0).apply {
        visit(Opcodes.V21, Opcodes.ACC_PUBLIC, name, null, "java/lang/Object", null)
        visitField(
            Opcodes.ACC_PUBLIC, "project", "Lorg/jetbrains/kotlin/com/intellij/openapi/project/Project;", null, null
        ).visitEnd()
        visitField(
            Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC or Opcodes.ACC_FINAL,
            "version",
            "Ljava/lang/String;",
            null,
            version
        ).visitEnd()
        visitEnd()
    }.toByteArray()
}