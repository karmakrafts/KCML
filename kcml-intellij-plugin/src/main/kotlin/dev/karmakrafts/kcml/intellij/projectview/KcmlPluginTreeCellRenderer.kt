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

package dev.karmakrafts.kcml.intellij.projectview

import com.intellij.ide.util.treeView.NodeRenderer
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import com.intellij.util.ui.tree.TreeUtil
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Font
import java.awt.Graphics
import java.awt.Graphics2D
import javax.swing.BoxLayout
import javax.swing.Icon
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JTree
import javax.swing.tree.TreeCellRenderer

internal class KcmlPluginTreeCellRenderer : TreeCellRenderer {
    private val defaultRenderer = NodeRenderer()
    private val icon = JLabel()
    private val nameLabel = JLabel()
    private val authors = JLabel()
    private val version = JLabel()
    private val text = JPanel().apply {
        layout = BoxLayout(this, BoxLayout.Y_AXIS)
        isOpaque = false
        add(nameLabel)
        add(authors)
        add(version)
    }
    private val entry = JPanel(BorderLayout(JBUI.scale(10), 0)).apply {
        border = JBUI.Borders.empty(6, 0)
        add(icon, BorderLayout.WEST)
        add(text, BorderLayout.CENTER)
    }

    override fun getTreeCellRendererComponent(
        tree: JTree, value: Any?, selected: Boolean, expanded: Boolean, leaf: Boolean, row: Int, hasFocus: Boolean
    ): Component {
        val node = TreeUtil.getUserObject(value) as? KcmlPluginNode
            ?: return defaultRenderer.getTreeCellRendererComponent(tree, value, selected, expanded, leaf, row, hasFocus)
        val presentation = node.presentation
        icon.icon = presentation.getIcon(false)?.let(::PluginIcon)
        nameLabel.text = node.value.name
        nameLabel.font = tree.font.deriveFont(Font.BOLD)
        authors.text = node.value.authors.joinToString(", ") { it.name }
        authors.font = tree.font
        authors.isVisible = node.value.authors.isNotEmpty()
        version.text = node.value.version
        version.font = tree.font
        nameLabel.foreground = if (selected) UIUtil.getTreeSelectionForeground(hasFocus) else tree.foreground
        version.foreground = if (selected) nameLabel.foreground else UIUtil.getContextHelpForeground()
        authors.foreground = version.foreground
        entry.background = if (selected) UIUtil.getTreeSelectionBackground(hasFocus) else tree.background
        entry.toolTipText = presentation.tooltip
        return entry
    }

    private class PluginIcon(private val delegate: Icon) : Icon {
        override fun getIconWidth(): Int = JBUI.scale(40)
        override fun getIconHeight(): Int = JBUI.scale(40)

        override fun paintIcon(component: Component?, graphics: Graphics, x: Int, y: Int) {
            val width = delegate.iconWidth
            val height = delegate.iconHeight
            if (width <= 0 || height <= 0) return
            val scale = minOf(iconWidth.toDouble() / width, iconHeight.toDouble() / height)
            val scaledGraphics = graphics.create() as Graphics2D
            try {
                scaledGraphics.translate(x + (iconWidth - width * scale) / 2, y + (iconHeight - height * scale) / 2)
                scaledGraphics.scale(scale, scale)
                delegate.paintIcon(component, scaledGraphics, 0, 0)
            }
            finally {
                scaledGraphics.dispose()
            }
        }
    }
}