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
import java.awt.Dimension
import java.awt.Image
import java.awt.image.BufferedImage
import java.util.*
import javax.imageio.ImageIO
import javax.swing.BorderFactory
import javax.swing.DefaultListModel
import javax.swing.JButton
import javax.swing.JFrame
import javax.swing.JLabel
import javax.swing.JList
import javax.swing.JMenu
import javax.swing.JMenuBar
import javax.swing.JMenuItem
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.JSeparator
import javax.swing.JSplitPane
import javax.swing.JTabbedPane
import javax.swing.JTextArea
import javax.swing.SwingUtilities
import kotlin.concurrent.atomics.ExperimentalAtomicApi

@OptIn(ExperimentalAtomicApi::class)
internal class MonitorWindow : JFrame("KCML Monitor") {
    private val portTextField: PlaceholderTextField = PlaceholderTextField("Default 65000")

    private val serverLogTextArea: JTextArea = JTextArea().apply {
        isEditable = false
        background = Colors.consoleBackground
        foreground = Colors.consoleForeground
    }

    private val tabbedPane: JTabbedPane = JTabbedPane(JTabbedPane.TOP, JTabbedPane.SCROLL_TAB_LAYOUT).apply {
        border = BorderFactory.createTitledBorder("Clients")
    }
    private val agentPanels: HashMap<UUID, AgentPanel> = HashMap()
    private val agentListModel: DefaultListModel<Agent> = DefaultListModel()
    private val agentList: JList<Agent> = JList(agentListModel)

    val logger: Logger = Logger(serverLogTextArea)

    val server: MonitorServer = MonitorServer(logger).apply {
        onAgentAdded { agent ->
            SwingUtilities.invokeLater {
                updateConnectionsLabelText()
                addAgent(agent)
            }
        }
        onAgentRemoved { agent ->
            SwingUtilities.invokeLater {
                updateConnectionsLabelText()
                disconnectAgent(agent)
            }
        }
    }

    fun addAgent(agent: Agent) {
        val panel = AgentPanel(agent)
        agentPanels[agent.clientId] = panel
        addAgentTab(agent, panel)
        agentListModel.add(0, agent)
        agentList.repaint()
        agentList.revalidate()
    }

    private fun addAgentTab(agent: Agent, panel: AgentPanel) {
        tabbedPane.addClosableTab(
            agent.processId.toString(), FontIcon.of(MaterialDesign.MDI_ETHERNET_CABLE, 16, Color.WHITE), panel
        )
        tabbedPane.repaint()
        tabbedPane.revalidate()
    }

    fun disconnectAgent(agent: Agent) {
        agentPanels[agent.clientId] ?: return
        // TODO: handle disconnection in panel
    }

    private inline val statusLabelText: String
        get() {
            val isRunning = server.isRunning
            val color = if (isRunning) "#7FFF00" else "#DC143C"
            val text = if (isRunning) "ONLINE" else "OFFLINE"
            return "<html><b>Status:</b> <span style='color: $color;'>$text</span></html>"
        }
    private val statusLabel: JLabel = JLabel(statusLabelText)

    fun updateStatusText() {
        statusLabel.text = statusLabelText
        statusLabel.repaint()
        statusLabel.revalidate()
    }

    private inline val connectionsLabelText: String
        get() = "<html><b>Connections:</b> ${server.connectionCount}</html>"
    private val connectionsLabel: JLabel = JLabel(connectionsLabelText)

    fun updateConnectionsLabelText() {
        connectionsLabel.text = connectionsLabelText
        connectionsLabel.repaint()
        connectionsLabel.revalidate()
    }

    private val startButton: JButton =
        JButton("Start", FontIcon.of(MaterialDesign.MDI_TOGGLE_SWITCH, 16, Color.WHITE)).apply {
            addActionListener {
                isEnabled = false
                stopButton.isEnabled = false
                val port = portTextField.text.toIntOrNull() ?: 65000
                logger.info("Starting server on port $port")
                SwingUtilities.invokeLater {
                    server.start(port)
                    stopButton.isEnabled = true
                    isEnabled = false
                    updateStatusText()
                    logger.info("Server started")
                }
            }
        }

    private val stopButton: JButton =
        JButton("Stop", FontIcon.of(MaterialDesign.MDI_TOGGLE_SWITCH_OFF, 16, Color.WHITE)).apply {
            addActionListener {
                isEnabled = false
                stopButton.isEnabled = false
                logger.info("Stopping server")
                SwingUtilities.invokeLater {
                    server.stop()
                    startButton.isEnabled = true
                    isEnabled = false
                    updateStatusText()
                    logger.info("Server stopped")
                }
            }
            isEnabled = false
        }

