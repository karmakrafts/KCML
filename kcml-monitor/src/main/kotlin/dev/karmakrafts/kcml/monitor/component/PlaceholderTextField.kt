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

import java.awt.Color
import java.awt.event.FocusEvent
import java.awt.event.FocusListener
import javax.swing.JTextField

internal class PlaceholderTextField(
    private val placeholder: String, text: String = ""
) : JTextField(text) {
    private val defaultForeground: Color = foreground
    private var isPlaceholderVisible: Boolean = false

    init {
        addFocusListener(object : FocusListener {
            override fun focusGained(event: FocusEvent?) {
                updateTextIfNeeded(true)
            }

            override fun focusLost(event: FocusEvent?) {
                updateTextIfNeeded(false)
            }
        })
        updateTextIfNeeded()
    }

    fun updateTextIfNeeded(hasFocus: Boolean = hasFocus()) {
        if (hasFocus) {
            if (!isPlaceholderVisible) return
            text = ""
            foreground = defaultForeground
            repaint()
            revalidate()
            isPlaceholderVisible = false
            return
        }
        if (text.isNotBlank() || isPlaceholderVisible) return
        foreground = Color.GRAY
        text = placeholder
        repaint()
        revalidate()
        isPlaceholderVisible = true
    }
}