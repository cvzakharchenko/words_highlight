package com.github.cvzakharchenko.wordshighlight.settings

import com.github.cvzakharchenko.wordshighlight.MyBundle
import com.intellij.openapi.ui.Messages
import com.intellij.ui.CollectionListModel
import com.intellij.ui.ColoredListCellRenderer
import com.intellij.ui.ColorPanel
import com.intellij.ui.ColorUtil
import com.intellij.ui.ToolbarDecorator
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBPanel
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.scale.JBUIScale
import com.intellij.util.ui.ColorIcon
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.Color
import javax.swing.JComponent
import javax.swing.JList
import javax.swing.JPanel
import javax.swing.ListSelectionModel

class WordHighlightSettingsComponent {

    private val colorListModel = CollectionListModel<String>()
    private val colorList = JBList(colorListModel).apply {
        selectionMode = ListSelectionModel.SINGLE_SELECTION
        visibleRowCount = 8
        cellRenderer = ColorRenderer()
        emptyText.text = MyBundle.message("settings.colors.empty")
    }

    private val foregroundColorPanel = ColorPanel().apply {
        selectedColor = Color.WHITE
    }

    private val panel: JPanel = JBPanel<JBPanel<*>>(BorderLayout()).apply {
        add(
            panel {
                row(MyBundle.message("settings.foreground.label")) {
                    cell(foregroundColorPanel)
                }
            },
            BorderLayout.NORTH,
        )

        val decorator = ToolbarDecorator.createDecorator(colorList)
            .setAddAction { addColor() }
            .setEditAction { editSelectedColor() }
            .setRemoveAction { removeSelectedColor() }
            .setPreferredSize(JBUI.size(350, 200))

        add(decorator.createPanel(), BorderLayout.CENTER)
    }

    fun getPanel(): JComponent = panel

    fun setColors(colors: List<String>) {
        colorListModel.replaceAll(colors)
        if (colorListModel.size > 0) {
            colorList.selectedIndex = 0
        }
    }

    fun getColors(): List<String> = currentColors()

    fun setForegroundColor(color: String) {
        foregroundColorPanel.selectedColor = parseColor(color)
    }

    fun getForegroundColor(): String {
        val color = foregroundColorPanel.selectedColor ?: Color.WHITE
        return "#${ColorUtil.toHex(color)}".uppercase()
    }

    private fun addColor() {
        val value = promptForColor(
            title = MyBundle.message("settings.colors.add.title"),
            initial = colorList.selectedValue ?: currentColors().lastOrNull(),
        ) ?: return
        colorListModel.add(value)
        colorList.selectedIndex = colorListModel.size - 1
    }

    private fun editSelectedColor() {
        val index = colorList.selectedIndex.takeIf { it >= 0 } ?: return
        val current = colorListModel.getElementAt(index)
        val value = promptForColor(
            title = MyBundle.message("settings.colors.edit.title"),
            initial = current,
        ) ?: return
        colorListModel.setElementAt(value, index)
        colorList.selectedIndex = index
    }

    private fun removeSelectedColor() {
        val index = colorList.selectedIndex.takeIf { it >= 0 } ?: return
        colorListModel.remove(index)
        if (colorListModel.size > 0) {
            colorList.selectedIndex = index.coerceAtMost(colorListModel.size - 1)
        }
    }

    private fun promptForColor(title: String, initial: String?): String? {
        while (true) {
            val input = Messages.showInputDialog(
                panel,
                MyBundle.message("settings.colors.dialog.label"),
                title,
                null,
                initial,
                null,
            ) ?: return null

        val normalized = normalizeColor(input)
            if (normalized != null) {
                return normalized
            }

            Messages.showErrorDialog(
                panel,
                MyBundle.message("settings.colors.dialog.validation"),
                title,
            )
        }
    }

    private fun normalizeColor(value: String?): String? {
        if (value.isNullOrBlank()) return null
        val withPrefix = value.trim().let { if (it.startsWith("#")) it else "#$it" }
        val upper = withPrefix.uppercase()
        return if (upper.matches(Regex("^#[0-9A-F]{6}$"))) upper else null
    }

    private fun currentColors(): List<String> =
        (0 until colorListModel.size).map { colorListModel.getElementAt(it) }

    private fun parseColor(value: String?): Color {
        return runCatching { Color.decode(value) }.getOrDefault(Color.WHITE)
    }

    private class ColorRenderer : ColoredListCellRenderer<String>() {
        override fun customizeCellRenderer(
            list: JList<out String>,
            value: String?,
            index: Int,
            selected: Boolean,
            hasFocus: Boolean,
        ) {
            val color = parseColor(value)
            icon = ColorIcon(JBUIScale.scale(12), color, false)
            append(value ?: "")
        }

        private fun parseColor(value: String?): Color {
            return runCatching { Color.decode(value) }.getOrDefault(Color.GRAY)
        }
    }
}

