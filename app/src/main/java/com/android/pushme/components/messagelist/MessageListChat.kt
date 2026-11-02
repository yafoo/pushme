package com.android.pushme.components.messagelist

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.android.pushme.LocalAppViewModel
import com.android.pushme.LocalHomeMessageViewModel
import com.android.pushme.LocalUserMessageViewModel
import com.android.pushme.R
import com.android.pushme.utils.LangUtil
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Chat 页面的消息列表容器
 * 包含搜索框、反转布局的消息列表、新消息指示器、批量标记已读逻辑
 */
@Composable
fun MessageListChat(
    updateHomeGroupMessageIfLatest: (messageId: Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val viewModel = LocalUserMessageViewModel.current
    val homeViewModel = LocalHomeMessageViewModel.current
    val searchResult by viewModel.searchResult
    val messageList = searchResult.list
    val fontScale = LocalAppViewModel.current.fontScale
    val lazyListState = LocalMessageListState.current
    val scope = rememberCoroutineScope()

    // 分页逻辑（Chat 模式强制自动加载，需要 firstID 滚动保持底部）
    val paginationState = rememberListPagination(
        lazyListState = lazyListState,
        viewModel = viewModel,
        enableFirstIDScroll = true,
        forceAutoLoad = true
    )
    val isAtTop = paginationState.isAtTop

    // 可见消息批量标记已读（延迟执行，避免快速滚动时频繁写数据库）
    val lastBatchReadTime = remember { mutableLongStateOf(0L) }
    val pendingReadIds = remember { mutableStateOf(setOf<Int>()) }

    LaunchedEffect(lazyListState, messageList.size) {
        snapshotFlow {
            val layoutInfo = lazyListState.layoutInfo
            layoutInfo.visibleItemsInfo.mapNotNull { item ->
                messageList.getOrNull(item.index)?.id
            }
        }.collect { visibleIds ->
            val unreadIds = visibleIds.filter { id ->
                messageList.find { it.id == id }?.read == false
            }
            if(unreadIds.isNotEmpty()) {
                pendingReadIds.value = pendingReadIds.value + unreadIds
                val now = System.currentTimeMillis()
                if(now - lastBatchReadTime.longValue > 500) {
                    lastBatchReadTime.longValue = now
                    val idsToMark = pendingReadIds.value.toList()
                    pendingReadIds.value = emptySet()
                    viewModel.batchMarkAsRead(idsToMark) {
                        // 数据库写入完成后再更新首页
                        val lastId = messageList.maxByOrNull { it.id }?.id
                        if(lastId != null && idsToMark.contains(lastId)) {
                            updateHomeGroupMessageIfLatest(lastId)
                        }
                        homeViewModel.updateUserUnreadList()
                    }
                }
            }
        }
    }

    // 延迟批量标记已读（处理滚动停止后的剩余消息）
    LaunchedEffect(pendingReadIds.value) {
        if(pendingReadIds.value.isNotEmpty()) {
            delay(800)
            if(pendingReadIds.value.isNotEmpty()) {
                val idsToMark = pendingReadIds.value.toList()
                pendingReadIds.value = emptySet()
                viewModel.batchMarkAsRead(idsToMark) {
                    // 数据库写入完成后再更新首页
                    val lastId = messageList.maxByOrNull { it.id }?.id
                    if(lastId != null && idsToMark.contains(lastId)) {
                        updateHomeGroupMessageIfLatest(lastId)
                    }
                    homeViewModel.updateUserUnreadList()
                }
                lastBatchReadTime.longValue = System.currentTimeMillis()
            }
        }
    }

    Column(modifier.fillMaxSize()) {
        // 搜索框
        if(viewModel.searchBox.value.show) {
            MessageListSearch()
        }

        Box(modifier = Modifier.fillMaxSize()) {
            // 消息列表（反转布局，index 0 在底部）
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                state = lazyListState,
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                reverseLayout = true
            ) {
                // 消息气泡（反转后，最新消息在 index 0，即底部）
                items(messageList, key = { it.id }) { message ->
                    Box(
                        modifier = Modifier.fillMaxWidth().animateItem(fadeInSpec = null, fadeOutSpec = null),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        ChatBubbleItem(
                            message = message,
                            fontScale = fontScale.value,
                            onDelete = {
                                viewModel.delMessage(message.id, message.user, message.type)
                                homeViewModel.updateMessageCount()
                                if(message.user != "" || homeViewModel.msgUserEmpty.value == "开启") {
                                    homeViewModel.updateUserUnreadList()
                                }
                            },
                            onRead = {
                                viewModel.updateMessageRead(message.id)
                                // 仅当最后一条消息状态变化时更新首页消息显示
                                updateHomeGroupMessageIfLatest(message.id)
                                if(message.user != "" || homeViewModel.msgUserEmpty.value == "开启") {
                                    homeViewModel.updateUserUnreadList()
                                }
                            },
                            onStar = {
                                viewModel.updateMessageStar(message.id)
                                // 仅当最后一条消息状态变化时更新首页消息显示
                                updateHomeGroupMessageIfLatest(message.id)
                            }
                        )
                    }
                }
                // 加载更多提示（反转后，这个 item 在顶部）
                if(searchResult.hasMore && messageList.size >= searchResult.pageSize) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp)
                                .clickable(enabled = !searchResult.isLoading) {
                                    viewModel.getListMore()
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if(searchResult.isLoading)
                                    LangUtil.lang(R.string.loading)
                                else
                                    LangUtil.lang(R.string.load_older_messages),
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                            )
                        }
                    }
                }
            }

            // 悬浮按钮区域（脱离 ColumnScope）
            Box(modifier = Modifier.fillMaxSize()) {
                // 新消息提示按钮（反转后，新消息在底部 index 0）
                androidx.compose.animation.AnimatedVisibility(
                    visible = !isAtTop.value && searchResult.newerCount > 0,
                    modifier = Modifier.align(Alignment.BottomCenter),
                    enter = fadeIn() + slideInVertically { it },
                    exit = fadeOut() + slideOutVertically { it }
                ) {
                    NewMessageIndicator(
                        count = searchResult.newerCount,
                        onClick = {
                            scope.launch {
                                lazyListState.animateScrollToItem(0) // 反转后 0 是底部
                                viewModel.updateResult(searchResult.copy(newerCount = 0))
                            }
                        }
                    )
                }

                // 滚动到底部按钮（当不在底部时显示）
                androidx.compose.animation.AnimatedVisibility(
                    visible = !isAtTop.value && searchResult.newerCount == 0,
                    modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    IconButton(
                        onClick = {
                            scope.launch {
                                lazyListState.animateScrollToItem(0) // 反转后 0 是底部
                            }
                        },
                        modifier = Modifier
                            .size(40.dp)
                            .shadow(4.dp, CircleShape)
                            .background(MaterialTheme.colorScheme.surface, CircleShape)
                    ) {
                        Icon(
                            ImageVector.vectorResource(R.drawable.keyboard_arrow_down),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }
        }
    }
}
