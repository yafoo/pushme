package com.android.pushme.screen

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.android.pushme.LocalNavController
import com.android.pushme.MainActivity
import com.android.pushme.R
import com.android.pushme.components.MeContent
import com.android.pushme.components.MeTopBarWithBack
import com.android.pushme.components.OnResumeEffect
import com.android.pushme.components.SmallSwitch
import com.android.pushme.task.TaskInfo
import com.android.pushme.task.TaskScheduler
import com.android.pushme.task.TaskStore
import com.android.pushme.utils.LangUtil
import com.android.pushme.utils.Settings
import com.android.pushme.Utils

/**
 * 定时任务列表页。
 *
 * 只做展示、启停和排序；任务的增删改在 [TaskEditScreen] 里。
 * 启停和排序都是即时生效的：改完立刻落盘（启停还会立刻重排闹钟），不需要保存按钮。
 *
 * 样式对齐插件列表（`PluginScreen` / `PluginEnvScreen`）：
 * `Card` 包一行，左侧内容区可点进编辑页，右侧是「上移 / 下移 / 开关」三个 40dp 控件。
 *
 * ⚠️ 列表数据每次都从 [TaskStore] 重新读，不缓存到 ViewModel ——
 * 任务数量很少，读一次就是一次 JSON 反序列化，成本可以忽略；
 * 缓存反而要处理「编辑页改了、列表页没刷新」的一致性问题。
 */
