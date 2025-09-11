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

import com.formdev.flatlaf.extras.FlatSVGUtils
import com.formdev.flatlaf.intellijthemes.FlatAllIJThemes
import dev.karmakrafts.kcml.monitor.protocol.log.Logger
import dev.karmakrafts.kcml.monitor.protocol.log.NoopLogger
import dev.karmakrafts.kcml.monitor.server.Agent
import dev.karmakrafts.kcml.monitor.server.MonitorServer
import dev.karmakrafts.kcml.monitor.ui.AdaptiveButton
import dev.karmakrafts.kcml.monitor.ui.AdaptiveLabel
import dev.karmakrafts.kcml.monitor.ui.AdaptiveMenu
import dev.karmakrafts.kcml.monitor.ui.AdaptiveMenuItem
import dev.karmakrafts.kcml.monitor.ui.AdaptiveTabbedPane
import dev.karmakrafts.kcml.monitor.ui.AgentPanel
import dev.karmakrafts.kcml.monitor.ui.ClosableTabComponent
import dev.karmakrafts.kcml.monitor.ui.ConnectionListCell
import dev.karmakrafts.kcml.monitor.ui.ConsoleTextArea
import dev.karmakrafts.kcml.monitor.ui.PlaceholderTextField
import dev.karmakrafts.kcml.monitor.ui.SearchControls
import dev.karmakrafts.kcml.monitor.ui.adaptive
import dev.karmakrafts.kcml.monitor.ui.addClosableTab
import dev.karmakrafts.kcml.monitor.ui.createSearchControls
import dev.karmakrafts.kcml.monitor.util.AgentHolder
import dev.karmakrafts.kcml.monitor.util.PositiveIntFilter
import dev.karmakrafts.kcml.monitor.util.SettingsHolder
import dev.karmakrafts.kcml.monitor.util.UILogger
import dev.karmakrafts.kcml.monitor.util.onTextChanged
import dev.karmakrafts.kcml.monitor.util.saveToFile
import dev.karmakrafts.kcml.monitor.util.setLookAndFeel
import dev.karmakrafts.kcml.monitor.util.tryLoadLookAndFeel
import io.netty.channel.Channel
import net.miginfocom.swing.MigLayout
import org.kordamp.ikonli.materialdesign.MaterialDesign
import java.awt.Desktop
import java.awt.Dimension
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import java.net.URI
import java.util.*
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutorService
import javax.swing.BorderFactory
import javax.swing.ButtonGroup
import javax.swing.DefaultListModel
import javax.swing.JFrame
import javax.swing.JList
import javax.swing.JMenuBar
import javax.swing.JPanel
import javax.swing.JRadioButtonMenuItem
import javax.swing.JScrollPane
import javax.swing.JSeparator
import javax.swing.JSplitPane
import javax.swing.JTabbedPane
import javax.swing.LookAndFeel
import javax.swing.SwingUtilities
import javax.swing.text.AbstractDocument
import kotlin.concurrent.atomics.ExperimentalAtomicApi

