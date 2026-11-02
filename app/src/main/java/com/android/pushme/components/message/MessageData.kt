package com.android.pushme.components.message

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.android.pushme.RepostManager
import com.android.pushme.data.Message
import com.android.pushme.model.AppDataBase
import com.android.pushme.richtext.markdown.Markdown
import com.android.pushme.richtext.ui.BlockQuoteGutter
import com.android.pushme.richtext.ui.CodeBlockStyle
import com.android.pushme.richtext.ui.ListStyle
import com.android.pushme.richtext.ui.LocalRichTextStyle
import com.android.pushme.richtext.ui.RichText
import com.android.pushme.richtext.ui.RichTextStyle
import com.android.pushme.richtext.ui.TableStyle
import com.android.pushme.utils.Settings

@Composable
fun MessageData(
    modifier: Modifier = Modifier,
    message: Message,
    compact: Boolean = false,
    textColor: Color? = null,
    onTaskToggled: ((Int) -> Unit)? = null
) {
    if(message.content == "") return

    val content: @Composable () -> Unit = {
        when(message.type) {
            "markdata", "note" -> RichText(
                modifier = modifier,
                style = RichTextStyle.Default.copy(blockQuoteGutter = BlockQuoteGutter.BarGutter(startMargin = 0.sp))
            ) {
                Markdown(message.content, onTaskToggled = onTaskToggled)
            }

            "chart" -> run {
                val context = LocalContext.current
                val chartData = remember(message.content) { jsonChartData(message.content, context) }
                Chart(modifier, chartData)
            }

            "echarts" -> run {
                Echarts(modifier, message.content)
            }

            "svg" -> Svg(modifier, content = message.content)

            else -> {
                val (modifierText, fontSize) = rememberFontSizeModifier()
                Row(
                    modifier = modifier.fillMaxWidth().then(modifierText),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val density = LocalDensity.current
                    val y = remember(fontSize) {
                        with(density) {
                            // 微调偏移量，根据字体大小调整
                            (fontSize.value * fontSize.value / 12 * 0.02f).toDp()
                        }
                    }
                    Text(
                        text = message.content,
                        fontSize = fontSize,
                        lineHeight = fontSize,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8F),
                        modifier = Modifier.fillMaxWidth().offset(
                            y = -y
                        ),
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }

    if (compact) {
        // 紧凑样式：更小的字体和间距
        val compactTextStyle = LocalTextStyle.current.copy(
            fontSize = 14.sp,
            lineHeight = 18.sp,
            color = textColor ?: LocalTextStyle.current.color
        )
        // 默认值: paragraphSpacing=8.sp, heading=36/26/22/20/18.sp, listIndent=8/4/4.sp
        //         blockQuote=6/3/6.sp, codeBlock=16.sp, table=8.sp
        val compactRichTextStyle = (LocalRichTextStyle.current ?: RichTextStyle.Default).copy(
            paragraphSpacing = 0.sp,
            headingStyle = { level, textStyle ->
                when (level) {
                    0 -> textStyle.copy(fontSize = 24.sp, fontWeight = FontWeight.Bold)
                    1 -> textStyle.copy(fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    2 -> textStyle.copy(fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    3 -> textStyle.copy(fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    4 -> textStyle.copy(fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    else -> textStyle.copy(fontWeight = FontWeight.Bold)
                }
            },
            listStyle = ListStyle(
                markerIndent = 4.sp,
                contentsIndent = 2.sp,
                itemSpacing = 2.sp
            ),
            blockQuoteGutter = BlockQuoteGutter.BarGutter(
                startMargin = 3.sp,
                barWidth = 2.sp,
                endMargin = 3.sp
            ),
            codeBlockStyle = CodeBlockStyle(padding = 8.sp),
            tableStyle = TableStyle(cellPadding = 6.sp)
        )
        CompositionLocalProvider(
            LocalTextStyle provides compactTextStyle,
            LocalRichTextStyle provides compactRichTextStyle
        ) {
            content()
        }
    } else {
        content()
    }
}

@Composable
fun rememberFontSizeModifier(): Pair<Modifier, TextUnit> {
    val density = LocalDensity.current
    var width by remember { mutableIntStateOf(10) }
    val modifierText = remember {
        Modifier.fillMaxWidth().onSizeChanged {
            width = it.width
        }
    }
    val fontSize = remember(width) {
//        with(density){ (width.toDp().value / 120f * 24f) }.coerceIn(8f, 24f).sp
        with(density){ calculateFontSize(width.toDp().value) }.sp
    }
    return Pair(modifierText, fontSize)
}

fun calculateFontSize(width: Float): Float {
    // 定义边界条件
    val minFontSize = 8f
    val maxFontSize = 24f

    // 根据需求点计算线性关系
    // 已知点1: (80, 12)
    // 已知点2: (100, 20)

    // 计算斜率
//    val slope = (20f - 13f) / (100f - 80f) // = 8/20 = 0.4
    // 线性方程: fontSize = 0.4 * width + b
    // 代入点(80, 12): 12 = 0.4 * 80 + b → b = 12 - 32 = -20
//    val fontSize = slope * width - 20

    // 计算斜率
    val slope = (20f - 13f) / (100f - 80f) // = 7/20 = 0.35
    // 线性方程: fontSize = 0.35 * width + b
    // 代入点(80, 13): 13 = 0.35 * 80 + b → b = 13 - 28 = -15
    val fontSize = slope * width - 15

    // 限制在8-24范围内
    return fontSize.coerceIn(minFontSize, maxFontSize)
}

@Composable
fun rememberChartModifier(type: String = ""): Modifier {
    return remember {
        when(type) {
            "echarts", "chart" -> Modifier.aspectRatio(1f)
            "data" -> {
                Modifier.fillMaxWidth().padding(vertical = 30.dp)
            }
            else -> Modifier
        }
    }
}

/**
 * 切换 Markdown 内容中第 taskIndex 个任务列表项的勾选状态
 */
fun toggleTaskInContent(content: String, taskIndex: Int): String {
    val pattern = Regex("""^(\s*[-*+]\s+)\[([ xX])]""", RegexOption.MULTILINE)
    var currentIndex = 0
    return pattern.replace(content) { matchResult ->
        if (currentIndex == taskIndex) {
            currentIndex++
            val isChecked = matchResult.groupValues[2].lowercase() == "x"
            val newMarker = if (isChecked) " " else "x"
            "${matchResult.groupValues[1]}[$newMarker]"
        } else {
            currentIndex++
            matchResult.value
        }
    }
}

/**
 * 转发消息（如果配置了转发）
 */
fun repostMessage(title: String, content: String, date: String, type: String) {
    val repostManager = RepostManager()
    repostManager.refreshRepostData()
    if (repostManager.needRepost(title)) {
        RepostManager.doRepost(
            Settings.repostUrl,
            title,
            content,
            date,
            type
        )
    }
}

/**
 * 处理任务切换后的数据库更新和转发
 * @return 更新后的消息，如果消息不存在返回 null
 */
fun handleTaskToggled(messageId: Int, taskIndex: Int): Message? {
    val messageDao = AppDataBase.instance.getMessageDao()
    val dbMessage = messageDao.getByID(messageId) ?: return null

    val newContent = toggleTaskInContent(dbMessage.content, taskIndex)
    dbMessage.content = newContent
    messageDao.update(dbMessage)

    // 转发消息
    repostMessage(dbMessage.title, newContent, dbMessage.date, dbMessage.type)

    return dbMessage
}