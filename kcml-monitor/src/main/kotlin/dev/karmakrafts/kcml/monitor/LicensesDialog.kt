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

import com.mikepenz.aboutlibraries.entity.Library
import dev.karmakrafts.kcml.monitor.ui.AdaptiveButton
import dev.karmakrafts.kcml.monitor.ui.LibraryListCell
import dev.karmakrafts.kcml.monitor.ui.adaptive
import dev.karmakrafts.kcml.monitor.util.BuildInfo
import net.miginfocom.swing.MigLayout
import org.kordamp.ikonli.materialdesign.MaterialDesign
import java.awt.Desktop
import java.awt.Dimension
import java.awt.Frame
import java.net.URI
import javax.swing.JDialog
import javax.swing.JList
import javax.swing.JPanel
import javax.swing.JScrollPane

internal class LicensesDialog(owner: Frame) : JDialog(owner, "Licenses") {
    companion object {
        fun show(owner: Frame) {
            LicensesDialog(owner).apply {
                isVisible = true
            }
        }
    }

    private fun Library.getWebsiteUrl(): String? = website // @formatter:off
        ?: organization?.url
        ?: developers.firstOrNull()?.organisationUrl // @formatter:on

    private val websiteButton: AdaptiveButton = AdaptiveButton("Website", MaterialDesign.MDI_WEB.adaptive()).apply {
        isEnabled = false
        addActionListener { // @formatter:off
            val url = libraryList.model.getElementAt(libraryList.selectedIndex)?.getWebsiteUrl()
                ?: return@addActionListener
            Desktop.getDesktop().browse(URI(url))
        } // @formatter:on
    }

    private fun computeLibraries(): Array<Library> = BuildInfo.licenses.libraries // @formatter:off
        .asSequence()
        .distinctBy { lib -> lib.name }
        .toList()
        .toTypedArray() // @formatter:on

    private val libraryList: JList<Library> = JList(computeLibraries()).apply {
        cellRenderer = LibraryListCell.CellRenderer
        addListSelectionListener { event ->
            val firstIndex = event.firstIndex
            val lastIndex = event.lastIndex
            if (firstIndex == -1 || lastIndex == -1) {
                websiteButton.isEnabled = false
                return@addListSelectionListener
            }
            val url = model.getElementAt(event.firstIndex).getWebsiteUrl()
            websiteButton.isEnabled = url != null
        }
    }

    init {
        layout = MigLayout("nogrid")
        isResizable = false
        defaultCloseOperation = DISPOSE_ON_CLOSE
        preferredSize = Dimension(500, 350)
        add(JScrollPane(libraryList), "w 100%, h 100%, wrap")
        add(JPanel(MigLayout("nogrid, alignx center")).apply {
            add(AdaptiveButton("OK", MaterialDesign.MDI_CHECK.adaptive()).apply {
                addActionListener { dispose() }
            }, "w 120px, h 24px")
            add(websiteButton, "w 120px, h 24px")
        }, "w 100%")
        pack()
        setLocationRelativeTo(owner)
    }
}