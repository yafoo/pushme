package com.android.pushme.screen

import androidx.activity.compose.LocalActivity
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.android.pushme.LocalHomeMessageViewModel
import com.android.pushme.LocalNavController
import com.android.pushme.LocalUserMessageViewModel
import com.android.pushme.MainActivity
import com.android.pushme.R
import com.android.pushme.Utils
import com.android.pushme.cancelAllNotifications
import com.android.pushme.components.message.Html
import com.android.pushme.components.MeConfirm
import com.android.pushme.components.MeContent
import com.android.pushme.components.MeTopBarWithBack
import com.android.pushme.components.message.MessageData
import com.android.pushme.components.message.handleTaskToggled
import com.android.pushme.components.message.rememberChartModifier
import com.android.pushme.data.Message
import com.android.pushme.model.AppDataBase
import com.android.pushme.richtext.markdown.Markdown
import com.android.pushme.richtext.ui.BlockQuoteGutter
import com.android.pushme.richtext.ui.RichText
import com.android.pushme.richtext.ui.RichTextStyle
import com.android.pushme.sendServiceBroadcast
import com.android.pushme.showToast
import com.android.pushme.utils.CacheUtil
import com.android.pushme.utils.LangUtil
import com.android.pushme.utils.Permission.checkFloatingPermission
import com.android.pushme.utils.Permission.goFloatingPermission
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.abs


