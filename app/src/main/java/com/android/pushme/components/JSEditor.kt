package com.android.pushme.components

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldColors
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.text.Regex

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JSEditor(
    modifier: Modifier = Modifier,
    code: String = "",
    onCodeChange: (String) -> Unit = {},
    enabled: Boolean = true,
    readOnly: Boolean = false,
    textStyle: TextStyle = LocalTextStyle.current,
    singleLine: Boolean = false,
    maxLines: Int = Int.MAX_VALUE,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    colors: TextFieldColors = TextFieldDefaults.colors()
) {
    val scrollState = rememberScrollState()

    val highlightedText = remember(code) { buildJsHighlightedString(code) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .imePadding() // 添加输入法padding
    ) {
        // 使用 TextFieldDefaults 的装饰框
        TextFieldDefaults.DecorationBox(
            value = code,
            visualTransformation = VisualTransformation.None,
            innerTextField = {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(scrollState)
                ) {
                    // 显示高亮文本作为背景
                    Text(
                        text = highlightedText,
                        style = textStyle.copy(
                            fontFamily = FontFamily.Monospace,
                            fontSize = MaterialTheme.typography.bodySmall.fontSize,
                            lineHeight = MaterialTheme.typography.bodySmall.lineHeight,
                            letterSpacing = 0.sp
                        ),
                        maxLines = maxLines,
                    )
                    // 实际输入框
                    BasicTextField(
                        value = code,
                        onValueChange = onCodeChange,
                        modifier = Modifier.fillMaxSize(),
                        enabled = enabled,
                        readOnly = readOnly,
                        textStyle = textStyle.copy(
                            fontFamily = FontFamily.Monospace,
                            fontSize = MaterialTheme.typography.bodySmall.fontSize,
                            lineHeight = MaterialTheme.typography.bodySmall.lineHeight,
                            letterSpacing = 0.sp,
                            color = Color.Transparent // 透明，通过高亮文本显示颜色
                        ),
                        singleLine = singleLine,
                        maxLines = maxLines,
                        interactionSource = interactionSource,
                    )
                }
            },
            enabled = enabled,
            singleLine = singleLine,
            interactionSource = interactionSource,
            colors = colors,
        )
    }
}

/**
 * 只读代码展示：语法高亮 + 可选滚动。
 *
 * 和 [JSEditor] 共用同一套高亮规则，所以「插件商店里看到的源码」和「编辑页里看到的」颜色完全一致。
 *
 * @param maxHeight 超过这个高度就内部滚动，内容少时高度自适应。传 `Dp.Unspecified` 表示不限。
 * @param scrollable 自己滚不滚。和 [maxHeight] 搭配：传了 `maxHeight` 才需要开，
 *   否则内容会无限撑开。放在别的滚动容器里时两者都开着也没问题 ——
 *   Compose 的 `verticalScroll` 之间走 nested scroll 协议，内层先滚，滚到底再把剩余距离交给外层。
 */
@Composable
fun CodeView(
    code: String,
    modifier: Modifier = Modifier,
    scrollable: Boolean = true,
    maxHeight: Dp = Dp.Unspecified,
    fontSize: TextUnit = 10.sp,
    lineHeight: TextUnit = 15.sp
) {
    val highlighted = remember(code) { buildJsHighlightedString(code) }
    var boxModifier = modifier.background(
        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.04f),
        RoundedCornerShape(3.dp)
    )
    // 约束要在 scroll 之前给：verticalScroll 自己会吃掉高度约束，后面再加 heightIn 就晚了
    if (maxHeight != Dp.Unspecified) {
        boxModifier = boxModifier.heightIn(max = maxHeight)
    }
    if (scrollable) {
        boxModifier = boxModifier.verticalScroll(rememberScrollState())
    }
    Box(modifier = boxModifier.padding(8.dp)) {
        Text(
            text = highlighted,
            fontFamily = FontFamily.Monospace,
            fontSize = fontSize,
            lineHeight = lineHeight,
            letterSpacing = 0.sp
        )
    }
}

