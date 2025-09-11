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

import com.formdev.flatlaf.FlatLaf
import java.awt.Color
import javax.swing.UIManager

internal operator fun Color.times(factor: Float): Color = Color(
    (((red.toFloat() / 255F) * factor) * 255F).toInt().coerceIn(0..255),
    (((green.toFloat() / 255F) * factor) * 255F).toInt().coerceIn(0..255),
    (((blue.toFloat() / 255F) * factor) * 255F).toInt().coerceIn(0..255)
)

internal fun Color.lerp(other: Color, factor: Float): Color {
    val ra = red.toFloat() / 255F
    val ga = green.toFloat() / 255F
    val ba = blue.toFloat() / 255F
    val aa = alpha.toFloat() / 255F

    val rb = other.red.toFloat() / 255F
    val gb = other.green.toFloat() / 255F
    val bb = other.blue.toFloat() / 255F
    val ab = other.alpha.toFloat() / 255F

    return Color(
        (Math.fma(factor, rb - ra, ra) * 255F).toInt().coerceIn(0..255),
        (Math.fma(factor, gb - ga, ga) * 255F).toInt().coerceIn(0..255),
        (Math.fma(factor, bb - ba, ba) * 255F).toInt().coerceIn(0..255),
        (Math.fma(factor, ab - aa, aa) * 255F).toInt().coerceIn(0..255)
    )
}

internal inline fun lightOrDark(light: () -> Color, dark: () -> Color): Color {
    val laf = UIManager.getLookAndFeel()
    return if (laf is FlatLaf && laf.isDark) dark() else light()
}

internal fun lightOrDark(key: String, lightFactor: Float, darkFactor: Float): Color {
    return lightOrDark({ UIManager.getColor(key) * lightFactor }) { UIManager.getColor(key) * darkFactor }
}