@OptIn(ExperimentalAtomicApi::class)
internal class MonitorWindow( // @formatter:off
    val settingsHolder: SettingsHolder,
    val executor: ExecutorService
) : JFrame("KCML Monitor") { // @formatter:on
    // @formatter:off
    val lafs: Map<String, LookAndFeel> = FlatAllIJThemes.INFOS.asSequence()
        .mapNotNull(::tryLoadLookAndFeel)
        .toList()
        .associateBy(LookAndFeel::getName)
    // @formatter:on
    init {
        setLookAndFeel(lafs[settingsHolder.settings.lookAndFeel] ?: lafs.values.first())
    }

    private val portTextField: PlaceholderTextField = PlaceholderTextField( // @formatter:off
        placeholder = "Default 65000",
        text = settingsHolder.settings.port.toString()
    ).apply { // @formatter:on
        document.onTextChanged { text ->
            settingsHolder.update {
                it.copy(port = text.toIntOrNull() ?: 65000)
            }
        }
        (document as AbstractDocument).documentFilter = PositiveIntFilter
    }

    private val serverLogTextArea: ConsoleTextArea = ConsoleTextArea()
    private val serverLogSearchControls: SearchControls<String> = serverLogTextArea.createSearchControls()
    val logger: UILogger = UILogger(serverLogTextArea)

    private val tabbedPane: AdaptiveTabbedPane =
        AdaptiveTabbedPane(JTabbedPane.TOP, JTabbedPane.SCROLL_TAB_LAYOUT).apply {
            border = BorderFactory.createTitledBorder("Clients")
        }

    private val connectionListModel: DefaultListModel<AgentHolder> = DefaultListModel()
    private val connectionList: JList<AgentHolder> = JList(connectionListModel).apply {
        cellRenderer = ConnectionListCell.CellRenderer
    }
    private val connectionListSearchControls: SearchControls<AgentHolder> = connectionList.createSearchControls()

    val server: MonitorServer = MonitorServer(logger, ::getAgentLogger).apply { // @formatter:on
        onError { error -> logger.error("Could not process packet: ${error.stackTraceToString()}") }
        onClientConnect(this@MonitorWindow::onClientConnect)
        onClientDisconnect(this@MonitorWindow::onClientDisconnect)
    }

    private inline val statusLabelText: String
        get() = "Status: ${if (server.isRunning) "ONLINE" else "OFFLINE"}"
    private val statusLabel: AdaptiveLabel = AdaptiveLabel(statusLabelText)

    private inline val connectionsLabelText: String
        get() = "Connections: ${server.connectionCount}"
    private val connectionsLabel: AdaptiveLabel = AdaptiveLabel(connectionsLabelText)

    private fun findAgentHolderById(clientId: UUID): AgentHolder? =
        connectionListModel.elements().asSequence().find { holder -> holder.agent.clientId == clientId }

    private fun getAgentLogger(clientId: UUID): Logger =
        findAgentHolderById(clientId)?.panel?.logger ?: NoopLogger.INSTANCE

    private fun onClientConnect(channel: Channel, agent: Agent) = SwingUtilities.invokeLater {
        var holder = findAgentHolderById(agent.clientId)
        if (holder != null) {
            // If this client was connected already, we re-use the list entry/panel
            holder.isConnected = true
            if (holder.tabIndex != AgentHolder.CLOSED_TAB_INDEX) {
                tabbedPane.setIconAt(holder.tabIndex, MaterialDesign.MDI_POWER_PLUG.adaptive())
                (tabbedPane.getTabComponentAt(holder.tabIndex) as ClosableTabComponent).updateIcon()
                tabbedPane.repaint()
            }
            updateConnections()
            return@invokeLater
        }
        // Otherwise we have to add it as a new AgentHolder
        val tabName = agent.clientId.toString()
        holder = AgentHolder(true, agent, channel)
        val panel = AgentPanel(holder, settingsHolder)
        holder.panel = panel
        tabbedPane.addClosableTab( // @formatter:off
            title = tabName,
            icon = MaterialDesign.MDI_POWER_PLUG.adaptive(),
            panel = panel,
            closeCallback = { index ->
                (tabbedPane.getComponentAt(index) as AgentPanel).agentHolder.tabIndex = AgentHolder.CLOSED_TAB_INDEX
            }
        ) // @formatter:on
        holder.tabIndex = tabbedPane.indexOfTab(tabName)
        tabbedPane.revalidate()
        tabbedPane.repaint()
        connectionListModel.addElement(holder)
        updateConnections()
    }

    private fun onClientDisconnect(channel: Channel, agent: Agent) = SwingUtilities.invokeLater {
        val holder = findAgentHolderById(agent.clientId) ?: return@invokeLater
        holder.isConnected = false
        if (holder.tabIndex != AgentHolder.CLOSED_TAB_INDEX) {
            tabbedPane.setIconAt(holder.tabIndex, MaterialDesign.MDI_POWER_PLUG_OFF.adaptive())
            (tabbedPane.getTabComponentAt(holder.tabIndex) as ClosableTabComponent).updateIcon()
            tabbedPane.repaint()
        }
        updateConnections()
    }

    private fun updateStatusText() {
        statusLabel.text = statusLabelText
        statusLabel.repaint()
        statusLabel.revalidate()
    }

    private fun updateConnections() {
        updateConnectionsList()
        updateConnectionsLabelText()
    }

    private fun updateConnectionsList() {
        connectionList.revalidate()
        connectionList.repaint()
    }

    private fun updateConnectionsLabelText() {
        connectionsLabel.text = connectionsLabelText
        connectionsLabel.repaint()
        connectionsLabel.revalidate()
    }

    fun startServer(): CompletableFuture<Unit> = CompletableFuture.supplyAsync({
        Thread.currentThread().name = "Server Control"
        SwingUtilities.invokeLater {
            startButton.isEnabled = false
            stopButton.isEnabled = false
        }
        val port = portTextField.text.toIntOrNull() ?: 65000
        server.start(port)
        SwingUtilities.invokeLater {
            stopButton.isEnabled = true
            startButton.isEnabled = false
            updateStatusText()
        }
    }, executor)

    private val startButton: AdaptiveButton =
        AdaptiveButton("Start", MaterialDesign.MDI_TOGGLE_SWITCH.adaptive()).apply {
            addActionListener { startServer() }
        }

    fun stopServer(): CompletableFuture<Unit> = CompletableFuture.supplyAsync({
        Thread.currentThread().name = "Server Control"
        SwingUtilities.invokeLater {
            stopButton.isEnabled = false
            startButton.isEnabled = false
        }
        server.close()
        SwingUtilities.invokeLater {
            startButton.isEnabled = true
            stopButton.isEnabled = false
            updateStatusText()
        }
    }, executor)

    private val stopButton: AdaptiveButton =
        AdaptiveButton("Stop", MaterialDesign.MDI_TOGGLE_SWITCH_OFF.adaptive()).apply {
            addActionListener { stopServer() }
            isEnabled = false
        }

    init {
        val settings = settingsHolder.settings
        val hasDefaultPosition = settings.windowX == -1 || settings.windowY == -1
        layout = MigLayout("nogrid")
        defaultCloseOperation = EXIT_ON_CLOSE
        preferredSize = Dimension(settings.windowWidth, settings.windowHeight)
        iconImages = FlatSVGUtils.createWindowIconImages("/appicon.svg")
        if (!hasDefaultPosition) setLocation(settings.windowX, settings.windowY)
        setup()
        pack()
        if (hasDefaultPosition) setLocationRelativeTo(null) // Default means center on primary display
        addComponentListener(object : ComponentAdapter() {
            override fun componentResized(event: ComponentEvent) {
                settingsHolder.update { it.copy(windowWidth = width, windowHeight = height) }
            }

            override fun componentMoved(event: ComponentEvent) {
                settingsHolder.update { it.copy(windowX = x, windowY = y) }
            }
        })
    }

    override fun dispose() {
        server.close() // Make sure server is stopped before we exit
        super.dispose()
    }

    private fun JPanel.setupControlsPanel() {
        border = BorderFactory.createTitledBorder("Controls")
        add(statusLabel, "w 100%, wrap")
        add(connectionsLabel, "w 100%, wrap")
        add(JSeparator(JSeparator.HORIZONTAL), "w 100%, wrap")
        add(AdaptiveLabel("Port"), "w 100%, wrap")
        add(portTextField, "w 100%, h 24px, wrap")
        add(JSeparator(JSeparator.HORIZONTAL), "w 100%, wrap")
        add(startButton, "w 100%, h 24px, wrap")
        add(stopButton, "w 100%, h 24px")
    }

    private fun JPanel.setupConnectionsPanel() {
        border = BorderFactory.createTitledBorder("Connections")
        add(JScrollPane(connectionList), "w 100%, h 100%, wrap")
        add(AdaptiveButton("Inspect", MaterialDesign.MDI_MAGNIFY.adaptive()).apply {
            addActionListener {
                for (index in connectionList.selectedIndices) {
                    val holder = connectionListModel.get(index)
                    if (holder.tabIndex != AgentHolder.CLOSED_TAB_INDEX) continue
                    val tabName = holder.agent.clientId.toString()
                    val icon = if (holder.isConnected) MaterialDesign.MDI_POWER_PLUG.adaptive()
                    else MaterialDesign.MDI_POWER_PLUG_OFF.adaptive()
                    tabbedPane.addClosableTab( // @formatter:off
                        title = tabName,
                        icon = icon,
                        panel = holder.panel,
                        closeCallback = {
                            (tabbedPane.getComponentAt(index) as AgentPanel).agentHolder.tabIndex =
                                AgentHolder.CLOSED_TAB_INDEX
                        }
                    ) // @formatter:on
                    holder.tabIndex = tabbedPane.indexOfTab(tabName)
                    tabbedPane.revalidate()
                    tabbedPane.repaint()
                }
            }
        })
        add(AdaptiveButton("Remove", MaterialDesign.MDI_DELETE.adaptive()).apply {
            addActionListener {
                for (index in connectionList.selectedIndices) {
                    val holder = connectionListModel.get(index)
                    if (holder.tabIndex != AgentHolder.CLOSED_TAB_INDEX) {
                        tabbedPane.remove(holder.tabIndex)
                        tabbedPane.revalidate()
                        tabbedPane.repaint()
                    }
                    connectionListModel.remove(index)
                    connectionList.revalidate()
                    connectionList.repaint()
                }
            }
        })
        add(connectionListSearchControls, "w 100%")
    }

    private fun JPanel.setupConsolePanel() {
        border = BorderFactory.createTitledBorder("Console")
        add(JScrollPane(serverLogTextArea), "w 100%, h 100%, wrap")
        add(serverLogSearchControls, "w 100%")
        add(AdaptiveButton(icon = MaterialDesign.MDI_DELETE_SWEEP.adaptive()).apply {
            addActionListener { // Clear internal buffer of text area
                serverLogTextArea.text = ""
            }
        }, "w 24px, h 24px")
        add(AdaptiveButton(icon = MaterialDesign.MDI_EXCLAMATION.adaptive()).apply {
            addActionListener { // Jump to end of document, scroll pane will follow
                serverLogTextArea.caretPosition = serverLogTextArea.document.length
            }
        }, "w 24px, h 24px")
        add(AdaptiveButton(icon = MaterialDesign.MDI_CONTENT_SAVE.adaptive()).apply {
            addActionListener {
                serverLogTextArea.saveToFile(this@MonitorWindow)
            }
        }, "w 24px, h 24px")
    }

    private fun setupCenterPane() {
        add(JSplitPane(JSplitPane.VERTICAL_SPLIT).apply {
            resizeWeight = 0.0
            dividerLocation = settingsHolder.settings.vDividerLocation
            addPropertyChangeListener(JSplitPane.DIVIDER_LOCATION_PROPERTY) { event ->
                settingsHolder.update { it.copy(vDividerLocation = event.newValue as Int) }
            }
            add(JSplitPane(JSplitPane.HORIZONTAL_SPLIT).apply {
                border = BorderFactory.createTitledBorder("Server")
                resizeWeight = 0.4
                dividerLocation = settingsHolder.settings.nh0DividerLocation
                addPropertyChangeListener(JSplitPane.DIVIDER_LOCATION_PROPERTY) { event ->
                    settingsHolder.update { it.copy(nh0DividerLocation = event.newValue as Int) }
                }
                add(JSplitPane(JSplitPane.HORIZONTAL_SPLIT).apply {
                    resizeWeight = 0.4
                    dividerLocation = settingsHolder.settings.nh1DividerLocation
                    addPropertyChangeListener(JSplitPane.DIVIDER_LOCATION_PROPERTY) { event ->
                        settingsHolder.update { it.copy(nh1DividerLocation = event.newValue as Int) }
                    }
                    add(JPanel(MigLayout("nogrid")).apply { setupControlsPanel() })
                    add(JPanel(MigLayout("nogrid")).apply { setupConnectionsPanel() })
                })
                add(JPanel(MigLayout("nogrid")).apply { setupConsolePanel() })
            })
            add(tabbedPane)
        }, "w 100%, h 100%")
    }

    private fun setup() {
        setupMenuBar()
        setupCenterPane()
    }

    private fun setupMenuBar() {
        jMenuBar = JMenuBar().apply {
            add(AdaptiveMenu("File").apply {
                add(AdaptiveMenu("Theme").apply {
                    icon = MaterialDesign.MDI_PALETTE.adaptive()
                    val group = ButtonGroup()
                    for ((name, laf) in lafs) {
                        val isSelected = name == settingsHolder.settings.lookAndFeel
                        val button = JRadioButtonMenuItem(name, isSelected).apply {
                            addActionListener {
                                setLookAndFeel(laf)
                                settingsHolder.update { it.copy(lookAndFeel = name) }
                            }
                        }
                        group.add(button)
                        add(button)
                    }
                })
                add(AdaptiveMenuItem("Mock Agent", MaterialDesign.MDI_LAN_CONNECT.adaptive()).apply {
                    addActionListener { MockAgentWindow.show(this@MonitorWindow) }
                })
                addSeparator()
                add(AdaptiveMenuItem("Exit", MaterialDesign.MDI_EXIT_TO_APP.adaptive()).apply {
                    addActionListener { dispose() }
                })
            })
            add(AdaptiveMenu("Help").apply {
                add(AdaptiveMenuItem("API Documentation", MaterialDesign.MDI_WEB.adaptive()).apply {
                    addActionListener { Desktop.getDesktop().browse(URI("https://docs.karmakrafts.dev/kcml")) }
                })
                add(AdaptiveMenuItem("Licenses", MaterialDesign.MDI_COPYRIGHT.adaptive()).apply {
                    addActionListener { LicensesDialog.show(this@MonitorWindow) }
                })
                add(AdaptiveMenuItem("About", MaterialDesign.MDI_INFORMATION.adaptive()).apply {
                    addActionListener { AboutDialog.show(this@MonitorWindow) }
                })
            })
        }
    }
}