// ==================== 高亮 ====================

val grayColor = Color(0xFFA1A2A2)
val pinkColor = Color(0xFFD76736)
val redColor = Color(0xFFC22525)
val purpleColor = Color(0xFFA504CE)
val lightBlueColor = Color(0xFF04B08E)
val blueColor = Color(0xFF1E88E5)
val greenColor = Color(0xFF43A047)

/** 超过这个长度就不高亮了 —— 十几条正则跑一遍 + 逐字符标记，大文件会把主线程拖住 */
private const val MAX_HIGHLIGHT_LENGTH = 20000

/**
 * 除字面量之外的着色规则。
 *
 * 注释 / 字符串 / 正则**不在这里**，它们由 [scanLiterals] 一次线性扫描处理 —— 详见那边的注释。
 */
val rules = listOf(
    // 关键字 - 紫色
    HighlightRule(
        Regex("\\b(function|return|const|let|var|if|else|for|while|do|switch|case|break|continue|class|extends|import|export|default|from|as|await|async|yield|try|catch|finally|throw|new|this|super|with|debugger)\\b"),
        purpleColor
    ),
    // 数据类型和存储类 - 浅蓝色
    HighlightRule(
        Regex("\\b(typeof|instanceof|in|of|void|delete|static|public|private|protected|get|set|interface|implements|package|enum)\\b"),
        lightBlueColor
    ),
    // 内置对象和全局函数 - 浅蓝色。PMF 是插件侧唯一的 API 入口，别写成 PM
    HighlightRule(
        Regex("\\b(console|log|warn|error|info|debug|time|timeEnd|assert|Math|Date|String|Number|Boolean|Array|Object|Function|Symbol|Promise|JSON|RegExp|Error|Map|Set|WeakMap|WeakSet|Proxy|Reflect|Intl|ArrayBuffer|DataView|ENV|PMF)\\b"),
        lightBlueColor
    ),
    // 字面量 - 蓝色
    HighlightRule(Regex("\\b(true|false|null|undefined|NaN|Infinity)\\b"), blueColor),
    // 数字 - 橙色
    HighlightRule(Regex("\\b(\\d+\\.?\\d*)\\b"), pinkColor),
    HighlightRule(Regex("\\b(0x[0-9a-fA-F]+)\\b"), pinkColor),
    HighlightRule(Regex("\\b(0b[01]+)\\b"), pinkColor),
    HighlightRule(Regex("\\b(0o[0-7]+)\\b"), pinkColor),

    // 函数名和方法名 - 绿色
    HighlightRule(Regex("\\b([a-zA-Z_$][a-zA-Z0-9_$]*)(?=\\s*\\()"), greenColor),

    // 类名 - 浅蓝色 (以大写字母开头的标识符)
    HighlightRule(Regex("\\b([A-Z][a-zA-Z0-9_$]*)\\b"), lightBlueColor),

    // 属性访问 - 蓝色。只染属性名，不染点号（用 lookbehind，别把 . 也吃进去）
    HighlightRule(Regex("(?<=\\.)([a-zA-Z_$][a-zA-Z0-9_$]*)"), blueColor),
)

data class HighlightRule(
    val regex: Regex,
    val color: Color,
    val priority: Int = 50
)

private data class HighlightSpan(val start: Int, val end: Int, val color: Color)

/**
 * 把 JS 源码转成带颜色的 [AnnotatedString]。
 *
 * 两步走：
 *  1. 线性扫描标出**注释 / 字符串 / 正则**这三种「内部不该再着色」的字面量；
 *  2. 剩下的规则（关键字、数字、函数名…）只在没被占用的区间上色。
 *
 * 为什么第 1 步不能交给正则：`"http://a"` 里的 `//` 会被行注释规则吃掉，
 * 而注释里的引号又会被字符串规则当成开头 —— 两边互相污染，越改越乱。
 * 一次扫描按字符流走，天生不会串味。
 */
