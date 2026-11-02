package com.android.pushme.task

import java.util.Calendar

/**
 * 标准 cron 表达式解析器（5 段：分 时 日 月 周），语义与 Linux crontab 一致。
 *
 * 支持语法：
 * - `*`            任意值
 * - `5`            单个值
 * - `1-10`         范围
 * - `*` + `/5`     步进（任意值里每 5 个取一个）
 * - `1-30/5`       范围步进
 * - `1,3,5`        列表，可与上面任意形式混用（如 `1-10/2,20,30`）
 * - 月份支持 `JAN`~`DEC`，星期支持 `SUN`~`SAT`（大小写不敏感）
 * - 星期里 `0` 和 `7` 都代表周日
 *
 * ⚠️ 注意：上面步进那条刻意写成 `*` + `/5` 而不是连在一起 ——
 * KDoc 里出现连续的「星号斜杠」会**提前结束块注释**，后面的文档全被当成代码。
 *
 * 另外两点与标准 cron 保持一致、但容易被忽略的行为：
 * 1. 「日」和「周」**都不是** `*` 时，两者是 **OR** 关系（满足任一即触发），
 *    而不是 AND。这是 cron 的历史行为，很多实现会搞错。
 * 2. `5/10`（单值带步进）表示「从 5 开始，每 10 一步，直到上限」，不是「5 和 10」。
 */
