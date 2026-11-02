package com.android.pushme

import android.content.Context
import android.util.Log
import android.widget.Toast
import com.android.pushme.data.Message
import com.android.pushme.data.TitleInfo
import com.android.pushme.model.AppDataBase
import com.android.pushme.model.MessageDao
import com.android.pushme.plugin.Plugin
import com.android.pushme.utils.CacheUtil
import com.android.pushme.utils.FaceManager
import com.android.pushme.utils.Settings
import androidx.work.workDataOf
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException

/**
 * 消息处理器
 * 负责消息解析、插件处理、数据库存储
 * 可在Service和主App中复用
 */
class MessageProcessor(private val context: Context) {
    private val tag = "$TAG[MessageProcessor]"
    private val messageDao: MessageDao = AppDataBase.instance.getMessageDao()
    private val plugin = Plugin(context)
    private val gson = Gson()

    /**
     * 消息处理结果
     * @param newMessage 保存到数据库后的消息（含id）
     * @param originalTitle 原始标题（用于转发匹配）
     * @param titleInfo 标题解析信息
     */
    data class ProcessResult(
        val newMessage: Message,
        val originalTitle: String,
        val titleInfo: TitleInfo
    )

    /**
     * 处理原始消息（MQTT、API、通知监听共用）
     * 包含：JSON解析 → 插件处理 → 默认值填充 → 标题解析 → 数据库存储
     * @return 处理结果，JSON 解析失败或内容为空时返回 null
     */
    fun processMessage(rawMessage: String): ProcessResult? {
        return try {
            // 收到消息通知
            // fromJson 对空串之类会返回 null，这里必须拦住，否则后面取属性就是 NPE
            val messageInfo = gson.fromJson(rawMessage, MessageInfo::class.java) ?: return null
            var title = messageInfo.title ?: ""
            var content = messageInfo.content ?: ""
            var date = messageInfo.date ?: ""
            var type = messageInfo.type ?: ""

            // 消息插件
            if(plugin.pluginCount > 0) {
                try {
                    val pluginMessage = plugin.calcMessage(Message(0, title, content, date, type))
                    title = pluginMessage.title
                    content = pluginMessage.content
                    date = pluginMessage.date
                    type = pluginMessage.type
                    Log.d(tag, "插件处理 title: $title, content: $content, date: $date, type: $type")
                } catch (e: Exception) {
                    Log.e(tag, e.toString())
                    val error = e.message?.split("ReferenceError:")?.getOrNull(1) ?: e.message
                    context.showToast("插件Error: ${error?.trim()}", Toast.LENGTH_LONG)
                }
            }

            // 完善消息日期
            if(date == "") {
                date = Utils.getCurrentTime()
            }
            // 完善消息类型
            if(type == Message.TYPE_TASK) {
                // 定时任务触发的消息（见 Message.TYPE_TASK）：插件没接管，直接丢弃。
                // 不能像 notify 那样兜底成 text —— 定时任务的内容通常是给插件看的
                // （比如一段脚本参数），原样入库并弹通知只会变成噪音。
                // 返回 null 后 handleReceivedMessage 会直接返回，不入库、不通知、不转发。
                Log.d(tag, "定时任务未被插件接管，丢弃: $title")
                return null
            } else if(type == Message.TYPE_NOTIFY) {
                // 通知转发进来的消息（见 Message.TYPE_NOTIFY）：按约定必须由插件处理成系统支持的类型。
                // 走到这里还是 notify，说明没有插件接管，退化成 markdown 代码块，
                // 把原始通知 JSON 原样展示出来，至少是可读的。
                // 注意：必须放在插件处理之后，插件改过 type 的就不会进这个分支。
                content = "```\n$content\n```"
                type = "markdown"
            } else if(type == "" || !Message.hasType(type)) {
                type = "text"
            }

            // 标题信息提取
            val titleInfo = Message.calcTitleInfo(title)
            Log.d(tag, "标题信息提取 $titleInfo")

            // 保存到数据库
            val newMessage = messageDao.addMessage(
                Message(
                    0,
                    titleInfo.title,
                    content,
                    date,
                    type,
                    false,
                    false,
                    titleInfo.user,
                    titleInfo.face,
                    titleInfo.theme
                ), context)
            Log.d(tag, "Message.add: ${newMessage.id}")

            ProcessResult(newMessage, title, titleInfo)
        } catch (e: JsonSyntaxException) {
            Log.e(tag, "processMessage:", e)
            null
        }
    }

