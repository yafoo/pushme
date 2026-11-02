package com.android.pushme

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.graphics.Bitmap
import android.util.Log
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat.Builder
import androidx.core.app.NotificationCompat.PRIORITY_DEFAULT
import androidx.core.app.NotificationCompat.VISIBILITY_PRIVATE
import androidx.core.app.NotificationCompat.VISIBILITY_PUBLIC
import androidx.core.app.TaskStackBuilder
import com.android.pushme.model.ChannelDetail
import com.android.pushme.notify.ACTION_NOTIFY_LISTENER_RECEIVER
import com.android.pushme.notify.KEY_NOTIFY_LISTENER_ACTION
import com.android.pushme.notify.NOTIFY_LISTENER_REFRESH_CONFIG
import com.android.pushme.utils.LangUtil

// ==================== 常量定义 ====================

const val MAIN_CHANNEL = "MainChannel"
const val STATE_CHANNEL = "StateChannel"

// 前台服务常驻通知的固定 id（状态通知用的是 1，这里避开）
const val SERVICE_FOREGROUND_NOTIFICATION_ID = 2

// ==================== 数据模型 ====================

/**
 * 通知数据模型
 */
data class Notification(
    var id: Int,
    var title: String,
    var message: String,
    var action: String? = null,
    var actionFlag: String? = null,
    var visibility: Int = VISIBILITY_PUBLIC,
    var activityIntent: Intent? = null,
    val isDeepLink: Boolean = false,
    var builder: Builder? = null,
    var manager: NotificationManager? = null,
    var importance: Int = NotificationManager.IMPORTANCE_HIGH,
    var channel: String = MAIN_CHANNEL,
    var group: String = "",
    var permission: Boolean = false,
    var largeIcon: Bitmap? = null
) {
    companion object {
        @Volatile
        private var messageIdCounter = 3
        
        @Synchronized
        fun generateId(): Int = messageIdCounter++
    }

    /**
     * 添加通知操作按钮
     * @param context 上下文
     * @param buttonText 按钮显示文本
     * @param actionFlag 动作标识（如 KEY_NOTIFICATION_ACTION_READ、KEY_NOTIFICATION_ACTION_DELETE）
     */
    fun addActionButton(context: Context, buttonText: String, actionFlag: String) {
        if (builder == null) return

        val intent = Intent(context, ReceiverNotificationAction::class.java).apply {
            putExtra(KEY_NOTIFICATION_ACTION, actionFlag)
            putExtra(KEY_NOTIFICATION_ID, id)
            setPackage(context.packageName)
        }

        val pendingIntent = createPendingIntent(context, id + actionFlag.hashCode(), intent)
        builder?.addAction(0, buttonText, pendingIntent)
    }
}

// ==================== 通知构建与显示 ====================

/**
 * 构建通知对象
 */
fun Context.buildNotification(
    id: Int,
    title: String,
    message: String,
    action: String? = null,
    actionFlag: String? = null,
    visibility: Int = VISIBILITY_PUBLIC,
    activityIntent: Intent? = null,
    isDeepLink: Boolean = false,
    importance: Int = NotificationManager.IMPORTANCE_HIGH,
    channel: String = MAIN_CHANNEL,
    group: String = "",
    largeIcon: Bitmap? = null
): Notification {
    val notification = Notification(
        id = id,
        title = title,
        message = message,
        action = action,
        actionFlag = actionFlag,
        visibility = visibility,
        activityIntent = activityIntent,
        isDeepLink = isDeepLink,
        importance = importance,
        channel = channel,
        group = group,
        largeIcon = largeIcon
    )
    notification.manager = getNotificationManager(importance, channel)
    notification.builder = notification.createBuilder(this)
    return notification
}

/**
 * 创建通知 Builder
 */
private fun Notification.createBuilder(context: Context): Builder {
    // 自动生成 ID
    if (id <= 0) {
        id = Notification.generateId()
    }

    val builder = Builder(context, channel)
        .setContentTitle(title)
        .setContentText(message)
        .setSmallIcon(R.mipmap.ic_launcher)
        .setPriority(PRIORITY_DEFAULT)
        .setVisibility(visibility)
        .setAutoCancel(false)
        .setShowWhen(true)
        .setTicker(title)

    // 设置大图标（分组头像）
    largeIcon?.let {
        builder.setLargeIcon(it)
    }

    // 设置通知组
    if (group == "status") {
        builder.setGroup(group)
    }

    // 设置锁屏隐私版本
    if (visibility == VISIBILITY_PRIVATE) {
        builder.setPublicVersion(
            Builder(context, channel)
                .setContentTitle("收到一条新的消息")
                .setContentText("请解锁屏幕后查看！")
                .build()
        )
    }

    // 低优先级通知静音
    if (importance == NotificationManager.IMPORTANCE_LOW || 
        importance == NotificationManager.IMPORTANCE_MIN) {
        builder.setSound(null)
        builder.setOnlyAlertOnce(true)
    }

    // 添加操作按钮
    action?.let { addActionToBuilder(context, builder) }

    // 设置点击意图
    setContentIntent(context, builder)

    // 检查通知权限
    checkNotificationPermission(context)

    return builder
}