@Composable
fun MessageScreen(modifier: Modifier = Modifier, messageId: Int = 0) {
    val mainActivity = LocalActivity.current as MainActivity
    val context = LocalContext.current
    val navController = LocalNavController.current
    val viewModel = LocalHomeMessageViewModel.current
    var msg by remember { mutableStateOf(viewModel.getMessage(messageId)) }

    if (msg == null) {
        mainActivity.showToast(LangUtil.lang(mainActivity, R.string.message_not_exist_tips))
        navController.popBackStack()
        return
    }

    if(Message.isData(msg!!.type)) {
        LaunchedEffect(viewModel.dashboardList) {
            snapshotFlow {viewModel.dashboardList.toList()} .collect {
                val dashboardMessage = viewModel.getMessage(messageId)
                if (dashboardMessage != null) {
                    msg = dashboardMessage
                }
            }

        }
    }

    var message by remember(msg) { mutableStateOf(msg?: Message()) }
    fun getPrevMessage(currMsg: Message? = null, type: String = "prev", callback: () -> Unit) {
        val prevMsg = viewModel.getPrevMessage(currMsg ?: msg!!, type)
        if(prevMsg == null) {
            mainActivity.showToast(LangUtil.lang(mainActivity, R.string.message_no_more))
            callback()
            return
        }
        msg = prevMsg
        callback()
    }
    fun getNextMessage(callback: () -> Unit) {
        getPrevMessage(msg!!, "next", callback)
    }

    val userViewModel = LocalUserMessageViewModel.current
    val user = userViewModel.search.value.user

    LaunchedEffect(message) {
        if(!message.read) {
            viewModel.updateMessageRead(message.id)
            if(user != "") {
                userViewModel.updateListItem(message.id)
            }
            if(message.user != "" || viewModel.msgUserEmpty.value == "开启") {
                viewModel.updateUserUnreadList()
            }
        }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            MeTopBarWithBack(
                title = LangUtil.lang(R.string.message_content),
                onIconClick = {
                    viewModel.cancelAllNotifications(mainActivity)
                },
                actions = {
                    if(Message.isNotify(message.type)) {
                        var star by remember(message) { mutableStateOf(message.star) }
                        IconButton(onClick = {
                            star = !star
                            viewModel.updateMessageStar(message.id)
                            if(user != "") {
                                userViewModel.updateListItem(message.id)
                            }
                        }) {
                            Icon(ImageVector.vectorResource(if(star) R.drawable.star else R.drawable.unstar), contentDescription = null, Modifier.size(24.dp))
                        }
                    } else {
                        var star by remember(message) { mutableStateOf(message.star) }
                        if(star) {
                            IconButton(onClick = {
                                mainActivity.sendServiceBroadcast("reset_floating_message")
                            }) {
                                Icon(ImageVector.vectorResource(R.drawable.reset), contentDescription = null, Modifier.size(24.dp))
                            }
                        }
                        IconButton(onClick = {
                            if(!checkFloatingPermission(context)) {
                                mainActivity.showToast(LangUtil.lang(mainActivity, R.string.floating_permission_tips))
                                goFloatingPermission(mainActivity)
                                return@IconButton
                            }
                            star = !star
                            viewModel.updateMessageFloating(message.id)
                            mainActivity.sendServiceBroadcast("refresh_floating_message")
                        }) {
                            Icon(ImageVector.vectorResource(if(star) R.drawable.floating else R.drawable.unfloating), contentDescription = null, Modifier.size(24.dp))
                        }
                    }

                    // 便签消息显示编辑按钮
                    if(message.type == "note") {
                        IconButton(onClick = {
                            navController.navigate("note_edit/$messageId")
                        }) {
                            Icon(ImageVector.vectorResource(R.drawable.edit), contentDescription = null, Modifier.size(24.dp))
                        }
                    }

                    val confirmShow = remember{mutableStateOf(false)}
                    IconButton(onClick = {confirmShow.value = true}) {
                        Icon(ImageVector.vectorResource(R.drawable.delete), contentDescription = null, Modifier.size(24.dp))
                    }

                    MeConfirm(show = confirmShow, content = LangUtil.lang(R.string.confirm_delete), confirm = {
                        viewModel.delMessage(messageId, message.user, message.type)
                        if(user != "") {
                            userViewModel.delMessage(messageId, message.user, message.type)
                        }
                        if(Message.isNotify(message.type)) {
                            viewModel.updateMessageCount()
                            if(message.user != "" || viewModel.msgUserEmpty.value == "开启") {
                                viewModel.updateUserUnreadList()
                            }
                            viewModel.cancelNotification(mainActivity, messageId)
                        } else {
                            viewModel.updateDashboardList()
                        }
                        confirmShow.value = false
                        if(messageId == CacheUtil.floatingMessageId) {
                            mainActivity.sendServiceBroadcast("refresh_floating_message")
                        }
                        mainActivity.showToast(LangUtil.lang(mainActivity, R.string.delete_success))
                        navController.popBackStack()
                    })
                }
            ) },
    ) { innerPadding -> MeContent(
            modifier = Modifier
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05F))
        ) {
            if(Message.isData(message.type)) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(color = MaterialTheme.colorScheme.surface)
                        .verticalScroll(rememberScrollState()),
                ) {
                    MessageDetails(message = message, onTaskToggled = { taskIndex ->
                        val dbMessage = handleTaskToggled(message.id, taskIndex)
                        if (dbMessage != null) {
                            msg = dbMessage
                            viewModel.updateDashboardList()
                            if (message.id == CacheUtil.floatingMessageId) {
                                mainActivity.sendServiceBroadcast("refresh_floating_message")
                            }
                        }
                    })
                }
            } else {
                UpDownSwiper(
                    onSwipeUp = { cb ->
                        getNextMessage(cb)
                    },
                    onSwipeDown = { cb ->
                        getPrevMessage(callback = cb)
                    }
                ) {
                    MessageDetails(message = message, onTaskToggled = { taskIndex ->
                        val dbMessage = handleTaskToggled(message.id, taskIndex)
                        if (dbMessage != null) {
                            msg = dbMessage
                            viewModel.updateDashboardList()
                            if (message.id == CacheUtil.floatingMessageId) {
                                mainActivity.sendServiceBroadcast("refresh_floating_message")
                            }
                        }
                    })
                }
            }
        }
    }
}

/** 记录一次手势“刚开始”时，内容是否已经停在顶部/底部边界。 */
private class SwipeBoundaryState {
    var startedAtTop = false
    var startedAtBottom = false
}

