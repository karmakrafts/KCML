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

import dev.karmakrafts.kcml.monitor.server.Agent
import net.miginfocom.swing.MigLayout
import java.awt.Component
import javax.swing.JList
import javax.swing.JPanel
import javax.swing.ListCellRenderer

internal class ConnectionListCell( // @formatter:off
    private val agent: Agent,
    private val isSelected: Boolean
) : JPanel(MigLayout("nogrid")) { // @formatter:on
    object CellRenderer : ListCellRenderer<Agent> {
        override fun getListCellRendererComponent( // @formatter:off
            list: JList<out Agent?>?,
            value: Agent,
            index: Int,
            isSelected: Boolean,
            cellHasFocus: Boolean
        ): Component = ConnectionListCell(value, isSelected) // @formatter:on
    }
}