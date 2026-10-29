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

package dev.karmakrafts.kcml.util

import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class FileLockTest {
    @Test
    fun `waits for overlapping locks in the same JVM`() {
        val path = Files.createTempFile(Path.of("build"), "bootstrap-lock-test-", ".lock")
        val executor = Executors.newSingleThreadExecutor()
        try {
            FileLock(path).use { first ->
                FileLock(path).use { second ->
                    val started = CountDownLatch(1)
                    val pending = first.withLock {
                        val future = executor.submit<String> {
                            started.countDown()
                            second.withLock { "acquired" }
                        }
                        assertTrue(started.await(5, TimeUnit.SECONDS))
                        assertFailsWith<TimeoutException> { future.get(100, TimeUnit.MILLISECONDS) }
                        future
                    }
                    assertEquals("acquired", pending.get(5, TimeUnit.SECONDS))
                }
            }
        } finally {
            executor.shutdownNow()
            executor.awaitTermination(5, TimeUnit.SECONDS)
            Files.deleteIfExists(path)
        }
    }

    @Test
    fun `releases lock after a failed operation`() {
        val path = Files.createTempFile(Path.of("build"), "bootstrap-lock-test-", ".lock")
        try {
            FileLock(path).use { lock ->
                assertFailsWith<IllegalStateException> { lock.withLock { error("failed") } }
            }
            FileLock(path).use { lock -> assertEquals("acquired", lock.withLock { "acquired" }) }
        } finally {
            Files.deleteIfExists(path)
        }
    }
}