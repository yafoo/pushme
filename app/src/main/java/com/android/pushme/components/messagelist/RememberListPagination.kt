package com.android.pushme.components.messagelist

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.android.pushme.model.SearchViewModel

/**
 * 列表分页状态
 */
data class ListPaginationState(
    val isAtTop: State<Boolean>,
    val isAtBottom: State<Boolean>
)

/**
 * 列表分页逻辑共用组件
 * 
 * @param lazyListState LazyList 状态
 * @param viewModel 继承自 SearchViewModel 的 ViewModel
 * @param enableFirstIDScroll 是否启用 firstID 滚动（MessageList 用，ChatScreen 不用）
 * @param forceAutoLoad 是否强制自动加载（ChatScreen 用，不受 autoLoadMore 设置控制）
 */
@Composable
fun rememberListPagination(
    lazyListState: LazyListState,
    viewModel: SearchViewModel,
    enableFirstIDScroll: Boolean = false,
    forceAutoLoad: Boolean = false
): ListPaginationState {
    val resultValue = viewModel.searchResult.value

    // firstID 滚动（新消息到达时自动滚动到顶部）
    if(enableFirstIDScroll) {
        var rememberID = remember { resultValue.firstID }
        LaunchedEffect(resultValue.firstID) {
            if(rememberID != resultValue.firstID) {
                rememberID = resultValue.firstID
                if(resultValue.isAtTop) {
                    lazyListState.animateScrollToItem(0)
                }
            }
        }
    }

    // isAtTop 检测 + newerCount 重置
    val isAtTop = remember {
        derivedStateOf { lazyListState.firstVisibleItemIndex == 0 }
    }
    LaunchedEffect(isAtTop.value) {
        viewModel.updateResult(resultValue.copy(
            isAtTop = isAtTop.value,
            newerCount = if(isAtTop.value) 0 else resultValue.newerCount
        ))
    }

    // isAtBottom 检测 + 自动加载更多
    val isAtBottom = remember {
        derivedStateOf {
            val layoutInfo = lazyListState.layoutInfo
            val lastItem = layoutInfo.visibleItemsInfo.lastOrNull()
            lastItem != null && lastItem.index >= layoutInfo.totalItemsCount - 1
        }
    }

    val shouldAutoLoad = resultValue.autoLoadMore || forceAutoLoad
    if(shouldAutoLoad) {
        val lastGetTime = remember { mutableLongStateOf(0L) }
        LaunchedEffect(isAtBottom.value) {
            if(isAtBottom.value) {
                val currentTime = System.currentTimeMillis()
                if(currentTime - lastGetTime.longValue > 200) {
                    lastGetTime.longValue = currentTime
                    viewModel.getListMore()
                }
            }
        }
    }

    return ListPaginationState(isAtTop, isAtBottom)
}
