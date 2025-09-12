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

package dev.karmakrafts.kcml.monitor.ui

import dev.karmakrafts.kcml.monitor.protocol.log.MonitorLogLevel
import dev.karmakrafts.kcml.monitor.server.Agent
import dev.karmakrafts.kcml.monitor.server.MonitorServer
import dev.karmakrafts.kcml.monitor.util.AgentHolder
import dev.karmakrafts.kcml.monitor.util.SettingsHolder
import dev.karmakrafts.kcml.monitor.util.UILogger
import dev.karmakrafts.kcml.monitor.util.saveToFile
import net.miginfocom.swing.MigLayout
import org.kordamp.ikonli.materialdesign.MaterialDesign
import java.util.concurrent.ExecutorService
import javax.swing.BorderFactory
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.JSplitPane
import javax.swing.JTextArea

internal class AgentPanel(
    val executor: ExecutorService,
    val server: MonitorServer,
    val agentHolder: AgentHolder,
    val settingsHolder: SettingsHolder,
    val debugEnabled: Boolean
) : JSplitPane(HORIZONTAL_SPLIT) {
    private fun Map<*, *>.getPreviewString(): String {
        var result = ""
        for ((key, value) in this) {
            result += "${value?.let { "$key=$it" } ?: key}\n"
        }
        return result
    }

    private inline val agent: Agent get() = agentHolder.agent

    private val consoleTextArea: ConsoleTextArea = ConsoleTextArea()
    private val consoleSearchControls: SearchControls<String> = consoleTextArea.createSearchControls()
    val logger: UILogger = UILogger(consoleTextArea).apply {
        if (debugEnabled) setLevel(MonitorLogLevel.DEBUG)
    }

    private val jvmOptionsTextArea: JTextArea = JTextArea(agent.jvmOptions.getPreviewString()).apply {
        isEditable = false
    }
    private val jvmOptionsSearchControls: SearchControls<String> = jvmOptionsTextArea.createSearchControls()

    private val agentOptionsTextArea: JTextArea = JTextArea(agent.options.getPreviewString()).apply {
        isEditable = false
    }
    private val agentOptionsSearchControls: SearchControls<String> = agentOptionsTextArea.createSearchControls()

    init {
        add(JPanel(MigLayout("nogrid")).apply {
            add(AdaptiveLabel("Client ID: ${agent.clientId}"), "w 100%, wrap")
            add(AdaptiveLabel("Process ID: ${agent.processId}"), "w 100%, wrap")
            add(AdaptiveLabel("JVM Vendor: ${agent.jvmVendor}"), "w 100%, wrap")
            add(AdaptiveLabel("JVM Name: ${agent.jvmName}"), "w 100%, wrap")
            add(AdaptiveLabel("JVM Version: ${agent.jvmVersion}"), "w 100%, wrap")

            add(JSplitPane(VERTICAL_SPLIT).apply {
                add(JPanel(MigLayout("nogrid, insets 0")).apply {
                    border = BorderFactory.createTitledBorder("JVM Options")
                    add(JScrollPane(jvmOptionsTextArea), "w 100%, h 100%, wrap")
                    add(JPanel(MigLayout("nogrid, insets 0")).apply {
                        add(jvmOptionsSearchControls, "w 100%")
                        add(AdaptiveButton().apply {
                            icon = MaterialDesign.MDI_CONTENT_COPY.adaptive()
                            addActionListener {}
                        }, "w 24px, h 24px")
                        add(AdaptiveButton().apply {
                            icon = MaterialDesign.MDI_RELOAD.adaptive()
                            addActionListener {
                                executor.submit {
                                    server.sendUpdateJvmOptionsPacket(agent.clientId)
                                }
                            }
                        }, "w 24px, h 24px")
                    }, "w 100%")
                })
                add(JPanel(MigLayout("nogrid, insets 0")).apply {
                    border = BorderFactory.createTitledBorder("Options")
                    add(JScrollPane(agentOptionsTextArea), "w 100%, h 100%, wrap")
                    add(JPanel(MigLayout("nogrid, insets 0")).apply {
                        add(agentOptionsSearchControls, "w 100%")
                        add(AdaptiveButton().apply {
                            icon = MaterialDesign.MDI_CONTENT_COPY.adaptive()
                            addActionListener {}
                        }, "w 24px, h 24px")
                    }, "w 100%")
                })
                resizeWeight = 0.5
                setDividerLocation(0.5)
            }, "w 100%, h 100%")
        })
        add(JPanel(MigLayout("nogrid")).apply {
            add(JScrollPane(consoleTextArea), "w 100%, h 100%, wrap")
            add(consoleSearchControls, "w 100%")
            add(AdaptiveButton().apply {
                icon = MaterialDesign.MDI_DELETE_SWEEP.adaptive()
                addActionListener { // Clear internal buffer of text area
                    consoleTextArea.text = ""
                }
            }, "w 24px, h 24px")
            add(AdaptiveButton().apply {
                icon = MaterialDesign.MDI_EXCLAMATION.adaptive()
                addActionListener { // Jump to end of document, scroll pane will follow
                    consoleTextArea.caretPosition = consoleTextArea.document.length
                }
            }, "w 24px, h 24px")
            add(AdaptiveButton().apply {
                icon = MaterialDesign.MDI_CONTENT_SAVE.adaptive()
                addActionListener {
                    consoleTextArea.saveToFile(this@AgentPanel)
                }
            }, "w 24px, h 24px")
        })
        resizeWeight = 0.0
        dividerLocation = settingsHolder.settings.sh0DividerLocation
        addPropertyChangeListener(DIVIDER_LOCATION_PROPERTY) { event ->
            settingsHolder.update { it.copy(sh0DividerLocation = event.newValue as Int) }
        }
    }
}