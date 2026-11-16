package dev.willc.brandbar

import java.awt.Color
import java.awt.Dimension
import java.awt.Font
import java.awt.RenderingHints
import java.awt.geom.RoundRectangle2D
import java.awt.image.BufferedImage
import java.nio.file.Files
import java.nio.file.Path
import javax.imageio.ImageIO
import javax.swing.JProgressBar

data class ScreenshotSpec(
    val fileName: String,
    val label: String,
    val indeterminate: Boolean,
    val value: Int = 0,
    val modeOverride: IndeterminateMode? = null,
    val state: IndeterminateState = IndeterminateState(),
)

object BrandScreenshotRenderer {
    private const val BAR_WIDTH = 420
    private const val BAR_HEIGHT = 28
    private const val CARD_PADDING_X = 24
    private const val CARD_PADDING_Y = 18
    private const val SCALE = 2.0

    fun defaultSpecs(): List<ScreenshotSpec> = listOf(
        ScreenshotSpec(
            fileName = "determinate-000.png",
            label = "Determinate — 0%",
            indeterminate = false,
            value = 0,
        ),
        ScreenshotSpec(
            fileName = "determinate-025.png",
            label = "Determinate — 25%",
            indeterminate = false,
            value = 25,
        ),
        ScreenshotSpec(
            fileName = "determinate-050.png",
            label = "Determinate — 50%",
            indeterminate = false,
            value = 50,
        ),
        ScreenshotSpec(
            fileName = "determinate-075.png",
            label = "Determinate — 75%",
            indeterminate = false,
            value = 75,
        ),
        ScreenshotSpec(
            fileName = "determinate-100.png",
            label = "Determinate — 100%",
            indeterminate = false,
            value = 100,
        ),
        ScreenshotSpec(
            fileName = "indeterminate-bounce-1.png",
            label = "Indeterminate (Bounce) — Forward 25%",
            indeterminate = true,
            modeOverride = IndeterminateMode.BOUNCE,
            state = IndeterminateState(offsetPx = 64, direction = TravelDirection.RIGHT),
        ),
        ScreenshotSpec(
            fileName = "indeterminate-bounce-2.png",
            label = "Indeterminate (Bounce) — Forward 80%",
            indeterminate = true,
            modeOverride = IndeterminateMode.BOUNCE,
            state = IndeterminateState(offsetPx = 205, direction = TravelDirection.RIGHT),
        ),
        ScreenshotSpec(
            fileName = "indeterminate-bounce-3-reverse.png",
            label = "Indeterminate (Bounce) — Reverse 55%",
            indeterminate = true,
            modeOverride = IndeterminateMode.BOUNCE,
            state = IndeterminateState(offsetPx = 140, direction = TravelDirection.LEFT),
        ),
        ScreenshotSpec(
            fileName = "indeterminate-travel-1.png",
            label = "Indeterminate (Travel) — Frame A",
            indeterminate = true,
            modeOverride = IndeterminateMode.TRAVEL,
            state = IndeterminateState(offsetPx = 95, direction = TravelDirection.RIGHT),
        ),
        ScreenshotSpec(
            fileName = "indeterminate-travel-2.png",
            label = "Indeterminate (Travel) — Frame B",
            indeterminate = true,
            modeOverride = IndeterminateMode.TRAVEL,
            state = IndeterminateState(offsetPx = 245, direction = TravelDirection.RIGHT),
        ),
    )

    fun renderTheme(outputDir: Path, baseTheme: BrandTheme): List<Path> = renderAll(outputDir, baseTheme)

    fun renderGallery(outputDir: Path, themesRoot: Path): List<Path> {
        Files.createDirectories(outputDir)
        val slugs = Files.list(themesRoot).use { stream ->
            stream.filter { Files.isDirectory(it) }.map { it.fileName.toString() }.sorted().toList()
        }
        val written = mutableListOf<Path>()
        val galleryCards = mutableListOf<Pair<ScreenshotSpec, BufferedImage>>()
        for (slug in slugs) {
            val theme = BrandThemeLoader.loadFromDirectory(themesRoot.resolve(slug))
            written += renderAll(outputDir.resolve(slug), theme)
            val spec = defaultSpecs().first { it.fileName == "determinate-050.png" }
                .copy(label = "${theme.name} — 50%")
            galleryCards += spec to renderSingleCard(theme, spec)
        }
        val overviewPath = outputDir.resolve("gallery-overview.png")
        ImageIO.write(renderOverviewSheet("Example Gallery", galleryCards), "PNG", overviewPath.toFile())
        written.add(overviewPath)
        return written
    }

    fun renderAll(
        outputDir: Path,
        baseTheme: BrandTheme = BrandThemeLoader.loadBundled(),
    ): List<Path> {
        Files.createDirectories(outputDir)
        val specs = defaultSpecs()
        val written = mutableListOf<Path>()
        val cardImages = mutableListOf<Pair<ScreenshotSpec, BufferedImage>>()

        for (spec in specs) {
            val effectiveTheme = if (spec.modeOverride != null) {
                baseTheme.copy(indeterminateMode = spec.modeOverride)
            } else {
                baseTheme
            }
            val card = renderSingleCard(effectiveTheme, spec)
            val target = outputDir.resolve(spec.fileName)
            ImageIO.write(card, "PNG", target.toFile())
            written.add(target)
            cardImages.add(spec to card)
        }

        val overviewPath = outputDir.resolve("brandbar-overview.png")
        val overview = renderOverviewSheet(baseTheme.name, cardImages)
        ImageIO.write(overview, "PNG", overviewPath.toFile())
        written.add(overviewPath)
        return written
    }

