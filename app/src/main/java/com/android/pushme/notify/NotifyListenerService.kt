package com.android.pushme.notify

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.android.pushme.MessageInfo
import com.android.pushme.TAG
import com.android.pushme.Utils
import com.android.pushme.data.Message
import com.android.pushme.sendNotifyEventBroadcast
import com.google.gson.Gson
import com.google.gson.GsonBuilder

// 通知监听服务自己的广播（与 MQTTService 的广播区分开）
// action 用本应用真实包名前缀，不要用 com.example（Android Studio 模板命名空间）
const val ACTION_NOTIFY_LISTENER_RECEIVER = "com.android.pushme.NOTIFY_LISTENER_RECEIVER"
const val KEY_NOTIFY_LISTENER_ACTION = "NotifyListenerAction"
const val NOTIFY_LISTENER_REFRESH_CONFIG = "refresh_notify_config"

/**
 * 读取其它应用通知的监听服务。
 *
 * 使用前提：用户在系统设置里为本应用打开「通知使用权」。
 * 没有授权时这个服务不会被系统绑定，onCreate 都不会被调用。
 *
 * 开关关闭时服务仍然被系统绑定着（系统绑定与否不受应用控制），
 * 但所有回调都会直接 return，等同于不监听。
 */
class NotifyListenerService : NotificationListenerService() {
    private val tag = "$TAG[NOTIFY LISTENER]"
    private val gson = Gson()

    /**
     * 事件 JSON 专用的美化 Gson。
     *
     * 只用在外层 MessageInfo 的 content 字段上（事件本身的序列化），外层 MessageInfo 仍走紧凑的 [gson]。
     * 用 setPrettyPrinting 的原因：
     * - 有插件时：插件对 content 做 JSON.parse，空白字符不影响解析结果；
     * - 没插件时：MessageProcessor 会把 content 包进 ``` 代码块按 markdown 展示，
     *   美化后的多行缩进 JSON 比一整行更容易看。
     * 代价只是几十个字节的空白，可以忽略。
     */
    private val prettyGson = GsonBuilder().setPrettyPrinting().create()

    /** 过滤规则快照，避免每条通知都去同步读 DataStore */
    @Volatile
    private var config: NotifyConfig = NotifyConfig()

    /** 最近转发过的通知签名 -> 时间戳，用于吞掉同一条通知的重复回调 */
    private val recentSignatures = LinkedHashMap<String, Long>()

    private var configReceiver: BroadcastReceiver? = null

    override fun onCreate() {
        super.onCreate()
        Log.d(tag, "onCreate")

        config = NotifyConfig.load()
        registerConfigReceiver()
    }

    override fun onDestroy() {
        Log.d(tag, "onDestroy")
        unregisterConfigReceiver()
        recentSignatures.clear()
        super.onDestroy()
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        Log.d(tag, "onListenerConnected: enabled=${config.enabled}")
        // 重新连接时同步一次设置，避免用户在授权页里改完开关后状态不一致
        refreshConfig()
    }

    override fun onListenerDisconnected() {
        Log.w(tag, "onListenerDisconnected，尝试重新绑定")
        super.onListenerDisconnected()

        // 应用更新、系统内存紧张等情况下系统会解绑监听服务，
        // 这里主动请求重新绑定，否则用户会以为功能失效了
        try {
            requestRebind(ComponentName(this, NotifyListenerService::class.java))
        } catch (e: Exception) {
            Log.e(tag, "requestRebind: ", e)
        }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        super.onNotificationPosted(sbn)

        // ① 开关关闭：不处理任何通知。
        // 放在最前面是因为它最廉价（只读一个布尔字段），而且关闭时什么都不转发，
        // 下面的防回环守卫也就没有必要执行。
        val current = config
        if (!current.enabled) return

        // ② 本应用自己的通知：无条件丢弃。
        // 这是**唯一**的「忽略自己」判断点（buildEvent 不再重复判断），也是防回环的硬约束：
        // 转发出去的通知最终又会由本应用发出一条通知，不拦住就会「通知 → 消息 → 再通知」无限循环。
        // 它不受任何**过滤设置**影响（应用白/黑名单、关键词、常驻、空内容都在它之后判断）。
        if (NotifyFilter.isSelf(this, sbn.packageName)) return

        try {
            val event = NotifyFilter.buildEvent(this, sbn) ?: return
            if (!NotifyFilter.isAllowed(current, event)) return
            if (isDuplicate(event)) return

            Log.d(tag, "转发通知: ${event.appName}(${event.packageName}) | ${event.title} | ${event.content}")
            sendNotifyEventBroadcast(buildMessageJson(event))
        } catch (e: Exception) {
            Log.e(tag, "onNotificationPosted: ", e)
        }
    }

