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

package dev.karmakrafts.kcml.log

import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSeverity
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSourceLocation
import org.jetbrains.kotlin.cli.common.messages.GroupingMessageCollector
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MessageCollectorLoggerTest {
    private class RecordingCollector(private val requireLock: Boolean = false) : MessageCollector {
        val messages = ArrayList<Pair<CompilerMessageSeverity, String>>()

        override fun clear() = messages.clear()

        override fun hasErrors(): Boolean = messages.any { it.first.isError }

        override fun report(
            severity: CompilerMessageSeverity,
            message: String,
            location: CompilerMessageSourceLocation?
        ) {
            if (requireLock) assertTrue(Thread.holdsLock(this), "Loggers must lock their shared collector")
            messages += severity to message
        }
    }

    private fun reportConcurrently(collector: MessageCollector) {
        val start = CountDownLatch(1)
        val executor = Executors.newFixedThreadPool(8) { task -> Thread(task).apply { isDaemon = true } }
        try {
            val reports = (0 until 8).map { worker ->
                executor.submit {
                    val logger = MessageCollectorLogger(collector, "worker-$worker")
                    check(start.await(5, TimeUnit.SECONDS))
                    repeat(250) { message ->
                        logger.debug("debug-$message")
                        logger.info("info-$message")
                        logger.warn("warn-$message")
                        logger.error("error-$message")
                    }
                }
            }
            start.countDown()
            reports.forEach { it.get(10, TimeUnit.SECONDS) }
        }
        finally {
            executor.shutdownNow()
        }
    }

    @Test
    fun `loggers preserve severity and name prefixes`() {
        val collector = RecordingCollector()
        val logger = MessageCollectorLogger(collector, "example")
        logger.debug("debug")
        logger.info("info")
        logger.warn("warn")
        logger.error("error")
        assertEquals(
            listOf(
                CompilerMessageSeverity.LOGGING to "[example] debug",
                CompilerMessageSeverity.INFO to "[example] info",
                CompilerMessageSeverity.WARNING to "[example] warn",
                CompilerMessageSeverity.ERROR to "[example] error"
            ),
            collector.messages
        )
    }

    @Test
    fun `loggers sharing a collector serialize all severity reports`() {
        val collector = RecordingCollector(requireLock = true)
        reportConcurrently(collector)
        assertEquals(8_000, collector.messages.size)
        assertEquals(8_000, collector.messages.distinct().size)
    }

    @Test
    fun `parallel backend logging does not corrupt grouped compiler messages`() {
        val delegate = RecordingCollector()
        val collector = GroupingMessageCollector(delegate, false, true)
        reportConcurrently(collector)
        assertTrue(collector.hasErrors())
        collector.flush()
        assertEquals(8_000, delegate.messages.size)
        assertEquals(8_000, delegate.messages.distinct().size)
    }
}