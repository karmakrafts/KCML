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

package dev.karmakrafts.kcml.monitor.ui.animation

import kotlin.math.pow

internal object Easings {
    // https://easings.net/en#easeInOutQuart
    fun easeInOutQuart(x: Float): Float {
        if (x < 0.5F) {
            return (8F * x * x * x * x).coerceIn(0F..1F)
        }
        return (1F - (-2F * x + 2F).pow(4F) * 0.5F).coerceIn(0F..1F)
    }
}