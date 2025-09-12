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

import dev.karmakrafts.kcml.monitor.ui.animation.Animated
import dev.karmakrafts.kcml.monitor.ui.animation.AnimationHandler
import dev.karmakrafts.kcml.monitor.ui.animation.ColorTransition
import dev.karmakrafts.kcml.monitor.ui.animation.Easings
import dev.karmakrafts.kcml.monitor.ui.animation.registerAsAnimated
import dev.karmakrafts.kcml.monitor.util.lightOrDark
import net.miginfocom.swing.MigLayout
import org.kordamp.ikonli.materialdesign.MaterialDesign
import java.awt.Color
import java.awt.Dimension
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.event.FocusAdapter
import java.awt.event.FocusEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.BorderFactory
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JSeparator
import javax.swing.UIManager
import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.ExperimentalAtomicApi

// TODO: Implement keyboard accessibility for this
@OptIn(ExperimentalAtomicApi::class)
internal class CollapsiblePanel( // @formatter:off
    animationHandler: AnimationHandler,
    private val title: String,
    isCollapsed: Boolean = true,
    content: JPanel.() -> Unit = {}
) : JPanel(MigLayout("nogrid, insets 0")), Animated { // @formatter:on
    companion object {
        private const val ANIMATION_SPEED: Float = 4F
    }

    var shouldBeCollapsed: Boolean = isCollapsed // Should it be collapsed in the future?
        private set
    var isCollapsed: Boolean = isCollapsed       // Is it collapsed right now?
        private set
    inline val isExpanded: Boolean
        get() = !isCollapsed

    private val headerArrow: HeaderArrow = HeaderArrow()
    private val headerLabel: HeaderLabel = HeaderLabel()
    private val separator: HeaderSeparator = HeaderSeparator()

    private val hoverAnimation: ColorTransition = ColorTransition( // @formatter:off
        startColor = { UIManager.getColor("Label.foreground") },
        endColor = { UIManager.getColor("Button.hoverBorderColor") }
    ).apply { // @formatter:on
        onUpdate { color ->
            separator.foreground = color
            separator.repaint()
            headerLabel.foreground = color
            headerLabel.repaint()
            headerArrow.foreground = color
            headerArrow.update()
        }
        registerAsAnimated(animationHandler, this)
    }

    private val content: JPanel = JPanel(MigLayout("nogrid, insets 0")).apply {
        isOpaque = false
        background = Color(0, 0, 0, 0)
        content()
    }

    private val contentWrapper: ContentWrapper = ContentWrapper()
    private var animationProgress: Float = 0F
    private var animationValue: Float = 0F
    private var animationReversed: Boolean = false
    override var isAnimationActive: Boolean = false
    private var isMouseOverHeader: Boolean = false

    private val onCollapsing: AtomicReference<() -> Unit> = AtomicReference {}
    private val onExpanding: AtomicReference<() -> Unit> = AtomicReference {}

    init {
        border = BorderFactory.createEmptyBorder()
        registerAsAnimated(animationHandler)
        // Header bar
        add(JPanel(MigLayout("nogrid, insets 0, align center")).apply {
            isOpaque = false
            add(headerArrow)
            add(headerLabel)
            add(separator, "w 100%")
            addMouseListener(object : MouseAdapter() {
                override fun mouseClicked(e: MouseEvent?) {
                    if (shouldBeCollapsed) expand()
                    else collapse()
                }

                override fun mouseEntered(e: MouseEvent?) {
                    isMouseOverHeader = true
                }

                override fun mouseExited(e: MouseEvent?) {
                    isMouseOverHeader = false
                }
            })
            hoverAnimation.whenMouseOver(this)
        }, "w 100%, wrap")
        // Content wrapper
        add(contentWrapper, "w 100%, h pref")
        addFocusListener(object : FocusAdapter() {
            override fun focusLost(e: FocusEvent?) {
                isMouseOverHeader = false
            }
        })
    }

    inline fun onCollapsing(crossinline handler: () -> Unit) {
        val oldHandler = onCollapsing.load()
        onCollapsing.store {
            oldHandler()
            handler()
        }
    }

    inline fun onExpanding(crossinline handler: () -> Unit) {
        val oldHandler = onExpanding.load()
        onExpanding.store {
            oldHandler()
            handler()
        }
    }

    override fun updateAnimation(deltaTime: Float) {
        var movement = ANIMATION_SPEED * deltaTime
        if (animationReversed) movement = -movement
        animationProgress = (animationProgress + movement).coerceIn(0F..1F)
        animationValue = Easings.easeInOutQuart(animationProgress)
        contentWrapper.updateSize()
        revalidate()
        repaint()
        // Animation stops itself when done playing once in either direction
        when {
            animationReversed && animationProgress == 0F -> {
                isCollapsed = true
                contentWrapper.isVisible = false
                isAnimationActive = false
            }

            !animationReversed && animationProgress == 1F -> {
                isCollapsed = false
                isAnimationActive = false
            }
        }
    }

    fun expand() {
        contentWrapper.isVisible = true
        shouldBeCollapsed = false
        animationReversed = false
        isAnimationActive = true
        onExpanding.load()()
    }

    fun collapse() {
        shouldBeCollapsed = true
        animationReversed = true
        isAnimationActive = true
        onCollapsing.load()()
    }

    // Allows auto-collapsing other panels in a group of collapsibles
    @JvmInline
    value class Group(val panels: ArrayList<CollapsiblePanel> = ArrayList()) {
        fun add(panel: CollapsiblePanel) {
            if (panel in panels) return
            panels += panel
            panel.onExpanding {
                for (groupPanel in panels) {
                    if (panel === groupPanel) continue
                    groupPanel.collapse()
                }
            }
        }
    }

    private inner class HeaderArrow : JLabel() {
        init {
            verticalAlignment = CENTER
            icon = MaterialDesign.MDI_ARROW_RIGHT_BOLD.adaptive { foreground }
        }

        fun update() {
            (icon as? AdaptiveIcon)?.update()
            repaint()
        }

        override fun paintComponent(g: Graphics) {
            val g2d = g as Graphics2D
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
            g2d.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE)
            g2d.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON)
            val cx = x.toDouble() + (width.toDouble() * 0.5)
            val cy = y.toDouble() + (height.toDouble() * 0.5)
            g2d.translate(cx, cy)
            // We rotate at most 90.0 from pointing left to pointing down
            g2d.rotate(Math.toRadians(animationValue * 90.0))
            g2d.translate(-cx, -cy)
            super.paintComponent(g2d)
        }
    }

    private inner class HeaderLabel : JLabel(title) {
        init {
            verticalAlignment = CENTER
            foreground = UIManager.getColor("Label.foreground")
        }

        override fun updateUI() {
            super.updateUI()
            foreground = UIManager.getColor("Label.foreground")
        }
    }

    private class HeaderSeparator : JSeparator(HORIZONTAL) {
        init {
            foreground = UIManager.getColor("Label.foreground")
        }

        override fun updateUI() {
            super.updateUI()
            foreground = UIManager.getColor("Label.foreground")
        }
    }

    private inner class ContentWrapper : JPanel(MigLayout("nogrid, insets 8px")) {
        private var actualBackground: Color = lightOrDark("Panel.background", 1.085F, 0.85F)

        init {
            isOpaque = false
            background = Color(0, 0, 0, 0)
            border = BorderFactory.createEmptyBorder()
            add(content, "w 100%")
            if (isCollapsed) maximumSize = Dimension(Int.MAX_VALUE, 0)
            isVisible = isCollapsed
        }

        override fun updateUI() {
            super.updateUI()
            actualBackground = lightOrDark("Panel.background", 1.085F, 0.85F)
        }

        override fun paint(g: Graphics) {
            val g2d = g.create() as Graphics2D
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
            g2d.color = actualBackground
            g2d.fillRoundRect(0, 0, width, height, 12, 12)
            g2d.dispose()
            super.paint(g)
        }

        fun updateSize() {
            val newHeight = (preferredSize.height.toFloat() * animationValue).toInt()
            maximumSize = Dimension(Int.MAX_VALUE, newHeight)
        }
    }
}