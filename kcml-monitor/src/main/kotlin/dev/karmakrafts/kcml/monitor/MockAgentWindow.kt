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
import dev.karmakrafts.kcml.monitor.protocol.MonitorClient
import dev.karmakrafts.kcml.monitor.protocol.log.MonitorLogLevel
import dev.karmakrafts.kcml.monitor.ui.AdaptiveButton
import dev.karmakrafts.kcml.monitor.ui.AdaptiveLabel
import dev.karmakrafts.kcml.monitor.ui.CollapsiblePanel
import dev.karmakrafts.kcml.monitor.ui.ConsoleTextArea
import dev.karmakrafts.kcml.monitor.ui.SearchControls
import dev.karmakrafts.kcml.monitor.ui.adaptive
import dev.karmakrafts.kcml.monitor.ui.animation.AnimationHandler
import dev.karmakrafts.kcml.monitor.ui.createSearchControls
import dev.karmakrafts.kcml.monitor.util.UILogger
import net.miginfocom.swing.MigLayout
import org.kordamp.ikonli.materialdesign.MaterialDesign
import java.awt.Dimension
import java.util.concurrent.CompletableFuture
import javax.swing.BorderFactory
import javax.swing.JCheckBox
import javax.swing.JComboBox
import javax.swing.JFrame
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.JSplitPane
import javax.swing.JTextField
import javax.swing.ListCellRenderer
import javax.swing.UIManager

internal class MockAgentWindow(owner: MonitorWindow) : JFrame("Mock Agent") {
    companion object {
        private val exceptionTypes: List<Class<out Throwable>> = listOf(
            IllegalArgumentException::class.java,
            IllegalStateException::class.java,
            RuntimeException::class.java,
            NullPointerException::class.java,
            ArrayIndexOutOfBoundsException::class.java,
            IndexOutOfBoundsException::class.java,
            NoClassDefFoundError::class.java,
        )

        fun show(owner: MonitorWindow) {
            MockAgentWindow(owner).apply {
                isVisible = true
            }
        }
    }

    private val animationHandler: AnimationHandler = AnimationHandler(this).apply {
        onAnimationsResumed { logger.debug("Resuming window animations") }
        onAnimationsPaused { logger.debug("Pausing window animations") }
    }

    private val syncServerStateCheckbox: JCheckBox = JCheckBox("Sync server state", true)

    private val connectButton: AdaptiveButton =
        AdaptiveButton("Connect", MaterialDesign.MDI_LAN_CONNECT.adaptive()).apply {
            addActionListener {
                isEnabled = false
                CompletableFuture.supplyAsync({
                    Thread.currentThread().name = "Client Control"
                    val port = owner.settingsHolder.settings.port
                    if (syncServerStateCheckbox.isSelected) owner.startServer().join()
                    logger.info("Connecting to localhost:${port}")
                    // TODO: Add list input for simulating agent options
                    if (!client.tryConnect(port, emptyMap())) {
                        logger.error("Could not connect")
                        return@supplyAsync
                    }
                    logger.info("Connected")
                }, owner.executor)
            }
        }

    private val disconnectButton: AdaptiveButton =
        AdaptiveButton("Disconnect", MaterialDesign.MDI_LAN_DISCONNECT.adaptive()).apply {
            isEnabled = false
            addActionListener {
                isEnabled = false
                CompletableFuture.supplyAsync({
                    Thread.currentThread().name = "Client Control"
                    logger.info("Disconnecting from localhost:${owner.settingsHolder.settings.port}")
                    client.close()
                    logger.info("Disconnected")
                    if (syncServerStateCheckbox.isSelected) owner.stopServer().join()
                }, owner.executor)
            }
        }

    private val consoleTextArea: ConsoleTextArea = ConsoleTextArea()
    private val consoleSearchControls: SearchControls<String> = consoleTextArea.createSearchControls()
    private val logger: UILogger = UILogger(consoleTextArea)

    private val client: MonitorClient = MonitorClient().apply {
        onError { error -> logger.error(error.stackTraceToString()) }
        onConnect { disconnectButton.isEnabled = true }
        onDisconnect { connectButton.isEnabled = true }
    }

