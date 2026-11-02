package com.android.pushme.data

import androidx.compose.ui.graphics.Color
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.android.pushme.ui.theme.FailureColor
import com.android.pushme.ui.theme.InfoColor
import com.android.pushme.ui.theme.SuccessColor
import com.android.pushme.ui.theme.WarningColor
import org.commonmark.parser.Parser
import org.commonmark.renderer.text.LineBreakRendering
import org.commonmark.renderer.text.TextContentRenderer
import java.util.regex.Pattern

@Entity(tableName = "Message", indices = [Index(value = ["type"]), Index(value = ["user"])])
data class Message(
    @PrimaryKey(autoGenerate = true)
    var id: Int = 0,

    @ColumnInfo(name = "title")
    var title: String = "",

    @ColumnInfo(name = "content")
    var content: String = "",

    @ColumnInfo(name = "date")
    var date: String = "",

    @ColumnInfo(name = "type")
    var type: String = "",

    @ColumnInfo(name = "read")
    var read: Boolean = true,

    @ColumnInfo(name = "star")
    var star: Boolean = false,

    @ColumnInfo(name = "user")
    var user: String = "",

    @ColumnInfo(name = "face")
    var face: String = "",

    @ColumnInfo(name = "theme")
    var theme: String = "",
) {
    companion object {
        /**
         * 其它应用通知转发进来的消息类型。
         *
         * ⚠️ 这**不是**一种最终消息类型，只是通知监听链路交给插件处理前的中间标记。
         *
         * 约定：插件必须把它处理成系统支持的类型（见文档）。
         * 如果插件处理后 type 仍然是 notify，说明没有插件接管，
         * `MessageProcessor.processMessage` 会强制改成 markdown，并把 content 用 ``` 包成代码块，
         * 把原始 JSON 原样展示出来。
         *
         * 因此它**既不在 notifyTypes 里，也不在 allTypes 里**——
         * 入库的消息不可能带这个类型，发送页也不会给用户这个选项。
         */
        const val TYPE_NOTIFY = "notify"

        /**
         * 定时任务触发时产生的消息类型。
         *
         * 和 [TYPE_NOTIFY] 一样，这**不是**一种最终消息类型，只是交给插件处理前的中间标记。
         *
         * 约定：插件必须把它处理成系统支持的类型（见文档）。
         * 如果插件处理后 type 仍然是 task，说明没有插件接管，
         * `MessageProcessor.processMessage` 会直接**丢弃这条消息**（不入库、不通知、不转发）。
         *
         * 因此它同样**既不在 notifyTypes 里，也不在 allTypes 里**。
         */
        const val TYPE_TASK = "task"

        val notifyTypes = listOf("", "text", "markdown", "html", "url")
        val dataTypes = listOf("data", "markdata", "chart", "echarts", "svg", "note")
        val allTypes = notifyTypes.filter { it != "" } + dataTypes

        val iconMaps = mapOf("i" to "⬜️", "s" to "\uD83D\uDFE9", "w" to "\uD83D\uDFE8", "f" to "\uD83D\uDFE5")
        val colorMaps = mapOf("i" to InfoColor, "s" to SuccessColor, "w" to WarningColor, "f" to FailureColor)

        // 预编译正则表达式，避免每次调用时重复编译
        private val themePattern = """^\[([iswfISWF])\]\s*(.*)""".toRegex()
        private val channelPattern = Pattern.compile("\\[~([^\\]]+)\\]")
        private val groupPattern = Pattern.compile("\\[#([^!\\]]+)(?:!([^\\]]+))?\\]")
        private val htmlTagPattern = Regex("<[^>]+>|&[^>]+;")

        fun themeIcon(theme: String): String {
            return iconMaps.getOrDefault(theme, "")
        }

        fun themeColor(entity: Message): Color? {
            if(entity.theme == "") { // 兼容老数据
                val titleTheme = getTitleTheme(entity.title)
                if(titleTheme.theme != "") {
                    entity.title = titleTheme.title
                    entity.theme = titleTheme.theme
                }
            }
            return colorMaps[entity.theme]
        }

        fun copyWithContentToText(entity: Message): Message {
            return entity.copy(
                content = when(entity.type) {
                    "markdown" -> markdownToText(entity.content)
                    "html" -> markdownToText(removeStyleScript(entity.content))
                    else -> textToInline(entity.content)
                }
            )
        }

        fun getTitleTheme(title: String): TitleTheme {
            return when (val match = themePattern.find(title)) {
                null -> TitleTheme("", title)
                else -> {
                    val (themeChar, actualTitle) = match.destructured
                    val theme = when (themeChar.uppercase()) {
                        "I" -> "i"
                        "S" -> "s"
                        "W" -> "w"
                        "F" -> "f"
                        else -> ""
                    }
                    TitleTheme(theme, actualTitle)
                }
            }
        }

        fun calcTitleInfo(input: String): TitleInfo {
            if (input.isBlank()) {
                return TitleInfo()
            }

            var title = input.trim()
            var user = ""
            var face = ""
            var channel = ""

            val titleTheme = getTitleTheme(title)
            val theme = titleTheme.theme
            title = titleTheme.title

            // 提取频道名字
            val channelMatcher = channelPattern.matcher(title)
            if (channelMatcher.find()) {
                channel = channelMatcher.group(1)?.trim() ?: ""
                title = title.replace(channelMatcher.group(0)?:"", "").trim()
            }

            // 提取分组信息
            val userMatcher = groupPattern.matcher(title)
            if (userMatcher.find()) {
                user = userMatcher.group(1)?.trim() ?: ""
                face = userMatcher.group(2)?.trim() ?: ""
                title = title.replace(userMatcher.group(0)?:"", "").trim()
            }

            // 清理标题中可能剩余的空格和多余的标点
            // title = title.trim().removeSuffix(" ").removeSuffix("]").removeSuffix("[")
            title = title.trim()

            return TitleInfo(theme, title, user, face, channel)
        }

        fun markdownToText(markdown: String, stripNewLine: Boolean = true): String {
            val parser = Parser.builder().build()
            val document = parser.parse(markdown)
            val renderer = TextContentRenderer.builder().lineBreakRendering(if (stripNewLine) LineBreakRendering.STRIP else LineBreakRendering.SEPARATE_BLOCKS).build()
            val text = renderer.render(document).replace(htmlTagPattern, " ")
            return textToInline(text)
        }

        fun textToInline(str: String): String {
            val text = str.replace(Regex("\\s+"), " ").trim(' ')
            return text.take(if (text.length > 255) 255 else text.length)
        }

        fun removeStyleScript(html: String): String {
            return html.replace(Regex("""(?s)<(style|script)(\s[^>]*)?>(.*?)</\1>|<(style|script)(\s[^>]*)?/>"""), "")
        }

        fun isNotify(type: String): Boolean {
            return notifyTypes.contains(type)
        }

        fun isData(type: String): Boolean {
            return dataTypes.contains(type)
        }

        fun hasType(type: String): Boolean {
            return isNotify(type) || isData(type)
        }
    }
}

