package dev.willc.brandbar

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import javax.swing.JProgressBar
import javax.swing.UIManager
import javax.swing.plaf.basic.BasicProgressBarUI

class BrandProgressBarInstallerTest {
    private var previousUiValue: Any? = null
    private var previousClassMapping: Any? = null

    @Before
    fun captureDefaults() {
        val defaults = UIManager.getDefaults()
        previousUiValue = defaults[BrandProgressBarInstaller.PROGRESS_BAR_UI_KEY]
        previousClassMapping = defaults[BrandProgressBarUI::class.java.name]
    }

    @After
    fun restoreDefaults() {
        val defaults = UIManager.getDefaults()
        if (previousUiValue != null) {
            defaults[BrandProgressBarInstaller.PROGRESS_BAR_UI_KEY] = previousUiValue
        } else {
            defaults.remove(BrandProgressBarInstaller.PROGRESS_BAR_UI_KEY)
        }
        if (previousClassMapping != null) {
            defaults[BrandProgressBarUI::class.java.name] = previousClassMapping
        } else {
            defaults.remove(BrandProgressBarUI::class.java.name)
        }
    }

    @Test
    fun installRegistersUiOnUiManagerAndReinstallsAfterThemeReset() {
        BrandProgressBarInstaller.install()
        assertEquals(
            BrandProgressBarUI::class.java.name,
            UIManager.get(BrandProgressBarInstaller.PROGRESS_BAR_UI_KEY),
        )
        assertEquals(
            BrandProgressBarUI::class.java,
            UIManager.getDefaults()[BrandProgressBarUI::class.java.name],
        )

        val progressBar = JProgressBar(0, 100)
        assertTrue(
            "Expected newly constructed JProgressBar to use BrandProgressBarUI, got ${progressBar.ui::class.java.name}",
            progressBar.ui is BrandProgressBarUI,
        )

        // Simulate a Look-and-Feel switch resetting ProgressBarUI back to BasicProgressBarUI.
        UIManager.getDefaults()[BrandProgressBarInstaller.PROGRESS_BAR_UI_KEY] = BasicProgressBarUI::class.java.name
        progressBar.updateUI()
        assertFalse(progressBar.ui is BrandProgressBarUI)

        // Reinstall via the same entry point LafManagerListener and ApplicationActivationListener invoke.
        BrandProgressBarInstaller.install()
        progressBar.updateUI()
        assertTrue(progressBar.ui is BrandProgressBarUI)

        BrandProgressBarInstaller.uninstall()
        val afterUninstall = JProgressBar(0, 100)
        assertFalse(afterUninstall.ui is BrandProgressBarUI)
    }
}
