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

import org.kordamp.ikonli.materialdesign.MaterialDesign
import java.awt.Component
import java.awt.FlowLayout
import javax.swing.BorderFactory
import javax.swing.Icon
import javax.swing.JPanel
import javax.swing.JTabbedPane

internal class ClosableTabComponent( // @formatter:off
    private val parent: JTabbedPane,
    private val closingCallback: (Int) -> Unit,
    private val closeCallback: (Int) -> Unit
) : JPanel() { // @formatter:on
    inline val index: Int
        get() = parent.indexOfTabComponent(this)

    private val titleLabel: AdaptiveLabel by lazy {
        AdaptiveLabel(parent.getTitleAt(index), parent.getIconAt(index)).apply {
            border = BorderFactory.createEmptyBorder(0, 0, 0, 6)
        }
    }

    init {
        layout = FlowLayout(FlowLayout.LEFT, 0, 0)
        isOpaque = false
    }

    fun updateIcon() {
        titleLabel.icon = parent.getIconAt(index)
    }

    fun setupTabComponents() {
        add(titleLabel)
        add(AdaptiveButton().apply {
            icon = MaterialDesign.MDI_CLOSE_CIRCLE.adaptive(12)
            addActionListener {
                closingCallback(index)
                this@ClosableTabComponent.parent.remove(index)
                this@ClosableTabComponent.parent.revalidate()
                this@ClosableTabComponent.parent.repaint()
                closeCallback(index)
            }
        })
    }
}

internal inline fun JTabbedPane.addClosableTab( // @formatter:off
    title: String,
    icon: Icon? = null,
    component: Component,
    init: ClosableTabComponent.() -> Unit = {},
    noinline closingCallback: (Int) -> Unit,
    noinline closeCallback: (Int) -> Unit = {}
): ClosableTabComponent { // @formatter:on
    addTab(title, icon, component)
    val tab = ClosableTabComponent(this, closingCallback, closeCallback).apply(init)
    setTabComponentAt(tabCount - 1, tab)
    tab.setupTabComponents()
    return tab
}