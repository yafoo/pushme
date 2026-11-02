package com.android.pushme.task

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.android.pushme.MessageInfo
import com.android.pushme.MQTTService
import com.android.pushme.R
import com.android.pushme.SERVICE_NAME
import com.android.pushme.TAG
import com.android.pushme.Utils
import com.android.pushme.data.Message
import com.android.pushme.sendTaskEventBroadcast
import com.android.pushme.utils.Settings
import com.google.gson.Gson

/**
 * 定时任务调度器。
 *
 * ## 调度方式
 * 用 `AlarmManager.setExactAndAllowWhileIdle`，**一个任务一个闹钟**（requestCode = task.id）。
 *
 * 为什么不用「单闹钟 + 重排」：那样触发时无法知道是哪个任务到期，
 * 得额外把每个任务的 nextRun 存起来，多一份需要保持一致的状态。
 * 一任务一闹钟没有状态，增删改都只是「取消/重排这一条」，最不容易出错。
 * 任务数量是个位数，闹钟个数完全不是问题。
 *
 * ## 「迟到」和「错过」是两件事，处理方式相反
 *
 * **迟到 = 照常执行（本类不做任何丢弃判断）。**
 * 闹钟一旦被系统排上，就一定会触发，只是时间可能晚一点：
 * - Doze 休眠：`setExactAndAllowWhileIdle` 会把闹钟推迟到下一个维护窗口，
 *   且同一应用在 idle 期间最多每 9 分钟被放行一次 —— 可能晚十几分钟，但**一定会触发**；
 * - 未授予精确闹钟权限时降级为 `setAndAllowWhileIdle`，同样会触发，只是更不准。
 *
 * 所以 [fire] 里**没有**「晚了太久就跳过」这类判断，晚到多久都照常执行。
 * 只把「晚了多久」打进日志（见 [fire] 里的迟到日志），方便排查 Doze / ROM 延迟。
 *
 * **错过 = 跳过（真正的丢）。**
 * 会丢的只有「闹钟本身没了」的情况：设备重启（系统不持久化第三方闹钟）、
 * 被 ROM 的省电策略清掉闹钟。这种没法补 —— 重启后 [scheduleAll] 用
 * `CronExpression.next(now)` 从**当前时间**重算下一次，永远只看向未来。
 *
 * 注意这不是「算出来发现过期了就跳过」，而是**根本不看过去**：
 * 关机 3 天、任务本该触发 100 次，开机后也只会排下一次，不会补发 100 条。
 * 理由：定时任务通常带副作用（调接口、发通知），补发会造成刷屏和重复副作用；
 * 而 Doze 只是晚几分钟，语义上仍是「这一次该执行」，所以照常执行。
 *
 * ## 消息怎么进插件
 * 触发后包装成 `type = Message.TYPE_TASK` 的消息，通过广播交给 [MQTTService]，
 * 走和 MQTT / API / 通知监听完全相同的处理链路（插件 → 入库 → 通知 → 转发）。
 * 插件处理完如果 type 还是 `task`，[com.android.pushme.MessageProcessor] 会直接丢弃。
 */
object TaskScheduler {
    private val tag = "$TAG[TaskScheduler]"
    private val gson = Gson()

    /** 闹钟广播的 action */
    const val ACTION_FIRE = "com.android.pushme.TASK_FIRE"

    /** 闹钟广播里携带的任务 id */
    const val EXTRA_TASK_ID = "TaskId"

    /**
     * 闹钟广播里携带的「本该触发的时间」。
     * 只用于 [fire] 里计算迟到时长并打日志 —— 迟到照样执行，这个值不参与任何丢弃判断。
     */
    const val EXTRA_TRIGGER_AT = "TriggerAt"

    /** 迟到超过这个秒数就打 warning 级日志（准点触发只打 debug） */
    private const val LATE_WARN_SECONDS = 60L

    /**
     * 重排全部任务。
     *
     * 调用时机：总开关切换、任务增删改、开机/应用更新后（见 ReceiverBootBroadcast）。
     * 这是个幂等操作，多调几次没有副作用。
     */
    fun scheduleAll(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
        if (alarmManager == null) {
            Log.e(tag, "拿不到 AlarmManager")
            return
        }

        val list = TaskStore.load()

        // 总开关关闭：把所有闹钟撤掉，一条都不留
        if (Settings.taskState != "开启") {
            list.forEach { cancelAlarm(alarmManager, context, it.id) }
            Log.d(tag, "定时任务总开关未开启，已撤销全部闹钟")
            return
        }

        list.forEach { task ->
            if (!task.enabled) {
                cancelAlarm(alarmManager, context, task.id)
                return@forEach
            }
            val next = task.nextRun()
            if (next == null) {
                // 表达式非法或永不成立（如 2 月 30 日），别留个死闹钟占着
                cancelAlarm(alarmManager, context, task.id)
                Log.w(tag, "任务 ${task.id} 无法计算下次触发时间，跳过: ${task.cron}")
                return@forEach
            }
            setAlarm(alarmManager, next, pendingIntent(context, task.id, next))
            Log.d(tag, "任务 ${task.id}「${task.name}」下次触发: ${Utils.formatTime(next)}")
        }
    }

