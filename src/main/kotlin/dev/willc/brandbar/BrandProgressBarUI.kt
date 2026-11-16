package dev.willc.brandbar

import com.intellij.util.ui.JBUI
import java.awt.BasicStroke
import java.awt.Dimension
import java.awt.GradientPaint
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.geom.RoundRectangle2D
import javax.swing.JComponent
import javax.swing.JProgressBar
import javax.swing.SwingConstants
import javax.swing.Timer
import javax.swing.plaf.ComponentUI
import javax.swing.plaf.basic.BasicProgressBarUI
import kotlin.math.roundToInt

class BrandProgressBarUI(
    private val themeSupplier: () -> BrandTheme = { BrandThemeHolder.theme },
) : BasicProgressBarUI() {
    var indeterminateState: IndeterminateState = IndeterminateState()
    private var animationTimer: Timer? = null

    override fun installDefaults() {
        super.installDefaults()
        progressBar?.isOpaque = false
        progressBar?.border = JBUI.Borders.empty(1, 2)
    }

    override fun uninstallUI(c: JComponent) {
        stopAnimationTimer()
        super.uninstallUI(c)
    }

    override fun startAnimationTimer() {
        stopAnimationTimer()
        val interval = themeSupplier().frameIntervalMs.coerceIn(8, 1000)
        animationTimer = Timer(interval) {
            stepAnimation()
            progressBar?.repaint()
        }.apply {
            isRepeats = true
            start()
        }
    }

    override fun stopAnimationTimer() {
        animationTimer?.stop()
        animationTimer = null
    }

    fun stepAnimation() {
        val theme = themeSupplier()
        val bar = progressBar
        val insets = bar?.insets
        val innerWidth = if (bar != null && insets != null && bar.width > insets.left + insets.right) {
            bar.width - insets.left - insets.right
        } else {
            JBUI.scale(240)
        }
        val innerHeight = if (bar != null && insets != null && bar.height > insets.top + insets.bottom) {
            bar.height - insets.top - insets.bottom
        } else {
            JBUI.scale(theme.barHeightPx)
        }
        val (_, runnerW) = scaledRunnerDimensions(theme, innerHeight)
        indeterminateState = BrandBarGeometry.advanceIndeterminate(
            innerWidthPx = innerWidth,
            state = indeterminateState,
            mode = theme.indeterminateMode,
            segmentRatio = theme.indeterminateSegmentRatio,
            runnerWidthPx = runnerW,
            stepPx = JBUI.scale(theme.stepPx).coerceAtLeast(1),
        )
    }

    override fun getPreferredSize(c: JComponent?): Dimension {
        val theme = themeSupplier()
        val base = super.getPreferredSize(c) ?: Dimension(JBUI.scale(146), JBUI.scale(theme.barHeightPx))
        val insets = c?.insets ?: JBUI.emptyInsets()
        val barHeight = JBUI.scale(theme.barHeightPx) + insets.top + insets.bottom
        return if ((c as? JProgressBar)?.orientation == SwingConstants.VERTICAL) {
            Dimension(barHeight, base.height.coerceAtLeast(JBUI.scale(146)))
        } else {
            Dimension(base.width.coerceAtLeast(JBUI.scale(146)), barHeight)
        }
    }

    override fun getMinimumSize(c: JComponent?): Dimension {
        val pref = getPreferredSize(c)
        return if ((c as? JProgressBar)?.orientation == SwingConstants.VERTICAL) {
            Dimension(pref.width, JBUI.scale(48))
        } else {
            Dimension(JBUI.scale(48), pref.height)
        }
    }

    override fun getMaximumSize(c: JComponent?): Dimension {
        val pref = getPreferredSize(c)
        return if ((c as? JProgressBar)?.orientation == SwingConstants.VERTICAL) {
            Dimension(pref.width, Short.MAX_VALUE.toInt())
        } else {
            Dimension(Short.MAX_VALUE.toInt(), pref.height)
        }
    }

    override fun paintDeterminate(g: Graphics?, c: JComponent?) {
        val g2 = (g as? Graphics2D)?.create() as? Graphics2D ?: return
        val bar = c as? JProgressBar ?: run {
            g2.dispose()
            return
        }
        try {
            val inner = innerBoundsOf(bar) ?: return
            if (bar.orientation == SwingConstants.VERTICAL) {
                super.paintDeterminate(g2, bar)
                return
            }
            val theme = themeSupplier()
            val (runnerH, runnerW) = scaledRunnerDimensions(theme, inner.height)
            val trailH = JBUI.scale(theme.trailHeightPx).coerceIn(2, inner.height.coerceAtLeast(2))
            val fraction = bar.percentComplete.coerceIn(0.0, 1.0)
            val layout = BrandBarGeometry.computeDeterminate(
                innerBounds = inner,
                fraction = fraction,
                runnerWidthPx = runnerW,
                runnerHeightPx = runnerH,
                trailHeightPx = trailH,
            )

            paintBarFrame(
                g2 = g2,
                bar = bar,
                theme = theme,
                track = layout.track,
                trail = layout.trail,
                runner = layout.runner,
                direction = TravelDirection.RIGHT,
                amountFull = layout.trail.width,
            )
        } finally {
            g2.dispose()
        }
    }

    override fun paintIndeterminate(g: Graphics?, c: JComponent?) {
        val g2 = (g as? Graphics2D)?.create() as? Graphics2D ?: return
        val bar = c as? JProgressBar ?: run {
            g2.dispose()
            return
        }
        try {
            val inner = innerBoundsOf(bar) ?: return
            if (bar.orientation == SwingConstants.VERTICAL) {
                super.paintIndeterminate(g2, bar)
                return
            }
            val theme = themeSupplier()
            val (runnerH, runnerW) = scaledRunnerDimensions(theme, inner.height)
            val trailH = JBUI.scale(theme.trailHeightPx).coerceIn(2, inner.height.coerceAtLeast(2))
            val layout = BrandBarGeometry.computeIndeterminate(
                innerBounds = inner,
                state = indeterminateState,
                mode = theme.indeterminateMode,
                segmentRatio = theme.indeterminateSegmentRatio,
                runnerWidthPx = runnerW,
                runnerHeightPx = runnerH,
                trailHeightPx = trailH,
            )

            paintBarFrame(
                g2 = g2,
                bar = bar,
                theme = theme,
                track = layout.track,
                trail = layout.trail,
                runner = layout.runner,
                direction = layout.direction,
                amountFull = layout.trail.width,
            )
        } finally {
            g2.dispose()
        }
    }

    private fun innerBoundsOf(bar: JProgressBar): BarBounds? {
        val insets = bar.insets
        val w = bar.width - insets.left - insets.right
        val h = bar.height - insets.top - insets.bottom
        if (w <= 0 || h <= 0) return null
        return BarBounds(insets.left, insets.top, w, h)
    }

    private fun scaledRunnerDimensions(theme: BrandTheme, availableHeight: Int): Pair<Int, Int> {
        val targetHeight = JBUI.scale(theme.barHeightPx)
            .coerceAtMost(availableHeight)
            .coerceAtLeast(4)
        val aspect = theme.runner.logicalWidth.toDouble() / theme.runner.logicalHeight.coerceAtLeast(1).toDouble()
        val targetWidth = (targetHeight * aspect).roundToInt().coerceAtLeast(4)
        return targetHeight to targetWidth
    }

    private fun paintBarFrame(
        g2: Graphics2D,
        bar: JProgressBar,
        theme: BrandTheme,
        track: BarBounds,
        trail: BarBounds,
        runner: BarBounds,
        direction: TravelDirection,
        amountFull: Int,
    ) {
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE)

        val arc = JBUI.scale(theme.cornerRadiusPx).toFloat() * 2f
        val trackShape = RoundRectangle2D.Float(
            track.x.toFloat(),
            track.y.toFloat(),
            track.width.toFloat(),
            track.height.toFloat(),
            arc,
            arc,
        )

        g2.color = theme.trackColor
        g2.fill(trackShape)

        val priorClip = g2.clip
        g2.clip(trackShape)

        if (trail.width > 0 && trail.height > 0) {
            paintTrail(g2, theme, trail)
        }

        g2.clip = priorClip
        g2.color = theme.borderColor
        g2.stroke = BasicStroke(JBUI.scale(1).toFloat())
        g2.draw(
            RoundRectangle2D.Float(
                track.x + 0.5f,
                track.y + 0.5f,
                (track.width - 1).coerceAtLeast(1).toFloat(),
                (track.height - 1).coerceAtLeast(1).toFloat(),
                arc,
                arc,
            ),
        )

        theme.paintRunner(
            g = g2,
            x = runner.x,
            y = runner.y,
            width = runner.width,
            height = runner.height,
            direction = direction,
        )

        if (bar.isStringPainted) {
            val insets = bar.insets
            paintString(g2, track.x, track.y, track.width, track.height, amountFull, insets)
        }
    }

    private fun paintTrail(g2: Graphics2D, theme: BrandTheme, trail: BarBounds) {
        val trailClip = g2.create(trail.x, trail.y, trail.width, trail.height) as Graphics2D
        try {
            val trailArc = (trail.height.toFloat()).coerceAtMost(JBUI.scale(theme.cornerRadiusPx).toFloat() * 2f)
            val roundedTrail = RoundRectangle2D.Float(
                0f,
                0f,
                trail.width.toFloat(),
                trail.height.toFloat(),
                trailArc,
                trailArc,
            )
            trailClip.clip(roundedTrail)

            val colors = theme.trailColors
            if (colors.isNotEmpty()) {
                when (theme.trailStyle) {
                    TrailStyle.STRIPES -> {
                        val bandCount = colors.size
                        for (index in colors.indices) {
                            val y0 = (trail.height * index) / bandCount
                            val y1 = (trail.height * (index + 1)) / bandCount
                            trailClip.color = colors[index]
                            trailClip.fillRect(0, y0, trail.width, (y1 - y0).coerceAtLeast(1))
                        }
                    }
                    TrailStyle.GRADIENT -> {
                        if (colors.size == 1) {
                            trailClip.color = colors.first()
                            trailClip.fillRect(0, 0, trail.width, trail.height)
                        } else {
                            val segmentCount = colors.size - 1
                            for (index in 0 until segmentCount) {
                                val x0 = (trail.width * index) / segmentCount
                                val x1 = (trail.width * (index + 1)) / segmentCount
                                trailClip.paint = GradientPaint(
                                    x0.toFloat(),
                                    0f,
                                    colors[index],
                                    x1.coerceAtLeast(x0 + 1).toFloat(),
                                    0f,
                                    colors[index + 1],
                                )
                                trailClip.fillRect(x0, 0, (x1 - x0).coerceAtLeast(1), trail.height)
                            }
                        }
                    }
                }
            }

            val tile = theme.trailTile
            if (tile != null) {
                val tileH = trail.height.coerceAtLeast(1)
                val tileAspect = tile.logicalWidth.toDouble() / tile.logicalHeight.coerceAtLeast(1).toDouble()
                val tileW = (tileH * tileAspect).roundToInt().coerceAtLeast(2)
                var cursorX = 0
                while (cursorX < trail.width) {
                    tile.paint(trailClip, cursorX, 0, tileW, tileH, flipHorizontal = false)
                    cursorX += tileW
                }
            }
        } finally {
            trailClip.dispose()
        }
    }

    companion object {
        @Suppress("ACCIDENTAL_OVERRIDE", "UNUSED_PARAMETER")
        @JvmStatic
        fun createUI(component: JComponent): ComponentUI = BrandProgressBarUI()
    }
}