@Composable
fun UpDownSwiper(modifier: Modifier = Modifier, onSwipeUp: (() -> Unit) -> Unit, onSwipeDown: (() -> Unit) -> Unit, content: @Composable () -> Unit) {
    val scrollState = rememberScrollState()
    var offsetY by remember { mutableFloatStateOf(0f) }
    var animationJob by remember { mutableStateOf<Job?>(null) }
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val threshold = with(density) {
        100.dp.toPx()
    }

    // 只有本次手势“一开始”就已经在顶部/底部时，才允许把多余位移拉成“上一条/下一条”提示。
    // 长内容第一次滑到边界时只会把位移吃掉，需要再滑一次才会触发切换，避免误触。
    val boundary = remember { SwipeBoundaryState() }

    // 定义父组件响应并处理滑动事件的行为
    val connection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(
                available: Offset,
                source: NestedScrollSource
            ): Offset {
                if(source != NestedScrollSource.UserInput) return Offset.Zero
                animationJob?.cancel()

                // 手指反向拖动：先把已经拉出的偏移收回去，收完再把手势交给内容滚动
                if((offsetY > 0 && available.y < 0f) || (offsetY < 0 && available.y > 0f)) {
                    val newOffset = offsetY + available.y
                    return if((offsetY > 0 && newOffset >= 0f) || (offsetY < 0 && newOffset <= 0f)) {
                        offsetY = newOffset
                        Offset(x = 0f, available.y)
                    } else {
                        // 越过 0，只消费回到 0 的部分，剩余位移交给内容滚动
                        val consumed = -offsetY
                        offsetY = 0f
                        Offset(x = 0f, consumed)
                    }
                }
                return Offset.Zero
            }

            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource
            ): Offset {
                if(source != NestedScrollSource.UserInput) return Offset.Zero
                // available 为 0 说明内容本身还能滚，尚未到达边界
                if(available.y == 0f) return Offset.Zero

                // 内容可滚动时，必须本次手势一开始就在对应边界才允许拉出提示；
                // 否则（第一次滑到边界）把位移吃掉，不触发切换。
                val allowed = if(available.y > 0f) boundary.startedAtTop else boundary.startedAtBottom
                if(!allowed) return Offset(x = 0f, available.y)

                if(offsetY <= -threshold || offsetY >= threshold) {
                    return Offset(x = 0f, available.y)
                }

                animationJob?.cancel()

                // 子组件处理滑动之后，消费掉剩余的垂直位移
                val temp = offsetY + available.y
                offsetY = if(temp > -threshold && temp < threshold) {
                    temp
                } else {
                    if(available.y < 0) -threshold else threshold
                }
                return Offset(x = 0f, available.y) // 返回直接消费的量
            }

            override suspend fun onPostFling(
                consumed: Velocity,
                available: Velocity
            ): Velocity {
                // 滑动结束后触发回弹
                if (offsetY != 0f) {
                    animationJob?.cancel()
                    animationJob = scope.launch {
                        // 使用弹性动画回弹
                        animate(
                            initialValue = offsetY,
                            targetValue = 0f,
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioLowBouncy,
                                stiffness = Spring.StiffnessLow,
                                visibilityThreshold = 0.01f // 设置可见阈值
                            )
                        ) { value, _ ->
                            offsetY = value
                        }
                    }

                    if(offsetY >= threshold * 0.95) {
                        onSwipeDown {
                            scope.launch {
                                scrollState.animateScrollTo(0)
                            }
                        }
                    } else if(offsetY <= -threshold * 0.95) {
                        onSwipeUp {
                            scope.launch {
                                scrollState.animateScrollTo(0)
                            }
                        }
                    }
                }
                return Velocity.Zero
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .clipToBounds()
            .pointerInput(scrollState) {
                awaitEachGesture {
                    // 每次按下时记录当前是否已在边界，作为本次手势是否允许拉出提示的依据
                    awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    val max = scrollState.maxValue
                    boundary.startedAtTop = max <= 0 || scrollState.value <= 0
                    boundary.startedAtBottom = max <= 0 || scrollState.value >= max
                }
            }
            .nestedScroll(connection)
    ) {
        SwiperHint(
            offsetY = { offsetY },
            threshold = threshold
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .offset{ IntOffset(0, offsetY.toInt()) }
                .background(color = MaterialTheme.colorScheme.surface)
                .verticalScroll(scrollState)
        ) {
            content()
        }
    }
}