/**
 * 添加通知操作按钮
 */
private fun Notification.addActionToBuilder(
    context: Context,
    builder: Builder?
) {
    val intent = Intent(context, ReceiverNotificationAction::class.java).apply {
        putExtra(KEY_NOTIFICATION_ACTION, actionFlag)
        putExtra(KEY_NOTIFICATION_ID, id)
    }

    val pendingIntent = createPendingIntent(context, id, intent)
    builder?.addAction(0, action, pendingIntent)
}

/**
 * 设置通知点击意图
 */
private fun Notification.setContentIntent(context: Context, builder: Builder) {
    activityIntent?.let { intent ->
        val flg = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }

        val pendingIntent = if (isDeepLink) {
            TaskStackBuilder.create(context).run {
                addNextIntentWithParentStack(intent)
                getPendingIntent(1, flg)
            }
        } else {
            PendingIntent.getActivity(context, id, intent, flg)
        }

        pendingIntent?.let { builder.setContentIntent(it) }
    }
}

/**
 * 创建 PendingIntent
 */
private fun createPendingIntent(context: Context, requestCode: Int, intent: Intent): PendingIntent {
    val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        PendingIntent.FLAG_IMMUTABLE
    } else {
        PendingIntent.FLAG_UPDATE_CURRENT
    }
    return PendingIntent.getBroadcast(context, requestCode, intent, flags)
}

/**
 * 检查通知权限
 */
private fun Notification.checkNotificationPermission(context: Context) {
    permission = ActivityCompat.checkSelfPermission(
        context,
        Manifest.permission.POST_NOTIFICATIONS
    ) == PackageManager.PERMISSION_GRANTED
}

/**
 * 显示通知
 */
fun Notification.show(): Notification {
    if (permission) {
        builder?.let {
            manager?.notify(id, it.build())
        }
    }
    Log.d("PUSHME[NOTIFICATION]", "$channel: $title $message")
    return this
}

// ==================== 通知管理操作 ====================

/**
 * 获取 NotificationManager 实例
 */
fun Context.getNotificationManager(
    importance: Int = NotificationManager.IMPORTANCE_HIGH,
    channel: String = MAIN_CHANNEL
): NotificationManager {
    return createNotificationChannel(this, channel, importance)
}

/**
 * 取消指定 ID 的通知
 */
fun Context.cancelNotification(id: Int) {
    getNotificationManager().cancel(id)
}

/**
 * 取消所有通知（可选择排除某个 ID）
 * 前台服务的常驻通知不会被清掉，否则服务就失去前台身份了
 */
fun Context.cancelAllNotifications(excludeId: Int = 0) {
    val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        ?: return

    if (excludeId == 0) {
        notificationManager.activeNotifications
            .filter { it.id != SERVICE_FOREGROUND_NOTIFICATION_ID }
            .forEach { notificationManager.cancel(it.id) }
        return
    }

    notificationManager.activeNotifications
        .filter { it.id != excludeId && it.id != SERVICE_FOREGROUND_NOTIFICATION_ID }
        .forEach { notificationManager.cancel(it.id) }
}

/**
 * 获取活动中的通知
 */
fun Context.getActiveNotification(notificationId: Int = 0): android.app.Notification? {
    val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        ?: return null

    return notificationManager.activeNotifications
        .find { it.id == notificationId }
        ?.notification
}

// ==================== 消息展示便捷方法 ====================

/**
 * 显示消息通知（带已读和删除按钮）
 */
fun Context.showMessage(
    id: Int = 0,
    title: String,
    message: String,
    channel: String = MAIN_CHANNEL,
    type: String = "",
    group: String = "",
    actionShow: Boolean = false,
    largeIcon: Bitmap? = null
) {
    val clickIntent = Intent(this, MainActivity::class.java).apply {
        putExtra(MESSAGE_ID, id)
        putExtra(MESSAGE_TYPE, type)
        putExtra(MESSAGE_CONTENT, if (type == "url") message else "")
        addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
    }

    val notification = buildNotification(
        id = id,
        title = title,
        message = message,
        visibility = VISIBILITY_PUBLIC,
        activityIntent = clickIntent,
        channel = channel,
        group = group,
        largeIcon = largeIcon
    )

    if(actionShow) {
        // 添加已读按钮
        notification.addActionButton(this, "已读", KEY_NOTIFICATION_ACTION_READ)
        // 添加删除按钮
        notification.addActionButton(this, "删除", KEY_NOTIFICATION_ACTION_DELETE)
    }
    
    notification.show()
}

