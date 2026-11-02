package com.android.pushme.data

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import com.android.pushme.utils.Settings

data class SearchResult(
    val newerCount: Int = 0,

    val firstID: Int = 0,
    val isAtTop: Boolean = true,

    var page: Int = 1,
    var pageSize: Int = 10,
    val total: Int = 0,
    val list: SnapshotStateList<Message> = mutableStateListOf(),

    val isLoading: Boolean = false,
    val hasMore: Boolean = true,
    val autoLoadMore: Boolean = Settings.msgLoadMore != "关闭",
    val chatMode: Boolean = false,
) {
    fun clearList(): SearchResult {
        return copy(list = mutableStateListOf())
    }
}