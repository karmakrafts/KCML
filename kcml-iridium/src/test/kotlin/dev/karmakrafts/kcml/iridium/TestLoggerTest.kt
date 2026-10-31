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

package dev.karmakrafts.kcml.iridium

import java.io.ByteArrayOutputStream
import java.io.PrintStream
import kotlin.test.Test
import kotlin.test.assertEquals

class TestLoggerTest {
    private fun captureOutput(block: () -> Unit): Pair<String, String> {
        val stdout = System.out
        val stderr = System.err
        val output = ByteArrayOutputStream()
        val errors = ByteArrayOutputStream()
        try {
            PrintStream(output, true, Charsets.UTF_8).use { out ->
                PrintStream(errors, true, Charsets.UTF_8).use { err ->
                    System.setOut(out)
                    System.setErr(err)
                    block()
                }
            }
        }
        finally {
            System.setOut(stdout)
            System.setErr(stderr)
        }
        return output.toString(Charsets.UTF_8) to errors.toString(Charsets.UTF_8)
    }

    @Test
    fun `Debug info and warning messages go to stdout with level and logger name`() {
        val logger = TestLogger("test.plugin")
        assertEquals("test.plugin", logger.name)
        val (output, errors) = captureOutput {
            logger.debug("debug message")
            logger.info("info message")
            logger.warn("warning message")
        }
        assertEquals(
            listOf(
                "[test.plugin][DEBUG] debug message",
                "[test.plugin][INFO-] info message",
                "[test.plugin][WARN-] warning message"
            ).joinToString(System.lineSeparator(), postfix = System.lineSeparator()), output
        )
        assertEquals("", errors)
    }

    @Test
    fun `Errors go only to stderr`() {
        val (output, errors) = captureOutput { TestLogger("test").error("failure") }
        assertEquals("", output)
        assertEquals("[test][ERROR] failure${System.lineSeparator()}", errors)
    }

    @Test
    fun `Empty names and multiline messages are preserved`() {
        val message = "first\nsecond"
        val (output, errors) = captureOutput {
            TestLogger("").info("")
            TestLogger("test").warn(message)
        }
        assertEquals("[][INFO-] ${System.lineSeparator()}[test][WARN-] $message${System.lineSeparator()}", output)
        assertEquals("", errors)
    }
}