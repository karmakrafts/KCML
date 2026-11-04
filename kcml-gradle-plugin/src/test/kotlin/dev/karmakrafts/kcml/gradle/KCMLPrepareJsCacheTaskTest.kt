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
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class KCMLPrepareJsCacheTaskTest {
    private fun withTask(test: (KCMLPrepareJsCacheTask, Path) -> Unit) {
        val directory = Files.createTempDirectory(Path.of("build"), "js-cache-test-").toAbsolutePath()
        try {
            val project = ProjectBuilder.builder().withProjectDir(directory.toFile()).build()
            val task = project.tasks.register("prepareJsCache", KCMLPrepareJsCacheTask::class.java).get()
            task.cacheDirectory.set(directory.resolve("caches/testDevelopmentExecutable").toFile())
            task.markerFile.set(directory.resolve("markers/testDevelopmentExecutable.marker").toFile())
            test(task, directory)
        }
        finally {
            directory.toFile().deleteRecursively()
        }
    }

    @Test
    fun `removes stale ASTs without touching other executables`() = withTask { task, directory ->
        val stale = directory.resolve("caches/testDevelopmentExecutable/module/ast.bin")
        Files.createDirectories(stale.parent)
        Files.writeString(stale, "stale")
        val other = directory.resolve("caches/otherDevelopmentExecutable/ast.bin")
        Files.createDirectories(other.parent)
        Files.writeString(other, "unrelated")

        task.performAction()

        assertFalse(task.cacheDirectory.get().asFile.exists())
        assertEquals("unrelated", Files.readString(other))
        assertTrue(task.markerFile.get().asFile.isFile)
    }

    @Test
    fun `prepares missing caches and supports repeated invalidation`() = withTask { task, _ ->
        repeat(2) {
            task.performAction()

            assertFalse(task.cacheDirectory.get().asFile.exists())
            assertTrue(task.markerFile.get().asFile.isFile)
        }
    }

    @Test
    fun `Gradle preserves caches until plugin or runtime contents change`() = withTask { _, directory ->
        val plugin = directory.resolve("plugin.jar")
        val runtime = directory.resolve("runtime.jar")
        fun writeJar(path: Path, value: String) {
            ZipOutputStream(Files.newOutputStream(path)).use { output ->
                output.putNextEntry(ZipEntry("extension.txt"))
                output.write(value.toByteArray())
                output.closeEntry()
            }
        }
        writeJar(plugin, "plugin v1")
        writeJar(runtime, "runtime v1")
        Files.writeString(directory.resolve("settings.gradle"), "rootProject.name = 'js-cache-test'\n")
        val taskClasses = Path.of(KCMLPrepareJsCacheTask::class.java.protectionDomain.codeSource.location.toURI())
        Files.writeString(
            directory.resolve("build.gradle"), """
            buildscript {
                dependencies { classpath files('${taskClasses.toFile().invariantSeparatorsPath}') }
            }
            tasks.register('prepareJsCache', dev.karmakrafts.kcml.gradle.KCMLPrepareJsCacheTask) {
                pluginClasspath.from(files('plugin.jar', 'runtime.jar'))
                cacheDirectory.set(layout.projectDirectory.dir('cache'))
                markerFile.set(layout.buildDirectory.file('cache.marker'))
            }
        """.trimIndent()
        )
        val runner = GradleRunner.create()
            .withProjectDir(directory.toFile())
            .withTestKitDir(directory.resolve("testkit").toFile())
            .withArguments("prepareJsCache", "--configuration-cache", "--console=plain")
        val cachedAst = directory.resolve("cache/module/ast.bin")
        fun populateCache() {
            Files.createDirectories(cachedAst.parent)
            Files.writeString(cachedAst, "cached AST")
        }

        assertEquals(TaskOutcome.SUCCESS, runner.build().task(":prepareJsCache")?.outcome)
        populateCache()
        assertEquals(TaskOutcome.UP_TO_DATE, runner.build().task(":prepareJsCache")?.outcome)
        assertEquals("cached AST", Files.readString(cachedAst))

        writeJar(plugin, "plugin v2")
        assertEquals(TaskOutcome.SUCCESS, runner.build().task(":prepareJsCache")?.outcome)
        assertFalse(Files.exists(cachedAst))

        populateCache()
        writeJar(runtime, "runtime v2")
        assertEquals(TaskOutcome.SUCCESS, runner.build().task(":prepareJsCache")?.outcome)
        assertFalse(Files.exists(cachedAst))
    }
}