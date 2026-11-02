package com.android.pushme.model

import com.android.pushme.TAG

class UserMessageViewModel: SearchViewModel() {
    override val tag = "$TAG[UserMessageViewModel]"

    // 初始化数据
    fun initData() {
        updateResult(searchResult.value.copy(chatMode = false))
        refreshList()
    }

    // 初始化对话模式数据
    fun initChatData() {
        updateResult(searchResult.value.copy(chatMode = true, newerCount = 0))
        refreshList()
    }
}