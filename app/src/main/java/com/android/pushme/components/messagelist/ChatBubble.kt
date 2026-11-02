package com.android.pushme.components.messagelist

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.android.pushme.LocalHomeMessageViewModel
import com.android.pushme.LocalNavController
import com.android.pushme.LocalUserMessageViewModel
import com.android.pushme.MainActivity
import com.android.pushme.R
import com.android.pushme.Utils
import com.android.pushme.data.Message
import com.android.pushme.components.message.Html
import com.android.pushme.richtext.markdown.Markdown
import com.android.pushme.richtext.ui.BlockQuoteGutter
import com.android.pushme.richtext.ui.RichText
import com.android.pushme.richtext.ui.RichTextStyle
import com.android.pushme.showShortDate
import com.android.pushme.utils.VibrationHelper

/**
 * 聊天气泡条目（Chat 页面使用）
 */
@Composable
fun ChatBubbleItem(
    message: Message,
    fontScale: Float,
    onDelete: () -> Unit,
    onRead: () -> Unit,
    onStar: () -> Unit
) {
    val context = LocalContext.current
    val vibrationHelper = remember { VibrationHelper(context) }

    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.CenterStart
    ) {
        MessageSwipe(
            modifier = Modifier.widthIn(max = 320.dp),
            message = message,
            cardShape = RoundedCornerShape(8.dp),
            vibrateAction = { vibrationHelper.vibrate(it) },
            onDismiss = { action ->
                when (action) {
                    "delete" -> onDelete()
                    "read" -> onRead()
                    "star" -> onStar()
                }
            }
        ) {
            ChatBubble(message = message, fontScale = fontScale)
        }
    }
}

/**
 * 聊天气泡内容
 */
@Composable
fun ChatBubble(message: Message, fontScale: Float) {
    val mainActivity = LocalActivity.current as MainActivity
    val navController = LocalNavController.current
    val viewModel = LocalUserMessageViewModel.current
    val homeViewModel = LocalHomeMessageViewModel.current
    // 未读消息用主题色背景，已读用表面色
    val bubbleColor = if(message.read) MaterialTheme.colorScheme.surface
        else MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
    val textColor = contentColorFor(bubbleColor)

    Card(
        modifier = Modifier
            .widthIn(max = 320.dp)
            .shadow(1.dp, RoundedCornerShape(8.dp))
            .clickable {
                if(message.type == "url") {
                    Utils.openUrl(message.content, mainActivity)
                } else {
                    navController.navigate("message/${message.id}") {
                        launchSingleTop = true
                    }
                }
                // 点击时立即标记已读
                if(!message.read) {
                    viewModel.updateMessageRead(message.id, true)
                    // 仅当最后一条消息状态变化时更新首页消息显示
                    val chatList = viewModel.searchResult.value.list
                    val lastId = chatList.maxByOrNull { m -> m.id }?.id
                    if(message.id == lastId) {
                        homeViewModel.searchResult.value.list
                            .find { it.user == message.user }
                            ?.let { item ->
                                // 直接更新首页列表项的已读状态
                                val updated = item.copy(read = true)
                                homeViewModel.searchResult.value.list.replaceAll {
                                    if (it.id == item.id) updated else it
                                }
                            }
                    }
                    homeViewModel.cancelNotification(mainActivity, message.id)
                    if(message.user != "" || homeViewModel.msgUserEmpty.value == "开启") {
                        homeViewModel.updateUserUnreadList()
                    }
                }
            },
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier
                .background(color = bubbleColor)
                .padding(
                    horizontal = (12 * fontScale).dp,
                    vertical = (10 * fontScale).dp
                )
                .widthIn(max = 300.dp)
        ) {
            // 标题行：主题色竖条 + 收藏图标 + 标题
            if(message.title != "") {
                Row(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // 主题色竖条（同详情页样式）
                    Message.themeColor(message)?.let {
                        Spacer(
                            modifier = Modifier
                                .padding(top = (2 * fontScale).dp, end = (4 * fontScale).dp)
                                .size((4 * fontScale).dp, (15 * fontScale).dp)
                                .background(color = it)
                        )
                    }

                    if (message.star) {
                        Icon(
                            ImageVector.vectorResource(R.drawable.star),
                            contentDescription = null,
                            modifier = Modifier
                                .padding(top = (2 * fontScale).dp, end = (4 * fontScale).dp)
                                .size((16 * fontScale).dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }

                    SelectionContainer(modifier = Modifier.weight(1f)) {
                        Text(
                            text = message.title,
                            fontSize = (15 * fontScale).sp,
                            lineHeight = (15 * fontScale).sp,
                            color = textColor,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            // 内容：根据类型渲染
            if (message.content != "") {
                Spacer(modifier = Modifier.height((6 * fontScale).dp))
                SelectionContainer {
                    MessageContentRenderer(message = message, fontScale = fontScale, mainActivity = mainActivity)
                }
            }

            // 时间
            if (message.date != "") {
                Spacer(modifier = Modifier.height((4 * fontScale).dp))
                Text(
                    text = message.date.showShortDate(),
                    fontSize = (11 * fontScale).sp,
                    lineHeight = (11 * fontScale).sp,
                    color = textColor.copy(alpha = 0.5f)
                )
            }
        }
    }
}

/**
 * 消息内容渲染器（根据类型渲染 markdown/html/url/text）
 */
@Composable
fun MessageContentRenderer(message: Message, fontScale: Float, mainActivity: MainActivity) {
    when (message.type) {
        "markdown" -> {
            RichText(
                style = RichTextStyle.Default.copy(blockQuoteGutter = BlockQuoteGutter.BarGutter(startMargin = 0.sp))
            ) {
                Markdown(
                    message.content,
                    onLinkClicked = { Utils.openUrl(it, mainActivity) }
                )
            }
        }
        "html" -> {
            Html(
                modifier = Modifier.wrapContentSize(),
                content = message.content
            )
        }
        "url" -> {
            Text(
                text = message.content,
                fontSize = (14 * fontScale).sp,
                lineHeight = (14 * fontScale * 1.5).sp,
                color = MaterialTheme.colorScheme.primary
            )
        }
        else -> {
            Text(
                text = message.content,
                fontSize = (14 * fontScale).sp,
                lineHeight = (14 * fontScale * 1.5).sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
            )
        }
    }
}
