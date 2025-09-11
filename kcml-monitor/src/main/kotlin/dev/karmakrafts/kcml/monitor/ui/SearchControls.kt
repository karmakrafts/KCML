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

import net.miginfocom.swing.MigLayout
import org.kordamp.ikonli.materialdesign.MaterialDesign
import javax.swing.BorderFactory
import javax.swing.DefaultListModel
import javax.swing.JList
import javax.swing.JPanel
import javax.swing.JTextArea

internal typealias SearchControlsFilter<T> = (value: T, term: String) -> Boolean

internal class SearchControls<T>(
    private val getter: () -> Collection<T>,
    private val setter: (List<T>) -> Unit,
    private val filter: SearchControlsFilter<T>
) : JPanel(MigLayout("nogrid, insets 0, gap 0")) {
    val textField: PlaceholderTextField = PlaceholderTextField("Search..")
    val nextButton: AdaptiveButton = AdaptiveButton().apply {
        icon = MaterialDesign.MDI_ARROW_DOWN.adaptive()
    }
    val previousButton: AdaptiveButton = AdaptiveButton().apply {
        icon = MaterialDesign.MDI_ARROW_UP.adaptive()
    }

    val clearButton: AdaptiveButton = AdaptiveButton().apply {
        icon = MaterialDesign.MDI_FILTER_REMOVE.adaptive()
        addActionListener {
            textField.text = ""
            textField.updateTextIfNeeded()
        }
    }

    private val snapshot: ArrayList<T> = ArrayList()

    init {
        border = BorderFactory.createEmptyBorder()
        add(textField, "w 100%, h 24px")
        add(nextButton, "w 24px, h 24px")
        add(previousButton, "w 24px, h 24px")
        add(clearButton, "w 24px, h 24px")
    }

    fun storeSnapshot() {
        snapshot.clear()
        snapshot += getter()
    }

    fun restoreSnapshot() {
        setter(snapshot)
    }
}

internal fun JTextArea.createSearchControls(): SearchControls<String> = SearchControls(
    getter = { text.split("\n") },
    setter = { lines -> text = lines.joinToString("\n") },
    filter = { value, term -> term in value })

internal fun <T> JList<T>.createSearchControls(
    filter: SearchControlsFilter<T> = { value, term -> term in value.toString() }
): SearchControls<T> = SearchControls( // @formatter:off
    getter = { (0..<model.size).map(model::getElementAt) },
    setter = { values ->
        model = DefaultListModel<T>().apply {
            addAll(values)
        }
    },
    filter = filter
) // @formatter:on