/**
 * 显示状态通知
 *
 * @param foreground 是否处于前台服务模式，true 时副标题显示"前台服务"，否则显示"服务状态"
 */
fun Context.showStatus(
    title: String,
    message: String,
    silent: Boolean = false,
    foreground: Boolean = false
) {
    val clickIntent = Intent(this, MainActivity::class.java)

    val notification = buildNotification(
        id = 1,
        title = title,
        message = message,
        visibility = VISIBILITY_PUBLIC,
        activityIntent = clickIntent,
        channel = STATE_CHANNEL,
        group = "status"
    )
    val subText = LangUtil.lang(
        this,
        if(foreground) R.string.service_state_foreground else R.string.service_state
    )
    notification.builder?.setShowWhen(false)?.setSubText(subText)?.setSilent(silent)
    notification.show()
}

/**
 * 构建前台服务的常驻通知
 *
 * 复用状态通知通道，静默、不可划掉；内容保持固定，避免心跳刷新时通知栏频繁闪动。
 */
fun Context.buildServiceForegroundNotification(): android.app.Notification {
    val clickIntent = Intent(this, MainActivity::class.java).apply {
        addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
    }

    // Android 14 起允许用户划掉 setOngoing(true) 的通知（官方行为变更），
    // 这里挂一个 deleteIntent：被划掉时把 action 回传给服务，由服务重新贴回通知
    val dismissIntent = Intent(this, MQTTService::class.java).apply {
        action = ACTION_FOREGROUND_NOTIFICATION_DISMISSED
    }
    val dismissPendingIntent = PendingIntent.getService(
        this,
        SERVICE_FOREGROUND_NOTIFICATION_ID,
        dismissIntent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    val notification = buildNotification(
        id = SERVICE_FOREGROUND_NOTIFICATION_ID,
        title = LangUtil.lang(this, R.string.service_running),
        message = LangUtil.lang(this, R.string.service_running_tips),
        visibility = VISIBILITY_PUBLIC,
        activityIntent = clickIntent,
        channel = STATE_CHANNEL,
        group = "status"
    )

    notification.builder
        ?.setShowWhen(false)
        ?.setOngoing(true)
        ?.setSilent(true)
        ?.setOnlyAlertOnce(true)
        ?.setDeleteIntent(dismissPendingIntent)

    return notification.builder!!.build()
}

// ==================== 广播发送工具 ====================
//
// ⚠️ Android 14（targetSdk 34+）行为变更：接收方用 RECEIVER_NOT_EXPORTED 注册时，
// 发送方若是「隐式广播」（只带 action、没指定包名/组件），系统不会投递给它。
// 本应用内所有动态接收器都用 RECEIVER_NOT_EXPORTED（更安全），
// 因此这里的每一个 Intent 都必须 setPackage(packageName)，否则广播静默丢失。
// 参考：https://developer.android.com/about/versions/14/behavior-changes-14

/**
 * 发送广播到服务
 */
fun Context.sendServiceBroadcast(serviceAction: String) {
    val intent = Intent(ACTION_SERVICE_RECEIVER).apply {
        setPackage(packageName)
        putExtra(KEY_SERVICE_ACTION, serviceAction)
    }
    sendBroadcast(intent)
}

/**
 * 发送广播到Main
 */
fun Context.sendMainBroadcast(mainAction: String) {
    val intent = Intent(ACTION_MAIN_RECEIVER).apply {
        setPackage(packageName)
        putExtra(KEY_MAIN_ACTION, mainAction)
    }
    sendBroadcast(intent)
}

/**
 * 把监听服务包装好的消息 JSON 发给服务（由 MQTTService 走现成的消息处理链路）
 */
fun Context.sendNotifyEventBroadcast(messageJson: String) {
    val intent = Intent(ACTION_SERVICE_RECEIVER).apply {
        setPackage(packageName)
        putExtra(KEY_SERVICE_ACTION, KEY_SERVICE_ACTION_NOTIFY_EVENT)
        putExtra(KEY_NOTIFY_EVENT_DATA, messageJson)
    }
    sendBroadcast(intent)
}

/**
 * 把定时任务包装好的消息 JSON 发给服务（由 MQTTService 走现成的消息处理链路）
 *
 * 与 [sendNotifyEventBroadcast] 的唯一区别是 action 常量，
 * 服务端两个分支都是原样转交给 handleReceivedMessage，不做额外解析。
 */
fun Context.sendTaskEventBroadcast(messageJson: String) {
    val intent = Intent(ACTION_SERVICE_RECEIVER).apply {
        setPackage(packageName)
        putExtra(KEY_SERVICE_ACTION, KEY_SERVICE_ACTION_TASK_EVENT)
        putExtra(KEY_TASK_EVENT_DATA, messageJson)
    }
    sendBroadcast(intent)
}

/**
 * 通知监听设置变更，让监听服务刷新过滤规则缓存
 */fun Context.sendNotifyConfigBroadcast() {
    val intent = Intent(ACTION_NOTIFY_LISTENER_RECEIVER).apply {
        setPackage(packageName)
        putExtra(KEY_NOTIFY_LISTENER_ACTION, NOTIFY_LISTENER_REFRESH_CONFIG)
    }
    sendBroadcast(intent)
}

/**
 * 发送新消息广播
 */
fun Context.sendMessageBroadcast(id: Int, type: String) {
    val messageIntent = Intent(ACTION_MAIN_RECEIVER).apply {
        setPackage(packageName)
        putExtra(KEY_MAIN_ACTION, "new_message")
        putExtra(KEY_NEW_MESSAGE_ID, id)
        putExtra(KEY_NEW_MESSAGE_TYPE, type)
    }
    sendBroadcast(messageIntent)
}

/**
 * 发送service状态广播
 */
fun Context.sendStatusBroadcast(status: String) {
    val messageIntent = Intent(ACTION_MAIN_RECEIVER).apply {
        setPackage(packageName)
        putExtra(KEY_MAIN_ACTION, "service_status")
        putExtra(KEY_SERVICE_STATUS, status)
    }
    sendBroadcast(messageIntent)
}

// ==================== UI 提示工具 ====================

/**
 * 显示 Toast 消息（自动处理线程）
 */
fun Context.showToast(msg: String, duration: Int = Toast.LENGTH_SHORT) {
    if (Looper.myLooper() == Looper.getMainLooper()) {
        Toast.makeText(this, msg, duration).show()
    } else {
        Handler(Looper.getMainLooper()).post {
            Toast.makeText(this, msg, duration).show()
        }
    }
}

/**
 * 语音播报消息
 */
fun Context.speakMessage(text: String = "") {
    TTSManager.getInstance().speak(this, text)
}

// ==================== 通知通道管理 ====================

/**
 * 获取通道名称
 */
fun getChannelName(channelId: String): String {
    return when (channelId) {
        MAIN_CHANNEL -> "消息通知"
        STATE_CHANNEL -> "状态通知"
        else -> channelId
    }
}

/**
 * 创建通知通道
 */
fun createNotificationChannel(
    context: Context,
    channelId: String,
    importance: Int
): NotificationManager {
    val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val channel = NotificationChannel(
            channelId,
            getChannelName(channelId),
            importance
        )
        notificationManager.createNotificationChannel(channel)
    }
    
    return notificationManager
}

