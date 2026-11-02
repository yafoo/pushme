package com.android.pushme.utils

import androidx.annotation.ArrayRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.res.stringArrayResource
import com.android.pushme.R
import com.android.pushme.data.AutoSave
import com.android.pushme.model.AppDataStore
import com.android.pushme.ui.theme.Material3Color
import io.mhssn.colorpicker.ext.toHex

// 系统设置类
object Settings {
    var appLanguage by AutoSave(KEY_APP_LANGUAGE, "系统")

    // MQTT
    var mqttState by AutoSave("mqtt_state", "PushMe")
    var pushId by AutoSave("push_id", 0)
    var pushKey by AutoSave("push_key", "")
    var tempKey by AutoSave("temp_key", "")
    var tempTime by AutoSave("temp_time", 0)
    var mqttHost by AutoSave("mqtt_host", "47.120.43.147")
    var mqttPort by AutoSave("mqtt_port", 9010)
    // MQTT 心跳间隔，**单位是秒**（Paho 的 keepAliveInterval 原生单位，随 CONNECT 报文发给服务端）。
    // 界面上按分钟展示（1~20 分钟），所以这里的取值是 60~1200，默认 60（=1 分钟）。
    // 改小 → 掉线发现更快；改大 → 更省电省流量，但掉线发现更慢。
    // ⚠️ 它是连接参数，改动后必须重新连接才生效（见 MQTTClient.initConnect 里的 mOptions）。
    var mqttHeartbeat by AutoSave("mqtt_heartbeat", 60)
    var mqttCert by AutoSave("mqtt_cert", "-----BEGIN CERTIFICATE-----\n" +
            "MIIDDzCCAfegAwIBAgIJF92HhT5y5k1nMA0GCSqGSIb3DQEBCwUAMDYxFjAUBgNV\n" +
            "BAMTDTQ3LjEyMC40My4xNDcxCzAJBgNVBAYTAkNOMQ8wDQYDVQQKEwZQdXNoTWUw\n" +
            "HhcNMjUxMjEyMDA0NDIzWhcNMzUxMjEwMDA0NDIzWjA2MRYwFAYDVQQDEw00Ny4x\n" +
            "MjAuNDMuMTQ3MQswCQYDVQQGEwJDTjEPMA0GA1UEChMGUHVzaE1lMIIBIjANBgkq\n" +
            "hkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAy5IIVR5N/crn4NaXJw/R57PoNn9GLZzS\n" +
            "wVNAOvTqs24Qij4DORWgVFZ21sWFDprL+7klKpYYOHh/Ilbg9gpRavVUBrU4yhXt\n" +
            "JwAqjYz35rmIk5x+IpXG6LKBa/C7e5WRqAxb/W842PNwtFbopR+U6Bq8PwFNEZeo\n" +
            "mPbMShGHKYyjy5V2Rm2qvxvbfkPZxekbl3XngsCO6uaxbdSbm6j/EJDzk3MgOyDA\n" +
            "MHN9brtLTUDEb0UoLEk0VbtzNIDiFfT7GOh3wEoFXLFTodxikhGApWHEVDdnlmTO\n" +
            "n6iHnjlzQtAdCRoj1A07092FspgKW6Qvjeemi9C/zY0ZNbYH68Q/xwIDAQABoyAw\n" +
            "HjAPBgNVHREECDAGhwQveCuTMAsGA1UdDwQEAwIFoDANBgkqhkiG9w0BAQsFAAOC\n" +
            "AQEAYok8lsTr1KaEkgmqmlCgJC8LfQB7Y5LfUfayiFTFeMRsTWEIgbzd9cWOyrPD\n" +
            "Ke6/r3dzJQ7WnBbrRBWHX83d2PUUHYJT84F8nEBuoe+kyypVHUzw2210hJHqdkcP\n" +
            "QE7nXVUZ5KikDjzCfpkbgJ2Qdn9m/YPUKSuUdj4P0RJsRrtaPcHEJXI1MINFaZWq\n" +
            "GnRgtkUp3EjDQdF0b5VOwIxJPj6L68L0ZXmFikx/CKHfy9HkiMZWgP+rtyQhJ8g8\n" +
            "wd/r2Jwxmogr47smkDTkpePMp1yxKB4ebXmhNAQQTd1fP957BVJtdogeGrHXLTVF\n" +
            "+DIU4PghenArtxA4l/O8Qum4jw==\n" +
            "-----END CERTIFICATE-----\n")

    // Self Host
    var selfKey by AutoSave("self_key", pushKey)
    var selfHost by AutoSave("self_host", "")
    var selfPort by AutoSave("self_port", 3100)
    var selfProtocol by AutoSave("self_protocol", "TCP")
    var selfTls by AutoSave("self_tls", "无证书")
    var selfCert by AutoSave("self_cert", "")
    var selfState by AutoSave("self_state", "关闭")
    var selfApi by AutoSave("self_api", "")

    // API Service
    var apiHost by AutoSave("api_host", "0.0.0.0")
    var apiPort by AutoSave("api_port", 3010)
    var apiVerifyKey by AutoSave("api_verify_key", "开启")
    var apiState by AutoSave("api_state", "关闭")

    // 前台服务：开启后核心服务以前台服务方式运行，避免被系统冻结导致 MQTT 掉线
    var serviceForeground by AutoSave("service_foreground", "关闭")

