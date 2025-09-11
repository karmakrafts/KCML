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

import dev.karmakrafts.kcml.monitor.util.lerp
import java.awt.Color
import java.awt.Component
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.ExperimentalAtomicApi

@OptIn(ExperimentalAtomicApi::class)
internal class ColorTransition( // @formatter:off
    val startColor: () -> Color,
    val endColor: () -> Color,
    val speed: Float = 4F,
    private val function: (Float) -> Float = Easings::easeInOutQuart
) : Animated { // @formatter:on
    override var isAnimationActive: Boolean = false
        private set
    private var animationProgress: Float = 0F
    private val onUpdate: AtomicReference<(Color) -> Unit> = AtomicReference {}
    var isReversed: Boolean = false

    inline fun onUpdate(crossinline handler: (Color) -> Unit) {
        val oldHandler = onUpdate.load()
        onUpdate.store { color ->
            oldHandler(color)
            handler(color)
        }
    }

    fun start() {
        isAnimationActive = true
    }

    fun pause() {
        isAnimationActive = false
    }

    fun stop() {
        animationProgress = 0F
        isAnimationActive = false
    }

    override fun updateAnimation(deltaTime: Float) {
        var delta = speed * deltaTime
        if (isReversed) delta = -delta
        animationProgress = (animationProgress + delta).coerceIn(0F..1F)
        val factor = function(animationProgress)
        onUpdate.load()(startColor().lerp(endColor(), factor))
        // Animation stops itself when done playing once in either direction
        when {
            isReversed && animationProgress == 0F -> {
                isAnimationActive = false
            }

            !isReversed && animationProgress == 1F -> {
                isAnimationActive = false
            }
        }
    }

    fun whenMouseOver(component: Component): ColorTransition {
        component.addMouseListener(object : MouseAdapter() {
            override fun mouseEntered(e: MouseEvent?) {
                isReversed = false
                start()
            }

            override fun mouseExited(e: MouseEvent?) {
                isReversed = true
                start()
            }
        })
        return this
    }
}