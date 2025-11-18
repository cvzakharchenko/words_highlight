package com.github.cvzakharchenko.wordshighlight.actions

import com.github.cvzakharchenko.wordshighlight.MyBundle
import com.github.cvzakharchenko.wordshighlight.highlight.WordHighlightManager
import com.intellij.codeInsight.hint.HintManager
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.DumbAware

class HighlightWordUnderCaretAction : AnAction(
    MyBundle.message("action.highlightWord.text"),
    MyBundle.message("action.highlightWord.description"),
    null,
), DumbAware {

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

    override fun update(e: AnActionEvent) {
        val editor = e.getData(CommonDataKeys.EDITOR)
        val request = editor?.let { buildRequest(it) }
        e.presentation.isEnabled = editor != null && request != null
        val inEditorPopup = e.place == ActionPlaces.EDITOR_POPUP
        e.presentation.isVisible = !inEditorPopup || editor != null
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val editor = e.getData(CommonDataKeys.EDITOR) ?: return
        val request = buildRequest(editor)
        if (request == null) {
            HintManager.getInstance().showInformationHint(
                editor,
                MyBundle.message("action.highlightWord.noWord"),
            )
            return
        }

        project.service<WordHighlightManager>().toggleHighlight(request)
    }

    private fun buildRequest(editor: Editor): WordHighlightManager.HighlightRequest? {
        val caret = editor.caretModel.currentCaret ?: return null
        val selection = caret.selectedText?.takeIf { it.isNotBlank() && it.all { ch -> ch.isWordPart() } }
        if (!selection.isNullOrBlank()) {
            val wordAtSelection = findWordAt(editor, caret.selectionStart)
            if (wordAtSelection != null) {
                return WordHighlightManager.HighlightRequest(wordAtSelection)
            }
        }

        val word = findWordAt(editor, caret.offset) ?: return null
        return WordHighlightManager.HighlightRequest(word)
    }

    private fun findWordAt(editor: Editor, position: Int): String? {
        val text = editor.document.charsSequence
        if (text.isEmpty()) return null

        var offset = position
        if (offset >= text.length) {
            offset = text.length - 1
        }
        if (offset < 0) return null

        if (!text[offset].isWordPart()) {
            if (offset > 0 && text[offset - 1].isWordPart()) {
                offset--
            } else {
                return null
            }
        }

        var start = offset
        while (start > 0 && text[start - 1].isWordPart()) {
            start--
        }

        var end = offset
        while (end < text.length && text[end].isWordPart()) {
            end++
        }

        return if (start < end) text.subSequence(start, end).toString() else null
    }

    private fun Char.isWordPart(): Boolean = isLetterOrDigit() || this == '_'
}

