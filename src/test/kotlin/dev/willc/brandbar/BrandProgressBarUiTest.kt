package dev.willc.brandbar

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.awt.Color
import java.awt.Dimension
import java.awt.image.BufferedImage
import java.nio.file.Files
import javax.imageio.ImageIO
import javax.swing.JProgressBar

class BrandProgressBarUiTest {
    @Test
    fun paintsDeterminateProgressWithMonotonicFillAcrossValues() {
        val theme = BrandThemeLoader.loadBundled()
        val values = listOf(0, 25, 50, 75, 100)
        val renders = values.map { value ->
            renderProgressBar(
                theme = theme,
                indeterminate = false,
                value = value,
            )
        }

        val rightmostActiveColumns = renders.map { image ->
            rightmostNonBackgroundColumn(image, theme.trackColor, theme.borderColor)
        }

        for (i in 1 until rightmostActiveColumns.size) {
            assertTrue(
                "Expected active leading edge at ${values[i]}% (${rightmostActiveColumns[i]}) > ${values[i - 1]}% (${rightmostActiveColumns[i - 1]})",
                rightmostActiveColumns[i] > rightmostActiveColumns[i - 1],
            )
            assertFalse(
                "Consecutive progress renders must not be identical",
                imagesEqual(renders[i], renders[i - 1]),
            )
        }
    }

    @Test
    fun paintsIndeterminateBounceAndTravelFrames() {
        val baseTheme = BrandThemeLoader.loadBundled()
        val bounceTheme = baseTheme.copy(indeterminateMode = IndeterminateMode.BOUNCE)
        val frame0 = renderProgressBar(
            theme = bounceTheme,
            indeterminate = true,
            state = IndeterminateState(offsetPx = 12, direction = TravelDirection.RIGHT),
        )
        val frame1 = renderProgressBar(
            theme = bounceTheme,
            indeterminate = true,
            state = IndeterminateState(offsetPx = 140, direction = TravelDirection.RIGHT),
        )
        val frameReverse = renderProgressBar(
            theme = bounceTheme,
            indeterminate = true,
            state = IndeterminateState(offsetPx = 140, direction = TravelDirection.LEFT),
        )

        assertFalse(imagesEqual(frame0, frame1))
        assertFalse(imagesEqual(frame1, frameReverse))

        val travelTheme = baseTheme.copy(indeterminateMode = IndeterminateMode.TRAVEL)
        val travelBar = JProgressBar().apply {
            isIndeterminate = true
            size = Dimension(320, 28)
            preferredSize = size
        }
        val ui = BrandProgressBarUI { travelTheme }
        travelBar.setUI(ui)
        val beforeStep = paintComponent(travelBar, scale = 1.0)
        repeat(6) { ui.stepAnimation() }
        val afterStep = paintComponent(travelBar, scale = 1.0)
        assertFalse(imagesEqual(beforeStep, afterStep))
    }

    @Test
    fun forkRebrandDirectoryChangesRenderedAppearanceWithoutEditingCode() {
        val customDir = Files.createTempDirectory("brandbar-fork-rebrand")
        try {
            val customRunner = BufferedImage(20, 20, BufferedImage.TYPE_INT_ARGB)
            customRunner.createGraphics().also { g ->
                g.color = Color(0xFF, 0x00, 0x7F)
                g.fillOval(1, 1, 18, 18)
                g.dispose()
            }
            ImageIO.write(customRunner, "PNG", customDir.resolve("runner.png").toFile())
            Files.writeString(
                customDir.resolve("theme.json"),
                """
                {
                  "name": "Forked Company Brand",
                  "runnerImage": "runner.png",
                  "trailColors": ["#7C3AED", "#C026D3"],
                  "trailStyle": "STRIPES",
                  "trackColor": "#1E1B4B",
                  "borderColor": "#4338CA",
                  "barHeightPx": 22,
                  "trailHeightPx": 12,
                  "cornerRadiusPx": 8,
                  "indeterminateMode": "BOUNCE",
                  "frameIntervalMs": 30,
                  "stepPx": 4,
                  "indeterminateSegmentRatio": 0.35
                }
                """.trimIndent(),
            )

            val defaultRender = renderProgressBar(BrandThemeLoader.loadBundled(), indeterminate = false, value = 50)
            val customTheme = BrandThemeLoader.loadFromDirectory(customDir)
            val customRender = renderProgressBar(customTheme, indeterminate = false, value = 50)

            assertFalse(imagesEqual(defaultRender, customRender))
            assertTrue(
                "Custom render should contain custom trail color",
                containsColor(customRender, Color(0x7C, 0x3A, 0xED)),
            )
        } finally {
            customDir.toFile().deleteRecursively()
        }
    }

