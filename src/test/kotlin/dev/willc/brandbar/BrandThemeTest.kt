package dev.willc.brandbar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.awt.Color
import java.awt.image.BufferedImage
import java.nio.file.Files
import java.nio.file.Path
import javax.imageio.ImageIO

class BrandThemeTest {
    @Test
    fun loadsBundledBrandTheme() {
        val theme = BrandThemeLoader.loadBundled()
        assertTrue(theme.name.isNotBlank())
        assertTrue(theme.runner.logicalWidth > 0)
        assertTrue(theme.runner.logicalHeight > 0)
        assertTrue(theme.trailTile != null || theme.trailColors.isNotEmpty())
        assertTrue(theme.barHeightPx >= theme.trailHeightPx)
        assertTrue(theme.frameIntervalMs > 0)
        assertTrue(theme.stepPx > 0)
    }

    @Test
    fun parsesRgbAndArgbHexColorsAndRejectsInvalidValues() {
        assertEquals(Color(0x0F, 0x76, 0x6E), BrandThemeLoader.parseHexColor("#0F766E", "trackColor"))
        val translucent = BrandThemeLoader.parseHexColor("#8014B8A6", "trailColor")
        assertEquals(0x80, translucent.alpha)
        assertEquals(0x14, translucent.red)
        assertEquals(0xB8, translucent.green)
        assertEquals(0xA6, translucent.blue)

        for (bad in listOf("", "0F766E", "#12345", "#1234567", "#GGGGGG", "rgb(1,2,3)")) {
            assertThrows(BrandThemeException::class.java) {
                BrandThemeLoader.parseHexColor(bad, "trackColor")
            }
        }
    }

