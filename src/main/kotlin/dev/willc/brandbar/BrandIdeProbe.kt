package dev.willc.brandbar

import com.intellij.openapi.wm.WindowManager
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Container
import java.awt.Dimension
import java.awt.Font
import java.awt.GridLayout
import java.awt.image.BufferedImage
import java.nio.file.Files
import java.nio.file.Path
import javax.imageio.ImageIO
import javax.swing.JComponent
import javax.swing.JFrame
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JProgressBar
import javax.swing.UIManager

object BrandIdeProbe {
    @JvmStatic
    fun verifyInstalledAndCaptureFrame(outputPngPath: String): String {
        val registeredUi = UIManager.get(BrandProgressBarInstaller.PROGRESS_BAR_UI_KEY)?.toString()
            ?: return "ERROR:UIManager ProgressBarUI key is null"
        if (registeredUi != BrandProgressBarUI::class.java.name) {
            return "ERROR:Expected ${BrandProgressBarUI::class.java.name}, got $registeredUi"
        }

        val bar25 = JProgressBar(0, 100).apply {
            value = 25
            preferredSize = Dimension(JBUI.scale(420), JBUI.scale(26))
        }
        val bar65 = JProgressBar(0, 100).apply {
            value = 65
            isStringPainted = true
            string = "Indexing workspace… 65%"
            preferredSize = Dimension(JBUI.scale(420), JBUI.scale(26))
        }
        val barIndeterminate = JProgressBar().apply {
            isIndeterminate = true
            preferredSize = Dimension(JBUI.scale(420), JBUI.scale(26))
        }

        for ((label, bar) in listOf("25%" to bar25, "65%" to bar65, "indeterminate" to barIndeterminate)) {
            if (bar.ui !is BrandProgressBarUI) {
                return "ERROR:Bar '$label' received ${bar.ui::class.java.name} instead of BrandProgressBarUI"
            }
        }
        (barIndeterminate.ui as? BrandProgressBarUI)?.indeterminateState =
            IndeterminateState(offsetPx = JBUI.scale(150), direction = TravelDirection.RIGHT)

        val frame: JFrame = WindowManager.getInstance().findVisibleFrame()
            ?: return "ERROR:No visible IDE JFrame found"
        if (frame.width < 640 || frame.height < 420) {
            frame.setSize(1120, 720)
        }

        val banner = buildPreviewBanner(bar25, bar65, barIndeterminate)
        val layeredPane = frame.layeredPane
        val bannerW = JBUI.scale(580).coerceAtMost(frame.width - JBUI.scale(48))
        val bannerH = JBUI.scale(180)
        val bannerX = (frame.width - bannerW - JBUI.scale(28)).coerceAtLeast(JBUI.scale(16))
        val bannerY = JBUI.scale(84).coerceAtMost((frame.height - bannerH - JBUI.scale(24)).coerceAtLeast(JBUI.scale(16)))
        banner.setBounds(bannerX, bannerY, bannerW, bannerH)
        layeredPane.add(banner, Integer.valueOf(400))
        try {
            banner.doLayout()
            layoutRecursively(banner)
            frame.validate()

            val image = BufferedImage(frame.width, frame.height, BufferedImage.TYPE_INT_ARGB)
            val g = image.createGraphics()
            try {
                frame.paint(g)
            } finally {
                g.dispose()
            }

            val outPath = Path.of(outputPngPath)
            outPath.parent?.let { Files.createDirectories(it) }
            ImageIO.write(image, "PNG", outPath.toFile())
            return "OK|ui=${bar25.ui::class.java.name}|frame=${frame.width}x${frame.height}"
        } finally {
            layeredPane.remove(banner)
            layeredPane.repaint()
        }
    }

    private fun buildPreviewBanner(
        bar25: JProgressBar,
        bar65: JProgressBar,
        barIndeterminate: JProgressBar,
    ): JComponent {
        val theme = BrandThemeHolder.theme
        return JPanel(BorderLayout()).apply {
            isOpaque = true
            background = theme.trackColor.darker()
            border = JBUI.Borders.compound(
                JBUI.Borders.customLine(theme.borderColor, 1),
                JBUI.Borders.empty(12, 16),
            )
            val stack = JPanel(GridLayout(4, 1, 0, JBUI.scale(8))).apply {
                isOpaque = false
                add(
                    JLabel("Brand Bar — Live In-IDE UIManager Preview (${theme.name})").apply {
                        font = font.deriveFont(Font.BOLD, JBUI.scaleFontSize(13f).toFloat())
                    },
                )
                add(labeledRow("Determinate 25%", bar25))
                add(labeledRow("Determinate 65%", bar65))
                add(labeledRow("Indeterminate", barIndeterminate))
            }
            add(stack, BorderLayout.CENTER)
        }
    }

    private fun labeledRow(caption: String, bar: JProgressBar): JPanel =
        JPanel(BorderLayout(JBUI.scale(12), 0)).apply {
            isOpaque = false
            add(
                JLabel(caption).apply {
                    preferredSize = Dimension(JBUI.scale(135), JBUI.scale(26))
                },
                BorderLayout.WEST,
            )
            add(bar, BorderLayout.CENTER)
        }

    private fun layoutRecursively(component: Component) {
        component.doLayout()
        if (component is Container) {
            for (child in component.components) {
                layoutRecursively(child)
            }
        }
    }
}