@Composable
fun TaskScreen(modifier: Modifier = Modifier) {
    val navController = LocalNavController.current
    val mainActivity = LocalActivity.current as MainActivity

    var tasks by remember { mutableStateOf(TaskStore.load()) }

    // 从编辑页返回时重新读一次（编辑页是即时保存的）
    OnResumeEffect {
        tasks = TaskStore.load()
        // 任务的增删改都发生在应用内，不会触发 onStart，这里顺手刷新首页 TSK 标签
        mainActivity.refreshTaskTag()
    }

    fun toggle(task: TaskInfo, enabled: Boolean) {
        val list = TaskStore.load()
        val target = list.firstOrNull { it.id == task.id } ?: return
        target.enabled = enabled
        TaskStore.save(list)

        tasks = list
        // 启停要立刻反映到闹钟上：启用 → 排上；停用 → 撤掉
        TaskScheduler.scheduleTask(mainActivity, target)
        // 启用状态变了，首页 TSK 标签可能要从亮变灭（或反过来）
        mainActivity.refreshTaskTag()
    }

    fun sort(task: TaskInfo, direction: String) {
        // 排序只影响展示顺序：每个任务一个闹钟、requestCode = task.id，
        // 换位置跟闹钟无关，不用重排。move 返回 null 表示已经到顶/到底。
        TaskStore.move(task.id, direction)?.let { tasks = it }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            MeTopBarWithBack(
                title = LangUtil.lang(R.string.task),
                actions = {
                    // ⚠️ 这里不能用 TextButton：它的默认文字色是 colorScheme.primary，
                    // 而顶栏背景色正好也是 colorScheme.primary（Theme.kt），青底青字＝看不见。
                    // 用 IconButton 才会继承顶栏的 actionIconContentColor（onPrimary）。
                    // 图标用 add，与「新建便签」「新建环境变量」保持一致。
                    IconButton(onClick = { navController.navigate("task_edit/0") }) {
                        Icon(
                            ImageVector.vectorResource(R.drawable.add),
                            contentDescription = LangUtil.lang(R.string.task_add),
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
        if (tasks.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = LangUtil.lang(R.string.task_empty),
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(10.dp)
            ) {
                itemsIndexed(tasks, key = { _, task -> task.id }) { index, task ->
                    Card(
                        shape = RoundedCornerShape(3.dp),
                        modifier = Modifier
                            .padding(bottom = 10.dp)
                            .fillMaxWidth()
                            .animateItem(fadeInSpec = null, fadeOutSpec = null),
                        backgroundColor = MaterialTheme.colorScheme.surface
                    ) {
                        TaskItem(
                            index = index + 1,
                            length = tasks.size,
                            task = task,
                            onToggle = { toggle(task, it) },
                            onSort = { sort(task, it) },
                            onClick = { navController.navigate("task_edit/${task.id}") }
                        )
                    }
                }

                // 总开关关闭时，任务照常保留但不会触发，这里明确提示一下
                if (Settings.taskState != "开启") {
                    item {
                        Text(
                            text = LangUtil.lang(R.string.task_tips),
                            fontSize = 10.sp,
                            lineHeight = 14.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                            modifier = Modifier.padding(horizontal = 2.dp, vertical = 4.dp)
                        )
                    }
                }
            }
        }
    }
    }
}

/**
 * 一行任务。
 *
 * 布局与 `PluginItem` / `EnvItem` 保持一致：左侧内容区 `weight(1f)` 且可点击，
 * 右侧固定三个 40dp 控件（上移 / 下移 / 开关），到顶或到底时箭头置灰。
 */
@Composable
private fun TaskItem(
    index: Int,
    length: Int,
    task: TaskInfo,
    onToggle: (Boolean) -> Unit,
    onSort: (String) -> Unit,
    onClick: () -> Unit
) {
    val textColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8F)
    val disableColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3F)
    val upColor = if(index == 1) disableColor else textColor
    val downColor = if(index == length) disableColor else textColor

    // 只在表达式 / 启用状态变化时重算，避免每次重组都跑一遍 cron 求值
    val nextRun = remember(task.cron, task.enabled) {
        if (task.enabled) task.nextRun() else null
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top
    ) {
        Column(
            Modifier
                .weight(1f)
                .clickable(onClick = onClick)
                .padding(vertical = 10.dp)
                .padding(start = 10.dp, end = 5.dp)
        ) {
            Text(
                text = task.name.ifBlank { task.cron },
                fontSize = 14.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Text(
                text = task.cron,
                fontSize = 11.sp,
                lineHeight = 15.sp,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.85f),
                modifier = Modifier.padding(top = 2.dp)
            )

            Text(
                text = "${LangUtil.lang(R.string.task_next_run)}: " +
                    (nextRun?.let { Utils.formatTime(it, "yyyy-MM-dd HH:mm") }
                        ?: LangUtil.lang(R.string.task_cron_never)),
                fontSize = 10.sp,
                lineHeight = 14.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                modifier = Modifier.padding(top = 4.dp)
            )

            Text(
                text = "${LangUtil.lang(R.string.task_last_run)}: " +
                    (if (task.lastRun > 0) Utils.formatTime(task.lastRun, "yyyy-MM-dd HH:mm")
                    else LangUtil.lang(R.string.task_never_run)),
                fontSize = 10.sp,
                lineHeight = 14.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)
            )
        }

        Spacer(Modifier.width(2.dp))
        IconButton(
            modifier = Modifier.padding(top = 2.dp).size(40.dp),
            onClick = { if(index != 1) onSort("up") }
        ) {
            Icon(ImageVector.vectorResource(R.drawable.keyboard_arrow_up), tint = upColor, modifier = Modifier.size(28.dp), contentDescription = null)
        }

        Spacer(Modifier.width(2.dp))
        IconButton(
            modifier = Modifier.padding(top = 2.dp).size(40.dp),
            onClick = { if(index != length) onSort("down") }
        ) {
            Icon(ImageVector.vectorResource(R.drawable.keyboard_arrow_down), tint = downColor, modifier = Modifier.size(28.dp), contentDescription = null)
        }

        Spacer(Modifier.width(2.dp))
        SmallSwitch(
            modifier = Modifier.padding(top = 2.dp, end = 8.dp),
            checked = task.enabled,
            onCheckedChange = onToggle
        )
    }
}