    /**
     * 把通知事件包装成 PushMe 的标准消息 JSON。
     *
     * 结构要和 MQTT / API 消息完全一致，这样 MQTTService 收到后可以原样丢进现成的消息处理链路
     * （插件 → 入库 → 通知/悬浮窗 → 消息转发），服务侧不需要再做任何解析或包装。
     *
     * - title   通知标题，前面带上来源应用的分组标记
     * - content 完整的事件 JSON（美化输出），插件可以从里面取包名、应用名、通知原文等
     * - date    通知自身的产生时间
     * - type    固定 notify，按约定必须由插件处理成系统支持的类型
     *
     * 关于异常：这里**不单独 try/catch**。
     * - [NotifyEvent] 是 9 个 String/Long/Boolean 字段的扁平 data class，Gson 反射序列化不可能失败
     *   （只有循环引用、非序列化类型才会抛 JsonIOException/IllegalArgumentException）；
     * - 即便真抛了，调用方 onNotificationPosted 已用 try/catch(Exception) 兜住并打日志，
     *   不会崩溃、也不会外泄。这里再包一层只会多一层日志噪音。
     *
     * ⚠️ 注意：content 里的字段名能被正确输出，依赖 proguard-rules.pro 里的
     * `-keep class com.android.pushme.notify.NotifyEvent {*;}`，漏了 release 包 key 会变成 a/b/c。
     */
    private fun buildMessageJson(event: NotifyEvent): String {
        // 标题为空时退回应用名，避免消息列表里出现一条没有标题的记录
        val rawTitle = event.title.ifBlank { event.appName }

        // 来源应用名用标题里的分组标记 [#应用名] 承载：
        // 这样消息会按来源应用分组，通知上也会显示成「[微信] 张三」。
        // 应用名里若含 [ ] ! 会破坏分组/头像的解析规则（Message.calcTitleInfo），先清掉。
        val source = event.appName.replace(Regex("[\\[\\]!]"), "").trim()

        val title = if (source.isNotEmpty() && rawTitle != source) "[#$source] $rawTitle" else rawTitle

        return gson.toJson(
            MessageInfo(
                title = title,
                content = prettyGson.toJson(event),
                date = Utils.formatTime(event.time),
                type = Message.TYPE_NOTIFY
            )
        )
    }

    /**
     * 同一条通知会因为内容更新、大文本展开等原因重复回调 onNotificationPosted，
     * 内容没变且在时间窗口内的一律视为同一条，只转发一次。
     */
    private fun isDuplicate(event: NotifyEvent): Boolean {
        val now = System.currentTimeMillis()
        val signature = "${event.key}\u0000${event.title}\u0000${event.content}"

        // 先清掉过期记录，避免长期占用内存
        val iterator = recentSignatures.entries.iterator()
        while (iterator.hasNext()) {
            if (now - iterator.next().value > DEDUPE_WINDOW) {
                iterator.remove()
            }
        }

        val last = recentSignatures[signature]
        if (last != null && now - last < DEDUPE_WINDOW) return true

        recentSignatures[signature] = now
        return false
    }

    private fun refreshConfig() {
        config = NotifyConfig.load()
        Log.d(tag, "refreshConfig: enabled=${config.enabled}")
    }

    private fun registerConfigReceiver() {
        if (configReceiver != null) return
        val filter = IntentFilter(ACTION_NOTIFY_LISTENER_RECEIVER)
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                val action = intent.getStringExtra(KEY_NOTIFY_LISTENER_ACTION)
                Log.d(tag, "ConfigReceiver: $action")
                if (action == NOTIFY_LISTENER_REFRESH_CONFIG) {
                    refreshConfig()
                }
            }
        }
        configReceiver = receiver
        if (Build.VERSION.SDK_INT >= 33) {
            // 这条广播只有本应用会发（MainActivity 改完设置后通知刷新过滤规则），
            // 用 NOT_EXPORTED：别的应用即使知道 action 字符串也发不进来。
            registerReceiver(receiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(receiver, filter)
        }
    }

    private fun unregisterConfigReceiver() {
        configReceiver?.let {
            try {
                unregisterReceiver(it)
            } catch (e: IllegalArgumentException) {
                Log.w(tag, "unregisterConfigReceiver: $e")
            }
        }
        configReceiver = null
    }

    companion object {
        // 同一条通知内容的去重时间窗口
        private const val DEDUPE_WINDOW = 5_000L
    }
}
