package com.android.pushme.notify

/**
 * 从其它应用通知里提取出来的事件数据。
 *
 * 这份数据只存在于监听服务内部：`NotifyListenerService` 拿到它、按设置页的规则过滤掉一部分后，
 * 立刻把它**包装成 PushMe 的标准消息 JSON**（见 `NotifyListenerService.buildMessageJson`），
 * 再通过广播交给 `MQTTService` 走现成的消息处理链路。
 *
 * 因此它不需要实现 Parcelable —— 过广播的是包装好的消息字符串，不是这个对象本身。
 */
data class NotifyEvent(
    // 发出通知的应用包名
    var packageName: String = "",
    // 应用名称（拿不到时回退为包名）
    var appName: String = "",
    // 通知的唯一标识，同一条通知更新时 key 不变
    var key: String = "",
    var title: String = "",
    var content: String = "",
    var subText: String = "",
    // 通知产生时间（毫秒）
    var time: Long = 0L,
    // 是否为常驻通知（前台服务、音乐播放器等）
    var ongoing: Boolean = false,
    // 是否为清除通知（用户划掉或应用主动取消）
    var removed: Boolean = false
)
