package com.github.cvzakharchenko.wordshighlight.highlight

import com.github.cvzakharchenko.wordshighlight.settings.WordHighlightSettingsListener
import com.github.cvzakharchenko.wordshighlight.settings.WordHighlightSettingsState
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.event.EditorFactoryEvent
import com.intellij.openapi.editor.event.EditorFactoryListener
import com.intellij.openapi.editor.markup.EffectType
import com.intellij.openapi.editor.markup.HighlighterLayer
import com.intellij.openapi.editor.markup.HighlighterTargetArea
import com.intellij.openapi.editor.markup.RangeHighlighter
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.openapi.project.Project
import com.intellij.ui.ColorUtil
import java.awt.Color
import java.util.LinkedHashMap
import java.util.concurrent.atomic.AtomicInteger

@Service(Service.Level.PROJECT)
class WordHighlightManager(private val project: Project) {

    private val settingsState: WordHighlightSettingsState = WordHighlightSettingsState.getInstance()
    private val lock = Any()
    private val orderCounter = AtomicInteger()
    private val highlights = LinkedHashMap<String, HighlightEntry>()
    private val editorFactory = EditorFactory.getInstance()

    init {
        ApplicationManager.getApplication().messageBus.connect(project).subscribe(
            WordHighlightSettingsState.TOPIC,
            WordHighlightSettingsListener { _, _ -> refreshAllHighlights() },
        )

        editorFactory.addEditorFactoryListener(object : EditorFactoryListener {
            override fun editorCreated(event: EditorFactoryEvent) {
                val editor = event.editor
                if (editor.project != project) return
                applyHighlights(editor)
            }

            override fun editorReleased(event: EditorFactoryEvent) {
                val editor = event.editor
                if (editor.project != project) return
                removeEditorReferences(editor)
            }
        }, project)

        editorFactory.allEditors
            .filter { it.project == project }
            .forEach { applyHighlights(it) }
    }

    fun toggleHighlight(request: HighlightRequest): HighlightResult {
        val key = request.text.trim()
        if (key.isEmpty()) return HighlightResult.Noop

        val action = synchronized(lock) {
            val existing = highlights[key]
            if (existing != null) {
                highlights.remove(key)
                HighlightAction.Remove(existing)
            } else {
                val entry = HighlightEntry(
                    word = key,
                    order = orderCounter.getAndIncrement(),
                )
                highlights[key] = entry
                HighlightAction.Add(entry)
            }
        }

        return when (action) {
            is HighlightAction.Add -> {
                applyHighlightToAllEditors(action.entry)
                HighlightResult.Added
            }

            is HighlightAction.Remove -> {
                disposeEntry(action.entry)
                HighlightResult.Removed
            }
        }
    }

    fun removeHighlight(word: String): Boolean {
        val entry = synchronized(lock) { highlights.remove(word) } ?: return false
        disposeEntry(entry)
        return true
    }

    fun clearAll() {
        val removedEntries = synchronized(lock) {
            val list = highlights.values.toList()
            highlights.clear()
            list
        }
        removedEntries.forEach { disposeEntry(it) }
    }

    fun getHighlights(): List<WordHighlightPresentation> = synchronized(lock) {
        highlights.values.map { entry ->
            WordHighlightPresentation(entry.word, colorFor(entry))
        }
    }

    fun hasHighlights(): Boolean = synchronized(lock) { highlights.isNotEmpty() }

    private fun applyHighlightToAllEditors(entry: HighlightEntry) {
        projectEditors().forEach { editor ->
            applyHighlightToEditor(entry, editor)
        }
    }

    private fun applyHighlights(editor: Editor) {
        if (editor.project != project) return
        synchronized(lock) {
            highlights.values.forEach { entry ->
                applyHighlightToEditor(entry, editor)
            }
        }
    }

    private fun applyHighlightToEditor(entry: HighlightEntry, editor: Editor) {
        if (editor.isDisposed || editor.project != project) return
        clearEditorHighlights(editor, entry)

        val text = editor.document.charsSequence
        val regex = entry.pattern
        val textAttributes = createTextAttributes(colorFor(entry))
        val markupModel = editor.markupModel

        regex.findAll(text).forEach { match ->
            val range = match.range
            val highlighter = markupModel.addRangeHighlighter(
                range.first,
                range.last + 1,
                HIGHLIGHT_LAYER,
                textAttributes,
                HighlighterTargetArea.EXACT_RANGE,
            )
            entry.editorHighlighters.getOrPut(editor) { mutableListOf() }.add(highlighter)
        }
    }

    private fun createTextAttributes(color: Color): TextAttributes =
        TextAttributes().apply {
            backgroundColor = color
            foregroundColor = textColor()
            effectColor = color.darker()
            effectType = EffectType.ROUNDED_BOX
        }

    private fun clearEditorHighlights(editor: Editor, entry: HighlightEntry) {
        if (editor.isDisposed) {
            entry.editorHighlighters.remove(editor)
            return
        }

        val markupModel = editor.markupModel
        entry.editorHighlighters.remove(editor)?.forEach { highlighter ->
            if (highlighter.isValid) {
                markupModel.removeHighlighter(highlighter)
            }
        }
    }

    private fun removeEditorReferences(editor: Editor) {
        synchronized(lock) {
            highlights.values.forEach { entry ->
                clearEditorHighlights(editor, entry)
            }
        }
    }

    private fun disposeEntry(entry: HighlightEntry) {
        entry.editorHighlighters.keys.toList().forEach { editor ->
            if (!editor.isDisposed) {
                clearEditorHighlights(editor, entry)
            }
        }
        entry.editorHighlighters.clear()
    }

    private fun refreshAllHighlights() {
        synchronized(lock) {
            highlights.values.forEach { entry ->
                applyHighlightToAllEditors(entry)
            }
        }
    }

    private fun projectEditors(): List<Editor> =
        editorFactory.allEditors.filter { it.project == project && !it.isDisposed }

    private fun colorFor(entry: HighlightEntry): Color {
        val colors = settingsState.getColors()
        val palette = if (colors.isEmpty()) WordHighlightSettingsState.DEFAULT_COLORS else colors
        val colorValue = palette[entry.order % palette.size]
        return ColorUtil.fromHex(colorValue.removePrefix("#"))
    }

    private fun textColor(): Color =
        ColorUtil.fromHex(settingsState.getForegroundColor().removePrefix("#"))

    private data class HighlightEntry(
        val word: String,
        val order: Int,
        val pattern: Regex = createPattern(word),
        val editorHighlighters: MutableMap<Editor, MutableList<RangeHighlighter>> = mutableMapOf(),
    )

    sealed interface HighlightResult {
        object Added : HighlightResult
        object Removed : HighlightResult
        object Noop : HighlightResult
    }

    data class HighlightRequest(val text: String)

    data class WordHighlightPresentation(
        val text: String,
        val color: Color,
    )

    private sealed interface HighlightAction {
        data class Add(val entry: HighlightEntry) : HighlightAction
        data class Remove(val entry: HighlightEntry) : HighlightAction
    }

    private companion object {
        private const val HIGHLIGHT_LAYER = HighlighterLayer.SELECTION - 1

        private fun createPattern(word: String): Regex {
            val escaped = Regex.escape(word)
            return Regex("\\b$escaped\\b")
        }
    }
}

