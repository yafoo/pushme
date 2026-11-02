package com.android.pushme.utils

import com.android.pushme.data.AutoSave
import com.android.pushme.model.KEY_APP_UPDATE_CHECK_HOUR
import com.android.pushme.model.KEY_APP_UPDATE_DISMISS_DATE

const val KEY_STATUS_BAR_HEIGHT = "STATUS_BAR_HEIGHT"
const val KEY_FLOATING_MESSAGE_ID = "FLOATING_MESSAGE_ID"

const val KEY_REPEAT_MESSAGE_ID = "REPEAT_MESSAGE_ID"
const val KEY_REPEAT_MESSAGE_TITLE = "REPEAT_MESSAGE_TITLE"
const val KEY_REPEAT_MESSAGE_CONTENT = "REPEAT_MESSAGE_CONTENT"
const val KEY_REPEAT_MESSAGE_TYPE = "REPEAT_MESSAGE_TYPE"
const val KEY_REPEAT_MESSAGE_CHANNEL = "REPEAT_MESSAGE_CHANNEL"
const val KEY_REPEAT_MESSAGE_GROUP = "REPEAT_MESSAGE_GROUP"
const val KEY_REPEAT_MESSAGE_SPEAK = "REPEAT_MESSAGE_SPEAK"

// 系统缓存类
object CacheUtil {
    var statusBarHeight by AutoSave(KEY_STATUS_BAR_HEIGHT, 72) // 72 = 24.dp * 3
    var floatingMessageId by AutoSave(KEY_FLOATING_MESSAGE_ID, 0)

    // APP版本更新
    var updateCheckHour by AutoSave(KEY_APP_UPDATE_CHECK_HOUR, 0)
    var updateDismissDate by AutoSave(KEY_APP_UPDATE_DISMISS_DATE, "")

    // 重复消息
    var repeatMessageId by AutoSave(KEY_REPEAT_MESSAGE_ID, 0)
    var repeatMessageTitle by AutoSave(KEY_REPEAT_MESSAGE_TITLE, "")
    var repeatMessageContent by AutoSave(KEY_REPEAT_MESSAGE_CONTENT, "")
    var repeatMessageType by AutoSave(KEY_REPEAT_MESSAGE_TYPE, "")
    var repeatMessageChannel by AutoSave(KEY_REPEAT_MESSAGE_CHANNEL, "")
    var repeatMessageGroup by AutoSave(KEY_REPEAT_MESSAGE_GROUP, "")
    var repeatMessageSpeak by AutoSave(KEY_REPEAT_MESSAGE_SPEAK, false)
}