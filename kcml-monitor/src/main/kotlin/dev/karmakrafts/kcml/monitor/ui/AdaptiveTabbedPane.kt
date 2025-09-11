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

import javax.swing.JTabbedPane

internal class AdaptiveTabbedPane( // @formatter:off
    tabPlacement: Int,
    tabLayoutPolicy: Int
) : JTabbedPane(tabPlacement, tabLayoutPolicy) { // @formatter:on
    override fun updateUI() {
        super.updateUI()
        val tabCount = tabCount
        for (index in 0..<tabCount) {
            val icon = getIconAt(index)
            if (icon !is AdaptiveIcon) continue
            icon.update()
        }
    }
}