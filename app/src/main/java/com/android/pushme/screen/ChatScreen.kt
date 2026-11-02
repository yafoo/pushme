package com.android.pushme.screen

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.dp
import com.android.pushme.LocalHomeMessageViewModel
import com.android.pushme.LocalScreen
import com.android.pushme.LocalUserMessageViewModel
import com.android.pushme.MainActivity
import com.android.pushme.R
import com.android.pushme.components.Avatar
import com.android.pushme.components.messagelist.LocalMessageListState
import com.android.pushme.components.messagelist.MessageListChat
import com.android.pushme.components.MeContent
import com.android.pushme.components.MeTopBarTitle
import com.android.pushme.components.MeTopBarWithBack
import kotlinx.coroutines.launch

@Composable
fun ChatScreen(modifier: Modifier = Modifier) {
    val mainActivity = LocalActivity.current as MainActivity
    val focusManager = LocalFocusManager.current
    val viewModel = LocalUserMessageViewModel.current
    val homeViewModel = LocalHomeMessageViewModel.current
    val search by viewModel.search
    val searchResult by viewModel.searchResult
    val messageList = searchResult.list

    val lazyListState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    // 更新首页对应分组的消息状态（仅当最后一条消息状态变化时）
    fun updateHomeGroupMessageIfLatest(messageId: Int) {
        val lastMessage = messageList.maxByOrNull { it.id }
        if(lastMessage?.id == messageId) {
            homeViewModel.updateListItem(messageId)
        }
    }

    // 初始化数据 + 清除所有通知
    LaunchedEffect(Unit) {
        viewModel.initChatData()
        homeViewModel.cancelAllNotifications(mainActivity)
    }

    Scaffold(
        modifier = modifier.clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) {
            focusManager.clearFocus()
        },
        topBar = {
            MeTopBarWithBack(
                onIconClick = {
                    viewModel.clearData()
                },
                titleContent = {
                    val title = if(search.user == " ") "?" else search.user
                    val badge = if(searchResult.newerCount > 0) searchResult.newerCount.toString() else ""
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // 头像在左侧
                        val user = if(search.user == " ") "" else search.user
                        Avatar(
                            user = user,
                            size = 28.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        MeTopBarTitle(
                            title = title,
                            badge = badge,
                            onTitleDbClick = {
                                scope.launch {
                                    // 反转布局，index 0 是底部
                                    lazyListState.animateScrollToItem(0)
                                }
                            }
                        )
                    }
                },
                actions = {
                    // 搜索按钮
                    IconButton(onClick = {
                        if(viewModel.searchBox.value.show) {
                            viewModel.setSearchBoxHide()
                            viewModel.refreshList()
                        } else {
                            viewModel.setSearchBoxShow()
                        }
                    }) {
                        Icon(ImageVector.vectorResource(R.drawable.search), contentDescription = null, Modifier.size(26.dp))
                    }
                }
            )
        },
    ) { innerPadding ->
        CompositionLocalProvider(
            LocalMessageListState provides lazyListState,
            LocalScreen provides "user"
        ) {
            MeContent(
                modifier = Modifier
                    .padding(innerPadding)
                    .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05F))
                    .fillMaxSize()
            ) {
                MessageListChat(
                    updateHomeGroupMessageIfLatest = { messageId ->
                        updateHomeGroupMessageIfLatest(messageId)
                    }
                )
            }
        }
    }
}
