package com.android.pushme.components.messagelist

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.android.pushme.LocalAppViewModel
import com.android.pushme.LocalHomeMessageViewModel
import com.android.pushme.LocalScreen
import com.android.pushme.LocalUserMessageViewModel
import com.android.pushme.MainActivity
import com.android.pushme.utils.VibrationHelper

/**
 * 消息列表组件（Home 和 User 页面使用）
 */
@Composable
fun MessageList(modifier: Modifier = Modifier) {
    val mainActivity = LocalActivity.current as MainActivity
    val screen = LocalScreen.current
    val viewModel = if(screen == "user") LocalUserMessageViewModel.current else LocalHomeMessageViewModel.current
    val homeViewModel = LocalHomeMessageViewModel.current
    val box = viewModel.searchBox.value
    val resultValue = viewModel.searchResult.value
    val messageList = resultValue.list
    val lazyListState = LocalMessageListState.current
    val fontScale = LocalAppViewModel.current.fontScale

    // 共用分页逻辑
    rememberListPagination(
        lazyListState = lazyListState,
        viewModel = viewModel,
        enableFirstIDScroll = true
    )

    // 获取上下文和震动工具
    val context = LocalContext.current
    val vibrationHelper = remember { VibrationHelper(context) }

    LazyColumn(
        modifier = modifier,
        state = lazyListState,
        contentPadding = PaddingValues(10.dp, if(box.show) 0.dp else 10.dp, 10.dp, 10.dp)
    ) {
        items(messageList, key = { it.id }) { message ->
            Box(
                modifier = Modifier.fillMaxWidth().padding(bottom = (10 * fontScale.value).dp).animateItem(fadeInSpec = null, fadeOutSpec = null),
                contentAlignment = Alignment.CenterStart
            ) {
                var leftPadding = 0.dp
                if(message.user != "" && screen == "") {
                    MessageFace(message = message, width = (50 * fontScale.value).dp)
                    leftPadding = (58 * fontScale.value).dp
                }

                MessageSwipe(
                    modifier = Modifier.padding(start = leftPadding),
                    message = message,
                    vibrateAction = {
                        vibrationHelper.vibrate(it)
                    },
                    onDismiss = { action ->
                        if(action == "delete") {
                            viewModel.delMessage(message.id, message.user, message.type)
                            if(screen == "user") {
                                homeViewModel.delMessage(message.id, message.user, message.type)
                            }
                            homeViewModel.updateMessageCount()
                            if(message.user != "" || homeViewModel.msgUserEmpty.value == "开启") {
                                homeViewModel.updateUserUnreadList()
                            }
                            homeViewModel.cancelNotification(mainActivity, message.id)
                        } else if(action == "read") {
                            viewModel.updateMessageRead(message.id)
                            if(screen == "user") {
                                homeViewModel.updateListItem(message.id)
                            }
                            if(!message.read) {
                                homeViewModel.cancelNotification(mainActivity, message.id)
                            }
                            if(message.user != "" || homeViewModel.msgUserEmpty.value == "开启") {
                                homeViewModel.updateUserUnreadList()
                            }
                        } else if(action == "star") {
                            viewModel.updateMessageStar(message.id)
                            if(screen == "user") {
                                homeViewModel.updateListItem(message.id)
                            }
                        }
                    }
                ) {
                    MessageItem(Modifier, message)
                }
            }
        }
        if(messageList.size >= resultValue.pageSize) {
            item {
                MessageListMore()
            }
        }
    }
}
