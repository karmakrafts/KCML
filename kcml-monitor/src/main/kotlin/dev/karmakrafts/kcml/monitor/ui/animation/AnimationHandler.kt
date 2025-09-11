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

import java.awt.Component
import java.awt.Frame
import java.awt.GraphicsDevice
import java.awt.event.ActionEvent
import java.awt.event.ComponentEvent
import java.awt.event.ComponentListener
import java.awt.event.HierarchyEvent
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import javax.swing.Timer
import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.ExperimentalAtomicApi

/**
 * This class handles all animations of a given window used by things like [dev.karmakrafts.kcml.monitor.ui.CollapsiblePanel].
 * It also handles the calculation of animation intervals based on the window's screen device.
 */
@OptIn(ExperimentalAtomicApi::class)
internal class AnimationHandler(
    private val frame: Frame
) {
    var device: GraphicsDevice = frame.graphicsConfiguration.device
        private set
    var frameRate: Int = device.displayMode.refreshRate
        private set
    var frameTime: Int = 1000 / frameRate
        private set
    private val frameTimer: Timer = Timer(frameTime, ::update)
    private val components: ArrayList<Animated> = ArrayList()
    private var lastTime: Long = System.nanoTime()
    private val onAnimationsResumed: AtomicReference<() -> Unit> = AtomicReference {}
    private val onAnimationsPaused: AtomicReference<() -> Unit> = AtomicReference {}

    init {
        // We want to be notified when the frame is moved so we can detect device changes
        frame.addComponentListener(object : ComponentListener {
            override fun componentShown(e: ComponentEvent?) = updateFrameTime()
            override fun componentHidden(e: ComponentEvent?) = updateFrameTime()
            override fun componentResized(e: ComponentEvent?) = updateFrameTime()
            override fun componentMoved(e: ComponentEvent?) = updateFrameTime()
        })
        // Start/stop animation timer accordingly to not waste CPU when window is iconified
        frame.addWindowListener(object : WindowAdapter() {
            override fun windowIconified(e: WindowEvent?) = pause()
            override fun windowDeiconified(e: WindowEvent?) = resume()
            override fun windowOpened(e: WindowEvent?) = resume()
            override fun windowClosed(e: WindowEvent?) = pause()
        })
    }

    private fun resume() {
        frameTimer.start()
        onAnimationsResumed.load()()
    }

    private fun pause() {
        frameTimer.stop()
        onAnimationsPaused.load()()
    }

    inline fun onAnimationsResumed(crossinline handler: () -> Unit) {
        val oldHandler = onAnimationsResumed.load()
        onAnimationsResumed.store {
            oldHandler()
            handler()
        }
    }

    inline fun onAnimationsPaused(crossinline handler: () -> Unit) {
        val oldHandler = onAnimationsPaused.load()
        onAnimationsPaused.store {
            oldHandler()
            handler()
        }
    }

    fun register(component: Animated) {
        if (component in components) return
        components += component
    }

    fun unregister(component: Animated) {
        if (component !in components) return
        components -= component
    }

    private fun updateFrameTime() {
        val currentDevice = frame.graphicsConfiguration.device
        // If the underlying device didn't change, don't recompute the frame time
        if (currentDevice === device) return
        frameRate = currentDevice.displayMode.refreshRate
        frameTime = 1000 / frameTime
        frameTimer.delay = frameTime
        device = currentDevice
    }

    private fun update(event: ActionEvent) {
        val time = System.nanoTime()
        val deltaTime = (time - lastTime).toFloat() * 0.000000001F
        for (component in components) {
            if (!component.isAnimationActive) continue
            component.updateAnimation(deltaTime)
        }
        lastTime = time
    }
}

internal fun <C> C.registerAsAnimated(handler: AnimationHandler, animated: Animated) where C : Component {
    addHierarchyListener { event ->
        if ((event.changeFlags and HierarchyEvent.PARENT_CHANGED.toLong()) == 0L) return@addHierarchyListener
        if (parent != null) {
            handler.register(animated)
            return@addHierarchyListener
        }
        handler.unregister(animated)
    }
}

internal fun <C> C.registerAsAnimated(handler: AnimationHandler): C where C : Component, C : Animated {
    addHierarchyListener { event ->
        if ((event.changeFlags and HierarchyEvent.PARENT_CHANGED.toLong()) == 0L) return@addHierarchyListener
        if (parent != null) {
            handler.register(this)
            return@addHierarchyListener
        }
        handler.unregister(this)
    }
    return this
}