    private fun renderSingleCard(theme: BrandTheme, spec: ScreenshotSpec): BufferedImage {
        val logicalW = BAR_WIDTH + CARD_PADDING_X * 2
        val logicalH = BAR_HEIGHT + CARD_PADDING_Y * 2 + 20
        val image = BufferedImage(
            (logicalW * SCALE).toInt(),
            (logicalH * SCALE).toInt(),
            BufferedImage.TYPE_INT_ARGB,
        )
        val g = image.createGraphics()
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
            g.scale(SCALE, SCALE)

            g.color = Color(0x0B, 0x11, 0x20)
            g.fill(RoundRectangle2D.Double(0.0, 0.0, logicalW.toDouble(), logicalH.toDouble(), 14.0, 14.0))
            g.color = Color(0x1E, 0x29, 0x3B)
            g.draw(RoundRectangle2D.Double(0.5, 0.5, logicalW - 1.0, logicalH - 1.0, 14.0, 14.0))

            g.color = Color(0xCBD5E1)
            g.font = Font(Font.SANS_SERIF, Font.BOLD, 12)
            g.drawString(spec.label, CARD_PADDING_X, CARD_PADDING_Y + 11)

            val ui = BrandProgressBarUI { theme }.also {
                it.indeterminateState = spec.state
            }
            val bar = JProgressBar(0, 100).apply {
                isIndeterminate = spec.indeterminate
                if (!spec.indeterminate) {
                    value = spec.value
                }
                isOpaque = false
                size = Dimension(BAR_WIDTH, BAR_HEIGHT)
                preferredSize = size
                setUI(ui)
            }

            val barGraphics = g.create(CARD_PADDING_X, CARD_PADDING_Y + 20, BAR_WIDTH, BAR_HEIGHT)
            try {
                bar.paint(barGraphics)
            } finally {
                barGraphics.dispose()
            }
        } finally {
            g.dispose()
        }
        return image
    }

    private fun renderOverviewSheet(
        themeName: String,
        cards: List<Pair<ScreenshotSpec, BufferedImage>>,
    ): BufferedImage {
        val marginPx = 28
        val gapPx = 14
        val headerHeightPx = 68
        val cardWidthPx = cards.first().second.width
        val cardHeightPx = cards.first().second.height
        val columns = 2
        val rows = (cards.size + columns - 1) / columns
        val totalWidth = marginPx * 2 + cardWidthPx * columns + gapPx * (columns - 1)
        val totalHeight = marginPx * 2 + headerHeightPx + cardHeightPx * rows + gapPx * (rows - 1)

        val sheet = BufferedImage(totalWidth, totalHeight, BufferedImage.TYPE_INT_ARGB)
        val g = sheet.createGraphics()
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)

            g.color = Color(0x02, 0x06, 0x17)
            g.fillRect(0, 0, totalWidth, totalHeight)

            g.color = Color(0xF8, 0xFA, 0xFC)
            g.font = Font(Font.SANS_SERIF, Font.BOLD, 28)
            g.drawString("Brand Bar — Headless Review Sheet ($themeName)", marginPx, marginPx + 30)

            g.color = Color(0x94, 0xA3, 0xB8)
            g.font = Font(Font.SANS_SERIF, Font.PLAIN, 18)
            g.drawString(
                "Determinate (0%–100%) and indeterminate (bounce + travel) states rendered via Java2D without an IDE",
                marginPx,
                marginPx + 56,
            )

            cards.forEachIndexed { index, (_, card) ->
                val col = index % columns
                val row = index / columns
                val x = marginPx + col * (cardWidthPx + gapPx)
                val y = marginPx + headerHeightPx + row * (cardHeightPx + gapPx)
                g.drawImage(card, x, y, null)
            }
        } finally {
            g.dispose()
        }
        return sheet
    }
}

object ScreenshotsCli {
    @JvmStatic
    fun main(args: Array<String>) {
        val targetDir = if (args.isNotEmpty()) {
            Path.of(args[0])
        } else {
            Path.of("build/reports/screenshots")
        }
        val themeDir = System.getProperty("brandbar.theme")?.takeIf { it.isNotBlank() }
            ?: args.getOrNull(1)
        val galleryRoot = System.getProperty("brandbar.themesRoot")?.takeIf { it.isNotBlank() }
        val written = when {
            galleryRoot != null -> BrandScreenshotRenderer.renderGallery(targetDir, Path.of(galleryRoot))
            themeDir != null -> BrandScreenshotRenderer.renderTheme(
                targetDir,
                BrandThemeLoader.loadFromDirectory(Path.of(themeDir)),
            )
            else -> BrandScreenshotRenderer.renderAll(targetDir)
        }
        println("Rendered ${written.size} Brand Bar screenshots to ${targetDir.toAbsolutePath()}")
    }
}
