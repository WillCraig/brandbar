package dev.willc.brandbar

import com.intellij.ide.plugins.DynamicPluginListener
import com.intellij.ide.plugins.IdeaPluginDescriptor
import com.intellij.ide.ui.LafManager
import com.intellij.ide.ui.LafManagerListener
import com.intellij.openapi.application.ApplicationActivationListener
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.openapi.wm.IdeFrame
import javax.swing.UIDefaults
import javax.swing.UIManager

object BrandThemeHolder {
    val theme: BrandTheme
        get() = BrandThemeLoader.loadBundled()

    fun reload() {
        throw NotImplementedError("BrandThemeHolder.reload is not implemented yet")
    }
}

object BrandProgressBarInstaller {
    const val PLUGIN_ID = "dev.willc.brandbar"
    const val PROGRESS_BAR_UI_KEY = "ProgressBarUI"

    fun install(defaults: UIDefaults = UIManager.getDefaults()) {
        throw NotImplementedError("BrandProgressBarInstaller.install is not implemented yet")
    }

    fun uninstall(defaults: UIDefaults = UIManager.getDefaults()) {
        throw NotImplementedError("BrandProgressBarInstaller.uninstall is not implemented yet")
    }

    fun refreshOpenWindows() {
        throw NotImplementedError("BrandProgressBarInstaller.refreshOpenWindows is not implemented yet")
    }
}

class BrandLafListener : LafManagerListener {
    override fun lookAndFeelChanged(source: LafManager) {
        BrandProgressBarInstaller.install()
    }
}

class BrandActivationListener : ApplicationActivationListener {
    override fun applicationActivated(ideFrame: IdeFrame) {
        BrandProgressBarInstaller.install()
    }
}

class BrandStartupActivity : ProjectActivity {
    override suspend fun execute(project: Project) {
        BrandProgressBarInstaller.install()
    }
}

class BrandDynamicPluginListener : DynamicPluginListener {
    override fun pluginLoaded(pluginDescriptor: IdeaPluginDescriptor) {
        if (pluginDescriptor.pluginId.idString == BrandProgressBarInstaller.PLUGIN_ID) {
            BrandProgressBarInstaller.install()
        }
    }

    override fun beforePluginUnload(pluginDescriptor: IdeaPluginDescriptor, isUpdate: Boolean) {
        if (pluginDescriptor.pluginId.idString == BrandProgressBarInstaller.PLUGIN_ID) {
            BrandProgressBarInstaller.uninstall()
        }
    }
}
