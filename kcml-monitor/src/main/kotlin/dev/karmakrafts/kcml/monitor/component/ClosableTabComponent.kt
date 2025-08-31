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

package dev.karmakrafts.kcml.monitor.component

import org.kordamp.ikonli.materialdesign.MaterialDesign
import org.kordamp.ikonli.swing.FontIcon
import java.awt.Color
import java.awt.Component
import java.awt.FlowLayout
import javax.swing.BorderFactory
import javax.swing.Icon
import javax.swing.JButton
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JTabbedPane

internal class ClosableTabComponent( // @formatter:off
    private val parent: JTabbedPane
) : JPanel() { // @formatter:on
    inline val index: Int
        get() = parent.indexOfTabComponent(this)

    init {
        layout = FlowLayout(FlowLayout.LEFT, 0, 0)
        isOpaque = false
    }

    fun setupTabComponents() {
        add(JLabel(parent.getTitleAt(index)).apply {
            border = BorderFactory.createEmptyBorder(0, 0, 0, 6)
        })
        add(JButton(FontIcon.of(MaterialDesign.MDI_CLOSE_CIRCLE, 12, Color.WHITE)).apply {
            addActionListener {
                this@ClosableTabComponent.parent.remove(index)
                this@ClosableTabComponent.parent.repaint()
                this@ClosableTabComponent.parent.revalidate()
            }
        })
    }
}

internal inline fun JTabbedPane.addClosableTab( // @formatter:off
    title: String,
    icon: Icon,
    panel: Component,
    init: ClosableTabComponent.() -> Unit = {}
) { // @formatter:on
    addTab(title, icon, panel)
    val tab = ClosableTabComponent(this).apply(init)
    setTabComponentAt(tabCount - 1, tab)
    tab.setupTabComponents()
}