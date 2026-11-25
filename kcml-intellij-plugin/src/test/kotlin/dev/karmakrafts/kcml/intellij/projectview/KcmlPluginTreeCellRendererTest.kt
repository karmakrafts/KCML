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
import com.intellij.ide.projectView.ViewSettings
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase
import com.intellij.ui.RoundedLineBorder
import com.intellij.ui.tree.ui.CustomBoundsTreeUI
import com.intellij.ui.tree.ui.DefaultTreeUI
import com.intellij.ui.treeStructure.Tree
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.Color
import java.awt.Component
import java.awt.Container
import java.awt.Graphics
import java.awt.Point
import java.awt.Rectangle
import java.awt.event.MouseEvent
import java.awt.image.BufferedImage
import javax.swing.JButton
import javax.swing.JPanel
import javax.swing.JTree
import javax.swing.SwingUtilities
import javax.swing.border.CompoundBorder
import javax.swing.border.EmptyBorder
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeModel

class KcmlPluginTreeCellRendererTest : LightJavaCodeInsightFixtureTestCase() {
    fun testButtonLayoutAndActions() {
        val metadata = listOf(
            KcmlPluginMetadata(
                id = "first",
                issueTrackerUrl = "https://example.com/first/issues",
                scmUrl = "https://example.com/first/source",
                authors = listOf(
                    KcmlPluginMetadata.Author("First", "https://example.com/first"),
                    KcmlPluginMetadata.Author("Second", "https://example.com/second")
                )
            ),
            KcmlPluginMetadata(
                id = "second",
                issueTrackerUrl = "https://example.com/second/issues",
                scmUrl = "https://example.com/second/source"
            ),
            KcmlPluginMetadata(id = "missing"),
            KcmlPluginMetadata(id = "blank", issueTrackerUrl = " ", scmUrl = " "),
            KcmlPluginMetadata(
                id = "missing-author-url",
                authors = listOf(
                    KcmlPluginMetadata.Author("First"),
                    KcmlPluginMetadata.Author("Second", "https://example.com/second")
                )
            ),
            KcmlPluginMetadata(
                id = "blank-author-url",
                authors = listOf(
                    KcmlPluginMetadata.Author("First", " "),
                    KcmlPluginMetadata.Author("Second", "https://example.com/second")
                )
            )
        )
        val root = DefaultMutableTreeNode("Plugins")
        metadata.forEach { root.add(DefaultMutableTreeNode(KcmlPluginNode(project, it, ViewSettings.DEFAULT))) }
        val renderer = KcmlPluginTreeCellRenderer()
        val tree = JTree(DefaultTreeModel(root)).apply {
            cellRenderer = renderer
            rowHeight = 0
            setSize(900, 600)
            expandRow(0)
        }
        val opened = mutableListOf<String>()
        renderer.installActions(tree, opened::add)
        val listener = tree.mouseListeners.last()
        fun event(point: Point, id: Int) = MouseEvent(
            tree, id, 0, 0, point.x, point.y, 1, false, MouseEvent.BUTTON1
        )
        fun click(point: Point) {
            listener.mousePressed(event(point, MouseEvent.MOUSE_PRESSED))
            listener.mouseReleased(event(point, MouseEvent.MOUSE_RELEASED))
        }
        val issuePoints = mutableListOf<Point>()
        val websitePoints = mutableListOf<Point>()
        val sourcePoints = mutableListOf<Point>()
        for ((index, plugin) in metadata.withIndex()) {
            val row = index + 1
            val path = tree.getPathForRow(row)
            val bounds = (tree.ui as? CustomBoundsTreeUI)?.getActualPathBounds(tree, path) ?: tree.getRowBounds(row)
            val cell = renderer.getTreeCellRendererComponent(tree, root.getChildAt(index), false, false, true, row, false)
            cell.setBounds(0, 0, bounds.width, bounds.height)
            layout(cell)
            val buttons = buttons(cell)
            assertEquals(3, buttons.size)
            assertSame(AllIcons.Actions.StartDebugger, buttons[0].icon)
            assertSame(AllIcons.General.Web, buttons[1].icon)
            assertSame(AllIcons.Actions.ShowCode, buttons[2].icon)
            assertEquals(!plugin.issueTrackerUrl.isNullOrBlank(), buttons[0].isEnabled)
            assertEquals(!plugin.authors.firstOrNull()?.url.isNullOrBlank(), buttons[1].isEnabled)
            assertEquals(!plugin.scmUrl.isNullOrBlank(), buttons[2].isEnabled)
            buttons.forEach {
                assertTrue(it.isContentAreaFilled)
                assertTrue(it.isBorderPainted)
                assertFalse(it.border is EmptyBorder)
            }
            val buttonBounds = buttons.map { SwingUtilities.convertRectangle(it.parent, it.bounds, cell) }
            buttonBounds.forEach { assertEquals(it.width, it.height) }
            assertTrue(buttonBounds[1].x > buttonBounds[0].x + buttonBounds[0].width)
            assertTrue(buttonBounds[2].x > buttonBounds[1].x + buttonBounds[1].width)
            val rightPadding = cell.width - buttonBounds[2].x - buttonBounds[2].width
            assertEquals(JBUI.scale(12) + JBUI.scale(1) + JBUI.scale(12), rightPadding)
            val panel = buttons.last().parent
            val panelBounds = SwingUtilities.convertRectangle(panel.parent, panel.bounds, cell)
            assertEquals((cell as Container).getComponent(0).x + JBUI.scale(8), cell.width - panelBounds.x - panelBounds.width)
            val points = buttonBounds.map { Point(bounds.x + it.x + it.width / 2, bounds.y + it.y + it.height / 2) }
            issuePoints += points.first()
            websitePoints += points[1]
            sourcePoints += points[2]
            click(points[0])
            click(points[1])
            click(points[2])
            click(Point(bounds.x + 2, bounds.y + bounds.height / 2))
        }
        val expected = listOf(
            metadata[0].issueTrackerUrl, metadata[0].authors[0].url, metadata[0].scmUrl,
            metadata[1].issueTrackerUrl, metadata[1].scmUrl
        )
        assertEquals(expected, opened)
        listener.mousePressed(event(issuePoints[0], MouseEvent.MOUSE_PRESSED))
        listener.mouseReleased(event(issuePoints[1], MouseEvent.MOUSE_RELEASED))
        listener.mousePressed(event(issuePoints[0], MouseEvent.MOUSE_PRESSED))
        listener.mouseReleased(event(websitePoints[0], MouseEvent.MOUSE_RELEASED))
        listener.mousePressed(event(websitePoints[0], MouseEvent.MOUSE_PRESSED))
        listener.mouseReleased(event(issuePoints[0], MouseEvent.MOUSE_RELEASED))
        listener.mousePressed(event(sourcePoints[0], MouseEvent.MOUSE_PRESSED))
        listener.mouseReleased(event(sourcePoints[1], MouseEvent.MOUSE_RELEASED))
        listener.mousePressed(event(sourcePoints[0], MouseEvent.MOUSE_PRESSED))
        listener.mouseReleased(event(websitePoints[0], MouseEvent.MOUSE_RELEASED))
        listener.mousePressed(event(issuePoints[0], MouseEvent.MOUSE_PRESSED))
        listener.mouseReleased(event(sourcePoints[0], MouseEvent.MOUSE_RELEASED))
        assertEquals(expected, opened)
    }

