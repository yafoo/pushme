package com.android.pushme.task

/**
 * 一条定时任务。
 *
 * 触发时会被包装成一条消息（type = `Message.TYPE_TASK`）交给插件处理，
 * 详见 [TaskScheduler.buildMessageJson]。
 *
 * ⚠️ 这个类会被 Gson 序列化后存进 DataStore，**字段名必须稳定**，
 * 已在 `proguard-rules.pro` 里加了 `-keep`。否则 release 包混淆字段名后，
 * 存进去的 key 会变成 `a`/`b`/`c`，升级后旧任务全部读不出来。
 *
 * @param id       自增主键，同时用作闹钟的 requestCode，不能重复
 * @param name     任务名称，也是消息标题
 * @param cron     5 段 cron 表达式（分 时 日 月 周）
 * @param content  任务内容，作为消息的 content 交给插件
 * @param enabled  是否启用。关闭的任务不排闹钟
 * @param lastRun  上次触发时间（毫秒），仅用于展示
 *
 * ⚠️ 曾经有个 `type` 字段（「期望的消息类型」），但 [TaskScheduler.buildMessageJson]
 * 一直硬编码 `type = Message.TYPE_TASK`，那个字段从头到尾没被读过 —— 已删除。
 * 消息最终类型完全由插件决定，见 [MessageProcessor.processMessage]。
 * 老数据里残留的 `type` 键会被 Gson 直接忽略，不需要迁移。
 */
data class TaskInfo(
    var id: Int = 0,
    var name: String = "",
    var cron: String = "",
    var content: String = "",
    var enabled: Boolean = true,
    var lastRun: Long = 0L
) {
    /** 下一次触发时间（毫秒）；表达式非法或永不成立时返回 null */
    fun nextRun(from: Long = System.currentTimeMillis()): Long? {
        return CronExpression.parse(cron)?.next(from)
    }
}
