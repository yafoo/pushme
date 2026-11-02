package com.android.pushme.screen

import android.content.pm.PackageManager
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import com.android.pushme.MainActivity
import com.android.pushme.R
import com.android.pushme.components.MeContent
import com.android.pushme.components.MeTopBarWithBack
import com.android.pushme.notify.InstalledApp
import com.android.pushme.notify.InstalledAppLoader
import com.android.pushme.notify.NotifyFilter
import com.android.pushme.sendNotifyConfigBroadcast
import com.android.pushme.showToast
import com.android.pushme.utils.LangUtil
import com.android.pushme.utils.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 应用选择页（应用过滤名单）。
 *
 * 列出设备上可见的应用，让用户勾选，并区分系统应用。
 *
 * 设计要点：
 * - **勾选即时保存**到 `Settings.notifyAppFilterList`，没有保存按钮。
 *   过滤页那边只负责展示「已选 N 个」，不再持有这份数据，避免两边各存一份导致互相覆盖。
 * - 离开页面时才广播一次 `sendNotifyConfigBroadcast()`，让监听服务刷新规则缓存；
 *   每勾一下就广播会导致监听服务反复做 DataStore 读取，没必要。
 * - 应用图标按可见项懒加载（见 [rememberAppIcon]），不在这里一次性解码全部图标。
 *
 * ⚠️ 列表可能不完整：Android 11+ 的软件包可见性过滤会挡掉一部分应用，
 * 详见 [InstalledAppLoader] 的注释。
 */
@Composable
fun NotifyAppFilterScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val mainActivity = LocalActivity.current as MainActivity
    val pm = context.packageManager

    var loading by remember { mutableStateOf(true) }
    var apps by remember { mutableStateOf(emptyList<InstalledApp>()) }
    var selected by remember { mutableStateOf(NotifyFilter.parsePackages(Settings.notifyAppFilterList).toSet()) }
    var keyword by remember { mutableStateOf("") }
    var showSystem by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        apps = withContext(Dispatchers.IO) { InstalledAppLoader.load(context) }
        loading = false
    }

    DisposableEffect(Unit) {
        onDispose { mainActivity.sendNotifyConfigBroadcast() }
    }

    // 已选的应用排到最前面（组内保持 InstalledAppLoader 的原始顺序，sortedWith 是稳定排序）。
    // 依赖 selected，勾选/取消勾选后立刻重排。
    val visible = remember(apps, keyword, showSystem, selected) {
        buildVisible(apps, keyword, showSystem, selected)
    }

    // 已选里有多少是系统应用：它们只在「显示系统应用」打开时出现在列表中，
    // 关掉开关后看不见却依然生效，所以要单独统计给用户看
    val selectedSystemCount = remember(apps, selected) {
        apps.count { it.isSystem && it.packageName in selected }
    }

    val listState = rememberLazyListState()

    /**
     * 只在「本来就在顶部」时把视口钉在顶部。
     *
     * 勾选/取消勾选会让列表重排，第 0 行换成另一个应用；而 LazyColumn 的滚动位置锚定在
     * 「原来那个首项」上，会把新置顶的那一行顶到屏幕上方 —— 所以停在顶部时要把位置重置到第 0 行，
     * 让「顶部」依然是顶部。
     *
     * 反过来，**已经滚下去了就什么也别做**：交给 LazyColumn 的锚定，视口原地不动。
     * 别做「把被点的那一行挪回原位」的补偿 —— 那一行一旦跑到视口上方，补偿就等于把整屏
     * 往上顶（勾选往上跳、取消勾选往下跳），比什么都不做难受得多。
     */
    fun pinTopIfAtTop() {
        if (listState.firstVisibleItemIndex == 0) {
            // 保留像素偏移：停在顶部但微微上滑了一点点时，别顺手弹回去
            listState.requestScrollToItem(0, listState.firstVisibleItemScrollOffset)
        }
    }

    fun toggle(packageName: String, checked: Boolean) {
        val next = if (checked) selected + packageName else selected - packageName
        selected = next
        Settings.notifyAppFilterList = next.joinToString("\n")
        pinTopIfAtTop()
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            MeTopBarWithBack(
                title = LangUtil.lang(R.string.notify_app_filter_select),
                actions = {
                    if (selected.isNotEmpty()) {
                        IconButton(onClick = {
                            selected = emptySet()
                            Settings.notifyAppFilterList = ""
                            mainActivity.showToast(LangUtil.lang(mainActivity, R.string.notify_app_filter_clear_success))
                            // 清空后整张列表排回原始顺序：在顶部就继续钉在顶部，滚下去了就别动
                            pinTopIfAtTop()
                        }) {
                            Text(text = LangUtil.lang(R.string.notify_app_filter_clear), fontSize = 12.sp)
                        }
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
                .fillMaxSize()
                .padding(horizontal = 12.dp),
        ) {
            TextField(
                value = keyword,
                onValueChange = {
                    keyword = it
                    // 结果集变了，从第一条看起
                    listState.requestScrollToItem(0, 0)
                },
                placeholder = {
                    Text(
                        text = LangUtil.lang(R.string.notify_app_filter_search),
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                },
                colors = TextFieldDefaults.colors(
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search)
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(40.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (selected.isEmpty()) {
                        LangUtil.lang(R.string.notify_app_filter_none)
                    } else {
                        LangUtil.lang(R.string.notify_app_filter_count_system, selected.size, selectedSystemCount)
                    },
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
                Spacer(modifier = Modifier.weight(1f))
                Text(text = LangUtil.lang(R.string.notify_app_filter_show_system), fontSize = 12.sp)
                Spacer(modifier = Modifier.width(4.dp))
                Switch(
                    checked = showSystem,
                    onCheckedChange = { checked ->
                        showSystem = checked
                        // 打开后已选的系统应用会排到最前面，同理会把新首项顶到屏幕上方；
                        // 停在顶部就钉住顶部，滚下去了就原地不动
                        pinTopIfAtTop()
                    }
                )
            }

            when {
                loading -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = LangUtil.lang(R.string.notify_app_filter_loading),
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }

                visible.isEmpty() -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = LangUtil.lang(R.string.notify_app_filter_empty),
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }

                else -> LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                    items(visible, key = { it.packageName }) { app ->
                        AppRow(
                            pm = pm,
                            app = app,
                            checked = selected.contains(app.packageName),
                            onCheckedChange = { toggle(app.packageName, it) }
                        )
                    }
                    item {
                        Text(
                            text = LangUtil.lang(R.string.notify_app_filter_self_tips),
                            fontSize = 10.sp,
                            lineHeight = 14.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                            modifier = Modifier.padding(vertical = 12.dp)
                        )
                    }
                }
            }
        }
    }
    }
}

