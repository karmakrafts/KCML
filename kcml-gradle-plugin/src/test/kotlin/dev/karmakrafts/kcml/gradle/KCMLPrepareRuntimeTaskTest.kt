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

package dev.karmakrafts.kcml.gradle

import org.gradle.testfixtures.ProjectBuilder
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class KCMLPrepareRuntimeTaskTest {
    private fun withTask(test: (KCMLPrepareRuntimeTask, Path) -> Unit) {
        val directory = Files.createTempDirectory(Path.of("build"), "runtime-test-").toAbsolutePath()
        try {
            val project = ProjectBuilder.builder().withProjectDir(directory.toFile()).build()
            val task = project.tasks.register("prepareRuntime", KCMLPrepareRuntimeTask::class.java).get()
            task.outputDirectory.set(directory.resolve("runtime").toFile())
            test(task, directory)
        }
        finally {
            directory.toFile().deleteRecursively()
        }
    }

    private fun writeArchive(path: Path, entries: Map<String, ByteArray>) {
        ZipOutputStream(Files.newOutputStream(path)).use { archive ->
            for ((name, bytes) in entries) {
                archive.putNextEntry(ZipEntry(name))
                archive.write(bytes)
                archive.closeEntry()
            }
        }
    }

    @Test
    fun `extracts runtime artifacts and refreshes existing contents`() = withTask { task, directory ->
        val archive = directory.resolve("compiler.jar")
        task.compilerClasspath.from(archive.toFile())
        repeat(2) { generation ->
            val loader = "loader-$generation".toByteArray()
            val bridge = "bridge-$generation".toByteArray()
            writeArchive(archive, mapOf("kcml-loader.jar" to loader, "kcml-mixin-bridge.jar" to bridge))

            task.performAction()

            assertContentEquals(loader, Files.readAllBytes(directory.resolve("runtime/kcml-loader.jar")))
            assertContentEquals(bridge, Files.readAllBytes(directory.resolve("runtime/kcml-mixin-bridge.jar")))
        }
    }

    @Test
    fun `rejects compiler artifacts without the runtime bridge`() = withTask { task, directory ->
        val archive = directory.resolve("compiler.jar")
        writeArchive(archive, mapOf("kcml-loader.jar" to byteArrayOf(1)))
        task.compilerClasspath.from(archive.toFile())

        val error = assertFailsWith<IllegalArgumentException> { task.performAction() }

        assertTrue(error.message.orEmpty().contains("kcml-mixin-bridge.jar"))
    }
}