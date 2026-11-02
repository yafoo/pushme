package com.android.pushme.model

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.mutableStateListOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.android.pushme.getNotificationChannels
import kotlinx.coroutines.launch

// 新增数据类表示通道详情
data class ChannelDetail(
    val id: String,
    val name: String,
    val importance: Int,
    val sound: Uri?,
    val vibrationPattern: LongArray?,
    val lockscreenVisibility: Int,
    val showBadge: Boolean,
    val bypassDnd: Boolean,
    val description: String?
)

class NotificationViewModel: ViewModel() {
    val channels = mutableStateListOf<ChannelDetail>()

    fun initChannels(context: Context) {
        viewModelScope.launch {
            val list = getNotificationChannels(context)
            channels.clear()
            channels.addAll(list)
        }
    }
}