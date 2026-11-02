package com.android.pushme.screen

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.android.pushme.LocalHomeMessageViewModel
import com.android.pushme.LocalNavController
import com.android.pushme.MainActivity
import com.android.pushme.R
import com.android.pushme.Utils
import com.android.pushme.components.MarkdownToolbar
import com.android.pushme.components.MeContent
import com.android.pushme.components.MeTopBarWithBack
import com.android.pushme.components.message.repostMessage
import com.android.pushme.data.Message
import com.android.pushme.model.AppDataBase
import com.android.pushme.showToast
import com.android.pushme.sendServiceBroadcast
import com.android.pushme.utils.CacheUtil
import com.android.pushme.utils.LangUtil

@Composable
fun NoteEditScreen(modifier: Modifier = Modifier, noteId: Int = 0) {
    val focusManager = LocalFocusManager.current
    val mainActivity = LocalActivity.current as MainActivity
    val navController = LocalNavController.current
    val viewModel = LocalHomeMessageViewModel.current
    val messageDao = AppDataBase.instance.getMessageDao()

    // 加载现有消息或创建新消息
    val existingMessage = if (noteId > 0) {
        remember { messageDao.getByID(noteId) }
    } else null

    var title by remember { mutableStateOf(existingMessage?.title ?: "") }
    var content by remember { mutableStateOf(TextFieldValue(existingMessage?.content ?: "")) }

    val isNew = noteId == 0
    val pageTitle = if (isNew) LangUtil.lang(R.string.note_add) else LangUtil.lang(R.string.note_edit)

    Scaffold(
        modifier = modifier.clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) {
            focusManager.clearFocus()
        },
        topBar = {
            MeTopBarWithBack(
                title = pageTitle,
                actions = {
                    IconButton(onClick = {
                        if (title.isBlank() && content.text.isBlank()) {
                            mainActivity.showToast(LangUtil.lang(mainActivity, R.string.note_empty_tips))
                            return@IconButton
                        }

                        if (isNew) {
                            // 新增便签
                            val newMessage = Message(
                                id = 0,
                                title = title,
                                content = content.text,
                                date = Utils.getCurrentTime(),
                                type = "note",
                                read = true,
                                star = false
                            )
                            messageDao.addMessage(newMessage, mainActivity)
                            viewModel.updateDashboardList()
                            mainActivity.showToast(LangUtil.lang(mainActivity, R.string.add_success))
                        } else {
                            // 更新便签
                            existingMessage?.let { msg ->
                                msg.title = title
                                msg.content = content.text
                                msg.date = Utils.getCurrentTime()
                                messageDao.update(msg)
                                viewModel.updateDashboardList()
                                if (msg.id == CacheUtil.floatingMessageId) {
                                    mainActivity.sendServiceBroadcast("refresh_floating_message")
                                }
                                mainActivity.showToast(LangUtil.lang(mainActivity, R.string.edit_success))
                            }
                        }

                        // 保存成功后转发便签消息
                        repostMessage(title, content.text, Utils.getCurrentTime(), "note")

                        navController.popBackStack()
                    }) {
                        Icon(ImageVector.vectorResource(R.drawable.save), contentDescription = null, Modifier.size(26.dp))
                    }
                }
            )
        }
    ) { innerPadding ->
        MeContent(
            modifier = Modifier
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05F))
        ) {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(15.dp)
                    .fillMaxSize()
            ) {
                TextField(
                    label = { Text(text = LangUtil.lang(R.string.title)) },
                    value = title,
                    onValueChange = { title = it },
                    colors = TextFieldDefaults.colors(
                        unfocusedContainerColor = MaterialTheme.colorScheme.surface
                    ),
                    placeholder = { Text(text = LangUtil.lang(R.string.title), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
                )
                MarkdownToolbar(
                    textFieldValue = content,
                    onValueChange = { content = it },
                    modifier = Modifier.fillMaxWidth()
                )
                TextField(
                    label = { Text(text = LangUtil.lang(R.string.content)) },
                    value = content,
                    onValueChange = { content = it },
                    colors = TextFieldDefaults.colors(
                        unfocusedContainerColor = MaterialTheme.colorScheme.surface
                    ),
                    placeholder = { Text(text = LangUtil.lang(R.string.note_content_placeholder), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(400.dp),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Default)
                )
            }
        }
    }
}
