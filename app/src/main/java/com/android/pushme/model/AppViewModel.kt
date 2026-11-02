package com.android.pushme.model

import android.content.Context
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.toUpperCase
import androidx.lifecycle.ViewModel
import com.android.pushme.Utils
import com.android.pushme.mqtt.MQTTSetting
import com.android.pushme.showToast
import com.android.pushme.utils.CacheUtil
import com.android.pushme.utils.Permission.checkPermission
import com.android.pushme.utils.PermissionName
import com.android.pushme.utils.PushMeApi.getVersion
import com.android.pushme.utils.Settings
import java.util.Locale
import java.util.Locale.getDefault
import kotlin.math.roundToInt

const val KEY_APP_UPDATE_CHECK_HOUR = "APP_UPDATE_CHECK_HOUR"
const val KEY_APP_UPDATE_DISMISS_DATE = "APP_UPDATE_DISMISS_DATE"

enum class AppUpdateMode {
    AUTO,
    HAND
}

data class AppUpdateState(
    val dialogShow: Boolean = false,
    val isChecking: Boolean = false,
    val newVersion: String = "",
    val updateLog: String = "",
    val checkHour: Int = CacheUtil.updateCheckHour,
    val dismissDate: String = CacheUtil.updateDismissDate,
) {
    fun setCheckHour(): AppUpdateState {
        val hour = Utils.getCurrentHour()
        CacheUtil.updateCheckHour = hour
        return this.copy(checkHour = hour)
    }

    fun setDismissDate(): AppUpdateState {
        val today = Utils.getCurrentDate()
        CacheUtil.updateDismissDate = today
        return this.copy(dismissDate = today)
    }
}

class AppViewModel: ViewModel() {
    var appUpdateState by mutableStateOf(AppUpdateState())

    // 版本检测
    fun appUpdateCheck(context: Context, mode: AppUpdateMode = AppUpdateMode.AUTO) {
        val hour = Utils.getCurrentHour()

        // 自动检测限制
        if(mode == AppUpdateMode.AUTO) {
            if(hour == appUpdateState.checkHour) {
                return
            }

            if(Utils.getCurrentDate() == appUpdateState.dismissDate) {
                return
            }
        }

        // 开始检测
        if(hour != appUpdateState.checkHour) {
            appUpdateState = appUpdateState.setCheckHour()
        }

        if(mode == AppUpdateMode.HAND) {
            appUpdateState = appUpdateState.copy(isChecking = true)
        }

        getVersion { error, versionNew, updateNew ->
            run {
                var dialogShow = false
                var newVersion = ""
                var updateLog = ""

                if(error == "" && versionNew != Settings.appVersion) {
                    dialogShow = true
                    newVersion = versionNew
                    updateLog = updateNew
                } else if(mode == AppUpdateMode.HAND) {
                    val tips = if(error != "") "检查失败！$error" else "已是最新版本！"
                    context.showToast(tips)
                }

                appUpdateState = appUpdateState.setCheckHour().copy(
                    isChecking = false,
                    dialogShow = dialogShow,
                    newVersion = newVersion,
                    updateLog = updateLog,
                )
            }
        }
    }

    // 设置今日不再提示
    fun setNoRemindToday() {
        appUpdateState = appUpdateState.setDismissDate()
    }

    // 关闭升级弹窗
    fun setDialogHide() {
        appUpdateState = appUpdateState.copy(dialogShow = false)
    }

    // 消息列表字体
    private val _fontScale = mutableFloatStateOf(Settings.msgFontScale)
    val fontScale: State<Float> get() = _fontScale

    // 更新字体大小
    fun updateFontScale(scale: Float) {
        val scaleRound = (scale * 100).roundToInt() / 100f
        Settings.msgFontScale = scaleRound
        _fontScale.floatValue = scaleRound
    }

    // 重置字体大小为默认
    fun resetFontScale() {
        Settings.msgFontScale = 1f
        _fontScale.floatValue = 1f
    }

    // 权限设置
    val alarmPermit = mutableStateOf(false)
    val batteryPermit = mutableStateOf(false)
    val notifyPermit = mutableStateOf(false)

    fun initPermit(context: Context) {
        alarmPermit.value = checkPermission(context, PermissionName.ALARM)
        batteryPermit.value = checkPermission(context, PermissionName.BATTERY)
        notifyPermit.value = checkPermission(context, PermissionName.NOTIFY)
    }


    // Service Status
    private val _status = mutableStateOf("")
    val serviceIsSelf = mutableStateOf(Settings.mqttState == "自建")
    val serviceProtocol = mutableStateOf(MQTTSetting.getProtocol().uppercase(getDefault()))
    val serviceApiState = mutableStateOf(Settings.apiState == "开启")
    // 服务当前是否真的以前台服务方式运行（由 MainActivity 查系统状态后回填，不看设置项）
    val serviceForeground = mutableStateOf(false)
    // 通知监听是否真的在生效：开关开启 且 系统「通知使用权」已授予（由 MainActivity 回填）。
    // 与 serviceForeground 同理，不看单一设置项 —— 开关开了但没授权时功能完全不工作，
    // 亮标签会误导用户以为在转发。
    val serviceNotifyListen = mutableStateOf(false)
    // 定时任务是否真的在跑：总开关开启 且 至少有一个启用中的任务（由 MainActivity 回填）。
    // 同样不看单一设置项 —— 开关开着但一条任务都没有、或任务全部停用时，
    // 到点不会有任何东西触发，亮标签就是误导。
    val serviceTask = mutableStateOf(false)
    var serviceStatus: String
        get() = _status.value
        set(value) {
            _status.value = value
            serviceIsSelf.value = Settings.mqttState == "自建"
            serviceProtocol.value = MQTTSetting.getProtocol().uppercase(getDefault())
            serviceApiState.value = Settings.apiState == "开启"
        }
}