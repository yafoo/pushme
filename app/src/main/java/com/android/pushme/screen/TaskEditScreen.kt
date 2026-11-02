package com.android.pushme.screen

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.android.pushme.LocalNavController
import com.android.pushme.MainActivity
import com.android.pushme.R
import com.android.pushme.components.MeConfirm
import com.android.pushme.components.MeContent
import com.android.pushme.components.MeTopBarWithBack
import com.android.pushme.showToast
import com.android.pushme.task.CronExpression
import com.android.pushme.task.TaskInfo
import com.android.pushme.task.TaskScheduler
import com.android.pushme.task.TaskStore
import com.android.pushme.utils.LangUtil
import com.android.pushme.Utils

/**
 * 定时任务编辑页。
 *
 * `taskId = 0` 表示新建。
 *
 * 保存时做三件事：落盘 → 排闹钟 → 返回。
 *
 * 底部的「发送测试消息」不走闹钟、不落盘，直接把当前编辑中的内容当成一次触发发出去
 * （见 [TaskScheduler.test]），方便调插件时不用等 cron 到点。
 * ⚠️ 它和真实触发一样走完整插件链路：插件没接管的消息会被丢弃，
 * 所以没有启用中的插件时直接提示、不发送。
 */
@Composable
fun TaskEditScreen(taskId: Int, modifier: Modifier = Modifier) {
    val focusManager = LocalFocusManager.current
    val mainActivity = LocalActivity.current as MainActivity
    val navController = LocalNavController.current

    val origin = remember(taskId) { if (taskId > 0) TaskStore.find(taskId) else null }

    var name by remember { mutableStateOf(origin?.name ?: "") }
    var cron by remember { mutableStateOf(origin?.cron ?: "") }
    var content by remember { mutableStateOf(origin?.content ?: "") }
    var enabled by remember { mutableStateOf(origin?.enabled ?: true) }
    // MeConfirm 要的是 MutableState 本身，所以这里不能用 by 委托
    val showDelete = remember { mutableStateOf(false) }

    // 表达式校验：只算一次，cron 变了才重算
    val parsed = remember(cron) { CronExpression.parse(cron) }
    val nextRun = remember(cron) { parsed?.next(System.currentTimeMillis()) }

    fun save() {
        val trimmedName = name.trim()
        val trimmedCron = cron.trim()

        if (trimmedName.isEmpty()) {
            mainActivity.showToast(LangUtil.lang(mainActivity, R.string.task_name_empty))
            return
        }
        if (trimmedCron.isEmpty()) {
            mainActivity.showToast(LangUtil.lang(mainActivity, R.string.task_cron_empty))
            return
        }
        if (CronExpression.parse(trimmedCron) == null) {
            mainActivity.showToast(LangUtil.lang(mainActivity, R.string.task_cron_invalid))
            return
        }

        // 编辑已有任务时 copy 一份，保住 id 和 lastRun
        val task = origin?.copy() ?: TaskInfo()
        task.name = trimmedName
        task.cron = trimmedCron
        task.content = content
        task.enabled = enabled

        val id = TaskStore.upsert(task)
        // 从存储里重新取一次：新建时 id 是 upsert 里才分配的
        TaskStore.find(id)?.let { TaskScheduler.scheduleTask(mainActivity, it) }

        // 任务数量 / 启用状态可能变了，刷新首页 TSK 标签（应用内操作不会触发 onStart）
        mainActivity.refreshTaskTag()

        mainActivity.showToast(LangUtil.lang(mainActivity, R.string.save_success))
        navController.popBackStack()
    }

    fun delete() {
        TaskStore.delete(taskId)
        TaskScheduler.cancel(mainActivity, taskId)
        // 删掉最后一条启用中的任务后，首页 TSK 标签要跟着灭
        mainActivity.refreshTaskTag()
        showDelete.value = false
        navController.popBackStack()
    }

    /**
     * 手动测试：把当前编辑中的名称和内容当成一次触发发出去。
     *
     * 没有启用中的插件就直接拦下来 —— 那种情况下消息 100% 会被
     * `MessageProcessor` 丢弃，发出去只会得到一个「假成功」。
     */
    fun sendTest() {
        if (mainActivity.pluginViewModel.getPluginCount(1) == 0) {
            mainActivity.showToast(LangUtil.lang(mainActivity, R.string.task_test_no_plugin))
            return
        }
        TaskScheduler.test(mainActivity, name, content)
        mainActivity.showToast(LangUtil.lang(mainActivity, R.string.task_test_sent))
    }

    MeConfirm(
        show = showDelete,
        title = LangUtil.lang(R.string.task_delete),
        content = LangUtil.lang(R.string.task_delete_confirm),
        confirm = { delete() }
    )

    Scaffold(
        modifier = modifier.clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) {
            focusManager.clearFocus()
        },
        topBar = {
            MeTopBarWithBack(
                title = LangUtil.lang(if (taskId > 0) R.string.task_edit else R.string.task_add),
                actions = {
                    // 删除放右上角（和插件编辑页同一位置、同一图标），只在编辑已有任务时出现
                    if (taskId > 0) {
                        IconButton(onClick = { showDelete.value = true }) {
                            Icon(
                                ImageVector.vectorResource(R.drawable.delete),
                                contentDescription = null,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                    IconButton(onClick = { save() }) {
                        Icon(
                            ImageVector.vectorResource(R.drawable.save),
                            contentDescription = null,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                }
            )
        },
    ) { innerPadding -> MeContent(
        modifier = Modifier
            .padding(innerPadding)
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05F))
    ) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(15.dp)
                .fillMaxSize(),
        ) {
            // 任务名称
            TextField(
                label = { Text(text = LangUtil.lang(R.string.task_name)) },
                value = name,
                onValueChange = { name = it },
                colors = TextFieldDefaults.colors(
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface
                ),
                placeholder = {
                    Text(
                        text = LangUtil.lang(R.string.task_name_placeholder),
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
            )

            // 定时表达式
            TextField(
                label = { Text(text = LangUtil.lang(R.string.task_cron)) },
                value = cron,
                onValueChange = { cron = it },
                colors = TextFieldDefaults.colors(
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface
                ),
                placeholder = {
                    Text(
                        text = LangUtil.lang(R.string.task_cron_placeholder),
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                // cron 里只有数字、字母和符号，键盘用 ASCII 更顺手（避免中文输入法逗号）
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Ascii,
                    imeAction = ImeAction.Next
                ),
                textStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace)
            )

            // 表达式校验结果 / 下次触发预览
            when {
                cron.isBlank() -> Unit
                parsed == null -> Text(
                    text = LangUtil.lang(R.string.task_cron_invalid),
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 4.dp)
                )
                nextRun == null -> Text(
                    text = LangUtil.lang(R.string.task_cron_never),
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 4.dp)
                )
                else -> Text(
                    text = "${LangUtil.lang(R.string.task_next_run)}: " +
                        Utils.formatTime(nextRun, "yyyy-MM-dd HH:mm"),
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            // 任务内容
            TextField(
                label = { Text(text = LangUtil.lang(R.string.task_content)) },
                value = content,
                onValueChange = { content = it },
                colors = TextFieldDefaults.colors(
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface
                ),
                placeholder = {
                    Text(
                        text = LangUtil.lang(R.string.task_content_placeholder),
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                minLines = 4,
                singleLine = false
            )

            // 启用开关
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = LangUtil.lang(R.string.task_enabled), fontSize = 14.sp)
                Spacer(modifier = Modifier.weight(1f))
                Switch(checked = enabled, onCheckedChange = { enabled = it })
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = LangUtil.lang(R.string.task_cron_tips),
                fontSize = 10.sp,
                lineHeight = 15.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )

            // 测试：不用等 cron 到点、也不用先保存，立刻把上面的内容当成一次触发发出去。
            // 新建任务时也给，方便先调通插件再决定要不要保存。
            Spacer(modifier = Modifier.height(20.dp))
            Button(
                onClick = { sendTest() },
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = 1.dp)
            ) {
                Text(text = LangUtil.lang(R.string.task_test), fontSize = 13.sp)
            }
            Text(
                text = LangUtil.lang(R.string.task_test_tips),
                fontSize = 10.sp,
                lineHeight = 15.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                modifier = Modifier.padding(top = 6.dp)
            )

            Spacer(modifier = Modifier.height(20.dp))
        }
    }
    }
}
