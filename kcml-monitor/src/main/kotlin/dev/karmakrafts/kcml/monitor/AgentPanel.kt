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

import net.miginfocom.swing.MigLayout
import org.kordamp.ikonli.materialdesign.MaterialDesign
import org.kordamp.ikonli.swing.FontIcon
import java.awt.Color
import javax.swing.JButton
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.JSeparator
import javax.swing.JSplitPane
import javax.swing.JTextArea

internal class AgentPanel(
    val agent: Agent
) : JSplitPane(HORIZONTAL_SPLIT) {
    private val consoleTextArea: JTextArea = JTextArea().apply {
        background = Colors.consoleBackground
        foreground = Colors.consoleForeground
        isEditable = false
    }

    private fun getOptionsPreview(): String {
        var result = ""
        for ((key, value) in agent.options) {
            result += "${value?.let { "$key=$it" } ?: key}\n"
        }
        return result
    }

    init {
        add(JPanel(MigLayout("nogrid")).apply {
            add(JLabel("<html><b>Client ID:</b> ${agent.clientId}</html>"), "w 100%, wrap")
            add(JLabel("<html><b>Process ID:</b> ${agent.processId}</html>"), "w 100%, wrap")
            add(JLabel("<html><b>JVM Vendor:</b> ${agent.jvmVendor}</html>"), "w 100%, wrap")
            add(JLabel("<html><b>JVM Name:</b> ${agent.jvmName}</html>"), "w 100%, wrap")
            add(JLabel("<html><b>JVM Version:</b> ${agent.jvmVersion}</html>"), "w 100%, wrap")
            add(JSeparator(JSeparator.HORIZONTAL), "w 100%, wrap")
            add(JLabel("Options"), "w 100%, wrap")
            add(JScrollPane(JTextArea(getOptionsPreview()).apply {
                isEditable = false
            }), "w 100%, h 100px")
        })
        add(JPanel(MigLayout("nogrid")).apply {
            add(JScrollPane(consoleTextArea), "w 100%, h 100%, wrap")
            add(SearchControls<String> { _, _ -> true }.apply {
                // TODO: ...
            }, "w 100%")
            add(JButton(FontIcon.of(MaterialDesign.MDI_DELETE_SWEEP, 16, Color.WHITE)).apply {
                addActionListener { // Clear internal buffer of text area
                    consoleTextArea.text = ""
                }
            })
            add(JButton(FontIcon.of(MaterialDesign.MDI_EXCLAMATION, 16, Color.WHITE)).apply {
                addActionListener { // Jump to end of document, scroll pane will follow
                    consoleTextArea.caretPosition = consoleTextArea.document.length
                }
            })
            add(JButton(FontIcon.of(MaterialDesign.MDI_CONTENT_SAVE, 16, Color.WHITE)).apply {
                addActionListener {
                    consoleTextArea.saveToFile(this@AgentPanel)
                }
            })
        })
        resizeWeight = 0.0
    }
}