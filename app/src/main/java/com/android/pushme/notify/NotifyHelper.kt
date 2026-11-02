package com.android.pushme.notify

import android.content.Context
import com.android.pushme.utils.Permission

/**
 * 通知使用权（Notification access）相关的工具方法。
 *
 * 「通知使用权」是特殊权限，只能由用户在系统设置里手动打开，
 * 应用侧既不能申请也不能静默获取，只能检测 + 引导。
 *
 * 检测和跳转都委托给项目里已有的权限库（XXPermissions）：
 * - 检测：Android 8.1+ 用 NotificationManager.isNotificationListenerAccessGranted，
 *   低版本回落到 Settings.Secure 的 enabled_notification_listeners；
 * - 跳转：Android 11+ 直接进「本应用的通知使用权详情页」，否则进通知使用权列表页。
 */
object NotifyHelper {

    /** 是否已获得通知使用权 */
    fun isGranted(context: Context): Boolean {
        return Permission.checkNotifyListenerPermission(context)
    }

    /**
     * 跳转到系统的「通知使用权」设置页（即本应用里的「监听系统通知」）。
     *
     * ⚠️ 这里是**无条件跳转**，不判断当前是否已授权。
     * 原因：授权状态由调用方展示，而「设置」按钮的语义是「带我去看看/去改」。
     * 如果走 `XXPermissions.request()`，已授权时它会直接判定成功并回调、
     * 什么都不做，按钮就会表现为点了没反应。
     */
    fun goNotificationAccessSetting(context: Context) {
        Permission.openNotifyListenerSetting(context)
    }
}
