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

import dev.karmakrafts.kcml.monitor.util.AgentHolder
import net.miginfocom.swing.MigLayout
import org.kordamp.ikonli.materialdesign.MaterialDesign
import java.awt.Component
import javax.swing.BorderFactory
import javax.swing.JList
import javax.swing.JPanel
import javax.swing.ListCellRenderer
import javax.swing.UIManager

internal class ConnectionListCell( // @formatter:off
    private val agentHolder: AgentHolder,
    private val isSelected: Boolean
) : JPanel(MigLayout("nogrid, insets 0, aligny center")) { // @formatter:on
    object CellRenderer : ListCellRenderer<AgentHolder> {
        override fun getListCellRendererComponent( // @formatter:off
            list: JList<out AgentHolder?>?,
            value: AgentHolder,
            index: Int,
            isSelected: Boolean,
            cellHasFocus: Boolean
        ): Component = ConnectionListCell(value, isSelected) // @formatter:on
    }

    init {
        border = BorderFactory.createEmptyBorder(4, 4, 4, 4)
        val icon = if (agentHolder.isConnected) MaterialDesign.MDI_POWER_PLUG.adaptive(24)
        else MaterialDesign.MDI_POWER_PLUG_OFF.adaptive(24)
        background = if (isSelected) UIManager.getColor("List.selectionBackground")
        else UIManager.getColor("List.background")
        add(AdaptiveLabel(icon = icon))
        add(JPanel(MigLayout("nogrid, insets 0, aligny center")).apply {
            isOpaque = false // This is only for grouping, not for visuals
            add(AdaptiveLabel(agentHolder.agent.clientId.toString()).apply {
                foreground = when {
                    !agentHolder.isConnected -> if (isSelected) UIManager.getColor("List.selectionInactiveForeground")
                    else UIManager.getColor("List.inactiveForeground")

                    isSelected -> UIManager.getColor("List.selectionForeground")
                    else -> UIManager.getColor("List.foreground")
                }
            }, "w 100%, wrap")
            val address = "Address: ${agentHolder.channel.remoteAddress()}"
            add(AdaptiveLabel(address), "w 100%")
        })
    }
}