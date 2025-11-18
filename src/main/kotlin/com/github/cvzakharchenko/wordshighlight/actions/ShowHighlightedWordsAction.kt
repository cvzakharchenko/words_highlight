package com.github.cvzakharchenko.wordshighlight.actions

import com.github.cvzakharchenko.wordshighlight.MyBundle
import com.github.cvzakharchenko.wordshighlight.highlight.WordHighlightManager
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.components.service
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.ui.CollectionListModel
import com.intellij.ui.ColoredListCellRenderer
import com.intellij.ui.ScrollPaneFactory
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBList
import com.intellij.ui.scale.JBUIScale
import com.intellij.util.ui.ColorIcon
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.Color
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.AbstractAction
import javax.swing.JComponent
import javax.swing.JList
import javax.swing.ListSelectionModel
import javax.swing.KeyStroke

class ShowHighlightedWordsAction : AnAction(
    MyBundle.message("action.showHighlights.text"),
    MyBundle.message("action.showHighlights.description"),
    null,
) {

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

    override fun update(e: AnActionEvent) {
        val project = e.project
        val manager = project?.service<WordHighlightManager>()
        val hasHighlights = manager?.hasHighlights() == true
        e.presentation.isEnabled = project != null && hasHighlights
        val inEditorPopup = e.place == ActionPlaces.EDITOR_POPUP
        e.presentation.isVisible = !inEditorPopup || e.getData(CommonDataKeys.EDITOR) != null
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val manager = project.service<WordHighlightManager>()
        val popupFactory = JBPopupFactory.getInstance()
        val listModel = CollectionListModel<HighlightListItem>()
        val list = JBList(listModel).apply {
            selectionMode = ListSelectionModel.SINGLE_SELECTION
            cellRenderer = HighlightRenderer()
            visibleRowCount = 8
        }

        installWrapNavigation(list)

        val scrollPane = ScrollPaneFactory.createScrollPane(list).apply {
            border = JBUI.Borders.empty()
        }

        val header = JBLabel(MyBundle.message("popup.highlightedWords.title")).apply {
            border = JBUI.Borders.empty(4, 8, 4, 8)
        }

        val container = javax.swing.JPanel(BorderLayout()).apply {
            border = JBUI.Borders.empty(4, 4, 4, 4)
            add(header, BorderLayout.NORTH)
            add(scrollPane, BorderLayout.CENTER)
        }

        val popup = popupFactory
            .createComponentPopupBuilder(container, list)
            .setResizable(true)
            .setMovable(true)
            .setRequestFocus(true)
            .createPopup()

        fun refresh(selectionIndex: Int? = null) {
            val entries = manager.getHighlights().map { HighlightListItem.Entry(it) }
            val items = if (entries.isEmpty()) {
                list.emptyText.text = MyBundle.message("popup.highlightedWords.empty")
                emptyList()
            } else {
                list.emptyText.text = ""
                entries + HighlightListItem.RemoveAll
            }
            listModel.replaceAll(items)
            if (items.isNotEmpty()) {
                val newIndex = selectionIndex?.coerceIn(0, items.lastIndex) ?: 0
                list.selectedIndex = newIndex
            } else {
                list.clearSelection()
            }
        }

        fun handleSelection() {
            val index = list.selectedIndex.takeIf { it >= 0 } ?: return
            val item = listModel.getElementAt(index)
            when (item) {
                is HighlightListItem.Entry -> manager.removeHighlight(item.presentation.text)
                HighlightListItem.RemoveAll -> manager.clearAll()
            }
            if (!manager.hasHighlights()) {
                popup.cancel()
            } else {
                refresh(index)
            }
        }

        list.addKeyListener(object : KeyAdapter() {
            override fun keyPressed(e: KeyEvent) {
                when (e.keyCode) {
                    KeyEvent.VK_ENTER -> {
                        handleSelection()
                        e.consume()
                    }
                    KeyEvent.VK_UP -> {
                        if (listModel.size > 0 && list.selectedIndex <= 0) {
                            list.selectedIndex = listModel.size - 1
                            e.consume()
                        }
                    }
                    KeyEvent.VK_DOWN -> {
                        if (listModel.size > 0 && list.selectedIndex == listModel.size - 1) {
                            list.selectedIndex = 0
                            e.consume()
                        }
                    }
                }
            }
        })

        list.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                if (e.button == MouseEvent.BUTTON1 && e.clickCount == 1) {
                    handleSelection()
                    e.consume()
                }
            }
        })

        refresh()
        popup.showInBestPositionFor(e.dataContext)
    }

    private sealed interface HighlightListItem {
        data class Entry(val presentation: WordHighlightManager.WordHighlightPresentation) : HighlightListItem
        object RemoveAll : HighlightListItem
    }

    private class HighlightRenderer : ColoredListCellRenderer<HighlightListItem>() {
        override fun customizeCellRenderer(
            list: JList<out HighlightListItem>,
            value: HighlightListItem?,
            index: Int,
            selected: Boolean,
            hasFocus: Boolean,
        ) {
            when (value) {
                is HighlightListItem.Entry -> {
                    val color = value.presentation.color
                    icon = ColorIcon(JBUIScale.scale(12), color, false)
                    append(value.presentation.text)
                }

                HighlightListItem.RemoveAll -> {
                    icon = null
                    append(MyBundle.message("popup.highlightedWords.removeAll"))
                }

                else -> {
                    append(MyBundle.message("popup.highlightedWords.empty"))
                }
            }
        }
    }

    private fun installWrapNavigation(list: JBList<HighlightListItem>) {
        val upAction = object : AbstractAction() {
            override fun actionPerformed(e: java.awt.event.ActionEvent?) {
                val size = list.model.size
                if (size == 0) return
                val current = list.selectedIndex.takeIf { it >= 0 } ?: 0
                val next = if (current <= 0) size - 1 else current - 1
                list.selectedIndex = next
                list.ensureIndexIsVisible(next)
            }
        }

        val downAction = object : AbstractAction() {
            override fun actionPerformed(e: java.awt.event.ActionEvent?) {
                val size = list.model.size
                if (size == 0) return
                val current = list.selectedIndex.takeIf { it >= 0 } ?: -1
                val next = if (current >= size - 1) 0 else current + 1
                list.selectedIndex = next
                list.ensureIndexIsVisible(next)
            }
        }

        list.inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_UP, 0), "wrapUp")
        list.actionMap.put("wrapUp", upAction)
        list.inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_DOWN, 0), "wrapDown")
        list.actionMap.put("wrapDown", downAction)
    }
}