    /** 只排一条任务（任务保存后调用） */
    fun scheduleTask(context: Context, task: TaskInfo) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return

        if (Settings.taskState != "开启" || !task.enabled) {
            cancelAlarm(alarmManager, context, task.id)
            return
        }

        val next = task.nextRun()
        if (next == null) {
            cancelAlarm(alarmManager, context, task.id)
            return
        }
        setAlarm(alarmManager, next, pendingIntent(context, task.id, next))
        Log.d(tag, "任务 ${task.id}「${task.name}」下次触发: ${Utils.formatTime(next)}")
    }

    /** 取消一条任务的闹钟（任务删除 / 停用时调用） */
    fun cancel(context: Context, taskId: Int) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        cancelAlarm(alarmManager, context, taskId)
    }

    /**
     * 闹钟到点，执行任务。
     *
     * 由 [TaskReceiver] 调用。流程：读任务 → 包装消息 → 交给服务 → 重排下一次。
     *
     * ⚠️ **迟到不丢**：这里没有任何「太晚了就跳过」的判断。
     * Doze 把闹钟推迟到维护窗口、ROM 延迟投递，都只是让 [triggerAt] 比当前时间早一些，
     * 任务照样执行（只是晚），`triggerAt` 仅用于打迟到日志。
     *
     * @param triggerAt 本该触发的时间（毫秒），来自 [EXTRA_TRIGGER_AT]；未知时传 0
     */
    fun fire(context: Context, taskId: Int, triggerAt: Long = 0L) {
        if (Settings.taskState != "开启") {
            Log.d(tag, "总开关已关闭，忽略触发: $taskId")
            return
        }

        val list = TaskStore.load()
        val task = list.firstOrNull { it.id == taskId }
        if (task == null) {
            Log.w(tag, "任务不存在（可能已被删除）: $taskId")
            return
        }
        if (!task.enabled) {
            Log.d(tag, "任务已停用，忽略触发: $taskId")
            return
        }

        // 记录触发时间，仅用于 UI 展示
        task.lastRun = System.currentTimeMillis()
        TaskStore.save(list)

        // 迟到诊断：只记录，不改变行为。Doze / ROM 延迟时能在这里看到晚了几秒
        if (triggerAt > 0) {
            val delaySeconds = (task.lastRun - triggerAt) / 1000
            if (delaySeconds >= LATE_WARN_SECONDS) {
                Log.w(tag, "任务 ${task.id}「${task.name}」迟到 ${delaySeconds}s 才触发（Doze 推迟 / ROM 延迟），照常执行")
            } else {
                Log.d(tag, "执行任务 ${task.id}「${task.name}」，延迟 ${delaySeconds}s")
            }
        } else {
            Log.d(tag, "执行任务 ${task.id}「${task.name}」")
        }

        dispatch(context, buildMessageJson(context, task.name.trim().ifBlank { task.cron }, task.content))

        // 排下一次。用 next(now) 而不是 next(lastRun)：
        // 永远只看向未来，所以「关机期间错过的 N 次」不会被补发成 N 条；
        // 而本次因为 Doze 迟到的那一次已经执行过了，不存在重复。
        scheduleTask(context, task)
    }

    /**
     * 手动发一条任务消息（任务详情页的「发送测试消息」按钮）。
     *
     * 不走闹钟、不改 `lastRun`、不看总开关和启用状态 —— 纯粹是把**当前编辑中的内容**
     * 当成一次触发，走一遍和真实触发完全相同的链路（插件 → 入库 → 通知 → 转发），
     * 方便调插件时不用等 cron 到点、也不用先保存。
     *
     * ⚠️ 和真实触发一样：插件没接管（处理后 type 仍是 `task`）的消息会被直接丢弃，
     * 所以调用方最好先确认「至少有一个启用中的插件」。
     *
     * @param name    任务名，为空时用 [R.string.task_test_name] 兜底
     * @param content 任务内容，原样交给插件
     */
    fun test(context: Context, name: String, content: String) {
        val title = name.trim().ifBlank { context.getString(R.string.task_test_name) }
        Log.d(tag, "手动测试任务消息: $title")
        dispatch(context, buildMessageJson(context, title, content))
    }

    // ==================== 内部实现 ====================

    /**
     * 构造闹钟用的 PendingIntent。
     *
     * `triggerAt` 会被塞进 extra，触发时用来算迟到时长（纯诊断用）。
     * 取消闹钟时不需要它 —— PendingIntent 的匹配只比 component / action / requestCode，
     * 不比 extras，所以 [cancelAlarm] 里传默认值 0 也能正确匹配到同一个闹钟。
     */
    private fun pendingIntent(context: Context, taskId: Int, triggerAt: Long = 0L): PendingIntent {
        val intent = Intent(context, TaskReceiver::class.java).apply {
            action = ACTION_FIRE
            putExtra(EXTRA_TASK_ID, taskId)
            putExtra(EXTRA_TRIGGER_AT, triggerAt)
        }
        var flags = PendingIntent.FLAG_UPDATE_CURRENT
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            flags = flags or PendingIntent.FLAG_IMMUTABLE
        }
        // requestCode 用 taskId：不同任务天然是不同的 PendingIntent，互不覆盖
        return PendingIntent.getBroadcast(context, taskId, intent, flags)
    }

    private fun setAlarm(alarmManager: AlarmManager, triggerAt: Long, pendingIntent: PendingIntent) {
        try {
            // Android 12+ 精确闹钟要单独授权，没授权时 setExact* 会抛 SecurityException。
            // 降级成 setAndAllowWhileIdle：不保证准点，但至少 Doze 下还能触发。
            val canExact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
                alarmManager.canScheduleExactAlarms()
            if (canExact) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
            } else {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
                Log.w(tag, "未授予精确闹钟权限，降级为非精确闹钟")
            }
        } catch (e: SecurityException) {
            // 权限在运行中被撤销的瞬间也可能抛，再降一次级兜底
            Log.e(tag, "setExactAndAllowWhileIdle 失败，降级", e)
            try {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
            } catch (e2: Exception) {
                Log.e(tag, "排闹钟失败", e2)
            }
        }
    }

    private fun cancelAlarm(alarmManager: AlarmManager, context: Context, taskId: Int) {
        try {
            alarmManager.cancel(pendingIntent(context, taskId))
        } catch (e: Exception) {
            Log.e(tag, "取消闹钟失败: $taskId", e)
        }
    }

    /**
     * 包装成一条消息。
     *
     * - `title`   = `[#定时任务]任务名`，复用分组标记，所有定时任务消息归到一组
     *   （分组标记后**不留空格**，`Message.calcTitleInfo` 的正则是 `\[#([^!\]]+)\]`，
     *   不依赖空格分隔，紧贴反而让消息列表里标题更紧凑）
     * - `content` = 任务内容，原样交给插件
     * - `type`    = `task`（中间类型，插件必须改成系统支持的类型，否则丢弃）
     *
     * `name` 为空时用什么兜底由调用方决定（真实触发用 cron，手动测试用「测试任务」）。
     */
    private fun buildMessageJson(context: Context, name: String, content: String): String {
        val group = context.getString(R.string.task_group)

        return gson.toJson(
            MessageInfo(
                title = "[#$group]$name",
                content = content,
                date = Utils.formatTime(System.currentTimeMillis()),
                type = Message.TYPE_TASK
            )
        )
    }

    /**
     * 把消息送到服务。
     *
     * 服务在跑：直接广播，走现成的消息处理链路。
     * 服务没在跑：先入队再拉起来，服务 onCreate 末尾会 drain 队列。
     * （闹钟触发的广播处于系统临时白名单窗口内，此时启动服务是被允许的；
     *   万一 ROM 仍然拒绝，消息留在队列里，下次服务启动时补处理。）
     */
    private fun dispatch(context: Context, messageJson: String) {
        if (Utils.isServiceRunning(context, SERVICE_NAME)) {
            context.sendTaskEventBroadcast(messageJson)
            return
        }

        Log.d(tag, "服务未运行，入队并拉起服务")
        TaskStore.pushPending(messageJson)

        try {
            val intent = Intent(context, MQTTService::class.java)
            if (Settings.serviceForeground == "开启" && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        } catch (e: Exception) {
            Log.e(tag, "启动服务失败，消息已留在队列里", e)
        }
    }
}
