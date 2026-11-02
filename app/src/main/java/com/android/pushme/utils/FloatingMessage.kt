package com.android.pushme.utils

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.android.pushme.App
import com.android.pushme.MESSAGE_ID
import com.android.pushme.MainActivity
import com.android.pushme.components.AttachedEdge
import com.android.pushme.components.FloatingState
import com.android.pushme.components.message.MessageData
import com.android.pushme.components.message.handleTaskToggled
import com.android.pushme.data.AutoSaveState
import com.android.pushme.data.AutoSaveDp
import com.android.pushme.data.AutoSaveDpSize
import com.android.pushme.data.AutoSaveOffset
import com.android.pushme.data.Message
import com.android.pushme.sendServiceBroadcast
import kotlinx.coroutines.CoroutineScope

const val KEY_FLOATING_POSITION = "FLOATING_POSITION"
const val KEY_FLOATING_SIZE = "FLOATING_SIZE"
const val KEY_FLOATING_RADIUS = "FLOATING_RADIUS"
const val KEY_FLOATING_OPACITY = "FLOATING_OPACITY"
const val KEY_FLOATING_EDGE= "FLOATING_EDGE"
const val KEY_FLOATING_MINI= "FLOATING_MINI"

data class AutoSaveEdge(
    private val key: String,
    private val defaultValue: AttachedEdge = AttachedEdge.NONE,
    private val debounceTime: Long = 0,
    private val coroutineScope: CoroutineScope? = null
): AutoSaveState<AttachedEdge>(
    key, defaultValue, debounceTime, coroutineScope,
    serializer = {
        it.value
    },
    deserializer = { str ->
        AttachedEdge.entries.find { it.value == str } ?: AttachedEdge.NONE
    }
)

class FloatingMessageState(
    defaultPosition: Offset = Offset(100f, 100f),
): FloatingState() {
    private val autoSavePosition = AutoSaveOffset(KEY_FLOATING_POSITION, defaultPosition, 500)
    private val autoSaveSize = AutoSaveDpSize(KEY_FLOATING_SIZE, defaultSize, 500)
    private val autoSaveRadius = AutoSaveDp(KEY_FLOATING_RADIUS, defaultRadius, 500)
    private val autoSaveOpacity = AutoSaveState(KEY_FLOATING_OPACITY, defaultOpacity, 500)
    private val autoSaveEdge = AutoSaveEdge(KEY_FLOATING_EDGE, defaultAttachedEdge, 500)
    private val autoSaveMini = AutoSaveState(KEY_FLOATING_MINI, defaultIsMinimized, 500)

    fun lateSetCoroutineScope(coroutineScope: CoroutineScope) {
        autoSavePosition.lateSetCoroutineScope(coroutineScope)
        autoSaveSize.lateSetCoroutineScope(coroutineScope)
        autoSaveRadius.lateSetCoroutineScope(coroutineScope)
        autoSaveOpacity.lateSetCoroutineScope(coroutineScope)
        autoSaveEdge.lateSetCoroutineScope(coroutineScope)
        autoSaveMini.lateSetCoroutineScope(coroutineScope)
    }

    override var position: Offset by autoSavePosition
    override var size: DpSize by autoSaveSize
    override var radius: Dp by autoSaveRadius
    override var opacity: Float by autoSaveOpacity
    override var attachedEdge: AttachedEdge by autoSaveEdge
    override var isMinimized: Boolean by autoSaveMini
}

class FloatingMessage(private val context: Context, msg: Message): FloatingManager(context) {
    private val floatingMessageState = FloatingMessageState()
    override val floatingState: FloatingMessageState = floatingMessageState

    private var message by mutableStateOf(Message(id= 0))
    private var isSetCoroutineScope = false

    init {
        message = msg

        // 显示悬浮窗
        show()
    }

    @Composable
    override fun FloatingBackgroundColor(): Color? {
        return if (message.type == "note") {
            if (isSystemInDarkTheme()) Color(0xFF3E3520) else Color(0xFFFFF8E1)
        } else null
    }

    @Composable
    override fun FloatingContent() {
        if(!isSetCoroutineScope) {
            floatingMessageState.lateSetCoroutineScope(LocalLifecycleOwner.current.lifecycleScope)
        }

        val isNote = message.type == "note" || message.type == "markdata"
        val noteTextColor = if (isNote) {
            if (isSystemInDarkTheme()) Color(0xFFFFE082) else Color(0xFF4E342E)
        } else null

        // 便签使用紧凑样式
        MessageData(
            message = message,
            compact = isNote,
            textColor = noteTextColor,
            onTaskToggled = { taskIndex ->
                val dbMessage = handleTaskToggled(message.id, taskIndex)
                if (dbMessage != null) {
                    // 更新悬浮窗显示
                    message = dbMessage
                    context.sendServiceBroadcast("refresh_floating_message")
                    // 通知数据小屏刷新
                    App.onFloatingDataChanged?.invoke()
                }
            }
        )
    }

    // 跳转消息详情页
    override fun onViewClick() {
        super.onViewClick()

        val clickIntent = Intent(context, MainActivity::class.java).apply {
            putExtra(MESSAGE_ID, message.id)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        context.startActivity(clickIntent)
    }

    // 刷新消息内容
    fun refreshMessage(msg: Message) {
        if (composeView == null) return
        message = msg
    }

    // 重置悬浮窗状态
    fun resetState() {
        floatingState.reset()
        updatePosition()
    }
}