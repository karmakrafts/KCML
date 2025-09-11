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

package dev.karmakrafts.kcml.monitor.util

import javax.swing.text.AttributeSet
import javax.swing.text.DocumentFilter

internal object PositiveIntFilter : DocumentFilter() {
    private val pattern: Regex = Regex("""\d+""")

    override fun insertString(fb: FilterBypass?, offset: Int, string: String?, attr: AttributeSet?) {
        if (string == null || !string.matches(pattern)) return
        super.insertString(fb, offset, string, attr)
    }

    override fun replace(fb: FilterBypass?, offset: Int, length: Int, text: String?, attrs: AttributeSet?) {
        if (text == null) return
        if (text.isEmpty() || text.matches(pattern)) {
            super.replace(fb, offset, length, text, attrs)
            return
        }
    }
}