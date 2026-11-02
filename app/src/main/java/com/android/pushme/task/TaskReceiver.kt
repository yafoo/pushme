package com.android.pushme.task

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import android.util.Log
import com.android.pushme.TAG

/**
 * 定时任务闹钟的接收器。
 *
 * ⚠️ **必须静态注册在 AndroidManifest.xml 里**，不能用动态注册。
 * 动态注册的接收器依附于进程存活，而闹钟触发时进程很可能已经被系统回收 ——
 * 那样广播根本没人收，任务就静默丢失了。
 * （对比 `MQTTAlarmReconnect`：它用动态注册是对的，因为那是「连接断开后重连」，
 *   服务活着才会走到那里。）
 *
 * 因为静态注册 + 显式 Intent（`Intent(context, TaskReceiver::class.java)`），
 * 不涉及 Android 14 的「隐式 intent 只能传送到导出组件」限制，
 * 清单里 `exported=false` 即可。
 */
class TaskReceiver : BroadcastReceiver() {
    private val tag = "$TAG[TaskReceiver]"

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != TaskScheduler.ACTION_FIRE) return

        val taskId = intent.getIntExtra(TaskScheduler.EXTRA_TASK_ID, 0)
        if (taskId <= 0) return

        // 本该触发的时间，仅用于在 fire() 里算迟到时长。迟到照样执行，不影响是否执行
        val triggerAt = intent.getLongExtra(TaskScheduler.EXTRA_TRIGGER_AT, 0L)

        Log.d(tag, "onReceive: taskId=$taskId")

        // 闹钟触发时 CPU 可能正要进入休眠，拿个短 wakelock 保证本次处理完整执行完
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        val wakeLock = powerManager?.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "com.android.pushme:TaskReceiver"
        )

        try {
            wakeLock?.acquire(30_000L)
            TaskScheduler.fire(context, taskId, triggerAt)
        } catch (e: Exception) {
            Log.e(tag, "onReceive: ", e)
        } finally {
            if (wakeLock?.isHeld == true) {
                wakeLock.release()
            }
        }
    }
}