/**
 * 删除通知通道
 */
fun deleteNotificationChannel(context: Context, channelId: String) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.deleteNotificationChannel(channelId)
    }
}

/**
 * 获取通知通道详情
 */
fun getNotificationChannelDetail(context: Context, channelId: String): ChannelDetail? {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return null

    val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    val channel = notificationManager.getNotificationChannel(channelId) ?: return null

    val channelName = when (channel.name.toString()) {
        "状态通知" -> LangUtil.lang(context, R.string.status_notify)
        "消息通知" -> LangUtil.lang(context, R.string.message_notify)
        else -> channel.name.toString()
    }

    return ChannelDetail(
        id = channel.id,
        name = channelName,
        importance = channel.importance,
        sound = channel.sound,
        vibrationPattern = channel.vibrationPattern,
        lockscreenVisibility = channel.lockscreenVisibility,
        showBadge = channel.canShowBadge(),
        bypassDnd = channel.canBypassDnd(),
        description = channel.description
    )
}

/**
 * 获取所有通知通道列表
 */
fun getNotificationChannels(context: Context): List<ChannelDetail> {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return emptyList()

    val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    return notificationManager.notificationChannels.mapNotNull { channel ->
        getNotificationChannelDetail(context, channel.id)
    }
}

/**
 * 打开通知通道设置界面
 */
fun openChannelSettings(context: Context, channelId: String) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val intent = Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS).apply {
            putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            putExtra(Settings.EXTRA_CHANNEL_ID, channelId)
        }
        context.startActivity(intent)
    }
}