    // 通知监听：读取其它应用的通知并转发到服务（需要用户手动开启「监听系统通知」）
    var notifyListenState by AutoSave("notify_listen_state", "关闭")
    // 应用过滤模式：关闭 / 白名单 / 黑名单
    var notifyAppFilterMode by AutoSave("notify_app_filter_mode", "关闭")
    // 应用过滤名单，一行一个包名。
    // ⚠️ 只由「应用选择页」(screen/NotifyAppFilterScreen.kt) 读写，勾选即时落盘；
    // 过滤页只展示数量，不参与保存，避免两边各存一份互相覆盖。
    var notifyAppFilterList by AutoSave("notify_app_filter_list", "")
    // 关键词白名单 / 黑名单，多个以 | 隔开
    var notifyLimit by AutoSave("notify_limit", "")
    var notifyOmit by AutoSave("notify_omit", "")
    // 忽略常驻通知（前台服务、音乐播放器等）
    var notifyIgnoreOngoing by AutoSave("notify_ignore_ongoing", "开启")
    // 忽略没有标题也没有内容的通知
    var notifyIgnoreEmpty by AutoSave("notify_ignore_empty", "开启")

    // 定时任务：总开关。关闭时不排任何闹钟（已排的会在 TaskScheduler.scheduleAll 里撤销）
    var taskState by AutoSave("task_state", "关闭")
    // 定时任务列表，整份 JSON 存储。⚠️ 只由 task/TaskStore.kt 读写，别在别处直接动。
    var taskList by AutoSave("task_list", "")
    // 闹钟触发时服务没在运行，消息先入队，等下次服务启动补处理（JSON 字符串数组）
    var pendingTaskQueue by AutoSave("task_pending_queue", "")

    // 消息转发
    var repostUrl by AutoSave("post_url", "")
    var repostWeek by AutoSave("post_week", listOf(),
        serializer = {
            it.joinToString(",")
        },
        deserializer = {
            it.split(",").toList()
        })
    var repostTimeStart by AutoSave("post_time_start", 0)
    var repostTimeEnd by AutoSave("post_time_end", 0)
    var repostLimit by AutoSave("post_limit", "")
    var repostOmit by AutoSave("post_omit", "")
    var repostPushKey by AutoSave("repost_push_key", "关闭")
    var repostState by AutoSave("post_state", "关闭")


    // 主页设置
    var msgUserTop by AutoSave("msg_user_top", "关闭")
    var msgUserEmpty by AutoSave("msg_user_empty", "关闭")
    var msgUserClick by AutoSave("msg_user_click", "详情")
    var msgLoadMore by AutoSave("msg_load_more", "关闭")
    var msgFontScale by AutoSave("font_scale", 1f)

    // 消息设置
    var linkClickMethod by AutoSave("link_click", "系统")
    var htmlJs by AutoSave("html_js", "禁用")
    var msgReadState by AutoSave("msg_read_init", "未读")

    // APP设置
    var appTheme by AutoSave("app_theme", "系统")
    var appColor by AutoSave("app_color", "PushMe")
    var selfColor by AutoSave("self_color", Material3Color.toHex(true))
    var recentTask by AutoSave("recent_task", "显示")
    var statusNotify by AutoSave("channel", "限断")
    var statusNotifyMuteStartTime by AutoSave("channel_start_time", 0)
    var statusNotifyMuteEndTime by AutoSave("channel_end_time", 0)
    var speakFields by AutoSave("speak", "标题")
    var repeatTime by AutoSave("repeat", 30)
    var messageAction by AutoSave("message_action", "显示")

    // 权限设置
    fun speakKey(channelId: String): String {
        return "spk_$channelId"
    }
    fun speakSet(channelId: String): AutoSave<String> { // 语音通知设置
        return AutoSave(speakKey(channelId), "关闭")
    }
    fun speakRemove(channelId: String) {
        AppDataStore.remove(speakKey(channelId))
    }
    fun repeatKey(channelId: String): String {
        return "rpt_$channelId"
    }
    fun repeatSet(channelId: String): AutoSave<String> { // 重复通知设置
        return AutoSave(repeatKey(channelId), "关闭")
    }
    fun repeatRemove(channelId: String) {
        AppDataStore.remove(repeatKey(channelId))
    }

    // 其他设置
    var appLogSend by AutoSave("app_log", "发送")
    var appVersion = ""

    // 数据小屏排序
    var dashboardSort by AutoSave("dashboard_sort", "desc")
    var dashboardSortBy by AutoSave("dashboard_sort_by", "date")

    // 插件环境变量及方法状态，暂不处理
    // 悬浮窗数据暂不处理

    @Composable
    fun getSettingOptions(keys: List<String>, @ArrayRes arrayResId: Int): Map<String, String> {
        val labels = stringArrayResource(arrayResId)
        return remember { keys.zip(labels.asList()).toMap() }
    }

    @Composable
    fun getSettingOptions(keys: List<String>, labels: List<String>): Map<String, String> {
        return remember { keys.zip(labels).toMap() }
    }

    @Composable
    fun getOnOffOptions(): Map<String, String> {
        val labels = stringArrayResource(R.array.on_off_options)
        return remember { listOf("开启", "关闭").zip(labels.asList()).toMap() }
    }
}