class CronExpression private constructor(
    private val minutes: BooleanArray,   // 0..59
    private val hours: BooleanArray,     // 0..23
    private val days: BooleanArray,      // 1..31
    private val months: BooleanArray,    // 1..12
    private val weekdays: BooleanArray,  // 0..7，0 和 7 都是周日
    private val dayAny: Boolean,
    private val weekAny: Boolean
) {
    companion object {
        private val MONTH_NAMES = mapOf(
            "JAN" to 1, "FEB" to 2, "MAR" to 3, "APR" to 4, "MAY" to 5, "JUN" to 6,
            "JUL" to 7, "AUG" to 8, "SEP" to 9, "OCT" to 10, "NOV" to 11, "DEC" to 12
        )
        private val WEEK_NAMES = mapOf(
            "SUN" to 0, "MON" to 1, "TUE" to 2, "WED" to 3, "THU" to 4, "FRI" to 5, "SAT" to 6
        )

        /**
         * 解析表达式，格式不合法返回 null（UI 用它做即时校验）。
         */
        fun parse(expression: String): CronExpression? {
            val fields = expression.trim().split(Regex("\\s+"))
            if (fields.size != 5) return null

            val minutes = parseField(fields[0], 0, 59, null) ?: return null
            val hours = parseField(fields[1], 0, 23, null) ?: return null
            val days = parseField(fields[2], 1, 31, null) ?: return null
            val months = parseField(fields[3], 1, 12, MONTH_NAMES) ?: return null
            val weekdays = parseField(fields[4], 0, 7, WEEK_NAMES) ?: return null

            // 7 也代表周日（cron 传统写法），归一到 0
            if (weekdays[7]) weekdays[0] = true

            return CronExpression(
                minutes = minutes,
                hours = hours,
                days = days,
                months = months,
                weekdays = weekdays,
                // 只看字面是不是 `*`，这是标准 cron 判定「日/周是否参与 OR」的依据
                dayAny = fields[2].trim() == "*",
                weekAny = fields[4].trim() == "*"
            )
        }

        /**
         * 解析单段。
         * @param min 该字段允许的最小值
         * @param max 该字段允许的最大值
         * @param names 可选的英文缩写表（月份 / 星期）
         * @return 命中的下标集合，下标范围是 [min, max]；格式非法返回 null
         */
        private fun parseField(
            field: String,
            min: Int,
            max: Int,
            names: Map<String, Int>?
        ): BooleanArray? {
            val text = field.trim()
            if (text.isEmpty()) return null

            val result = BooleanArray(max + 1)

            for (part in text.split(",")) {
                val item = part.trim()
                if (item.isEmpty()) return null

                val slash = item.indexOf('/')
                val rangeText = if (slash >= 0) item.substring(0, slash).trim() else item
                val step = if (slash >= 0) {
                    item.substring(slash + 1).trim().toIntOrNull() ?: return null
                } else 1
                if (step <= 0) return null

                val start: Int
                val end: Int
                when {
                    rangeText == "*" -> {
                        start = min
                        end = max
                    }
                    rangeText.contains('-') -> {
                        val bounds = rangeText.split("-")
                        if (bounds.size != 2) return null
                        start = resolve(bounds[0], names) ?: return null
                        end = resolve(bounds[1], names) ?: return null
                    }
                    else -> {
                        start = resolve(rangeText, names) ?: return null
                        // `5/10` = 从 5 开始每 10 一步直到上限；不带步进就是单值
                        end = if (slash >= 0) max else start
                    }
                }

                if (start > end) return null
                if (start < min || end > max) return null

                var value = start
                while (value <= end) {
                    result[value] = true
                    value += step
                }
            }

            return result
        }

        private fun resolve(text: String, names: Map<String, Int>?): Int? {
            val token = text.trim()
            if (token.isEmpty()) return null
            token.toIntOrNull()?.let { return it }
            return names?.get(token.uppercase())
        }
    }

    /**
     * 计算 [fromMillis] 之后的下一次触发时间（毫秒时间戳）。
     *
     * ⚠️ 这是「严格向后看」的语义：不管中间错过了多少次触发，都只返回未来最近的一次。
     * 定时任务的「错过就跳过」就是靠这个特性天然实现的 —— 重排时永远从当前时间算，
     * 不会去补发历史时间点。
     *
     * @return 下一次触发时间；表达式永远不可能成立（如 `0 0 30 2 *`）时返回 null
     */
    fun next(fromMillis: Long): Long? {
        val calendar = Calendar.getInstance()
        calendar.timeInMillis = fromMillis
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        // 从下一分钟开始找：当前这一分钟已经过去了，不能返回自己
        calendar.add(Calendar.MINUTE, 1)

        // 最多往后找 4 年。像 `0 0 30 2 *`（2 月 30 日）这种永不成立的表达式必须能退出。
        val limit = fromMillis + 4L * 366L * 24L * 60L * 60L * 1000L

        while (calendar.timeInMillis <= limit) {
            // 月份不匹配：直接跳到下个月 1 号 0 点，不用逐天试
            if (!months[calendar.get(Calendar.MONTH) + 1]) {
                calendar.set(Calendar.DAY_OF_MONTH, 1)
                calendar.set(Calendar.HOUR_OF_DAY, 0)
                calendar.set(Calendar.MINUTE, 0)
                calendar.add(Calendar.MONTH, 1)
                continue
            }
            // 日/周不匹配：跳到下一天 0 点
            if (!matchDay(calendar)) {
                calendar.set(Calendar.HOUR_OF_DAY, 0)
                calendar.set(Calendar.MINUTE, 0)
                calendar.add(Calendar.DAY_OF_MONTH, 1)
                continue
            }
            // 小时不匹配：跳到下一个小时整点
            if (!hours[calendar.get(Calendar.HOUR_OF_DAY)]) {
                calendar.set(Calendar.MINUTE, 0)
                calendar.add(Calendar.HOUR_OF_DAY, 1)
                continue
            }
            // 分钟不匹配：下一分钟
            if (!minutes[calendar.get(Calendar.MINUTE)]) {
                calendar.add(Calendar.MINUTE, 1)
                continue
            }
            return calendar.timeInMillis
        }

        return null
    }

    private fun matchDay(calendar: Calendar): Boolean {
        val dayOfMonth = days[calendar.get(Calendar.DAY_OF_MONTH)]
        // Calendar.DAY_OF_WEEK 是 1=周日 .. 7=周六，转成 cron 的 0=周日 .. 6=周六
        val dayOfWeek = weekdays[calendar.get(Calendar.DAY_OF_WEEK) - 1]

        return when {
            dayAny && weekAny -> true
            dayAny -> dayOfWeek
            weekAny -> dayOfMonth
            // 标准 cron：日和周都不是 `*` 时是 OR 关系
            else -> dayOfMonth || dayOfWeek
        }
    }
}
