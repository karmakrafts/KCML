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

import com.formdev.flatlaf.extras.FlatSVGIcon
import dev.karmakrafts.kcml.monitor.ui.AdaptiveButton
import dev.karmakrafts.kcml.monitor.ui.AdaptiveLabel
import dev.karmakrafts.kcml.monitor.ui.adaptive
import dev.karmakrafts.kcml.monitor.util.BuildInfo
import net.miginfocom.swing.MigLayout
import org.kordamp.ikonli.materialdesign.MaterialDesign
import java.awt.Desktop
import java.awt.Dimension
import java.awt.Font
import java.awt.Frame
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.net.URI
import javax.swing.BorderFactory
import javax.swing.JDialog
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JSeparator

internal class AboutDialog(owner: Frame) : JDialog(owner, "About") {
    companion object {
        fun show(owner: Frame) {
            AboutDialog(owner).apply {
                isVisible = true
            }
        }
    }

    init {
        layout = MigLayout("nogrid")
        isResizable = false
        defaultCloseOperation = DISPOSE_ON_CLOSE
        preferredSize = Dimension(400, 260)
        add(JPanel(MigLayout("nogrid, insets 0, aligny center")).apply {
            add(JPanel(MigLayout("nogrid, insets 0")).apply {
                add(AdaptiveLabel(icon = FlatSVGIcon("appicon.svg", 96, 96)).apply {
                    border = BorderFactory.createEmptyBorder(2, 8, 2, 8)
                }, "w 100%, h 100%")
            }, "h 100%")
            add(JPanel(MigLayout("nogrid, insets 0")).apply {
                add(AdaptiveLabel("KCML Monitor").apply {
                    font = font.deriveFont(20F).deriveFont(Font.BOLD)
                }, "w 100%, wrap")
                add(AdaptiveLabel("Version ${BuildInfo.current.version}").apply {
                    font = font.deriveFont(17F).deriveFont(Font.BOLD)
                }, "w 100%, wrap")
                add(JSeparator(JSeparator.HORIZONTAL), "w 100%, wrap")
                add(AdaptiveLabel("JVM Vendor: ${System.getProperty("java.vm.vendor")}"), "w 100%, wrap")
                add(AdaptiveLabel("JVM Name: ${System.getProperty("java.vm.name")}"), "w 100%, wrap")
                add(AdaptiveLabel("JVM Version: ${System.getProperty("java.vm.version")}"), "w 100%, wrap")
                add(JSeparator(JSeparator.HORIZONTAL), "w 100%, wrap")
                add(
                    AdaptiveLabel(
                        "Karma Krafts", FlatSVGIcon("karmakrafts.svg", 20, 20).adaptive(), JLabel.LEADING
                    ).apply {
                        addMouseListener(object : MouseAdapter() {
                            override fun mouseClicked(e: MouseEvent?) {
                                Desktop.getDesktop().browse(URI("https://git.karmakrafts.dev/kk"))
                            }
                        })
                    }, "w 100%"
                )
            }, "w 100%, h 100%")
        }, "w 100%, h 100%, wrap")
        add(JPanel(MigLayout("nogrid, alignx center")).apply {
            add(AdaptiveButton("OK", MaterialDesign.MDI_CHECK.adaptive()).apply {
                addActionListener { dispose() }
            }, "w 100%, h 24px")
            add(AdaptiveButton("Source", MaterialDesign.MDI_CODE_BRACES.adaptive()).apply {
                addActionListener { Desktop.getDesktop().browse(URI("https://git.karmakrafts.dev/kk/kcml")) }
            }, "w 100%, h 24px")
            add(AdaptiveButton("Donate", MaterialDesign.MDI_CURRENCY_USD.adaptive()).apply {
                addActionListener { Desktop.getDesktop().browse(URI("https://paypal.me/ThatDamnFox")) }
            }, "w 100%, h 24px")
        }, "w 100%")
        pack()
        setLocationRelativeTo(owner)
    }
}