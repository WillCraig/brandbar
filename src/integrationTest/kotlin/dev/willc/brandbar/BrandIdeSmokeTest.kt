package dev.willc.brandbar

import com.intellij.driver.client.Remote
import com.intellij.driver.model.OnDispatcher
import com.intellij.driver.sdk.openFile
import com.intellij.driver.sdk.ui.components.ideFrame
import com.intellij.driver.sdk.ui.ui
import com.intellij.driver.sdk.waitForIndicators
import com.intellij.ide.starter.driver.engine.runIdeWithDriver
import com.intellij.ide.starter.ide.IdeProductProvider
import com.intellij.ide.starter.models.TestCase
import com.intellij.ide.starter.plugins.PluginConfigurator
import com.intellij.ide.starter.project.LocalProjectInfo
import com.intellij.ide.starter.runner.Starter
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import javax.imageio.ImageIO
import kotlin.io.path.Path
import kotlin.time.Duration.Companion.minutes

@Remote(value = "dev.willc.brandbar.BrandIdeProbe", plugin = "dev.willc.brandbar")
interface BrandIdeProbeRef {
    fun verifyInstalledAndCaptureFrame(outputPngPath: String): String
}

class BrandIdeSmokeTest {
    @Test
    @Timeout(value = 20, unit = TimeUnit.MINUTES)
    fun bootsCommunityIdeInstallsBrandProgressBarUiAndCapturesScreenshot() {
        val projectDir = Files.createTempDirectory("brandbar-ide-smoke")
        Files.writeString(
            projectDir.resolve("README.md"),
            "# Brand Bar Smoke Project\n\nVerifies BrandProgressBarUI inside a booted JetBrains IDE.\n",
        )

        val configuredDir = System.getProperty("brandbar.ide.screenshots.dir")
        val screenshotDir = if (!configuredDir.isNullOrBlank()) {
            Path.of(configuredDir)
        } else {
            Path.of("build/reports/ide-screenshots")
        }
        Files.createDirectories(screenshotDir)
        val inIdePng = screenshotDir.resolve("in-ide-brandbar.png").toAbsolutePath()

        val version = System.getProperty("ide.test.version")
        val context = Starter.newContext(
            testName = "brandBarSmoke",
            TestCase(IdeProductProvider.IC, LocalProjectInfo(projectDir)).withVersion(version),
        ).apply {
            PluginConfigurator(this).installPluginFromPath(Path(System.getProperty("path.to.build.plugin")))
            applyVMOptionsPatch {
                addSystemProperty("idea.trust.all.projects", "true")
                addSystemProperty("ide.show.tips.on.startup.default.value", "false")
                addSystemProperty("idea.qodana.thirdpartyplugins.accept", "true")
                addSystemProperty("jb.consents.confirmation.enabled", "false")
            }
        }

        var probeResult = ""
        var driverFailure: Throwable? = null
        try {
            context.runIdeWithDriver().useDriverAndCloseIde {
                waitForIndicators(3.minutes)
                openFile("README.md")
                val frame = ui.ideFrame()
                frame.toFront()

                probeResult = withContext(OnDispatcher.EDT) {
                    utility(BrandIdeProbeRef::class).verifyInstalledAndCaptureFrame(inIdePng.toString())
                }
            }
        } catch (failure: Throwable) {
            driverFailure = failure
        }

        try {
            driverFailure?.let { throw it }
            assertTrue(
                probeResult.startsWith("OK|"),
                "Expected BrandIdeProbe to report OK inside booted IDE, got: $probeResult",
            )
            assertTrue(Files.isRegularFile(inIdePng), "Expected in-IDE screenshot at $inIdePng")
            assertTrue(Files.size(inIdePng) > 4096, "Expected non-trivial in-IDE PNG at $inIdePng")
            val decoded = ImageIO.read(inIdePng.toFile())
            assertNotNull(decoded, "Expected readable PNG image at $inIdePng")
            assertTrue(decoded.width >= 640 && decoded.height >= 420, "Unexpected IDE screenshot dimensions: ${decoded.width}x${decoded.height}")
        } finally {
            if (driverFailure == null) {
                projectDir.toFile().deleteRecursively()
            }
        }
    }
}
