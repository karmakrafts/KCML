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

package dev.karmakrafts.kcml.plugin

import org.objectweb.asm.ClassWriter
import org.objectweb.asm.Opcodes
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.CompletionException
import java.util.concurrent.Executor
import java.util.concurrent.TimeUnit
import java.util.jar.JarEntry
import java.util.jar.JarOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PluginClassScannerTest {
    private val pluginAnnotation = "Ldev/karmakrafts/kcml/api/plugin/Plugin;"

    private fun writeJar(path: Path, vararg names: String) {
        JarOutputStream(Files.newOutputStream(path)).use { jar ->
            for (name in names) {
                val internal = name.replace('.', '/')
                jar.putNextEntry(JarEntry("$internal.class"))
                jar.write(classBytes(internal, Opcodes.ACC_PUBLIC, pluginAnnotation))
                jar.closeEntry()
            }
        }
    }

    /** Collects exactly [expected] tasks, then runs them on separate threads in reverse submission order. */
    private class ReversingExecutor(private val expected: Int) : Executor {
        private val tasks = ArrayList<Runnable>()
        val submitted get() = synchronized(tasks) { tasks.size }

        override fun execute(command: Runnable) {
            val ready = synchronized(tasks) {
                tasks += command
                if (tasks.size == expected) tasks.reversed() else null
            } ?: return
            Thread {
                for (task in ready) {
                    val thread = Thread(task)
                    thread.start()
                    thread.join()
                }
            }.start()
        }
    }

    private fun openJarFds(directory: Path): List<String> {
        val fds = Path.of("/proc/self/fd")
        if (!Files.isDirectory(fds)) return emptyList()
        val prefix = directory.toAbsolutePath().toString()
        return Files.list(fds).use { stream ->
            stream.map { runCatching { Files.readSymbolicLink(it).toString() }.getOrDefault("") }
                .filter { it.startsWith(prefix) && it.endsWith(".jar") }.toList()
        }
    }

    private fun <T> withTimeout(action: () -> T): T {
        val future = java.util.concurrent.CompletableFuture.supplyAsync(action)
        try {
            return future.get(10, TimeUnit.SECONDS)
        }
        catch (error: java.util.concurrent.ExecutionException) {
            throw error.cause ?: error
        }
    }

    @Test
    fun `executor scan preserves classpath order regardless of completion order and deduplicates`() {
        val directory = Files.createTempDirectory(Path.of("build"), "plugin-scan-order-")
        try {
            val a = directory.resolve("a.jar")
            val b = directory.resolve("b.jar")
            val c = directory.resolve("c.jar")
            writeJar(a, "p.A", "p.Shared")
            writeJar(b, "p.B", "p.Shared")
            writeJar(c, "p.C")
            val urls = listOf(a, b, c, a).map { it.toUri().toURL() }
            val serial = PluginClassScanner.scan(urls)
            assertEquals(listOf("p.A", "p.Shared", "p.B", "p.C"), serial)
            repeat(20) {
                val executor = ReversingExecutor(urls.size)
                val result = withTimeout { PluginClassScanner.scan(urls, executor) }
                assertEquals(serial, result)
                assertEquals(urls.size, executor.submitted)
            }
        }
        finally {
            directory.toFile().deleteRecursively()
        }
    }

    @Test
    fun `executor scan unwraps failures and closes all jars`() {
        val directory = Files.createTempDirectory(Path.of("build"), "plugin-scan-error-")
        try {
            val good = directory.resolve("good.jar")
            val good2 = directory.resolve("good2.jar")
            val bad = directory.resolve("bad.jar")
            writeJar(good, "p.Good")
            writeJar(good2, "p.Good2")
            Files.write(bad, byteArrayOf(1, 2, 3, 4))
            val urls = listOf(good, bad, good2).map { it.toUri().toURL() }
            val error = assertFailsWith<Throwable> {
                withTimeout { PluginClassScanner.scan(urls, ReversingExecutor(urls.size)) }
            }
            assertFalse(error is CompletionException, "Failure must be unwrapped but was $error")
            assertTrue(error is java.util.zip.ZipException, "Unexpected failure $error")
            assertEquals(emptyList(), openJarFds(directory))
        }
        finally {
            directory.toFile().deleteRecursively()
        }
    }

    @Test
    fun `null executor behaves like serial scan`() {
        val directory = Files.createTempDirectory(Path.of("build"), "plugin-scan-null-")
        try {
            val jar = directory.resolve("x.jar")
            writeJar(jar, "p.X")
            assertEquals(listOf("p.X"), PluginClassScanner.scan(listOf(jar.toUri().toURL()), null))
        }
        finally {
            directory.toFile().deleteRecursively()
        }
    }

    private fun classBytes(name: String, access: Int, annotation: String?, visible: Boolean = true): ByteArray {
        val writer = ClassWriter(0)
        writer.visit(Opcodes.V21, access, name, null, "java/lang/Object", null)
        annotation?.let { writer.visitAnnotation(it, visible).visitEnd() }
        writer.visitEnd()
        return writer.toByteArray()
    }

    @Test
    fun `scans class directories and jars without loading unrelated classes`() {
        val directory = Files.createTempDirectory(Path.of("build"), "plugin-scan-")
        try {
            val classes = linkedMapOf(
                "sample/Plugin.class" to classBytes(
                    "sample/Plugin", Opcodes.ACC_PUBLIC, "Ldev/karmakrafts/kcml/api/plugin/Plugin;"
                ), "sample/AbstractPlugin.class" to classBytes(
                    "sample/AbstractPlugin",
                    Opcodes.ACC_PUBLIC or Opcodes.ACC_ABSTRACT,
                    "Ldev/karmakrafts/kcml/api/plugin/Plugin;"
                ), "sample/PluginInterface.class" to classBytes(
                    "sample/PluginInterface",
                    Opcodes.ACC_PUBLIC or Opcodes.ACC_ABSTRACT or Opcodes.ACC_INTERFACE,
                    "Ldev/karmakrafts/kcml/api/plugin/Plugin;"
                ), "sample/OtherAnnotation.class" to classBytes(
                    "sample/OtherAnnotation", Opcodes.ACC_PUBLIC, "Ldev/karmakrafts/kcml/api/plugin/PluginExtra;"
                ), "sample/InvisiblePlugin.class" to classBytes(
                    "sample/InvisiblePlugin",
                    Opcodes.ACC_PUBLIC,
                    "Ldev/karmakrafts/kcml/api/plugin/Plugin;",
                    visible = false
                ), "sample/UnlinkablePlugin.class" to ClassWriter(0).run {
                    visit(Opcodes.V21, Opcodes.ACC_PUBLIC, "sample/UnlinkablePlugin", null, "missing/Parent", null)
                    visitAnnotation("Ldev/karmakrafts/kcml/api/plugin/Plugin;", true).visitEnd()
                    visitEnd()
                    toByteArray()
                })
            for ((entry, bytes) in classes) {
                val classFile = directory.resolve(entry)
                Files.createDirectories(classFile.parent)
                Files.write(classFile, bytes)
            }
            val archive = directory.resolve("plugins.jar")
            JarOutputStream(Files.newOutputStream(archive)).use { jar ->
                for ((entry, bytes) in classes) {
                    jar.putNextEntry(JarEntry(entry))
                    jar.write(bytes)
                    jar.closeEntry()
                }
            }

            val candidates = PluginClassScanner.scan(
                listOf(directory.toUri().toURL(), directory.toUri().toURL(), archive.toUri().toURL())
            )

            assertEquals(setOf("sample.Plugin", "sample.UnlinkablePlugin"), candidates.toSet())
            assertEquals(2, candidates.size)
        }
        finally {
            directory.toFile().deleteRecursively()
        }
    }
}