fun buildJsHighlightedString(code: String): AnnotatedString {
    if (code.isEmpty()) return AnnotatedString("")
    if (code.length > MAX_HIGHLIGHT_LENGTH) return AnnotatedString(code)

    // 0 = 空闲，1 = 已被占用（字面量或已上色的规则）
    val marks = IntArray(code.length)
    val spans = mutableListOf<HighlightSpan>()

    fun occupy(start: Int, end: Int, color: Color) {
        for (k in start until end) marks[k] = 1
        spans.add(HighlightSpan(start, end, color))
    }

    var i = 0
    while (i < code.length) {
        val c = code[i]
        when {
            // 行注释
            c == '/' && code.getOrNull(i + 1) == '/' -> {
                var j = i
                while (j < code.length && code[j] != '\n') j++
                occupy(i, j, grayColor)
                i = j
            }
            // 块注释
            c == '/' && code.getOrNull(i + 1) == '*' -> {
                val close = code.indexOf("*/", i + 2)
                val j = if (close < 0) code.length else close + 2
                occupy(i, j, grayColor)
                i = j
            }
            // 字符串 / 模板串（反引号里允许换行）
            c == '"' || c == '\'' || c == '`' -> {
                var j = i + 1
                while (j < code.length && code[j] != c) {
                    if (code[j] == '\\') j++
                    j++
                }
                j = (j + 1).coerceAtMost(code.length)
                occupy(i, j, pinkColor)
                i = j
            }
            // 正则字面量
            c == '/' && isRegexStart(code, i) -> {
                val j = scanRegex(code, i)
                if (j > i) {
                    occupy(i, j, redColor)
                    i = j
                } else {
                    i++
                }
            }
            else -> i++
        }
    }

    // 其余规则：跳过已被占用的区间
    rules.forEach { rule ->
        rule.regex.findAll(code).forEach match@{ match ->
            val start = match.range.first
            val end = match.range.last + 1
            if (start < 0 || end > code.length || start >= end) return@match
            for (k in start until end) {
                if (marks[k] != 0) return@match
            }
            occupy(start, end, rule.color)
        }
    }

    // 按位置拼装
    spans.sortBy { it.start }
    return buildAnnotatedString {
        var last = 0
        spans.forEach { span ->
            if (span.start > last) append(code.substring(last, span.start))
            withStyle(SpanStyle(color = span.color)) {
                append(code.substring(span.start, span.end))
            }
            last = span.end
        }
        if (last < code.length) append(code.substring(last))
    }
}

/**
 * 判断这个 `/` 是正则开头还是除号。
 *
 * 看它前面最近的非空白字符：只有出现在运算符/左括号/行首这些位置才可能是正则。
 * `a / b` 前面是标识符、`(a+b) / 2` 前面是右括号，都判成除法。
 */
private fun isRegexStart(code: String, index: Int): Boolean {
    var k = index - 1
    while (k >= 0 && code[k].isWhitespace()) k--
    if (k < 0) return true
    return code[k] in "(,=:[!&|?{};+-*%<>~^"
}

/** 从 [start] 处的 `/` 往后扫到闭合的 `/`，连同 flags 一起返回结束位置；没闭合返回 [start] */
private fun scanRegex(code: String, start: Int): Int {
    var j = start + 1
    var inClass = false
    while (j < code.length) {
        when (val d = code[j]) {
            '\\' -> j += 2
            '[' -> { inClass = true; j++ }
            ']' -> { inClass = false; j++ }
            '\n' -> return start
            '/' -> {
                if (inClass) { j++ } else {
                    j++
                    while (j < code.length && code[j].isLetter()) j++ // g i m u y s
                    return j
                }
            }
            else -> j++
        }
    }
    return start
}
