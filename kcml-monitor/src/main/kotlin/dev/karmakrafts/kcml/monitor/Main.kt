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

package dev.karmakrafts.kcml.monitor

import com.formdev.flatlaf.FlatLaf
import dev.karmakrafts.kcml.monitor.util.SettingsHolder
import java.util.concurrent.Executors
import javax.swing.JDialog
import javax.swing.JFrame
import javax.swing.SwingUtilities
import javax.swing.UIManager
import kotlin.io.path.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.div
import kotlin.io.path.exists

fun main() {
    FlatLaf.setUseNativeWindowDecorations(false) // Enable FlatLaf custom decorations
    JFrame.setDefaultLookAndFeelDecorated(true)
    JDialog.setDefaultLookAndFeelDecorated(true)
    UIManager.put("TitlePane.showIcon", true)
    val userHome = Path(System.getProperty("user.home"))
    val workingDir = userHome / ".kcmlmon"
    if (!workingDir.exists()) workingDir.createDirectories()
    val settingsFilePath = workingDir / "settings.json"
    val settingsHolder = SettingsHolder.load(settingsFilePath)
    SwingUtilities.invokeLater {
        val executor = Executors.newVirtualThreadPerTaskExecutor()
        val window = MonitorWindow(settingsHolder, executor)
        Runtime.getRuntime().addShutdownHook(Thread {
            executor.shutdown()
            settingsHolder.save()
        })
        window.isVisible = true
    }
}