    init {
        layout = MigLayout("nogrid")
        preferredSize = Dimension(800, 600)
        defaultCloseOperation = DISPOSE_ON_CLOSE
        iconImages = FlatSVGUtils.createWindowIconImages("/appicon.svg")
        add(JSplitPane(JSplitPane.HORIZONTAL_SPLIT).apply {
            add(JPanel(MigLayout("nogrid")).apply {
                border = BorderFactory.createTitledBorder("Controls")
                add(syncServerStateCheckbox, "w 100%, wrap")
                add(connectButton, "w 100%, h 24px, wrap")
                add(disconnectButton, "w 100%, h 24px, wrap")
                val collapsibleGroup = CollapsiblePanel.Group()
                // ---------- CONNECT
                add(CollapsiblePanel(animationHandler, "Connect") {
                    add(AdaptiveButton("Send", MaterialDesign.MDI_SEND.adaptive()).apply {
                        addActionListener {
                            client.sendConnectPacket(emptyMap()) // TODO: implement simulating agent options
                        }
                    }, "w 100%, h 24px")
                }.apply {
                    collapsibleGroup.add(this)
                }, "w 100%, wrap")
                // ---------- EXCEPTION
                add(CollapsiblePanel(animationHandler, "Exception") {
                    val typeComboBox = JComboBox(exceptionTypes.toTypedArray()).apply {
                        renderer = ListCellRenderer { _, value, _, isSelected, _ ->
                            AdaptiveLabel(value.simpleName).apply {
                                isOpaque = true
                                background = if (isSelected) UIManager.getColor("ComboBox.selectionBackground")
                                else UIManager.getColor("ComboBox.background")
                                foreground = if (isSelected) UIManager.getColor("ComboBox.selectionForeground")
                                else UIManager.getColor("ComboBox.foreground")
                            }
                        }
                    }
                    val messageTextField = JTextField("Hello, World!")
                    add(AdaptiveLabel("Type"), "w 100%, wrap")
                    add(typeComboBox, "w 100%, h 24px, wrap")
                    add(AdaptiveLabel("Message"), "w 100%, wrap")
                    add(messageTextField, "w 100%, h 24px, wrap")
                    add(AdaptiveButton("Send", MaterialDesign.MDI_SEND.adaptive()).apply {
                        addActionListener {
                            try { // @formatter:off
                                @Suppress("UNCHECKED_CAST")
                                val exception = (typeComboBox.selectedItem as Class<out Throwable>)
                                    .getConstructor(String::class.java)
                                    .newInstance(messageTextField.text)
                                client.sendExceptionPacket(exception)
                            } catch (error: Throwable) { // @formatter:on
                                logger.error("Could not create exception packet: ${error.stackTraceToString()}")
                            }
                        }
                    }, "w 100%, h 24px, wrap")
                }.apply {
                    collapsibleGroup.add(this)
                }, "w 100%, wrap")

                // ---------- LOG
                add(CollapsiblePanel(animationHandler, "Log") {
                    val levelComboBox = JComboBox(MonitorLogLevel.entries.toTypedArray())
                    val messageTextField = JTextField("Hello, World!")
                    add(AdaptiveLabel("Level"), "w 100%, wrap")
                    add(levelComboBox, "w 100%, h 24px, wrap")
                    add(AdaptiveLabel("Message"), "w 100%, wrap")
                    add(messageTextField, "w 100%, h 24px, wrap")
                    add(AdaptiveButton("Send", MaterialDesign.MDI_SEND.adaptive()).apply {
                        addActionListener {
                            client.logger.log(levelComboBox.selectedItem as MonitorLogLevel, messageTextField.text)
                        }
                    }, "w 100%, h 24px, wrap")
                }.apply {
                    collapsibleGroup.add(this)
                }, "w 100%, wrap")

                // ---------- TRANSFORMATION
                add(CollapsiblePanel(animationHandler, "Transform") {
                    add(AdaptiveButton("Send", MaterialDesign.MDI_SEND.adaptive()).apply {

                    }, "w 100%, h 24px")
                }.apply {
                    collapsibleGroup.add(this)
                }, "w 100%")
            })
            add(JPanel(MigLayout("nogrid")).apply {
                border = BorderFactory.createTitledBorder("Console")
                add(JScrollPane(consoleTextArea), "w 100%, h 100%, wrap")
                add(consoleSearchControls, "w 100%")
            })
        }, "w 100%, h 100%")
        pack()
        setLocationRelativeTo(owner)
    }

    override fun dispose() {
        client.close()
        super.dispose()
    }
}