    @Test
    fun loadsCustomDirectoryWithRetinaPngAndDedicatedReverseRunner() {
        val dir = Files.createTempDirectory("brandbar-custom-png")
        try {
            writeSolidPng(dir.resolve("runner.png"), 16, 16, Color(0xE1, 0x1D, 0x48))
            writeSolidPng(dir.resolve("runner@2x.png"), 32, 32, Color(0x25, 0x63, 0xEB))
            writeSolidPng(dir.resolve("runner-left.png"), 16, 16, Color(0x16, 0xA3, 0x4A))
            writeSolidPng(dir.resolve("trail.png"), 8, 8, Color(0xF5, 0x9E, 0x0B))
            Files.writeString(
                dir.resolve("theme.json"),
                """
                {
                  "name": "Acme Custom",
                  "runnerImage": "runner.png",
                  "reverseRunnerImage": "runner-left.png",
                  "flipRunnerOnReverse": true,
                  "trailImage": "trail.png",
                  "trailColors": ["#F59E0B", "#EF4444"],
                  "trailStyle": "STRIPES",
                  "trackColor": "#111827",
                  "borderColor": "#374151",
                  "barHeightPx": 22,
                  "trailHeightPx": 12,
                  "cornerRadiusPx": 6,
                  "indeterminateMode": "BOUNCE",
                  "frameIntervalMs": 28,
                  "stepPx": 5,
                  "indeterminateSegmentRatio": 0.4
                }
                """.trimIndent(),
            )

            val theme = BrandThemeLoader.loadFromDirectory(dir)
            assertEquals("Acme Custom", theme.name)
            assertNotNull(theme.reverseRunner)
            assertNotNull(theme.trailTile)
            assertEquals(22, theme.barHeightPx)
            assertEquals(12, theme.trailHeightPx)

            // 1x uses runner.png (crimson), 2x uses runner@2x.png (royal blue).
            val img1x = theme.runner.toBufferedImage(16, 16, scale = 1.0)
            val img2x = theme.runner.toBufferedImage(16, 16, scale = 2.0)
            assertEquals(32, img2x.width)
            assertEquals(Color(0xE1, 0x1D, 0x48).rgb, img1x.getRGB(8, 8))
            assertEquals(Color(0x25, 0x63, 0xEB).rgb, img2x.getRGB(16, 16))

            // Dedicated reverseRunner paints green when moving LEFT.
            val leftCanvas = BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB)
            val g = leftCanvas.createGraphics()
            try {
                theme.paintRunner(g, 0, 0, 16, 16, TravelDirection.LEFT)
            } finally {
                g.dispose()
            }
            assertEquals(Color(0x16, 0xA3, 0x4A).rgb, leftCanvas.getRGB(8, 8))
        } finally {
            dir.toFile().deleteRecursively()
        }
    }

    @Test
    fun flipsAsymmetricRunnerHorizontallyWhenReverseRunnerOmitted() {
        val dir = Files.createTempDirectory("brandbar-flip-test")
        try {
            // Left half red, right half cyan so horizontal mirroring is unmistakable.
            val asymmetric = BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB)
            for (y in 0 until 16) {
                for (x in 0 until 16) {
                    val color = if (x < 8) Color(0xDC, 0x26, 0x26) else Color(0x06, 0xB6, 0xD4)
                    asymmetric.setRGB(x, y, color.rgb)
                }
            }
            ImageIO.write(asymmetric, "PNG", dir.resolve("runner.png").toFile())
            Files.writeString(
                dir.resolve("theme.json"),
                """
                {
                  "name": "Flip Check",
                  "runnerImage": "runner.png",
                  "flipRunnerOnReverse": true,
                  "trailColors": ["#06B6D4"],
                  "trackColor": "#0F172A",
                  "borderColor": "#334155",
                  "barHeightPx": 20,
                  "trailHeightPx": 10,
                  "cornerRadiusPx": 4,
                  "indeterminateMode": "BOUNCE",
                  "frameIntervalMs": 30,
                  "stepPx": 4,
                  "indeterminateSegmentRatio": 0.35
                }
                """.trimIndent(),
            )

            val theme = BrandThemeLoader.loadFromDirectory(dir)
            assertNull(theme.reverseRunner)

            val rightCanvas = BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB)
            rightCanvas.createGraphics().also { g ->
                theme.paintRunner(g, 0, 0, 16, 16, TravelDirection.RIGHT)
                g.dispose()
            }
            val leftCanvas = BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB)
            leftCanvas.createGraphics().also { g ->
                theme.paintRunner(g, 0, 0, 16, 16, TravelDirection.LEFT)
                g.dispose()
            }

            assertEquals(Color(0xDC, 0x26, 0x26).rgb, rightCanvas.getRGB(2, 8))
            assertEquals(Color(0x06, 0xB6, 0xD4).rgb, rightCanvas.getRGB(13, 8))
            assertEquals(Color(0x06, 0xB6, 0xD4).rgb, leftCanvas.getRGB(2, 8))
            assertEquals(Color(0xDC, 0x26, 0x26).rgb, leftCanvas.getRGB(13, 8))
        } finally {
            dir.toFile().deleteRecursively()
        }
    }

    @Test
    fun loadsSvgRunnerAtStandardAndHiDpiScales() {
        val dir = Files.createTempDirectory("brandbar-svg-test")
        try {
            Files.writeString(
                dir.resolve("runner.svg"),
                """
                <svg xmlns="http://www.w3.org/2000/svg" width="20" height="20" viewBox="0 0 20 20">
                  <rect x="2" y="2" width="16" height="16" rx="3" fill="#7C3AED"/>
                </svg>
                """.trimIndent(),
            )
            Files.writeString(
                dir.resolve("theme.json"),
                """
                {
                  "name": "Vector Brand",
                  "runnerImage": "runner.svg",
                  "trailColors": ["#7C3AED", "#EC4899"],
                  "trailStyle": "GRADIENT",
                  "trackColor": "#18181B",
                  "borderColor": "#3F3F46",
                  "barHeightPx": 20,
                  "trailHeightPx": 8,
                  "cornerRadiusPx": 6,
                  "indeterminateMode": "TRAVEL",
                  "frameIntervalMs": 32,
                  "stepPx": 4,
                  "indeterminateSegmentRatio": 0.3
                }
                """.trimIndent(),
            )

            val theme = BrandThemeLoader.loadFromDirectory(dir)
            val raster1x = theme.runner.toBufferedImage(20, 20, scale = 1.0)
            val raster2x = theme.runner.toBufferedImage(20, 20, scale = 2.0)
            assertEquals(20, raster1x.width)
            assertEquals(40, raster2x.width)
            assertEquals(Color(0x7C, 0x3A, 0xED).rgb, raster1x.getRGB(10, 10))
            assertEquals(Color(0x7C, 0x3A, 0xED).rgb, raster2x.getRGB(20, 20))
        } finally {
            dir.toFile().deleteRecursively()
        }
    }

    @Test
    fun validatesMissingFilesMalformedJsonAndOutOfBoundsSettings() {
        val emptyDir = Files.createTempDirectory("brandbar-empty")
        try {
            assertThrows(BrandThemeException::class.java) {
                BrandThemeLoader.loadFromDirectory(emptyDir)
            }

            Files.writeString(emptyDir.resolve("theme.json"), "{ invalid json ")
            assertThrows(BrandThemeException::class.java) {
                BrandThemeLoader.loadFromDirectory(emptyDir)
            }
        } finally {
            emptyDir.toFile().deleteRecursively()
        }

        assertInvalidThemeJson(
            """{"name":"MissingRunner","runnerImage":"missing.png","trailColors":["#14B8A6"]}""",
            createRunner = false,
        )
        assertInvalidThemeJson(
            """{"name":"MissingReverse","runnerImage":"runner.png","reverseRunnerImage":"missing-rev.png","trailColors":["#14B8A6"]}""",
        )
        assertInvalidThemeJson(
            """{"name":"MissingTrailTile","runnerImage":"runner.png","trailImage":"missing-trail.png","trailColors":["#14B8A6"]}""",
        )
        assertInvalidThemeJson(
            """{"name":"NoTrailDefined","runnerImage":"runner.png","trailColors":[]}""",
        )
        assertInvalidThemeJson(
            """{"name":"TrailTallerThanBar","runnerImage":"runner.png","trailColors":["#14B8A6"],"barHeightPx":16,"trailHeightPx":20}""",
        )
        assertInvalidThemeJson(
            """{"name":"ZeroStep","runnerImage":"runner.png","trailColors":["#14B8A6"],"stepPx":0}""",
        )
        assertInvalidThemeJson(
            """{"name":"BadRatio","runnerImage":"runner.png","trailColors":["#14B8A6"],"indeterminateSegmentRatio":1.2}""",
        )
        assertInvalidThemeJson(
            """{"name":"BadMode","runnerImage":"runner.png","trailColors":["#14B8A6"],"indeterminateMode":"TELEPORT"}""",
        )
        assertFalse(false)
    }

    private fun assertInvalidThemeJson(json: String, createRunner: Boolean = true) {
        val dir = Files.createTempDirectory("brandbar-invalid")
        try {
            if (createRunner) {
                writeSolidPng(dir.resolve("runner.png"), 16, 16, Color.WHITE)
            }
            Files.writeString(dir.resolve("theme.json"), json)
            assertThrows(BrandThemeException::class.java) {
                BrandThemeLoader.loadFromDirectory(dir)
            }
        } finally {
            dir.toFile().deleteRecursively()
        }
    }

    private fun writeSolidPng(path: Path, width: Int, height: Int, color: Color) {
        val img = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
        val g = img.createGraphics()
        try {
            g.color = color
            g.fillRect(0, 0, width, height)
        } finally {
            g.dispose()
        }
        ImageIO.write(img, "PNG", path.toFile())
    }
}