    /**
     * 发送消息通知（含头像加载、TTS、重复通知）
     */
    fun showNotification(newMessage: Message, titleInfo: TitleInfo) {
        val content = newMessage.content
        val date = newMessage.date
        val type = newMessage.type

        // 消息主题
        val showEmoji = Message.themeIcon(titleInfo.theme)
        val showUser = if(titleInfo.user != "") "[${titleInfo.user}]" else ""
        var showTitle = showEmoji + showUser + titleInfo.title

        if(titleInfo.title == "") {
            showTitle = "${showEmoji}${showUser}收到消息"
        }

        var showContent = content
        if(showContent == "") {
            showContent = titleInfo.title
            showTitle = "${showEmoji}${showUser}收到消息"
        } else if(type == "markdown") {
            showContent = Message.markdownToText(showContent)
        } else if(type == "html") {
            showContent = Message.markdownToText(Message.removeStyleScript(showContent))
        }
        showContent = "$showContent\n$date"
        val channel = if(titleInfo.channel != "") titleInfo.channel else MAIN_CHANNEL

        // 加载本地缓存的分组头像（首次可能为空）
        val faceBitmap = if(titleInfo.user != "") {
            FaceManager.loadFaceBitmap(context, titleInfo.user)
        } else null

        context.showMessage(newMessage.id, showTitle, showContent, channel, type, titleInfo.user, Settings.messageAction == "显示", faceBitmap)

        // 如果有头像URL但本地没有缓存，异步下载，下载完成后更新通知
        if(titleInfo.user != "" && titleInfo.face != "" && faceBitmap == null) {
            FaceManager.downloadAndCacheFace(context, titleInfo.user, titleInfo.face) { success ->
                if(success) {
                    // 检查通知是否还在
                    val activeNotification = context.getActiveNotification(newMessage.id)
                    if(activeNotification != null) {
                        // 重新加载头像并更新通知
                        val newFaceBitmap = FaceManager.loadFaceBitmap(context, titleInfo.user)
                        context.showMessage(newMessage.id, showTitle, showContent, channel, type, titleInfo.user, Settings.messageAction == "显示", newFaceBitmap)
                        Log.d(tag, "通知已更新，添加头像: ${titleInfo.user}")
                    }
                }
            }
        }

        val speakSet by Settings.speakSet(channel)
        val isSpeak = speakSet == "开启"
        if(isSpeak) {
            val text = when(Settings.speakFields) {
                "标题" -> showTitle
                "内容" -> showContent
                else -> "$showTitle\n$showContent"
            }
            context.speakMessage(text)
        }
        val repeatSet by Settings.repeatSet(channel)
        if(repeatSet == "开启") {
            CacheUtil.repeatMessageId = newMessage.id
            CacheUtil.repeatMessageTitle = showTitle
            CacheUtil.repeatMessageContent = showContent
            CacheUtil.repeatMessageType = type
            CacheUtil.repeatMessageChannel = channel
            CacheUtil.repeatMessageGroup = titleInfo.user
            CacheUtil.repeatMessageSpeak = isSpeak
            startWorker(context, workDataOf(
                "action" to KEY_REPEAT_MESSAGE_ACTION,
            ), Settings.repeatTime, KEY_REPEAT_MESSAGE_WORKER)
        }
    }

    // ==================== 插件生命周期管理 ====================

    fun destroyPlugin() {
        plugin.destroy()
    }

    fun refreshPlugins() {
        plugin.initPluginJsCode()
    }

    fun refreshPluginEnv() {
        plugin.refreshPluginEnv()
    }

    fun refreshPluginPmf() {
        plugin.refreshPluginPmf()
    }

    /**
     * Ping回调，用于定时关闭QuickJS
     * @param pingedTimes 当前ping计数（由调用方维护）
     * @return 更新后的ping计数
     */
    fun onPinged(pingedTimes: Int): Int {
        var times = pingedTimes
        if(times < 2) times++
        if(times == 2) {
            plugin.destroy()
        }
        return times
    }
}
