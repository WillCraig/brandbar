package dev.willc.brandbar

import com.intellij.ide.plugins.DynamicPluginListener
import com.intellij.ide.plugins.IdeaPluginDescriptor
import com.intellij.ide.ui.LafManager
import com.intellij.ide.ui.LafManagerListener
import com.intellij.openapi.application.ApplicationActivationListener
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.openapi.wm.IdeFrame
import java.awt.Component
import java.awt.Container
import java.awt.EventQueue
import java.awt.Window
import javax.swing.JProgressBar
import javax.swing.UIDefaults
import javax.swing.UIManager
import javax.swing.plaf.basic.BasicProgressBarUI

object BrandThemeHolder {
    @Volatile
    private var cached: BrandTheme? = null

    val theme: BrandTheme
        get() = cached ?: synchronized(this) {
            cached ?: BrandThemeLoader.loadBundled().also { cached = it }
        }

    fun reload() {
        synchronized(this) {
            cached = BrandThemeLoader.loadBundled()
        }
    }
}

object BrandProgressBarInstaller {
    const val PLUGIN_ID = "dev.willc.brandbar"
    const val PROGRESS_BAR_UI_KEY = "ProgressBarUI"
    private val UI_CLASS_NAME = BrandProgressBarUI::class.java.name

    @Volatile
    private var fallbackUiValue: Any? = null

    fun install(defaults: UIDefaults = UIManager.getDefaults()) {
        val current = defaults[PROGRESS_BAR_UI_KEY]
        if (current != null && current != UI_CLASS_NAME) {
            fallbackUiValue = current
        }
        defaults[PROGRESS_BAR_UI_KEY] = UI_CLASS_NAME
        defaults[UI_CLASS_NAME] = BrandProgressBarUI::class.java
        if (defaults === UIManager.getDefaults() && EventQueue.isDispatchThread()) {
            refreshOpenWindows()
        }
    }

    fun uninstall(defaults: UIDefaults = UIManager.getDefaults()) {
        val restore = fallbackUiValue ?: BasicProgressBarUI::class.java.name
        defaults[PROGRESS_BAR_UI_KEY] = restore
        defaults.remove(UI_CLASS_NAME)
        if (defaults === UIManager.getDefaults() && EventQueue.isDispatchThread()) {
            refreshOpenWindows()
        }
    }

    fun refreshOpenWindows() {
        for (window in Window.getWindows()) {
            refreshHierarchy(window)
        }
    }

    private fun refreshHierarchy(component: Component) {
        if (component is JProgressBar) {
            component.updateUI()
        }
        if (component is Container) {
            for (child in component.components) {
                refreshHierarchy(child)
            }
        }
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
        val app = ApplicationManager.getApplication()
        if (app != null) {
            app.invokeLater {
                BrandProgressBarInstaller.install()
            }
        } else {
            BrandProgressBarInstaller.install()
        }
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
