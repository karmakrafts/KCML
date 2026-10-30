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
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class KCMLPrepareNativeCacheTaskTest {
    private fun withTask(test: (KCMLPrepareNativeCacheTask, Path) -> Unit) {
        val directory = Files.createTempDirectory(Path.of("build"), "native-cache-test-").toAbsolutePath()
        try {
            val project = ProjectBuilder.builder().withProjectDir(directory.toFile()).build()
            val task = project.tasks.register("prepareNativeCache", KCMLPrepareNativeCacheTask::class.java).get()
            task.cacheDirectory.set(directory.resolve("caches/debugTest").toFile())
            task.markerFile.set(directory.resolve("markers/debugTest.marker").toFile())
            test(task, directory)
        }
        finally {
            directory.toFile().deleteRecursively()
        }
    }

    @Test
    fun `removes stale bitcode without touching other binaries`() = withTask { task, directory ->
        val stale = directory.resolve("caches/debugTest/module/bitcode")
        Files.createDirectories(stale.parent)
        Files.writeString(stale, "stale")
        val other = directory.resolve("caches/releaseTest/bitcode")
        Files.createDirectories(other.parent)
        Files.writeString(other, "unrelated")

        task.performAction()

        assertFalse(Files.exists(task.cacheDirectory.get().asFile.toPath()))
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
}