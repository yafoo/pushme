package com.android.pushme.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.DefaultShadowColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.activity.compose.LocalActivity
import com.android.pushme.LocalHomeMessageViewModel
import com.android.pushme.LocalNavController
import com.android.pushme.MainActivity
import com.android.pushme.components.MeTopBarWithBack
import com.android.pushme.R
import com.android.pushme.components.RadioListNew
import com.android.pushme.components.message.MessageData
import com.android.pushme.components.message.handleTaskToggled
import com.android.pushme.components.message.rememberChartModifier
import com.android.pushme.data.Message
import com.android.pushme.model.AppDataBase
import com.android.pushme.model.HomeMessageViewModel
import com.android.pushme.sendServiceBroadcast
import com.android.pushme.utils.CacheUtil
import com.android.pushme.utils.LangUtil
import com.android.pushme.utils.Settings

@Composable
fun DashboardScreen(modifier: Modifier = Modifier) {
    var sortBoxShow by remember { mutableStateOf(false) }
    val viewModel = LocalHomeMessageViewModel.current
    val navController = LocalNavController.current

    // 页面显示时强制刷新数据（不受 dashboardShow 状态影响，确保从后台恢复时数据正确）
    LaunchedEffect(Unit) {
        viewModel.setDashboardShow()
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            MeTopBarWithBack(
                title = LangUtil.lang(R.string.small_dashboard),
                onIconClick = {
                    viewModel.setDashboardHide()
                },
                actions = {
                    IconButton(onClick = {sortBoxShow = !sortBoxShow}) {
                        Icon(ImageVector.vectorResource(R.drawable.sort), contentDescription = null, Modifier.size(26.dp))
                    }
                    IconButton(onClick = {
                        navController.navigate("note_add")
                    }) {
                        Icon(ImageVector.vectorResource(R.drawable.add), contentDescription = null, Modifier.size(26.dp))
                    }
                }
            ) },
    ) { innerPadding -> Column(
            modifier = Modifier
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05F))
                .fillMaxSize()
        ) {
            if(sortBoxShow) {
                DashboardSort()
            }

            DashboardList(sortBoxShow = sortBoxShow)
        }
    }
}

@Composable
fun DashboardSort(modifier: Modifier = Modifier) {
    val viewModel = LocalHomeMessageViewModel.current
    val sortBys = Settings.getSettingOptions(listOf("date", "id"), listOf("date", "id"))
    val sorts = Settings.getSettingOptions(listOf("desc", "asc"), listOf("desc", "asc"))
    val sortBy = viewModel.dashboardSortBy
    val sort = viewModel.dashboardSort
    Row(
        modifier
            .padding(10.dp, 10.dp, 10.dp, 10.dp)
            .clip(RoundedCornerShape(3.dp))
            .shadow(
                elevation = 1.dp,
                spotColor = DefaultShadowColor.copy(alpha = 0.6F),
                ambientColor = DefaultShadowColor.copy(alpha = 0.6F),
                clip = false
            )
            .background(MaterialTheme.colorScheme.surface)
            .clip(RoundedCornerShape(3.dp))
            .padding(10.dp, 10.dp, 0.dp, 10.dp)
    ) {
        RadioListNew(
            modifier = Modifier.weight(1f).padding(vertical = 5.dp),
            modifierItem = Modifier.padding(start = 2.dp, end = 3.dp),
            title = LangUtil.lang(R.string.sort)+":",
            list = sorts,
            selectedValue = sorts.entries.find { it.key == sort.value } ?: sorts.entries.first(),
            onClick = {
                viewModel.setDashboardSort(it.key)
            }
        )

        RadioListNew(
            modifier = Modifier.weight(1f).padding(vertical = 5.dp),
            modifierItem = Modifier.padding(start = 2.dp, end = 3.dp),
            title = LangUtil.lang(R.string.sort_by)+":",
            list = sortBys,
            selectedValue = sortBys.entries.find { it.key == sortBy.value } ?: sortBys.entries.first(),
            onClick = {
                viewModel.setDashboardSortBy(it.key)
            }
        )
    }
}

@Composable
fun DashboardList(modifier: Modifier = Modifier, sortBoxShow: Boolean = false) {
    val navController = LocalNavController.current
    val viewModel = LocalHomeMessageViewModel.current
    val messageList = viewModel.dashboardList
    val lazyStaggeredGridState = rememberLazyStaggeredGridState()

    LazyVerticalStaggeredGrid(
        modifier = modifier.fillMaxSize(),
        state = lazyStaggeredGridState,
        columns = StaggeredGridCells.Adaptive(160.dp),
        contentPadding = PaddingValues(10.dp, if(sortBoxShow) 0.dp else 10.dp, 10.dp, 10.dp),
        verticalItemSpacing = 10.dp,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(messageList, key = { it.id }) { message ->
            val isNote = message.type == "note"
            val cardBg = if (isNote) {
                if (isSystemInDarkTheme()) Color(0xFF3E3520) else Color(0xFFFFF8E1)
            } else MaterialTheme.colorScheme.surface
            Card(
                shape = RoundedCornerShape(3.dp),
                modifier = Modifier
                    .fillMaxWidth()
//                    .animateItem(fadeInSpec = null, fadeOutSpec = null)
                    .clickable {
                        navController.navigate("message/${message.id}") {
                            launchSingleTop = true
                        }
                    },
                backgroundColor = cardBg
            ) {
                DashboardItem(
                    message = message,
                    viewModel = viewModel
                )
            }
        }
    }
}

@Composable
fun DashboardItem(modifier: Modifier = Modifier, message: Message, viewModel: HomeMessageViewModel) {
    val mainActivity = LocalActivity.current as MainActivity
    val isNote = message.type == "note"
    val noteTextColor = if (isNote) {
        if (isSystemInDarkTheme()) Color(0xFFFFE082) else Color(0xFF4E342E)
    } else MaterialTheme.colorScheme.onSurface
    val messageDao = remember { AppDataBase.instance.getMessageDao() }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 5.dp, vertical = 5.dp)
    ) {
        if(message.title != "") {
            Text(
                text = message.title,
                fontSize = 14.sp,
                maxLines = 1,
                color = noteTextColor.copy(alpha = 0.85F),
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )
        }
        Text(
            text = message.date,
            fontSize = 8.sp,
            lineHeight = 8.sp,
            color = noteTextColor.copy(alpha = 0.5F),
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center
        )
        if(message.type == "markdata" || message.type == "chart" || isNote) {
            Spacer(Modifier.height(10.dp))
        }
        val modifierData = rememberChartModifier(message.type)
        // 数据小屏使用更紧凑的 Markdown 渲染样式
        MessageData(
            modifier = modifierData,
            message = message,
            compact = true,
            textColor = noteTextColor,
            onTaskToggled = { taskIndex ->
                val dbMessage = handleTaskToggled(message.id, taskIndex)
                if (dbMessage != null) {
                    message.content = dbMessage.content
                    viewModel.updateDashboardList()
                    // 如果是悬浮窗消息，刷新悬浮窗
                    if (message.id == CacheUtil.floatingMessageId) {
                        mainActivity.sendServiceBroadcast("refresh_floating_message")
                    }
                }
            }
        )
    }
}
