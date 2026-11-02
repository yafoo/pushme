package com.android.pushme.components.drawer

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.android.pushme.LocalNavController
import com.android.pushme.MainActivity
import com.android.pushme.R
import com.android.pushme.model.AppDataBase
import com.android.pushme.model.PluginDao
import com.android.pushme.notify.NotifyFilter
import com.android.pushme.notify.NotifyHelper
import com.android.pushme.sendNotifyConfigBroadcast
import com.android.pushme.sendServiceBroadcast
import com.android.pushme.showToast
import com.android.pushme.task.TaskScheduler
import com.android.pushme.task.TaskStore
import com.android.pushme.utils.LangUtil
import com.android.pushme.utils.Permission
import com.android.pushme.utils.Settings

@Composable
fun DrawerItemServiceSet(modifier: Modifier = Modifier, expandShow: Boolean, onClick: () -> Unit) {
    DrawerItemMore(modifier = modifier, expandShow, itemTitle = {
        DrawerItemTitle(LangUtil.lang(R.string.service_setting), expandShow = expandShow)
    }) {
        Column(modifier = modifier.fillMaxWidth()) {
            val mainActivity = LocalActivity.current as MainActivity
            val navController = LocalNavController.current
            // MQTT 服务设置
            DrawerItem(hasExpand = true) {
                DrawerItemRadioList(
                    title = LangUtil.lang(R.string.service_settings),
                    keys = listOf("关闭", "PushMe", "自建"),
                    labelsResId = R.array.mqtt_state_options,
                    storeValue = Settings.mqttState,
                    expandShow = it
                ) { value ->
                    Settings.mqttState = value
                    mainActivity.sendServiceBroadcast("refresh_mqtt_state")
                    if(Settings.selfHost == "" || Settings.selfKey == "") {
                        navController.navigate("host")
                    }
                }
            }
            val selfHost = Settings.selfHost
            DrawerItem {
                DrawerItemGoPage(title = LangUtil.lang(R.string.self_host)+": ", titleValue = selfHost, text = LangUtil.lang(R.string.set), page = "host", onClick = onClick)
            }
            // MQTT 心跳间隔：连接参数，改完自动重连生效
            DrawerItem(hasExpand = true) {
                DrawerItemMqttHeartbeat(expandShow = it)
            }
            // 前台服务
            DrawerItem(hasExpand = true) {
                DrawerItemRadioList(
                    title = LangUtil.lang(R.string.service_foreground),
                    keys = listOf("开启", "关闭"),
                    labelsResId = R.array.on_off_options,
                    storeValue = Settings.serviceForeground,
                    expandShow = it,
                    tips = LangUtil.lang(R.string.service_foreground_tips)
                ) { value ->
                    Settings.serviceForeground = value
                    mainActivity.sendServiceBroadcast("refresh_foreground_mode")
                }
            }

            // API 服务设置
            DrawerItem {
                DrawerItemGoPage(
                    title = LangUtil.lang(R.string.api_service) + ": ",
                    titleValue = LangUtil.langOnOff(Settings.apiState),
                    text = LangUtil.lang(R.string.set),
                    page = "api",
                    onClick = onClick
                )
            }

            // 系统通知监听：读取其它应用的通知
            DrawerItem(hasExpand = true) {
                DrawerItemRadioList(
                    title = LangUtil.lang(R.string.notify_listen),
                    keys = listOf("开启", "关闭"),
                    labelsResId = R.array.on_off_options,
                    storeValue = Settings.notifyListenState,
                    expandShow = it,
                    tips = LangUtil.lang(R.string.notify_listen_tips)
                ) { value ->
                    Settings.notifyListenState = value
                    // 让监听服务立刻刷新过滤规则缓存
                    mainActivity.sendNotifyConfigBroadcast()

                    // 首页 NFY 标签看的是「开关 + 授权」的真实生效状态，这里立即同步一次。
                    // （开关是在应用内改的，不会触发 onStart，等不到 MainActivity 那边回填）
                    mainActivity.appViewModel.serviceNotifyListen.value =
                        value == "开启" && NotifyHelper.isGranted(mainActivity)

                    // 「监听系统通知」是特殊权限，只能引导用户去系统设置里手动打开。
                    // 它的授权状态和入口在「权限设置 - 监听系统通知」里，这里只在开启时顺手引导一次。
                    if(value == "开启" && !NotifyHelper.isGranted(mainActivity)) {
                        mainActivity.showToast(LangUtil.lang(mainActivity, R.string.notify_listen_permission_tips))
                        NotifyHelper.goNotificationAccessSetting(mainActivity)
                    }
                }
            }

            // 系统通知过滤规则
            DrawerItem {
                val filterOptions = Settings.getSettingOptions(
                    listOf(NotifyFilter.MODE_CLOSE, NotifyFilter.MODE_WHITE, NotifyFilter.MODE_BLACK),
                    R.array.notify_app_filter_options
                )
                DrawerItemGoPage(
                    title = LangUtil.lang(R.string.notify_filter) + ": ",
                    titleValue = filterOptions[Settings.notifyAppFilterMode] ?: filterOptions.values.first(),
                    text = LangUtil.lang(R.string.set),
                    page = "notify_filter",
                    onClick = onClick
                )
            }

            // 定时任务：总开关 + 任务列表入口
            DrawerItem(hasExpand = true) {
                DrawerItemRadioList(
                    title = LangUtil.lang(R.string.task),
                    keys = listOf("开启", "关闭"),
                    labelsResId = R.array.on_off_options,
                    storeValue = Settings.taskState,
                    expandShow = it,
                    tips = LangUtil.lang(R.string.task_tips)
                ) { value ->
                    Settings.taskState = value
                    // 开关直接决定闹钟的存亡：开启时按任务列表排全部闹钟，关闭时全部撤销。
                    // scheduleAll 是幂等的，重复调没有副作用。
                    TaskScheduler.scheduleAll(mainActivity)

                    // 首页 TSK 标签看的是「总开关 + 有启用中的任务」的真实状态，这里立即同步一次。
                    // （开关是在应用内改的，不会触发 onStart，等不到 MainActivity 那边回填）
                    mainActivity.refreshTaskTag()

                    // 开了总开关但还没给精确闹钟权限时提醒一次。
                    // 没权限也能跑（内部会降级成 setAndAllowWhileIdle），只是不准时。
                    if(value == "开启" && !Permission.checkAlarmPermission(mainActivity)) {
                        mainActivity.showToast(LangUtil.lang(mainActivity, R.string.task_alarm_permission_tips))
                    }
                }
            }
            DrawerItem {
                // 只统计启用的任务：列表里可能有被单独停用的，它们不会触发，不该算进这个数
                val taskCount = remember { TaskStore.load().count { it.enabled } }
                DrawerItemGoPage(
                    title = LangUtil.lang(R.string.task) + ": ",
                    titleValue = if(taskCount == 0) LangUtil.lang(R.string.notify_app_filter_none)
                                 else LangUtil.lang(R.string.notify_app_filter_count, taskCount),
                    text = LangUtil.lang(R.string.set),
                    page = "task",
                    onClick = onClick
                )
            }

            DrawerItem {
                val pluginDao: PluginDao = AppDataBase.instance.getPluginDao()
                val pluginCount = pluginDao.getCount(1)
                val pluginTotal = pluginDao.getCount()
                DrawerItemGoPage(title = LangUtil.lang(R.string.message_plugin)+": ", titleValue = "$pluginCount/$pluginTotal", text = LangUtil.lang(R.string.set), page = "plugin", onClick = onClick)
            }
            DrawerItem {
                DrawerItemGoPage(title = LangUtil.lang(R.string.message_repost)+": ", titleValue = LangUtil.langOnOff(Settings.repostState), text = LangUtil.lang(R.string.set), page = "repost", onClick = onClick)
            }
        }
    }
}