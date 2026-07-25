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

package dev.karmakrafts.kcml.agent.console

import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.FlowLayout
import javax.swing.BorderFactory
import javax.swing.JButton
import javax.swing.JFileChooser
import javax.swing.JFrame
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.JTextArea
import javax.swing.SwingUtilities
import javax.swing.text.PlainDocument
import kotlin.io.path.deleteIfExists
import kotlin.io.path.writeText

internal class AgentConsole : JFrame("KCML Agent Console") {
    private val consoleDocument: PlainDocument = PlainDocument()

    init {
        layout = BorderLayout()
        preferredSize = Dimension(800, 600)
        defaultCloseOperation = EXIT_ON_CLOSE
        isResizable = true
        populate()
        pack()
        setLocationRelativeTo(null) // Center window
        rootPane.border = BorderFactory.createEmptyBorder(4, 4, 4, 4)
    }

    fun log(message: String) = SwingUtilities.invokeLater {
        consoleDocument.insertString(consoleDocument.length, "$message\n", null)
    }

    private fun populate() {
        add(createCenterPanel(), BorderLayout.CENTER)
        add(createSouthPanel(), BorderLayout.SOUTH)
    }

    private fun createSouthPanel(): JPanel = JPanel(FlowLayout(FlowLayout.LEFT)).apply {
        add(JButton("Clear").apply {
            addActionListener {
                consoleDocument.remove(0, consoleDocument.length)
            }
        }, 0)
        add(JButton("Save").apply {
            addActionListener {
                val fileChooser = JFileChooser().apply {
                    fileFilter = null
                }
                fileChooser.showSaveDialog(this@AgentConsole)
                val filePath = fileChooser.selectedFile.toPath()
                filePath.deleteIfExists()
                filePath.writeText(consoleDocument.getText(0, consoleDocument.length))
            }
        }, 1)
    }

    private fun createCenterPanel(): JPanel = JPanel(BorderLayout()).apply {
        add(JScrollPane(JTextArea(consoleDocument).apply {
            isEditable = false
        }, JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED, JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED))
    }
}