package com.github.cvzakharchenko.wordshighlight.settings

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.util.messages.Topic

@Service(Service.Level.APP)
@State(name = "WordHighlightSettings", storages = [Storage("WordHighlightSettings.xml")])
class WordHighlightSettingsState : PersistentStateComponent<WordHighlightSettingsState.State> {

    companion object {
        private val COLOR_REGEX = Regex("^#[0-9A-Fa-f]{6}$")

        const val DEFAULT_FOREGROUND_COLOR = "#FFFFFF"

        val DEFAULT_COLORS = listOf(
            "#FFB74D",
            "#81C784",
            "#64B5F6",
            "#BA68C8",
            "#E57373",
            "#4DD0E1",
            "#AED581",
            "#FFD54F",
            "#F06292",
            "#90A4AE",
        )

        val TOPIC: Topic<WordHighlightSettingsListener> = Topic.create(
            "WordHighlightSettingsColorsChanged",
            WordHighlightSettingsListener::class.java,
        )

        fun getInstance(): WordHighlightSettingsState =
            ApplicationManager.getApplication().getService(WordHighlightSettingsState::class.java)
    }

    data class State(
        var colors: MutableList<String> = DEFAULT_COLORS.toMutableList(),
        var foregroundColor: String = DEFAULT_FOREGROUND_COLOR,
    )

    private var state: State = State()

    override fun getState(): State = State(
        colors = getColors().toMutableList(),
        foregroundColor = getForegroundColor(),
    )

    override fun loadState(state: State) {
        this.state = State(
            colors = sanitize(state.colors).toMutableList(),
            foregroundColor = sanitizeColor(state.foregroundColor) ?: DEFAULT_FOREGROUND_COLOR,
        )
    }

    fun getColors(): List<String> = sanitize(state.colors)

    fun getForegroundColor(): String = sanitizeColor(state.foregroundColor) ?: DEFAULT_FOREGROUND_COLOR

    fun updateColors(colors: List<String>) {
        val sanitized = sanitize(colors)
        if (state.colors == sanitized) {
            return
        }

        state.colors = sanitized.toMutableList()
        notifyListeners()
    }

    fun updateForegroundColor(color: String) {
        val sanitized = sanitizeColor(color) ?: DEFAULT_FOREGROUND_COLOR
        if (state.foregroundColor == sanitized) {
            return
        }
        state.foregroundColor = sanitized
        notifyListeners()
    }

    private fun sanitize(colors: List<String>?): List<String> {
        val normalized = colors.orEmpty()
            .mapNotNull { normalizeColor(it) }
        return if (normalized.isEmpty()) DEFAULT_COLORS else normalized
    }

    private fun sanitizeColor(value: String?): String? = normalizeColor(value)

    private fun normalizeColor(value: String?): String? {
        if (value.isNullOrBlank()) {
            return null
        }

        val trimmed = value.trim().let {
            if (it.startsWith("#")) it else "#$it"
        }.uppercase()

        return trimmed.takeIf { COLOR_REGEX.matches(it) }
    }

    private fun notifyListeners() {
        ApplicationManager.getApplication()
            .messageBus
            .syncPublisher(TOPIC)
            .settingsChanged(getColors(), getForegroundColor())
    }
}

fun interface WordHighlightSettingsListener {
    fun settingsChanged(colors: List<String>, foregroundColor: String)
}

