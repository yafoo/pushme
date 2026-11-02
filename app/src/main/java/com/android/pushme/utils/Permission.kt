package com.android.pushme.utils

import android.content.Context
import android.os.Build
import com.android.pushme.MainActivity
import com.android.pushme.Utils.getCrashLog
import com.android.pushme.notify.NotifyListenerService
import com.android.pushme.showToast
import com.android.pushme.utils.PushMeApi.sendPushMeLog
import com.hjq.permissions.OnPermissionCallback
import com.hjq.permissions.XXPermissions
import com.hjq.permissions.permission.PermissionLists
import com.hjq.permissions.permission.base.IPermission

enum class PermissionName {
    START,
    ALARM,
    BATTERY,
    NOTIFY,
    FLOATING,
    STORAGE,
    INSTALL_APK,
    NOTIFY_LISTENER
}

object Permission {
    fun checkPermission(context: Context, permissionName: PermissionName): Boolean {
        return isGrantedPermission(context, permissionName)
    }

    fun checkAlarmPermission(context: Context): Boolean {
        return checkPermission(context, PermissionName.ALARM)
    }

    fun checkFloatingPermission(context: Context): Boolean {
        return checkPermission(context, PermissionName.FLOATING)
    }

    fun checkStoragePermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Android 10及以上，在应用专属目录下不需要权限
            true
        } else {
            checkPermission(context, PermissionName.STORAGE)
        }
    }

    fun checkInstallApkPermission(context: Context): Boolean {
        return checkPermission(context, PermissionName.INSTALL_APK)
    }

    fun goPermission(context: MainActivity, permissionName: PermissionName, force: Boolean = false, callback: (state: Boolean) -> Unit = {_->}) {
        try {
            if(permissionName == PermissionName.START) {
                startPermissionActivity(context)
                return
            }

            XXPermissions.with(context)
                .permission(getIPermission(permissionName))
                .request(object: OnPermissionCallback {
                    override fun onResult(grantedList: List<IPermission?>, deniedList: List<IPermission?>) {
                        val allGranted = deniedList.isEmpty()
                        if (!allGranted) {
                            val doNotAskAgain = XXPermissions.isDoNotAskAgainPermissions(context, deniedList);
                            if(doNotAskAgain) {
                                val tips = if(permissionName == PermissionName.ALARM) "安卓12需开启闹钟权限" else "请手工开启权限"
                                context.showToast(tips)
                                startPermissionActivity(context, permissionName)
                            }
                            return;
                        }

                        callback(true)
                        if(force) {
                            startPermissionActivity(context, permissionName)
                        }
                    }
                })
        } catch (e: Exception) {
            val tips = if(permissionName == PermissionName.ALARM) "安卓12需开启闹钟权限，请手工设置权限" else "出错了！请手工设置权限"
            context.showToast(tips)
            val log = getCrashLog("GoPermission", e, context)
            sendPushMeLog(log)
        }
    }

    fun goAlarmPermission(context: MainActivity, callback: (state: Boolean) -> Unit = {_->}) {
        goPermission(context, PermissionName.ALARM, false) {
            callback(it)
        }
    }

    fun goFloatingPermission(context: MainActivity, callback: (state: Boolean) -> Unit = {_->}) {
        goPermission(context, PermissionName.FLOATING, true) {
            callback(it)
        }
    }

    fun goStoragePermission(context: MainActivity, callback: (state: Boolean) -> Unit = {_->}) {
        goPermission(context, PermissionName.STORAGE, true) {
            callback(it)
        }
    }

    fun goInstallApkPermission(context: MainActivity, callback: (state: Boolean) -> Unit = {_->}) {
        goPermission(context, PermissionName.INSTALL_APK, true) {
            callback(it)
        }
    }

    /**
     * 通知使用权（通知栏监听）是否已授予。
     *
     * 框架在 Android 8.1 及以上用 NotificationManager.isNotificationListenerAccessGranted 判断，
     * 低版本回落到读取 Settings.Secure 的 enabled_notification_listeners，比手写解析更稳。
     */
    fun checkNotifyListenerPermission(context: Context): Boolean {
        return checkPermission(context, PermissionName.NOTIFY_LISTENER)
    }

    /**
     * 引导用户打开通知使用权。
     *
     * 这是特殊权限，框架不会弹系统授权框，而是直接跳到系统设置页；
     * Android 11+ 会优先跳到「本应用的通知使用权详情页」，用户回来后再回调结果。
     *
     * ⚠️ 只在「需要申请授权」时用这个（未授权 → 跳转，已授权 → 直接回调成功什么都不做）。
     * 如果是要让用户「去设置页看看/去改」，必须用 [openNotifyListenerSetting]。
     */
    fun goNotifyListenerPermission(context: MainActivity, callback: (state: Boolean) -> Unit = {_->}) {
        goPermission(context, PermissionName.NOTIFY_LISTENER, false) {
            callback(it)
        }
    }

    /**
     * 无条件跳转到「通知使用权」的系统设置页（不做授权判断、不发起申请）。
     *
     * ⚠️ 为什么不能用 [goNotifyListenerPermission] 代替：
     * `XXPermissions.request()` 在**已经授权**时会直接判定成功并回调，
     * 配上 `force = false` 就什么都不做 —— 表现为用户点了「设置」按钮毫无反应。
     * 「设置」按钮的语义是「带我去看看/去改」，必须无条件跳转。
     */
    fun openNotifyListenerSetting(context: Context) {
        try {
            XXPermissions.startPermissionActivity(context, getIPermission(PermissionName.NOTIFY_LISTENER))
        } catch (e: Exception) {
            context.showToast("出错了！请手工设置权限")
            val log = getCrashLog("OpenNotifyListenerSetting", e, context)
            sendPushMeLog(log)
        }
    }

    private fun isGrantedPermission(context: Context, permissionName: PermissionName): Boolean {
        try {
            return XXPermissions.isGrantedPermission(context, getIPermission(permissionName))
        } catch (e: Exception) {
            val log = getCrashLog("IsGrantedPermission", e, context)
            sendPushMeLog(log)
            return false
        }
    }

    private fun startPermissionActivity(context: Context, permissionName: PermissionName = PermissionName.START) {
        try {
            if(permissionName != PermissionName.START) {
                XXPermissions.startPermissionActivity(context, getIPermission(permissionName))
            } else {
                XXPermissions.startPermissionActivity(context)
            }
        } catch (e: Exception) {
            val log = getCrashLog("StartPermissionActivity", e, context)
            sendPushMeLog(log)
        }
    }

    private fun getIPermission(permissionName: PermissionName): IPermission {
        return when(permissionName) {
            PermissionName.ALARM -> PermissionLists.getScheduleExactAlarmPermission()
            PermissionName.BATTERY -> PermissionLists.getRequestIgnoreBatteryOptimizationsPermission()
            PermissionName.NOTIFY -> PermissionLists.getPostNotificationsPermission()
            PermissionName.FLOATING -> PermissionLists.getSystemAlertWindowPermission()
            PermissionName.STORAGE -> PermissionLists.getManageExternalStoragePermission()
            PermissionName.INSTALL_APK -> PermissionLists.getRequestInstallPackagesPermission()
            PermissionName.NOTIFY_LISTENER -> PermissionLists.getBindNotificationListenerServicePermission(NotifyListenerService::class.java)
            PermissionName.START -> throw Exception("PermissionName.START can't check")
        }
    }
}