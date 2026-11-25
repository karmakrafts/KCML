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

import com.intellij.icons.AllIcons
import com.intellij.ide.BrowserUtil
import com.intellij.ide.setToolTipText
import com.intellij.ide.ui.laf.darcula.DarculaUIUtil
import com.intellij.ide.util.treeView.NodeRenderer
import com.intellij.openapi.util.text.HtmlChunk
import com.intellij.ui.JBColor
import com.intellij.ui.RoundedLineBorder
import com.intellij.ui.tree.ui.CustomBoundsTreeUI
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import com.intellij.util.ui.tree.TreeUtil
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Container
import java.awt.Dimension
import java.awt.Font
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.GridBagLayout
import java.awt.GridLayout
import java.awt.RenderingHints
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.BoxLayout
import javax.swing.Icon
import javax.swing.JButton
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JTree
import javax.swing.SwingUtilities
import javax.swing.tree.TreeCellRenderer
import javax.swing.tree.TreePath

internal class KcmlPluginTreeCellRenderer : TreeCellRenderer {
    private val defaultRenderer: NodeRenderer = NodeRenderer()

    private val icon: JLabel = JLabel()
    private val nameLabel: JLabel = JLabel()
    private val authors: JLabel = JLabel()
    private val version: JLabel = JLabel()

    private val issues: JButton = button(AllIcons.Actions.StartDebugger, "Open issue tracker")
    private val website: JButton = button(AllIcons.General.Web, "Open homepage")
    private val source: JButton = button(AllIcons.Actions.ShowCode, "Open source code")

    private val actions = JPanel(GridBagLayout()).apply {
        isOpaque = false
        border = JBUI.Borders.empty(0, 6, 0, 8)
        add(object : JPanel(GridLayout(1, 3, JBUI.scale(4), 0)) {
            override fun paintComponent(graphics: Graphics) {
                val g = graphics.create() as Graphics2D
                try {
                    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
                    g.color = background
                    val arc = DarculaUIUtil.COMPONENT_ARC.get()
                    g.fillRoundRect(0, 0, width, height, arc, arc)
                }
                finally {
                    g.dispose()
                }
            }
        }.apply {
            isOpaque = false
            background = UIUtil.getPanelBackground()
            border = JBUI.Borders.compound(
                RoundedLineBorder(JBColor.border(), DarculaUIUtil.COMPONENT_ARC.get(), JBUI.scale(1)),
                JBUI.Borders.empty(4)
            )
            add(issues)
            add(website)
            add(source)
        })
    }

    private val text = JPanel().apply {
        layout = BoxLayout(this, BoxLayout.Y_AXIS)
        isOpaque = false
        add(nameLabel)
        add(authors)
        add(version)
    }

    private val entry = JPanel(BorderLayout(JBUI.scale(10), 0)).apply {
        border = JBUI.Borders.empty(6, 12)
        add(icon, BorderLayout.WEST)
        add(text, BorderLayout.CENTER)
        add(actions, BorderLayout.EAST)
    }

    override fun getTreeCellRendererComponent( // @formatter:off
        tree: JTree,
        value: Any?,
        selected: Boolean,
        expanded: Boolean,
        leaf: Boolean,
        row: Int,
        hasFocus: Boolean
    ): Component { // @formatter:on
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
        issues.isEnabled = !node.value.issueTrackerUrl.isNullOrBlank()
        website.isEnabled = !node.value.authors.firstOrNull()?.url.isNullOrBlank()
        source.isEnabled = !node.value.scmUrl.isNullOrBlank()
        nameLabel.foreground = if (selected) UIUtil.getTreeSelectionForeground(hasFocus) else tree.foreground
        version.foreground = if (selected) nameLabel.foreground else UIUtil.getContextHelpForeground()
        authors.foreground = version.foreground
        entry.background = if (selected) UIUtil.getTreeSelectionBackground(hasFocus) else tree.background
        presentation.tooltip?.let(HtmlChunk::text)?.let(entry::setToolTipText)
        return entry
    }

    fun installActions(tree: JTree, browse: (String) -> Unit = { BrowserUtil.browse(it) }) {
        tree.addMouseListener(object : MouseAdapter() {
            private var pressedTarget: Pair<TreePath, JButton>? = null

            override fun mousePressed(event: MouseEvent) {
                pressedTarget = if (SwingUtilities.isLeftMouseButton(event)) {
                    buttonAt(tree, event)
                }
                else null
            }

            override fun mouseReleased(event: MouseEvent) {
                val pressed = pressedTarget
                pressedTarget = null
                if (pressed == null || !SwingUtilities.isLeftMouseButton(event)) return
                val target = buttonAt(tree, event) ?: return
                if (target != pressed) return
                val node = TreeUtil.getUserObject(target.first.lastPathComponent) as? KcmlPluginNode ?: return
                val url = when (target.second) {
                    issues -> node.value.issueTrackerUrl
                    website -> node.value.authors.firstOrNull()?.url
                    source -> node.value.scmUrl
                    else -> null
                }
                url?.takeIf { it.isNotBlank() }?.let(browse)
            }
        })
    }

    @Suppress("UnstableApiUsage")
    private fun buttonAt(tree: JTree, event: MouseEvent): Pair<TreePath, JButton>? {
        val path = tree.getClosestPathForLocation(event.x, event.y) ?: return null
        if (TreeUtil.getUserObject(path.lastPathComponent) !is KcmlPluginNode) return null
        val bounds = if (tree.ui is CustomBoundsTreeUI) {
            (tree.ui as CustomBoundsTreeUI).getActualPathBounds(tree, path)
        }
        else tree.getPathBounds(path)
        if (bounds == null || !bounds.contains(event.point)) return null
        val cell = getTreeCellRendererComponent(
            tree,
            path.lastPathComponent,
            tree.isPathSelected(path),
            tree.isExpanded(path),
            true,
            tree.getRowForPath(path),
            tree.hasFocus()
        )
        cell.setBounds(0, 0, bounds.width, bounds.height)
        fun layout(component: Component) {
            if (component is Container) {
                component.doLayout()
                component.components.forEach(::layout)
            }
        }
        layout(cell)
        val button = SwingUtilities.getDeepestComponentAt(cell, event.x - bounds.x, event.y - bounds.y) as? JButton
        return button?.takeIf { it.isEnabled }?.let { path to it }
    }

    private fun button(icon: Icon, tooltip: String): JButton = JButton(icon).apply {
        setToolTipText(HtmlChunk.text(tooltip))
        accessibleContext.accessibleName = tooltip
        isFocusable = false
        isEnabled = false
        isOpaque = true
        isContentAreaFilled = true
        isBorderPainted = true
        margin = JBUI.insets(6)
        preferredSize = Dimension(JBUI.scale(28), JBUI.scale(28))
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