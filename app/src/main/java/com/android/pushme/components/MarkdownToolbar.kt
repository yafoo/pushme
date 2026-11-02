package com.android.pushme.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.android.pushme.R

/**
 * Markdown 编辑器工具栏
 * 提供标题、加粗、斜体、删除线、链接、图片、代码、引用、列表等快捷按钮
 * 两行布局
 */
@Composable
fun MarkdownToolbar(
    textFieldValue: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        ToolbarButton(R.drawable.format_size, "标题") { toggleHeading(textFieldValue, onValueChange) }
        ToolbarButton(R.drawable.format_bold, "加粗") { toggleBold(textFieldValue, onValueChange) }
        ToolbarButton(R.drawable.format_italic, "斜体") { toggleItalic(textFieldValue, onValueChange) }
        ToolbarButton(R.drawable.strikethrough_s, "删除线") { toggleStrikethrough(textFieldValue, onValueChange) }
        ToolbarButton(R.drawable.link, "链接") { insertLink(textFieldValue, onValueChange) }
        ToolbarButton(R.drawable.code, "代码") { toggleCode(textFieldValue, onValueChange) }
        ToolbarButton(R.drawable.format_quote, "引用") { toggleQuote(textFieldValue, onValueChange) }
        ToolbarButton(R.drawable.format_list_bulleted, "列表") { toggleList(textFieldValue, onValueChange) }
    }
}

/**
 * 工具栏按钮组件
 */
@Composable
private fun ToolbarButton(iconRes: Int, description: String, onClick: () -> Unit) {
    IconButton(
        onClick = onClick,
        modifier = Modifier.size(40.dp)
    ) {
        Icon(
            ImageVector.vectorResource(iconRes),
            contentDescription = description,
            modifier = Modifier.size(22.dp),
            tint = MaterialTheme.colorScheme.onSurface
        )
    }
}

/**
 * 切换标题级别
 * 在光标所在行的行首添加/循环 # 标记
 * 级别: 无 -> 1级(#) -> 2级(##) -> ... -> 6级(######) -> 无
 */
private fun toggleHeading(
    textFieldValue: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit
) {
    val text = textFieldValue.text
    val cursorPos = textFieldValue.selection.start

    // 找到光标所在行的起始和结束位置
    val lineStart = text.lastIndexOf('\n', cursorPos - 1) + 1
    val lineEnd = text.indexOf('\n', cursorPos).let {
        if (it == -1) text.length else it
    }
    val line = text.substring(lineStart, lineEnd)

    // 统计当前行首的 # 数量
    val headingCount = line.takeWhile { it == '#' }.length

    // 计算新的 # 数量
    val newHeadingCount = when {
        headingCount == 0 -> 1
        headingCount >= 6 -> 0
        else -> headingCount + 1
    }

    // 移除原有的 # 和空格
    val lineContent = line.substring(headingCount).trimStart()

    // 构建新的行
    val newLine = if (newHeadingCount > 0) {
        "#".repeat(newHeadingCount) + " " + lineContent
    } else {
        lineContent
    }

    // 更新文本
    val newText = text.substring(0, lineStart) + newLine + text.substring(lineEnd)
    val newCursorPos = lineStart + newLine.length

    onValueChange(
        TextFieldValue(
            text = newText,
            selection = TextRange(newCursorPos)
        )
    )
}

/**
 * 切换加粗
 * 在选中文本周围添加/移除 ** 标记
 */
private fun toggleBold(
    textFieldValue: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit
) {
    toggleWrapper(textFieldValue, onValueChange, "**", "**文本**", "文本")
}

/**
 * 切换斜体
 * 在选中文本周围添加/移除 * 标记
 */
private fun toggleItalic(
    textFieldValue: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit
) {
    toggleWrapper(textFieldValue, onValueChange, "*", "*文本*", "文本")
}

/**
 * 切换删除线
 * 在选中文本周围添加/移除 ~~ 标记
 */
