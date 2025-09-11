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

import com.formdev.flatlaf.extras.FlatSVGIcon
import org.kordamp.ikonli.Ikon
import org.kordamp.ikonli.swing.FontIcon
import java.awt.Component
import java.awt.Graphics
import javax.swing.Icon
import javax.swing.UIManager

internal class AdaptiveIcon(
    private val provider: () -> Icon
) : Icon {
    private var icon: Icon = provider()

    fun update() {
        icon = provider()
    }

    override fun paintIcon(c: Component?, g: Graphics?, x: Int, y: Int) = icon.paintIcon(c, g, x, y)
    override fun getIconWidth(): Int = icon.iconWidth
    override fun getIconHeight(): Int = icon.iconHeight
}

internal fun Ikon.adaptive(size: Int = 16): Icon = AdaptiveIcon {
    FontIcon.of(this, size, UIManager.getColor("Label.foreground"))
}

internal fun FlatSVGIcon.adaptive(): Icon = AdaptiveIcon {
    val color = UIManager.getColor("Label.foreground")
    colorFilter = FlatSVGIcon.ColorFilter { color }
    this@adaptive
}