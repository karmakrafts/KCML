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

import com.sun.tools.attach.AgentInitializationException
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.deleteRecursively
import kotlin.io.path.div
import kotlin.io.path.exists
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

@OptIn(ExperimentalPathApi::class)
class AgentInjectorTest {
    @Test
    fun `holds the shared file lock until agent loading completes`() {
        val directory = Files.createTempDirectory(Path.of("build"), "agent-injector-test-").toAbsolutePath()
        val executor = Executors.newFixedThreadPool(2)
        try {
            val injection = executor.submit<Boolean> {
                AgentInjector(directory).inject(mapOf("directory" to directory))
            }
            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
            while (!(directory / "started").exists() && !injection.isDone && System.nanoTime() < deadline) {
                Thread.sleep(10)
            }
            assertTrue((directory / "started").exists(), "Agent did not start; completed=${injection.isDone}")
            val started = CountDownLatch(1)
            val pending = executor.submit<String> {
                FileLock(directory / "agent.lock").use { lock ->
                    started.countDown()
                    lock.withLock { "acquired" }
                }
            }
            assertTrue(started.await(5, TimeUnit.SECONDS))
            assertFailsWith<TimeoutException> { pending.get(100, TimeUnit.MILLISECONDS) }
            Files.createFile(directory / "release")
            assertTrue(injection.get(5, TimeUnit.SECONDS))
            assertEquals("acquired", pending.get(5, TimeUnit.SECONDS))
        } finally {
            if (!(directory / "release").exists()) Files.createFile(directory / "release")
            executor.shutdown()
            assertTrue(executor.awaitTermination(15, TimeUnit.SECONDS))
            directory.deleteRecursively()
        }
    }

    @Test
    fun `supports concurrent injections from separate injector instances`() {
        val directory = Files.createTempDirectory(Path.of("build"), "agent-injector-test-")
        val executor = Executors.newFixedThreadPool(12)
        try {
            val start = CountDownLatch(1)
            val injections = (1..12).map {
                executor.submit<Boolean> {
                    start.await()
                    AgentInjector(directory).inject()
                }
            }
            start.countDown()
            injections.forEach { assertTrue(it.get(15, TimeUnit.SECONDS)) }
        } finally {
            executor.shutdown()
            assertTrue(executor.awaitTermination(15, TimeUnit.SECONDS))
            directory.deleteRecursively()
        }
    }

    @Test
    fun `propagates agent load failures and releases the lock for subsequent injections`() {
        val directory = Files.createTempDirectory(Path.of("build"), "agent-injector-test-")
        try {
            assertFailsWith<AgentInitializationException> {
                AgentInjector(directory).inject(mapOf("fail" to true))
            }
            assertTrue(AgentInjector(directory).inject())
        } finally {
            directory.deleteRecursively()
        }
    }
}