private fun toggleStrikethrough(
    textFieldValue: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit
) {
    toggleWrapper(textFieldValue, onValueChange, "~~", "~~文本~~", "文本")
}

/**
 * 通用包裹函数
 */
private fun toggleWrapper(
    textFieldValue: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    marker: String,
    placeholder: String,
    placeholderText: String
) {
    val text = textFieldValue.text
    val selection = textFieldValue.selection
    val markerLen = marker.length

    if (selection.start == selection.end) {
        // 没有选中文本，插入占位符
        val newText = text.substring(0, selection.start) + placeholder + text.substring(selection.end)
        val newCursorPos = selection.start + markerLen

        onValueChange(
            TextFieldValue(
                text = newText,
                selection = TextRange(newCursorPos, newCursorPos + placeholderText.length)
            )
        )
    } else {
        // 有选中文本
        val selectedText = text.substring(selection.start, selection.end)

        // 检查是否已被标记包裹
        val beforeText = if (selection.start >= markerLen)
            text.substring(selection.start - markerLen, selection.start)
        else ""
        val afterText = if (selection.end + markerLen <= text.length)
            text.substring(selection.end, selection.end + markerLen)
        else ""

        if (beforeText == marker && afterText == marker) {
            // 移除标记
            val newText = text.substring(0, selection.start - markerLen) +
                          selectedText +
                          text.substring(selection.end + markerLen)
            onValueChange(
                TextFieldValue(
                    text = newText,
                    selection = TextRange(selection.start - markerLen, selection.end - markerLen)
                )
            )
        } else {
            // 添加标记
            val newText = text.substring(0, selection.start) +
                          marker + selectedText + marker +
                          text.substring(selection.end)
            onValueChange(
                TextFieldValue(
                    text = newText,
                    selection = TextRange(selection.start + markerLen, selection.end + markerLen)
                )
            )
        }
    }
}

/**
 * 插入链接
 * 格式: [文本](URL)
 */
private fun insertLink(
    textFieldValue: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit
) {
    val text = textFieldValue.text
    val selection = textFieldValue.selection

    val insertText = if (selection.start == selection.end) {
        "[链接文本](URL)"
    } else {
        val selectedText = text.substring(selection.start, selection.end)
        "[$selectedText](URL)"
    }

    val newText = text.substring(0, selection.start) + insertText + text.substring(selection.end)

    // 选中 URL 部分让用户填写
    val urlStart = selection.start + insertText.lastIndexOf("(") + 1
    val urlEnd = urlStart + 3 // "URL" 的长度

    onValueChange(
        TextFieldValue(
            text = newText,
            selection = TextRange(urlStart, urlEnd)
        )
    )
}

/**
 * 切换代码（行内代码 ↔ 代码块）
 * 无选中文本：插入 `代码`
 * 有选中文本：切换 `code` → ```code``` → 无标记
 */
