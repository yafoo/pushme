package com.android.pushme.components.messagelist

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.android.pushme.LocalAppViewModel
import com.android.pushme.LocalHomeMessageViewModel
import com.android.pushme.LocalNavController
import com.android.pushme.LocalScreen
import com.android.pushme.LocalUserMessageViewModel
import com.android.pushme.MainActivity
import com.android.pushme.R
import com.android.pushme.Utils
import com.android.pushme.components.Avatar
import com.android.pushme.components.MeBadge
import com.android.pushme.components.leftBorder
import com.android.pushme.data.Message
import com.android.pushme.showShortDate
import com.android.pushme.utils.Settings

/**
 * 消息条目组件
 */
@Composable
fun MessageItem(modifier: Modifier = Modifier, message: Message) {
    val navController = LocalNavController.current
    val backgroundColor = if(message.read) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
    val textColor = contentColorFor(backgroundColor)
    val fontScale = LocalAppViewModel.current.fontScale
    val userViewModel = LocalUserMessageViewModel.current
    val screen = LocalScreen.current
    val mainActivity = LocalActivity.current as MainActivity

    val viewModel = LocalHomeMessageViewModel.current
    val user = userViewModel.search.value.user

    Box(modifier
        .background(backgroundColor)
        .fillMaxWidth()
        .leftBorder((3 * fontScale.value).dp, Message.themeColor(message) ?: Color.Transparent)
        .clickable(
            onClick = {
                if(message.user == "" ||  Settings.msgUserClick == "详情" || screen == "user") {
                    if(message.type == "url") {
                        Utils.openUrl(message.content, mainActivity)
                        if(!message.read) {
                            viewModel.updateMessageRead(message.id)
                            if(user != "") {
                                userViewModel.updateListItem(message.id)
                            }
                            if(message.user != "" || viewModel.msgUserEmpty.value == "开启") {
                                viewModel.updateUserUnreadList()
                            }
                        }
                    } else {
                        navController.navigate("message/${message.id}") {
                            launchSingleTop = true
                        }
                    }
                } else if(Settings.msgUserClick == "对话") {
                    navController.navigate("chat") {
                        launchSingleTop = true
                    }
                    userViewModel.updateUser(message.user)
                    userViewModel.initChatData()
                } else {
                    navController.navigate("user") {
                        launchSingleTop = true
                    }
                    userViewModel.updateUser(message.user)
                    userViewModel.initData()
                }
            }
        ),
        Alignment.TopStart
    ) {
        Column(
            Modifier
                .padding(horizontal = (10 * fontScale.value).dp, vertical = (10 * fontScale.value).dp)
                .fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = (4 * fontScale.value).dp),
                verticalAlignment = Alignment.Bottom
            ) {
                if(message.star) {
                    Icon(
                        ImageVector.vectorResource(R.drawable.star),
                        contentDescription = null,
                        Modifier
                            .padding(end = (1 * fontScale.value).dp, bottom = (2 * fontScale.value).dp)
                            .size((18 * fontScale.value).dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
                SelectionContainer(
                    modifier = Modifier.weight(1f)
                ) {
                    val titleFontSize = (17 * fontScale.value).sp
                    Text(
                        text = message.title,
                        fontSize = titleFontSize,
                        lineHeight = titleFontSize,
                        color = textColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if(message.date != "") {
                    val dateFontSize = (10 * fontScale.value).sp
                    Text(
                        text = message.date.showShortDate(),
                        fontSize = dateFontSize,
                        lineHeight = dateFontSize,
                        color = textColor.copy(alpha = 0.6F),
                    )
                }
            }
            if(message.content != "") {
                val contentFontSize = (14 * fontScale.value).sp
                SelectionContainer {
                    Text(
                        text = message.content,
                        fontSize = contentFontSize,
                        lineHeight = (contentFontSize.value * 1.4).sp,
                        color = textColor.copy(alpha = 0.6F),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

/**
 * 消息头像组件
 */
@Composable
fun MessageFace(modifier: Modifier = Modifier, message: Message, width: Dp = 42.dp) {
    val fontScale = LocalAppViewModel.current.fontScale
    val viewModel = LocalHomeMessageViewModel.current
    val userViewModel = LocalUserMessageViewModel.current
    val navController = LocalNavController.current
    Column(
        modifier.width(width)
    ) {
        val userFontSize = (10 * fontScale.value).sp
        val userPrimary = if(message.user == " ") "" else message.user
        val badge = if(viewModel.userUnreadList.containsKey(userPrimary)) viewModel.userUnreadList[userPrimary].toString() else ""

        Box(Modifier.clickable {
            navController.navigate("user") {
                launchSingleTop = true
            }
            userViewModel.updateUser(message.user)
            userViewModel.initData()
        }, contentAlignment = Alignment.Center,) {
            Avatar(Modifier, user = if(userPrimary == "") "?" else message.user, face = message.face, size = width, elevation = 1.dp)
            if(badge != "") {
                val offset = width.value / 4 + 3 * fontScale.value
                MeBadge(badge, modifier = Modifier.offset(x = offset.dp, y = -offset.dp))
            }
        }

        if(userPrimary != "") {
            SelectionContainer(
                modifier = Modifier.fillMaxWidth().padding(top = (2 * fontScale.value).dp)
            ) {
                Text(
                    text = message.user,
                    fontSize = userFontSize,
                    letterSpacing = .1.sp,
                    lineHeight = userFontSize,
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
