package com.android.pushme

import com.android.pushme.utils.PushMeApi.repostMessage
import com.android.pushme.utils.Settings
import com.android.pushme.Utils
import java.util.Calendar

/**
 * 消息转发管理器
 * 负责转发配置加载和转发条件判断
 */
class RepostManager {
    private var repostUrl: String = ""
    private var repostWeek = listOf<String>()
    private var repostTimeStart: Int = 0
    private var repostTimeEnd: Int = 0
    private var repostLimit: String = ""
    private var repostOmit: String = ""
    private var repostState: String = "关闭"
    private val weekList = listOf("日", "一", "二", "三", "四", "五", "六")

    fun refreshRepostData() {
        repostUrl = Settings.repostUrl
        repostWeek = Settings.repostWeek
        repostTimeStart = Settings.repostTimeStart
        repostTimeEnd = Settings.repostTimeEnd
        repostLimit = Settings.repostLimit
        repostOmit = Settings.repostOmit
        repostState = Settings.repostState
    }

    fun needRepost(title: String): Boolean {
        if(repostUrl == "" || repostWeek.isEmpty() || repostState == "关闭") {
            return false
        }

        if(repostWeek.size == 7 && repostTimeStart == repostTimeEnd) {
            return true
        }

        if(repostWeek.size < 7) {
            val now = Calendar.getInstance()
            val dayOfWeek = now.get(Calendar.DAY_OF_WEEK)
            val week = weekList[dayOfWeek - 1]
            if(!repostWeek.contains(week)) {
                return false
            }
        }

        if(repostTimeStart != repostTimeEnd) {
            val time = Utils.getCurrentTime("HHmm").toInt()
            if(repostTimeStart < repostTimeEnd) {
                if(time < repostTimeStart || time > repostTimeEnd) {
                    return false
                }
            } else if(time in (repostTimeEnd + 1) until repostTimeStart) {
                return false
            }
        }

        if(repostLimit != "") {
            val limitKeywords = repostLimit.split("|").filter { it.isNotEmpty() }
            val containsAnyLimitKeyword = limitKeywords.any { keyword ->
                title.contains(keyword, ignoreCase = true)
            }
            if(!containsAnyLimitKeyword) {
                return false
            }
        }

        if(repostOmit != "") {
            val omitKeywords = repostOmit.split("|").filter { it.isNotEmpty() }
            val containsNoOmitKeywords = omitKeywords.none { keyword ->
                title.contains(keyword, ignoreCase = true)
            }
            if(!containsNoOmitKeywords) {
                return false
            }
        }

        return true
    }

    companion object {
        /**
         * 执行消息转发（静态方法，任何地方都可调用）
         */
        fun doRepost(repostUrl: String, title: String, content: String, date: String, type: String) {
            val pushKey = if (Settings.repostPushKey == "开启") {
                if (Settings.mqttState == "自建") Settings.selfKey else Settings.pushKey
            } else null
            repostMessage(repostUrl, title, content, date, type, pushKey = pushKey)
        }
    }
}
