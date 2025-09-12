/*
 * Copyright 2025 Karma Krafts & associates
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

package dev.karmakrafts.kcml.monitor.util

import dev.karmakrafts.kcml.monitor.protocol.log.Logger
import dev.karmakrafts.kcml.monitor.protocol.log.MonitorLogLevel
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import javax.swing.JTextArea
import javax.swing.SwingUtilities

internal class UILogger(val textArea: JTextArea) : Logger {
    private var level: MonitorLogLevel = MonitorLogLevel.INFO

    override fun log(level: MonitorLogLevel, message: String) {
        if (level < this.level) return
        val threadName = Thread.currentThread().name
        val timestamp = ZonedDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
        SwingUtilities.invokeLater {
            textArea.append("[$timestamp]${level.consoleMarker}[$threadName] $message\n")
        }
    }

    override fun setLevel(level: MonitorLogLevel) {
        this.level = level
    }
}