private fun toggleCode(
    textFieldValue: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit
) {
    val text = textFieldValue.text
    val selection = textFieldValue.selection

    if (selection.start == selection.end) {
        // 无选中文本，插入行内代码占位符
        val placeholder = "`代码`"
        val newText = text.substring(0, selection.start) + placeholder + text.substring(selection.end)
        val cursorPos = selection.start + 1
        val selectionEnd = cursorPos + 2

        onValueChange(
            TextFieldValue(
                text = newText,
                selection = TextRange(cursorPos, selectionEnd)
            )
        )
        return
    }

    val selectedText = text.substring(selection.start, selection.end)

    // 检查是否已经是代码块
    if (selection.start >= 4 && selection.end + 4 <= text.length) {
        val before = text.substring(selection.start - 4, selection.start)
        val after = text.substring(selection.end, selection.end + 4)

        if (before == "```\n" && after == "\n```") {
            // 移除代码块标记
            val newText = text.substring(0, selection.start - 4) + selectedText + text.substring(selection.end + 4)
            onValueChange(
                TextFieldValue(
                    text = newText,
                    selection = TextRange(selection.start - 4, selection.end - 4)
                )
            )
            return
        }
    }

    // 检查是否已经是行内代码
    if (selection.start >= 1 && selection.end + 1 <= text.length) {
        val before = text.substring(selection.start - 1, selection.start)
        val after = text.substring(selection.end, selection.end + 1)

        if (before == "`" && after == "`") {
            // 移除行内代码标记，变成代码块
            val newText = text.substring(0, selection.start - 1) + "```\n" + selectedText + "\n```" + text.substring(selection.end + 1)
            onValueChange(
                TextFieldValue(
                    text = newText,
                    selection = TextRange(selection.start + 3, selection.end + 3)
                )
            )
            return
        }
    }

    // 添加行内代码标记
    val newText = text.substring(0, selection.start) + "`" + selectedText + "`" + text.substring(selection.end)
    onValueChange(
        TextFieldValue(
            text = newText,
            selection = TextRange(selection.start + 1, selection.end + 1)
        )
    )
}

/**
 * 切换列表类型（无序 → 有序 → 任务 → 无）
 */
private fun toggleList(
    textFieldValue: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit
) {
    val text = textFieldValue.text
    val cursorPos = textFieldValue.selection.start

    // 找到当前行的起始和结束位置
    val lineStart = text.lastIndexOf('\n', cursorPos - 1) + 1
    val lineEnd = text.indexOf('\n', cursorPos).let {
        if (it == -1) text.length else it
    }

    val line = text.substring(lineStart, lineEnd)

    // 检测当前列表类型并切换到下一种
    val (newLine, prefixLen) = when {
        line.startsWith("- [ ] ") -> {
            // 任务列表 → 移除
            val content = if (line.length > 6) line.substring(6) else ""
            content to 0
        }
        line.startsWith("- [ ]") -> {
            // 任务列表（无后续内容）→ 移除
            "" to 0
        }
        line.startsWith("- ") -> {
            // 无序列表 → 有序列表
            val content = if (line.length > 2) line.substring(2) else ""
            "1. $content" to 3
        }
        line.matches(Regex("^\\d+\\.\\s.*")) || line.matches(Regex("^\\d+\\.$")) -> {
            // 有序列表 → 任务列表
            val matchResult = Regex("^(\\d+\\.\\s?)(.*)").find(line)
            if (matchResult != null) {
                "- [ ] ${matchResult.groupValues[2]}" to 6
            } else {
                line to 0
            }
        }
        else -> {
            // 无 → 无序列表
            "- $line" to 2
        }
    }

    val newText = text.substring(0, lineStart) + newLine + text.substring(lineEnd)
    val newCursorPos = lineStart + prefixLen

    onValueChange(
        TextFieldValue(
            text = newText,
            selection = TextRange(newCursorPos)
        )
    )
}

/**
 * 切换引用
 * 在行首添加/移除 > 标记
 */
private fun toggleQuote(
    textFieldValue: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit
) {
    val text = textFieldValue.text
    val cursorPos = textFieldValue.selection.start

    // 找到光标所在行的起始和结束位置
    val lineStart = text.lastIndexOf('\n', cursorPos - 1) + 1
    val lineEnd = text.indexOf('\n', cursorPos).let {
        if (it == -1) text.length else it
    }
    val line = text.substring(lineStart, lineEnd)

    val newLine = if (line.startsWith("> ")) {
        // 移除引用标记
        line.substring(2)
    } else if (line.startsWith(">")) {
        // 移除引用标记（无空格）
        line.substring(1)
    } else {
        // 添加引用标记
        "> $line"
    }

    val newText = text.substring(0, lineStart) + newLine + text.substring(lineEnd)
    val newCursorPos = lineStart + newLine.length

    onValueChange(
        TextFieldValue(
            text = newText,
            selection = TextRange(newCursorPos)
        )
    )
}
