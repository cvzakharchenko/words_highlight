package com.github.cvzakharchenko.wordshighlight.settings

import com.github.cvzakharchenko.wordshighlight.MyBundle
import com.intellij.openapi.options.SearchableConfigurable
import javax.swing.JComponent

class WordHighlightSettingsConfigurable : SearchableConfigurable {

    private val settingsState: WordHighlightSettingsState
        get() = WordHighlightSettingsState.getInstance()

    private var component: WordHighlightSettingsComponent? = null

    override fun getId(): String = "com.github.cvzakharchenko.wordshighlight.settings"

    override fun getDisplayName(): String = MyBundle.message("settings.displayName")

    override fun createComponent(): JComponent {
        val currentComponent = component ?: WordHighlightSettingsComponent().also {
            it.setColors(settingsState.getColors())
            it.setForegroundColor(settingsState.getForegroundColor())
            component = it
        }
        return currentComponent.getPanel()
    }

    override fun isModified(): Boolean {
        val view = component ?: return false
        return view.getColors() != settingsState.getColors() ||
            view.getForegroundColor() != settingsState.getForegroundColor()
    }

    override fun apply() {
        val view = component ?: return
        settingsState.updateColors(view.getColors())
        settingsState.updateForegroundColor(view.getForegroundColor())
    }

    override fun reset() {
        component?.let {
            it.setColors(settingsState.getColors())
            it.setForegroundColor(settingsState.getForegroundColor())
        }
    }

    override fun disposeUIResources() {
        component = null
    }
}