/**
 * 列表的显示顺序：先按「显示系统应用 / 关键词」过滤，再把已选的排到最前面。
 *
 * `sortedWith` 是稳定排序，所以已选区、未选区各自都保持 [InstalledAppLoader] 的原始顺序
 * （普通应用在前 + 本地化排序）。
 */
private fun buildVisible(
    apps: List<InstalledApp>,
    keyword: String,
    showSystem: Boolean,
    selected: Set<String>
): List<InstalledApp> {
    val kw = keyword.trim()
    return apps
        .filter { app ->
            (showSystem || !app.isSystem) &&
                (kw.isEmpty() || app.label.contains(kw, true) || app.packageName.contains(kw, true))
        }
        .sortedWith(compareBy { if (it.packageName in selected) 0 else 1 })
}

@Composable
private fun AppRow(
    pm: PackageManager,
    app: InstalledApp,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(value = checked, onValueChange = onCheckedChange, role = Role.Checkbox)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        rememberAppIcon(pm, app.packageName)?.let { icon ->
            Image(
                bitmap = icon,
                contentDescription = null,
                modifier = Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(6.dp))
            )
            Spacer(modifier = Modifier.width(10.dp))
        }

        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = app.label,
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (app.isSystem) {
                    Spacer(modifier = Modifier.width(6.dp))
                    SystemTag()
                }
            }
            Text(
                text = app.packageName,
                fontSize = 10.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
            )
        }

        Spacer(modifier = Modifier.width(8.dp))
        Checkbox(
            checked = checked,
            onCheckedChange = null,
            colors = CheckboxDefaults.colors(checkedColor = MaterialTheme.colorScheme.primary)
        )
    }
}

/** 「系统」小标签，用来区分系统应用 */
@Composable
private fun SystemTag() {
    Text(
        text = LangUtil.lang(R.string.notify_app_filter_system_tag),
        fontSize = 9.sp,
        lineHeight = 9.sp,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
        modifier = Modifier
            .clip(RoundedCornerShape(3.dp))
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f))
            .padding(horizontal = 4.dp, vertical = 2.dp)
    )
}

/**
 * 应用图标懒加载。
 *
 * `getApplicationIcon` 要解码 Drawable，放在主线程会卡；而列表上百项时
 * 一次性全解码又浪费内存 —— LazyColumn 只会组合可见项，所以按项加载刚刚好。
 */
@Composable
private fun rememberAppIcon(pm: PackageManager, packageName: String): ImageBitmap? {
    val icon by produceState<ImageBitmap?>(initialValue = null, packageName) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                pm.getApplicationIcon(packageName).toBitmap(96, 96).asImageBitmap()
            }.getOrNull()
        }
    }
    return icon
}
