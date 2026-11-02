package com.android.pushme.notify

import android.app.Notification
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import android.service.notification.StatusBarNotification
import com.android.pushme.utils.Settings

/**
 * 过滤规则的内存快照。
 *
 * 项目里的 Settings 每次读取都是一次 DataStore 同步读取（runBlocking），
 * 通知回调又跑在主线程上、且可能成批到来，所以这里统一读一次缓存起来，
 * 由监听服务在「服务连接」和「设置变更广播」时刷新。
 */
data class NotifyConfig(
    val enabled: Boolean = false,
    val ignoreOngoing: Boolean = true,
    val ignoreEmpty: Boolean = true,
    val appFilterMode: String = NotifyFilter.MODE_CLOSE,
    val appFilterPackages: List<String> = emptyList(),
    val limit: List<String> = emptyList(),
    val omit: List<String> = emptyList()
) {
    companion object {
        fun load(): NotifyConfig = NotifyConfig(
            enabled = Settings.notifyListenState == "开启",
            ignoreOngoing = Settings.notifyIgnoreOngoing == "开启",
            ignoreEmpty = Settings.notifyIgnoreEmpty == "开启",
            appFilterMode = Settings.notifyAppFilterMode,
            appFilterPackages = NotifyFilter.parsePackages(Settings.notifyAppFilterList),
            limit = NotifyFilter.parseKeywords(Settings.notifyLimit),
            omit = NotifyFilter.parseKeywords(Settings.notifyOmit)
        )
    }
}

/**
 * 通知过滤规则。
 *
 * 只做纯粹的「提取」和「判断」，不碰网络、不碰服务，方便单独测试。
 * 判断顺序：忽略常驻 → 忽略空内容 → 应用白/黑名单 → 关键词白/黑名单。
 *
 * ⚠️「忽略自己」不在本类里判断：那是**调用方（NotifyListenerService.onNotificationPosted）
 * 做的防回环守卫**，且是唯一判断点，避免同一个规则散落多处后行为不一致。
 * 它排在「监听总开关」之后、其余过滤规则之前。
 */
object NotifyFilter {

    /** 应用过滤模式 */
    const val MODE_CLOSE = "关闭"
    const val MODE_WHITE = "白名单"
    const val MODE_BLACK = "黑名单"

    private val appNameCache = HashMap<String, String>()

    /**
     * 是否是本应用自己发出的通知。
     *
     * 供调用方做防回环守卫用（见类注释）。比较的是运行时包名
     * `context.packageName`（即 applicationId），**不要写死字符串**：
     * debug 构建带 `.debug` 后缀，写死会漏判。
     */
    fun isSelf(context: Context, packageName: String): Boolean {
        return packageName == context.packageName
    }

    /**
     * 把系统通知转换成事件对象。纯提取，不做任何策略判断。
     * 返回 null 表示这条通知拿不到内容。
     */
    fun buildEvent(context: Context, sbn: StatusBarNotification): NotifyEvent? {
        val notification = sbn.notification ?: return null
        val extras: Bundle = notification.extras ?: return null

        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.trim().orEmpty()
        val content = readContent(extras)
        val subText = extras.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString()?.trim().orEmpty()

        return NotifyEvent(
            packageName = sbn.packageName,
            appName = resolveAppName(context, sbn.packageName),
            key = sbn.key ?: "",
            title = title,
            content = content,
            subText = subText,
            time = sbn.postTime,
            ongoing = sbn.isOngoing,
            removed = false
        )
    }

    /** 判断这条通知是否允许转发 */
    fun isAllowed(config: NotifyConfig, event: NotifyEvent): Boolean {
        // 1. 常驻通知（前台服务、音乐播放器、下载进度等）默认忽略
        if (config.ignoreOngoing && event.ongoing) return false

        // 2. 既没有标题也没有内容的通知没有转发价值
        if (config.ignoreEmpty && event.title.isBlank() && event.content.isBlank()) return false

        // 3. 应用包名 白名单 / 黑名单
        when (config.appFilterMode) {
            // 白名单：列表非空时，只放行列表内的应用
            MODE_WHITE -> if (config.appFilterPackages.isNotEmpty() &&
                config.appFilterPackages.none { it.equals(event.packageName, true) }) {
                return false
            }
            // 黑名单：列表内的应用一律不放行
            MODE_BLACK -> if (config.appFilterPackages.any { it.equals(event.packageName, true) }) {
                return false
            }
        }

        // 4. 关键词 白名单 / 黑名单
        val text = "${event.title}\n${event.content}"
        if (config.limit.isNotEmpty() && config.limit.none { text.contains(it, true) }) return false
        if (config.omit.any { text.contains(it, true) }) return false

        return true
    }

    /**
     * 通知内容正文。
     * 优先取大文本（通知展开后的完整内容），其次普通文本，最后多行文本。
     */
    @Suppress("DEPRECATION", "UNCHECKED_CAST")
    private fun readContent(extras: Bundle): String {
        extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()?.trim()?.takeIf { it.isNotEmpty() }
            ?.let { return it }

        extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()?.trim()?.takeIf { it.isNotEmpty() }
            ?.let { return it }

        // 多行文本在不同实现里可能存成 CharSequence[] 或 ArrayList<CharSequence>，两种都试一下
        val lines = extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)
            ?: (extras.get(Notification.EXTRA_TEXT_LINES) as? ArrayList<CharSequence>)?.toTypedArray()

        return lines?.joinToString("\n") { it.toString().trim() }?.trim().orEmpty()
    }

    /** 解析包名列表：按换行、逗号、分号分隔，去掉空项 */
    fun parsePackages(raw: String): List<String> {
        if (raw.isBlank()) return emptyList()
        return raw.split('\n', ',', ';', '，', '；')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
    }

    /** 解析关键词列表：按 | 分隔（与消息转发保持一致），同时兼容换行和逗号 */
    fun parseKeywords(raw: String): List<String> {
        if (raw.isBlank()) return emptyList()
        return raw.split('|', '\n', ',', '，')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
    }

    /**
     * 包名 -> 应用名称。
     *
     * Android 11 起有软件包可见性限制，拿不到时直接回退成包名，不影响转发。
     */
    private fun resolveAppName(context: Context, packageName: String): String {
        appNameCache[packageName]?.let { return it }

        val name = try {
            val info = context.packageManager.getApplicationInfo(packageName, 0)
            context.packageManager.getApplicationLabel(info).toString()
        } catch (e: PackageManager.NameNotFoundException) {
            packageName
        } catch (e: Exception) {
            packageName
        }

        appNameCache[packageName] = name
        return name
    }
}