@Composable
private fun BoxScope.SwiperHint(
    offsetY: () -> Float,
    threshold: Float
) {
    val context = LocalContext.current
    val thresholdDp = with(LocalDensity.current) { threshold.toDp() }

    // 只在这些“档位”变化时才重组；滑动过程中逐帧变化的部分全部放到 graphicsLayer 里处理
    val hintIndex by remember(threshold) {
        derivedStateOf {
            val progress = (offsetY() / threshold).coerceIn(-1f, 1f)
            when {
                progress >= 0.95f -> 1
                progress > 0f -> 2
                progress <= -0.95f -> 3
                progress < 0f -> 4
                else -> 0
            }
        }
    }
    val isTop by remember { derivedStateOf { offsetY() > 0f } }
    val reached by remember(threshold) { derivedStateOf { abs(offsetY()) >= threshold * 0.95f } }

    val text = when(hintIndex) {
        1 -> LangUtil.lang(context, R.string.leave_hold_view_prev)
        2 -> LangUtil.lang(context, R.string.scroll_down_view_prev)
        3 -> LangUtil.lang(context, R.string.leave_hold_view_next)
        4 -> LangUtil.lang(context, R.string.scroll_down_view_next)
        else -> ""
    }
    val color = if(reached) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(0.8f)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(thresholdDp)
            .align(if(isTop) Alignment.TopCenter else Alignment.BottomCenter)
            .graphicsLayer {
                val progress = (offsetY() / threshold).coerceIn(-1f, 1f)
                val p = abs(progress)
                // 用 alpha + 缩放代替直接改 fontSize，避免每帧重新排版文字
                alpha = p
                val scale = 0.7f + 0.3f * p
                scaleX = scale
                scaleY = scale
                // 让提示始终落在内容露出的那条区域中央，随拉出距离一起移动
                translationY = if(progress > 0f) {
                    (offsetY() - threshold) / 2f
                } else {
                    (threshold + offsetY()) / 2f
                }
            },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Icon(
            ImageVector.vectorResource(if(isTop) R.drawable.keyboard_arrow_down else R.drawable.keyboard_arrow_up),
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(28.dp)
        )
        Text(
            text = text,
            fontSize = 14.sp,
            lineHeight = 16.sp,
            color = color,
            modifier = Modifier.padding(start = 4.dp)
        )
    }
}

@Composable
fun MessageDetails(modifier: Modifier = Modifier, message: Message, onTaskToggled: ((Int) -> Unit)? = null) {
    val mainActivity = LocalActivity.current as MainActivity

    Column(
        modifier = modifier
            .padding(15.dp)
    ) {
        if(message.title != "") {
            Row {
                Message.themeColor(message)?.let { Modifier
                    .padding(top = 3.dp, end = 5.dp)
                    .size(4.dp, 20.dp)
                    .background(color = it) }
                    ?.let { Spacer(it) }
                SelectionContainer {
                    Text(
                        text = message.title,
                        fontSize = 20.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }

        Text(
            modifier = Modifier.padding(top = 10.dp, bottom = 10.dp),
            text = LangUtil.lang(R.string.time)+"：${message.date}",
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7F),
            fontSize = 12.sp
        )

        SelectionContainer {
            when(message.type) {
                "markdown", "markdata", "note" -> RichText(
                    style = RichTextStyle.Default.copy(blockQuoteGutter = BlockQuoteGutter.BarGutter(startMargin = 0.sp))
                ) {
                    val onLinkClicked: (String) -> Unit = {
                        Utils.openUrl(it, mainActivity)
                    }
                    Markdown(
                        message.content,
                        onLinkClicked = onLinkClicked,
                        onTaskToggled = onTaskToggled
                    )
                }

                "data", "chart", "echarts", "svg" -> run {
                    val modifierData = rememberChartModifier(message.type)
                    MessageData(modifier = modifierData, message = message)
                }

                "html" -> {
                    Html(
                        modifier = Modifier.wrapContentSize(),
                        content = message.content
                    )
                }

                "url" -> {
                    val navController = LocalNavController.current
                    navController.popBackStack()
                    Utils.openUrl(message.content, mainActivity)
                }

                else -> Text(
                    text = message.content,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.9F)
                )
            }
        }
    }
}