@Entity(tableName = "MessagePlugin")
data class MessagePlugin(
    @PrimaryKey(autoGenerate = true)
    var id: Int?,

    @ColumnInfo(name = "title")
    var title: String = "",

    @ColumnInfo(name = "content")
    var content: String = "",

    @ColumnInfo(name = "sort")
    var sort: Int = 0,

    @ColumnInfo(name = "state")
    var state: Int = 1,

    // ==================== 以下字段只服务「插件商店」，手工添加的插件全部保持默认值 ====================
    //
    // ⚠️ 新字段必须**都带默认值**且加在末尾：`PluginEditScreen` 等处用的是位置参数构造，
    // 少一个默认值就编译不过；老数据迁移上来也靠这些默认值表示「不是商店装的」。

    /** 商店里的稳定标识（如 `pushme.plugin_time`）。手写插件为空串 —— 这是「是否来自商店」的判定依据 */
    @ColumnInfo(name = "plugin_key")
    var pluginKey: String = "",

    /** 版本号，用于判断可更新。手写插件为 0 */
    @ColumnInfo(name = "version_code")
    var versionCode: Int = 0,

    /** 版本号的可读形式（如 1.0.2），只用于展示 */
    @ColumnInfo(name = "version_name")
    var versionName: String = "",

    /** 来源：空串 = 本地手写；`official` = 官方商店 */
    @ColumnInfo(name = "source")
    var source: String = "",

    /**
     * 安装时从索引里带下来的 sha256（校验过文件完整性）。
     * ⚠️ 它同时是「本地是否被改过」的判据：用户在手写编辑器里改过内容后，
     * 实际内容的 sha256 会和这个值对不上 → 更新前要提示会覆盖。
     */
    @ColumnInfo(name = "sha256")
    var sha256: String = "",

    @ColumnInfo(name = "author")
    var author: String = "",

    @ColumnInfo(name = "description")
    var description: String = "",

    /** 用户选择「忽略此版本」时记下的 versionCode，避免每次进商店都提示同一条更新 */
    @ColumnInfo(name = "ignored_version")
    var ignoredVersion: Int = 0
)





data class TitleTheme(
    val theme: String = "", // 消息主题
    val title: String = "", // 消息标题
) {
    override fun toString(): String {
        return "主题: $theme, 标题: $title"
    }
}

data class TitleInfo(
    val theme: String = "", // 消息主题
    val title: String = "", // 消息标题
    val user: String = "", // 消息分组
    val face: String = "", // 分组图片
    val channel: String = "", // 消息通道
) {
    override fun toString(): String {
        return "主题: $theme, 标题: $title, 分组: $user, 图片: $face, 频道: $channel"
    }
}

data class FieldCount(
    var field: String = "",
    var fieldCount: Int = 0
)