    fun testButtonsKeepDefaultColorsAcrossSelection() {
        val renderer = KcmlPluginTreeCellRenderer()
        val defaultButton = JButton().apply {
            // Match the renderer's painting settings; Aqua buttons are non-opaque by default.
            isFocusable = false
            isOpaque = true
            isContentAreaFilled = true
            isBorderPainted = true
        }
        fun backgroundPixel(button: JButton): Int {
            val icon = button.icon
            button.icon = null
            button.setSize(JBUI.scale(28), JBUI.scale(28))
            val image = BufferedImage(button.width, button.height, BufferedImage.TYPE_INT_ARGB)
            val graphics = image.createGraphics()
            try {
                button.paint(graphics)
                return image.getRGB(button.width / 2, button.height / 2)
            }
            finally {
                button.icon = icon
                graphics.dispose()
            }
        }
        val tree = JTree().apply {
            background = Color(40, 50, 60)
            foreground = Color(210, 220, 230)
        }
        val nodes = listOf(
            KcmlPluginMetadata(id = "enabled", issueTrackerUrl = "https://example.com/issues"),
            KcmlPluginMetadata(id = "disabled")
        ).map { DefaultMutableTreeNode(KcmlPluginNode(project, it, ViewSettings.DEFAULT)) }
        for (node in nodes) {
            for ((selected, focused) in listOf(false to false, true to true, true to false, false to false)) {
                val cell = renderer.getTreeCellRendererComponent(tree, node, selected, false, true, 0, focused)
                val background = if (selected) UIUtil.getTreeSelectionBackground(focused) else tree.background
                assertEquals(background, cell.background)
                cell.setSize(900, cell.preferredSize.height)
                layout(cell)
                val panel = buttons(cell).first().parent as JPanel
                assertFalse(panel.isOpaque)
                assertEquals(UIUtil.getPanelBackground(), panel.background)
                assertEquals(panel.preferredSize, panel.size)
                val border = (panel.border as CompoundBorder).outsideBorder as RoundedLineBorder
                assertEquals(JBUI.scale(1), border.thickness)
                assertEquals(JBUI.scale(1) + JBUI.scale(4), panel.insets.left)
                assertEquals(panel.insets.left, panel.insets.right)
                val gap = SwingUtilities.convertPoint(panel, panel.width - panel.insets.right / 2, panel.height / 2, cell)
                val image = BufferedImage(cell.width, cell.height, BufferedImage.TYPE_INT_ARGB)
                val graphics = image.createGraphics()
                try {
                    cell.paint(graphics)
                }
                finally {
                    graphics.dispose()
                }
                assertEquals(panel.background.rgb, image.getRGB(gap.x, gap.y))
                buttons(cell).forEach {
                    assertEquals(defaultButton.background, it.background)
                    assertEquals(defaultButton.foreground, it.foreground)
                    assertTrue(it.isOpaque)
                    assertTrue(it.isContentAreaFilled)
                    assertTrue(it.isBorderPainted)
                    val pixel = backgroundPixel(it)
                    assertEquals(255, pixel ushr 24)
                    defaultButton.isEnabled = it.isEnabled
                    assertEquals(backgroundPixel(defaultButton), pixel)
                }
            }
        }
    }

