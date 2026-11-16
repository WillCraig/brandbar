package dev.willc.brandbar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import javax.imageio.ImageIO

class BrandScreenshotRendererTest {
    @Test
    fun rendersDeterminateAndIndeterminatePngScreenshots() {
        val configuredDir = System.getProperty("brandbar.screenshots.dir")
        val outputDir = if (!configuredDir.isNullOrBlank()) {
            Path.of(configuredDir)
        } else {
            Files.createTempDirectory("brandbar-screenshots")
        }

        val written = BrandScreenshotRenderer.renderAll(outputDir)
        assertEquals(11, written.size)

        val hashes = mutableSetOf<String>()
        for (path in written) {
            assertTrue("Expected screenshot file at $path", Files.isRegularFile(path))
            assertTrue("Expected non-empty PNG at $path", Files.size(path) > 256)
            val decoded = ImageIO.read(path.toFile())
            assertTrue("Expected valid PNG dimensions for $path", decoded != null && decoded.width >= 800 && decoded.height >= 100)
            val digest = MessageDigest.getInstance("SHA-256")
                .digest(Files.readAllBytes(path))
                .joinToString("") { "%02x".format(it) }
            hashes += digest
        }

        assertEquals(
            "Every rendered determinate and indeterminate screenshot should be visually distinct",
            written.size,
            hashes.size,
        )
    }
}
