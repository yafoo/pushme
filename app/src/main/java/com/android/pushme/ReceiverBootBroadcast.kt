package com.android.pushme

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.android.pushme.task.TaskScheduler
import com.android.pushme.utils.Settings

/**
 * 开机自启动 / 应用更新后自启动
 *
 * 这两个广播都在「允许从后台启动前台服务」的豁免清单里，
 * 所以前台服务模式下可以直接 startForegroundService。
 */
class ReceiverBootBroadcast: BroadcastReceiver() {
    private val tag = "$TAG[BootReceiver]"

    override fun onReceive(context: Context?, intent: Intent?) {
        val action = intent?.action ?: return
        if (context == null) return
        if (action != Intent.ACTION_BOOT_COMPLETED && action != Intent.ACTION_MY_PACKAGE_REPLACED) {
            return
        }

        Log.d(tag, "onReceive: $action")

        // 定时任务重排。
        // AlarmManager 里的闹钟在设备重启后会全部丢失（系统不持久化第三方闹钟），
        // 所以每次开机 / 应用更新后都要按当前任务列表重新排一遍。
        // 这里是「错过就跳过」的落点：scheduleAll 内部用 next(now) 计算，
        // 关机期间错过的触发点不会被补发（不会一开机刷出几十条）。
        // ⚠️ 别和 Doze 混：Doze 只是把闹钟推迟到维护窗口，闹钟本身还在，
        //    触发时会照常执行、只是晚几分钟，根本不会走到这里。
        try {
            TaskScheduler.scheduleAll(context)
        } catch (e: Exception) {
            Log.e(tag, "重排定时任务失败: ", e)
        }

        val newIntent = Intent(context, MQTTService::class.java)
        try {
            if (Settings.serviceForeground == "开启" && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(newIntent)
            } else {
                context.startService(newIntent)
            }
        } catch (e: Exception) {
            Log.e(tag, "启动服务失败: ", e)
        }
    }
}
