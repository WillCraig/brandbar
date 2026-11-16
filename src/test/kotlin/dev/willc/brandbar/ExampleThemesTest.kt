package dev.willc.brandbar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Path
import javax.imageio.ImageIO

class ExampleThemesTest {
    private val examplesRoot: Path = System.getProperty("brandbar.examples.dir")
        ?.takeIf { it.isNotBlank() }
        ?.let { Path.of(it) }
        ?: Path.of("examples/themes")

    companion object {
        val EXPECTED_SLUGS = listOf(
            "anthropic",
            "apple",
            "cursor",
            "google",
            "medical-mutual",
            "meta",
            "netflix",
            "peter-griffin",
            "pnc",
            "sherwin-williams",
            "strix-works",
            "union-home-mortgage",
            "will-craig-personal",
        )
    }

    @Test
    fun everyExpectedExampleThemeExistsAndLoads() {
        assertTrue("examples/themes must exist at $examplesRoot", Files.isDirectory(examplesRoot))
        val found = Files.list(examplesRoot).use { stream ->
            stream.filter { Files.isDirectory(it) }.map { it.fileName.toString() }.sorted().toList()
        }
        assertEquals(EXPECTED_SLUGS, found)
        for (slug in EXPECTED_SLUGS) {
            val dir = examplesRoot.resolve(slug)
            val theme = BrandThemeLoader.loadFromDirectory(dir)
            assertTrue("$slug: name blank", theme.name.isNotBlank())
            assertTrue("$slug: runner empty", theme.runner.logicalWidth > 0 && theme.runner.logicalHeight > 0)
            assertTrue("$slug: no trail", theme.trailTile != null || theme.trailColors.isNotEmpty())
            assertTrue("$slug: missing SOURCES.md", Files.isRegularFile(dir.resolve("SOURCES.md")))
        }
    }

    @Test
    fun examplesAreOutsidePluginResources() {
        val bundled = Path.of("src/main/resources/brand")
        for (slug in EXPECTED_SLUGS) {
            assertTrue("$slug leaked into plugin resources", !Files.exists(bundled.resolve(slug)))
        }
    }

    @Test
    fun rendersGalleryWithFramePerTheme() {
        val out = Files.createTempDirectory("brandbar-gallery")
        val written = BrandScreenshotRenderer.renderGallery(out, examplesRoot)
        assertTrue(Files.isRegularFile(out.resolve("gallery-overview.png")))
        for (slug in EXPECTED_SLUGS) {
            for (frame in listOf(
                "determinate-000.png", "determinate-025.png", "determinate-050.png",
                "determinate-075.png", "determinate-100.png",
                "indeterminate-bounce-1.png",
            )) {
                val p = out.resolve(slug).resolve(frame)
                assertTrue("missing $p", Files.isRegularFile(p) && Files.size(p) > 256)
                assertTrue(ImageIO.read(p.toFile()) != null)
            }
        }
        assertTrue(written.size >= EXPECTED_SLUGS.size * 6)
    }
}
