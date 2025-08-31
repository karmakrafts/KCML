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

import net.miginfocom.swing.MigLayout
import org.kordamp.ikonli.materialdesign.MaterialDesign
import org.kordamp.ikonli.swing.FontIcon
import java.awt.Color
import javax.swing.BorderFactory
import javax.swing.JButton
import javax.swing.JPanel
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener

internal class SearchControls<T>(
    private val filter: (T, String) -> Boolean
) : JPanel(MigLayout("nogrid, insets 0, gap 0")) {
    val textField: PlaceholderTextField = PlaceholderTextField("Search..")
    val nextButton: JButton = JButton(FontIcon.of(MaterialDesign.MDI_ARROW_DOWN, 16, Color.WHITE))
    val previousButton: JButton = JButton(FontIcon.of(MaterialDesign.MDI_ARROW_UP, 16, Color.WHITE))

    val clearButton: JButton = JButton(FontIcon.of(MaterialDesign.MDI_FILTER_REMOVE, 16, Color.WHITE)).apply {
        addActionListener {
            textField.text = ""
            textField.updateTextIfNeeded()
        }
    }

    init {
        border = BorderFactory.createEmptyBorder()
        add(textField, "w 100%, h 20px")
        add(nextButton)
        add(previousButton)
        add(clearButton)
    }

    inline fun <C : MutableCollection<T>> search(collection: Collection<T>, factory: () -> C): C {
        val results = factory()
        results += collection.filter { filter(it, textField.text) }
        return results
    }

    inline fun <C : MutableCollection<T>> searchOnInput(
        crossinline collectionGetter: () -> Collection<T>,
        crossinline factory: () -> C,
        crossinline callback: (C) -> Unit
    ) {
        textField.document.addDocumentListener(object : DocumentListener {
            override fun insertUpdate(event: DocumentEvent?) {
                callback(search(collectionGetter(), factory))
            }

            override fun removeUpdate(event: DocumentEvent?) {
                callback(search(collectionGetter(), factory))
            }

            override fun changedUpdate(event: DocumentEvent?) {
                callback(search(collectionGetter(), factory))
            }
        })
    }
}