    init {
        layout = MigLayout("nogrid")
        preferredSize = Dimension(1200, 1000)
        defaultCloseOperation = EXIT_ON_CLOSE
        setup()
        pack()
        setLocationRelativeTo(null)
    }

    override fun dispose() {
        server.stop() // Make sure server is stopped before we exit
        super.dispose()
    }

    private fun loadIcon() {
        var resolution = 16
        val iconImages = ArrayList<Image>(7)
        val baseImage = try {
            this::class.java.getResourceAsStream("/appicon.png").use(ImageIO::read)
        } catch (error: Throwable) {
            error("Could not load application icon: ${error.stackTraceToString()}")
        }
        for (i in 0..<7) {
            iconImages += baseImage.getScaledInstance(resolution, resolution, BufferedImage.SCALE_SMOOTH)
            resolution = resolution shl 1
        }
        this.iconImages = iconImages
    }

    private fun JPanel.setupControlsPanel() {
        border = BorderFactory.createTitledBorder("Controls")
        add(statusLabel, "w 100%, wrap")
        add(connectionsLabel, "w 100%, wrap")
        add(JSeparator(JSeparator.HORIZONTAL), "w 100%, wrap")
        add(JLabel("Port"), "w 100%, wrap")
        add(portTextField, "w 100%, wrap")
        add(JSeparator(JSeparator.HORIZONTAL), "w 100%, wrap")
        add(startButton, "w 100%, wrap")
        add(stopButton, "w 100%")
    }

    private fun JPanel.setupConnectionsPanel() {
        border = BorderFactory.createTitledBorder("Connections")
        add(JScrollPane(agentList), "w 100%, h 100%, wrap")
        add(JButton("Inspect", FontIcon.of(MaterialDesign.MDI_MAGNIFY, 16, Color.WHITE)).apply {
            addActionListener {
                for (i in agentList.selectedIndices) {
                    val agent = agentListModel.get(i)
                    val panel = agentPanels[agent.clientId] ?: continue
                    addAgentTab(agent, panel)
                }
                tabbedPane.repaint()
                tabbedPane.revalidate()
            }
        })
        add(SearchControls<String> { _, _ -> true }.apply {
            // TODO: ...
        }, "w 100%")
    }

    private fun JPanel.setupConsolePanel() {
        border = BorderFactory.createTitledBorder("Console")
        add(JScrollPane(serverLogTextArea), "w 100%, h 100%, wrap")
        add(SearchControls<String> { _, _ -> true }.apply {
            // TODO: ...
        }, "w 100%")
        add(JButton(FontIcon.of(MaterialDesign.MDI_DELETE_SWEEP, 16, Color.WHITE)).apply {
            addActionListener { // Clear internal buffer of text area
                serverLogTextArea.text = ""
            }
        })
        add(JButton(FontIcon.of(MaterialDesign.MDI_EXCLAMATION, 16, Color.WHITE)).apply {
            addActionListener { // Jump to end of document, scroll pane will follow
                serverLogTextArea.caretPosition = serverLogTextArea.document.length
            }
        })
        add(JButton(FontIcon.of(MaterialDesign.MDI_CONTENT_SAVE, 16, Color.WHITE)).apply {
            addActionListener {
                serverLogTextArea.saveToFile(this@MonitorWindow)
            }
        })
    }

    private fun setupCenterPane() {
        add(JSplitPane(JSplitPane.VERTICAL_SPLIT).apply {
            resizeWeight = 0.0
            add(JSplitPane(JSplitPane.HORIZONTAL_SPLIT).apply {
                border = BorderFactory.createTitledBorder("Server")
                resizeWeight = 0.4
                add(JSplitPane(JSplitPane.HORIZONTAL_SPLIT).apply {
                    resizeWeight = 0.4
                    add(JPanel(MigLayout("nogrid")).apply { setupControlsPanel() })
                    add(JPanel(MigLayout("nogrid")).apply { setupConnectionsPanel() })
                })
                add(JPanel(MigLayout("nogrid")).apply { setupConsolePanel() })
            })
            add(tabbedPane)
        }, "w 100%, h 100%")
    }

    private fun setup() {
        loadIcon()
        setupMenuBar()
        setupCenterPane()
    }

    private fun setupMenuBar() {
        jMenuBar = JMenuBar().apply {
            add(JMenu("File").apply {
                add(JMenuItem("Exit", FontIcon.of(MaterialDesign.MDI_EXIT_TO_APP, 16, Color.WHITE)).apply {
                    addActionListener { dispose() }
                })
            })
            add(JMenu("Help").apply {
                add(JMenuItem("About", FontIcon.of(MaterialDesign.MDI_INFORMATION, 16, Color.WHITE)).apply {
                    // TODO: ...
                })
            })
        }
    }
}