    @Test
    fun supportsHiDpiScaleAndPaintedProgressString() {
        val theme = BrandThemeLoader.loadBundled()
        val bar = JProgressBar(0, 100).apply {
            value = 60
            isStringPainted = true
            string = "Building… 60%"
            size = Dimension(320, 28)
            preferredSize = size
            setUI(BrandProgressBarUI { theme })
        }
        val hiDpi = paintComponent(bar, scale = 2.0)
        assertNotEquals(0, hiDpi.width)
        assertTrue(hiDpi.width == 640 && hiDpi.height == 56)
    }

    private fun renderProgressBar(
        theme: BrandTheme,
        indeterminate: Boolean,
        value: Int = 0,
        state: IndeterminateState = IndeterminateState(),
    ): BufferedImage {
        val ui = BrandProgressBarUI { theme }.also {
            it.indeterminateState = state
        }
        val bar = JProgressBar(0, 100).apply {
            isIndeterminate = indeterminate
            if (!indeterminate) {
                this.value = value
            }
            isOpaque = false
            size = Dimension(360, 28)
            preferredSize = size
            setUI(ui)
        }
        return paintComponent(bar, scale = 1.0)
    }

    private fun paintComponent(bar: JProgressBar, scale: Double): BufferedImage {
        val pixelW = (bar.width * scale).toInt()
        val pixelH = (bar.height * scale).toInt()
        val image = BufferedImage(pixelW, pixelH, BufferedImage.TYPE_INT_ARGB)
        val g = image.createGraphics()
        try {
            if (scale != 1.0) {
                g.scale(scale, scale)
            }
            bar.paint(g)
        } finally {
            g.dispose()
        }
        return image
    }

    private fun rightmostNonBackgroundColumn(
        image: BufferedImage,
        trackColor: Color,
        borderColor: Color,
    ): Int {
        val ignored = setOf(0, trackColor.rgb, borderColor.rgb)
        for (x in image.width - 1 downTo 0) {
            for (y in 3 until image.height - 3) {
                val argb = image.getRGB(x, y)
                val alpha = (argb ushr 24) and 0xFF
                if (alpha > 16 && argb !in ignored && !isCloseToEither(Color(argb, true), trackColor, borderColor)) {
                    return x
                }
            }
        }
        return -1
    }

    private fun isCloseToEither(pixel: Color, a: Color, b: Color): Boolean =
        colorDistance(pixel, a) <= 18 || colorDistance(pixel, b) <= 18

    private fun colorDistance(c1: Color, c2: Color): Int =
        kotlin.math.abs(c1.red - c2.red) +
            kotlin.math.abs(c1.green - c2.green) +
            kotlin.math.abs(c1.blue - c2.blue)

    private fun containsColor(image: BufferedImage, target: Color): Boolean {
        for (y in 0 until image.height) {
            for (x in 0 until image.width) {
                if (colorDistance(Color(image.getRGB(x, y), true), target) <= 6) {
                    return true
                }
            }
        }
        return false
    }

    private fun imagesEqual(a: BufferedImage, b: BufferedImage): Boolean {
        if (a.width != b.width || a.height != b.height) return false
        for (y in 0 until a.height) {
            for (x in 0 until a.width) {
                if (a.getRGB(x, y) != b.getRGB(x, y)) return false
            }
        }
        return true
    }
}
