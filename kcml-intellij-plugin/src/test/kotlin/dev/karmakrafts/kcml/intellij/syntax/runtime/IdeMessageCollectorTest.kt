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

import com.intellij.openapi.diagnostic.Logger
import junit.framework.TestCase
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSeverity
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSourceLocation

class IdeMessageCollectorTest : TestCase() {
    private class RecordingLogger : Logger() {
        val messages = ArrayList<Pair<String, String?>>()

        override fun isDebugEnabled(): Boolean = true

        override fun debug(message: String?, throwable: Throwable?) {
            messages += "debug" to message
        }

        override fun info(message: String?, throwable: Throwable?) {
            messages += "info" to message
        }

        override fun warn(message: String?, throwable: Throwable?) {
            messages += "warn" to message
        }

        override fun error(message: String?, throwable: Throwable?, vararg details: String) {
            messages += "error" to message
        }
    }

    fun testSeverityMappingAndPluginPrefixes() {
        val logger = RecordingLogger()
        val collector = IdeMessageCollector(logger)
        val levels = listOf(
            CompilerMessageSeverity.LOGGING to "debug",
            CompilerMessageSeverity.INFO to "info",
            CompilerMessageSeverity.OUTPUT to "info",
            CompilerMessageSeverity.WARNING to "warn",
            CompilerMessageSeverity.STRONG_WARNING to "warn",
            CompilerMessageSeverity.FIXED_WARNING to "warn",
            CompilerMessageSeverity.ERROR to "error",
            CompilerMessageSeverity.EXCEPTION to "error"
        )
        for ((severity, _) in levels) collector.report(severity, "[example] $severity", null)
        assertEquals(levels.map { (severity, level) -> level to "[example] $severity" }, logger.messages)
    }

    fun testErrorStateCanBeClearedWithoutClearingOutput() {
        val logger = RecordingLogger()
        val collector = IdeMessageCollector(logger)
        assertFalse(collector.hasErrors())
        collector.report(CompilerMessageSeverity.WARNING, "warning", null)
        assertFalse(collector.hasErrors())
        collector.report(CompilerMessageSeverity.ERROR, "error", null)
        assertTrue(collector.hasErrors())
        collector.report(CompilerMessageSeverity.INFO, "info", null)
        assertTrue(collector.hasErrors())
        collector.clear()
        assertFalse(collector.hasErrors())
        assertEquals(3, logger.messages.size)
        collector.report(CompilerMessageSeverity.EXCEPTION, "exception", null)
        assertTrue(collector.hasErrors())
    }

    fun testSourceLocationIsPreserved() {
        val logger = RecordingLogger()
        val location = object : CompilerMessageSourceLocation {
            override val path: String = "/src/Example.kt"
            override val line: Int = 12
            override val column: Int = 4
            override val lineContent: String? = null
        }
        IdeMessageCollector(logger).report(CompilerMessageSeverity.INFO, "[example] message", location)
        assertEquals(listOf("info" to "/src/Example.kt:12:4: [example] message"), logger.messages)
    }
}