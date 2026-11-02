package com.android.pushme.components.messagelist

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.android.pushme.LocalHomeMessageViewModel
import com.android.pushme.LocalScreen
import com.android.pushme.LocalUserMessageViewModel
import com.android.pushme.MainActivity
import com.android.pushme.R
import com.android.pushme.showToast
import com.android.pushme.utils.LangUtil

/**
 * 消息列表主视图容器（Home 和 User 页面共用）
 * 包含下拉刷新、搜索框、消息列表
 */
@OptIn(ExperimentalMaterialApi::class)
@Composable
fun MessageListView(modifier: Modifier = Modifier) {
    val mainActivity = LocalActivity.current as MainActivity
    val viewModel = if(LocalScreen.current == "user") LocalUserMessageViewModel.current else LocalHomeMessageViewModel.current

    val refreshText = remember { mutableStateOf("") }
    val pullRefreshState = rememberPullRefreshState(
        refreshing = false,
        refreshThreshold = 100.dp,
        onRefresh = {
            refreshText.value = LangUtil.lang(mainActivity, R.string.refreshing)
            viewModel.refreshList(callback = {
                refreshText.value = ""
                mainActivity.showToast(LangUtil.lang(mainActivity, R.string.refresh_success))
            })
        }
    )

    Column(
        modifier.pullRefresh(pullRefreshState)
    ) {
        if(viewModel.searchBox.value.show) {
            MessageListSearch()
        }
        PullRefreshIndicator(pullRefreshState = pullRefreshState, refreshText = refreshText)
        MessageList()
    }
}
