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

import com.mikepenz.aboutlibraries.entity.Library
import net.miginfocom.swing.MigLayout
import java.awt.Component
import java.awt.Dimension
import java.awt.Font
import javax.swing.JLabel
import javax.swing.JList
import javax.swing.JPanel
import javax.swing.ListCellRenderer
import javax.swing.UIManager

internal class LibraryListCell( // @formatter:off
    library: Library,
    isSelected: Boolean
) : JPanel(MigLayout("nogrid, aligny center")) { // @formatter:on
    object CellRenderer : ListCellRenderer<Library> {
        override fun getListCellRendererComponent( // @formatter:off
            list: JList<out Library?>?,
            value: Library,
            index: Int,
            isSelected: Boolean,
            cellHasFocus: Boolean
        ): Component = LibraryListCell(value, isSelected) // @formatter:on
    }

    init {
        preferredSize = Dimension(0, 96)
        if (isSelected) background = UIManager.getColor("List.selectionBackground")
        add(JLabel(library.name).apply {
            font = font.deriveFont(20F).deriveFont(Font.BOLD)
        }, "w 100%, h 100%, wrap")
        val developers = library.developers.mapNotNull { dev -> dev.name }
        val joinedDevelopers = if (developers.size > 3) "${developers.take(3).joinToString()} & more"
        else developers.joinToString()
        val author = joinedDevelopers.ifBlank { library.organization?.name ?: "unknown author(s)" }
        add(JLabel("by $author").apply {
            font = font.deriveFont(Font.BOLD)
        }, "w 100%, h 100%, wrap")
        add(JLabel(library.licenses.joinToString { license -> license.name }), "w 100%, h 100%")
    }
}