    fun testIssueTrackerClickAtPaintedButtonLocation() {
        val metadata = KcmlPluginMetadata(id = "plugin", issueTrackerUrl = "https://example.com/issues")
        val root = DefaultMutableTreeNode("Plugins").apply {
            add(DefaultMutableTreeNode(KcmlPluginNode(project, metadata, ViewSettings.DEFAULT)))
        }
        val renderer = KcmlPluginTreeCellRenderer()
        var issueBounds: Rectangle? = null
        val tree = Tree(DefaultTreeModel(root)).apply {
            setUI(DefaultTreeUI())
            cellRenderer = javax.swing.tree.TreeCellRenderer { tree, value, selected, expanded, leaf, row, focused ->
                val cell = renderer.getTreeCellRendererComponent(tree, value, selected, expanded, leaf, row, focused)
                object : JPanel(java.awt.BorderLayout()) {
                    init {
                        add(cell)
                    }

                    override fun paint(graphics: Graphics) {
                        layout(this)
                        val offsetX = x
                        val offsetY = y
                        buttons(this).firstOrNull()?.let {
                            issueBounds = SwingUtilities.convertRectangle(it.parent, it.bounds, this).apply {
                                translate(offsetX, offsetY)
                            }
                        }
                        super.paint(graphics)
                    }
                }
            }
            isRootVisible = false
            rowHeight = 0
            setSize(900, 200)
            expandRow(0)
        }
        val opened = mutableListOf<String>()
        renderer.installActions(tree, opened::add)
        val image = BufferedImage(tree.width, tree.height, BufferedImage.TYPE_INT_ARGB)
        val graphics = image.createGraphics()
        try {
            tree.paint(graphics)
        }
        finally {
            graphics.dispose()
        }
        val bounds = requireNotNull(issueBounds)
        val point = Point(bounds.x + bounds.width / 2, bounds.y + bounds.height / 2)
        for (id in listOf(MouseEvent.MOUSE_PRESSED, MouseEvent.MOUSE_RELEASED)) {
            tree.dispatchEvent(MouseEvent(tree, id, 0, 0, point.x, point.y, 1, false, MouseEvent.BUTTON1))
        }
        assertEquals(listOf(metadata.issueTrackerUrl), opened)
    }

    private fun layout(component: Component) {
        if (component is Container) {
            component.doLayout()
            component.components.forEach(::layout)
        }
    }

    private fun buttons(component: Component): List<JButton> = when (component) {
        is JButton -> listOf(component)
        is Container -> component.components.flatMap(::buttons)
        else -> emptyList()
    }
}