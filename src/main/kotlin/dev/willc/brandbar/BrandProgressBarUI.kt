package dev.willc.brandbar

import javax.swing.JComponent
import javax.swing.plaf.ComponentUI
import javax.swing.plaf.basic.BasicProgressBarUI

class BrandProgressBarUI(
    private val themeSupplier: () -> BrandTheme = { BrandThemeHolder.theme },
) : BasicProgressBarUI() {
    var indeterminateState: IndeterminateState = IndeterminateState()

    fun stepAnimation() {
        throw NotImplementedError("BrandProgressBarUI.stepAnimation is not implemented yet")
    }

    companion object {
        @Suppress("ACCIDENTAL_OVERRIDE", "UNUSED_PARAMETER")
        @JvmStatic
        fun createUI(component: JComponent): ComponentUI {
            throw NotImplementedError("BrandProgressBarUI.createUI is not implemented yet")
        }
    }
}
