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
import java.awt.Color
import java.awt.Component
import java.awt.Graphics
import javax.swing.Icon
import javax.swing.UIManager

internal class AdaptiveIcon(
    private val provider: () -> Icon
) : Icon {
    private var icon: Icon? = null

    fun update() {
        icon = provider()
    }

    private fun getOrCreateIcon(): Icon {
        if (icon == null) icon = provider()
        return icon!!
    }

    override fun paintIcon(c: Component?, g: Graphics?, x: Int, y: Int) {
        getOrCreateIcon().paintIcon(c, g, x, y)
    }

    override fun getIconWidth(): Int = getOrCreateIcon().iconWidth
    override fun getIconHeight(): Int = getOrCreateIcon().iconHeight
}

internal fun Ikon.adaptive( // @formatter:off
    size: Int = 16,
    colorGetter: () -> Color = { UIManager.getColor("Label.foreground") }
): AdaptiveIcon = AdaptiveIcon { // @formatter:on
    FontIcon.of(this, size, colorGetter())
}

internal fun FlatSVGIcon.adaptive(): AdaptiveIcon = AdaptiveIcon {
    val color = UIManager.getColor("Label.foreground")
    colorFilter = FlatSVGIcon.ColorFilter { color }
    this@adaptive
}