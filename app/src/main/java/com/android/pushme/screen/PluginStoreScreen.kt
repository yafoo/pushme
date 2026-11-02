package com.android.pushme.screen

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Card
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.android.pushme.MainActivity
import com.android.pushme.R
import com.android.pushme.Utils
import com.android.pushme.components.CodeView
import com.android.pushme.components.MeContent
import com.android.pushme.components.MeTopBarWithBack
import com.android.pushme.data.MessagePlugin
import com.android.pushme.model.AppDataBase
import com.android.pushme.model.PmfManager
import com.android.pushme.pluginstore.PluginInstaller
import com.android.pushme.pluginstore.PluginStore
import com.android.pushme.pluginstore.StoreIndex
import com.android.pushme.pluginstore.StorePlugin
import com.android.pushme.sendServiceBroadcast
import com.android.pushme.showToast
import com.android.pushme.utils.LangUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** 刷新失败时压在刷新图标上的黄点颜色 */
private val WarningDot = Color(0xFFFFC107)

/**
 * 插件商店。
 *
 * 数据来自官方仓库的 `index.json`（GitHub 主源 / Gitee 兜底，见 [PluginStore]）。
 *
 * ⚠️ 这里刻意**不做「一键全部更新」**：插件是任意 JS，能读 ENV、能联网，
 * 每一次安装都应该是用户明确看过来源和依赖之后的决定。
 */
@Composable
fun PluginStoreScreen(modifier: Modifier = Modifier) {
    val mainActivity = LocalActivity.current as MainActivity

    var loading by remember { mutableStateOf(true) }
    var refreshing by remember { mutableStateOf(false) }
    var index by remember { mutableStateOf<StoreIndex?>(null) }
    var error by remember { mutableStateOf("") }
    var fromCache by remember { mutableStateOf(false) }
    // 上次拉取是否失败 —— 失败就一直给刷新图标挂个黄点，直到某次成功
    var fetchFailed by remember { mutableStateOf(false) }
    var keyword by remember { mutableStateOf("") }
    // key -> 已安装的那条记录（只含来自商店的，pluginKey 非空的）
    var installed by remember { mutableStateOf<Map<String, MessagePlugin>>(emptyMap()) }
    var detail by remember { mutableStateOf<StorePlugin?>(null) }
    var installingKey by remember { mutableStateOf("") }

    fun reloadInstalled() {
        installed = AppDataBase.instance.getPluginDao().getList()
            .filter { it.pluginKey.isNotEmpty() }
            .associateBy { it.pluginKey }
    }

    /**
     * @param notify 是否是用户主动点刷新。进页面时的自动加载传 false ——
     *               否则每次进商店都会弹一次「已更新」，很吵。
     */
    fun load(notify: Boolean = false) {
        loading = true
        error = ""
        if (notify) refreshing = true
        PluginStore.fetchIndex { result, reason ->
            loading = false
            refreshing = false
            if (result != null) {
                index = result
                fromCache = false
                error = ""
                fetchFailed = false
                if (notify) {
                    // load() 不是 @Composable，只能用带 context 的重载
                    mainActivity.showToast(
                        LangUtil.lang(mainActivity, R.string.plugin_store_refresh_done)
                    )
                }
            } else {
                error = reason
                fetchFailed = true
                if (notify) {
                    mainActivity.showToast(
                        LangUtil.lang(mainActivity, R.string.plugin_store_refresh_failed, reason)
                    )
                }
                // 拉失败时退回缓存：至少让用户看到有哪些插件，只是装不了
                if (index == null) {
                    PluginStore.loadCache()?.let {
                        index = it
                        fromCache = true
                    }
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        reloadInstalled()
        // 先用缓存铺一下，避免进页面白屏等网络
        PluginStore.loadCache()?.let {
            index = it
            fromCache = true
        }
        load()
    }

    fun doInstall(item: StorePlugin, enableDeps: Boolean) {
        if (enableDeps) {
            // 用户点了「开启并安装」才动全局开关，绝不静默替用户开 PMF
            item.dependencyKeys().forEach { key ->
                if (PmfManager.pmfStateMap[key] == false) PmfManager.updatePmfState(key, true)
            }
            mainActivity.sendServiceBroadcast("refresh_plugins_pmf")
        }

        val isUpdate = installed[item.key] != null
        installingKey = item.key

        PluginInstaller.install(mainActivity, item) { ok, message ->
            installingKey = ""
            if (ok) {
                detail = null
                reloadInstalled()
                // 插件列表页读的是 ViewModel 里的快照，不刷新的话返回后看不到新插件
                mainActivity.pluginViewModel.getList()
                mainActivity.showToast(
                    LangUtil.lang(
                        mainActivity,
                        if (isUpdate) R.string.plugin_store_updated else R.string.plugin_store_success
                    )
                )
            } else {
                mainActivity.showToast(
                    LangUtil.lang(mainActivity, R.string.plugin_store_install_failed, message)
                )
            }
        }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            MeTopBarWithBack(
                title = LangUtil.lang(R.string.plugin_store),
                actions = {
                    IconButton(onClick = { if (!refreshing) load(notify = true) }) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                ImageVector.vectorResource(R.drawable.refresh),
                                contentDescription = LangUtil.lang(R.string.plugin_store_refresh),
                                modifier = Modifier.size(26.dp)
                            )
                            if (refreshing) {
                                // 正在拉：盖一圈进度，不然点了完全没反馈
                                CircularProgressIndicator(
                                    modifier = Modifier.size(30.dp),
                                    strokeWidth = 1.5.dp,
                                    color = LocalContentColor.current
                                )
                            } else if (fetchFailed) {
                                // 上次失败：右上角挂个黄点，直到刷新成功
                                Box(
                                    Modifier
                                        .align(Alignment.TopEnd)
                                        .offset(x = 1.dp, y = (-1).dp)
                                        .size(7.dp)
                                        .background(WarningDot, CircleShape)
                                )
                            }
                        }
                    }
                    IconButton(onClick = {
                        Utils.openUrl("https://github.com/yafoo/pushme-plugin", mainActivity, "blank")
                    }) {
                        Icon(
                            ImageVector.vectorResource(R.drawable.deployed_code),
                            contentDescription = LangUtil.lang(R.string.plugin_store_repo),
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
        val muted = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
        val plugins = index?.plugins ?: emptyList()
        val filtered = plugins.filter { item ->
            keyword.isBlank() ||
                    item.name.contains(keyword, true) ||
                    item.description.contains(keyword, true) ||
                    item.key.contains(keyword, true) ||
                    item.author.contains(keyword, true) ||
                    item.tags.any { it.contains(keyword, true) }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp)
        ) {
            TextField(
                value = keyword,
                onValueChange = { keyword = it },
                placeholder = {
                    Text(
                        text = LangUtil.lang(R.string.plugin_store_search),
                        fontSize = 13.sp,
                        color = muted
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

            if (fromCache && error.isNotEmpty()) {
                Text(
                    text = LangUtil.lang(
                        R.string.plugin_store_offline,
                        Utils.formatTime(PluginStore.cacheTime(), "MM-dd HH:mm")
                    ),
                    fontSize = 10.sp,
                    lineHeight = 14.sp,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            when {
                index == null && loading -> Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(modifier = Modifier.size(28.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.height(10.dp))
                        Text(LangUtil.lang(R.string.plugin_store_loading), fontSize = 12.sp, color = muted)
                    }
                }

                index == null -> Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = LangUtil.lang(R.string.plugin_store_failed, error),
                        fontSize = 12.sp,
                        lineHeight = 18.sp,
                        color = muted
                    )
                }

                filtered.isEmpty() -> Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(LangUtil.lang(R.string.plugin_store_empty), fontSize = 12.sp, color = muted)
                }

                else -> LazyColumn(
                    modifier = Modifier.padding(top = 6.dp),
                    contentPadding = PaddingValues(vertical = 4.dp)
                ) {
                    items(filtered, key = { it.key }) { item ->
                        StoreItem(
                            item = item,
                            installedPlugin = installed[item.key],
                            installing = installingKey == item.key,
                            modifier = Modifier.animateItem(fadeInSpec = null, fadeOutSpec = null),
                            onClick = { detail = item }
                        )
                    }
                }
            }
        }
    }
    }

    detail?.let { item ->
        StoreDetailDialog(
            item = item,
            installedPlugin = installed[item.key],
            installing = installingKey == item.key,
            onDismiss = { detail = null },
            onConfirm = { enableDeps -> doInstall(item, enableDeps) }
        )
    }
}

/**
 * 列表行。
 *
 * 不显示作者 —— 商店里的插件全部来自官方仓库，这行信息只会占地方。
 * 真正需要用户注意的「依赖了哪些内置方法 / 要填哪些环境变量」用小标签顶出来，
 * 缺的（PMF 没开）标红。
 */
@Composable
private fun StoreItem(
    item: StorePlugin,
    installedPlugin: MessagePlugin?,
    installing: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val muted = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
    val updatable = installedPlugin != null && installedPlugin.versionCode < item.versionCode

    Card(
        shape = RoundedCornerShape(3.dp),
        modifier = modifier
            .padding(bottom = 10.dp)
            .fillMaxWidth(),
        backgroundColor = MaterialTheme.colorScheme.surface
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = item.name.ifBlank { item.key },
                        fontSize = 14.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(text = "v${item.version}", fontSize = 10.sp, color = muted)
                }

                if (item.description.isNotBlank()) {
                    Spacer(Modifier.height(3.dp))
                    Text(
                        text = item.description,
                        fontSize = 11.sp,
                        lineHeight = 15.sp,
                        color = muted,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                if (item.dependencies.isNotEmpty() || item.env.isNotEmpty()) {
                    Spacer(Modifier.height(5.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        item.dependencies.forEach { dep ->
                            val enabled = PmfManager.pmfStateMap[dep.removePrefix("PMF.").trim()] != false
                            TagChip(text = dep, warn = !enabled)
                        }
                        item.env.forEach { key ->
                            TagChip(text = key, mono = true)
                        }
                    }
                }
            }

            Spacer(Modifier.width(8.dp))

            when {
                installing -> Text(
                    text = LangUtil.lang(R.string.plugin_store_installing),
                    fontSize = 10.sp,
                    color = muted
                )

                installedPlugin == null -> SmallActionButton(
                    text = LangUtil.lang(R.string.plugin_store_install),
                    onClick = onClick
                )

                updatable -> SmallActionButton(
                    text = LangUtil.lang(R.string.plugin_store_update),
                    onClick = onClick
                )

                else -> Text(
                    text = LangUtil.lang(R.string.plugin_store_installed),
                    fontSize = 10.sp,
                    color = muted
                )
            }
        }
    }
}

/**
 * 小标签。依赖的方法用主题色，未开启的用 error 色；环境变量用等宽字体区分。
 * 尺寸照项目既有约定（9sp / 圆角 3dp），别改成 Material 的 Chip（自带 32dp 高度，太重）。
 */
@Composable
private fun TagChip(text: String, warn: Boolean = false, mono: Boolean = false) {
    val color = if (warn) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
    Text(
        text = text,
        fontSize = 9.sp,
        lineHeight = 11.sp,
        color = color,
        fontFamily = if (mono) FontFamily.Monospace else FontFamily.Default,
        maxLines = 1,
        modifier = Modifier
            .background(color.copy(alpha = 0.10f), RoundedCornerShape(3.dp))
            .padding(horizontal = 5.dp, vertical = 2.dp)
    )
}

/**
 * 详情弹窗。安装前把「版本 / 依赖 / 需要填的 ENV / 源码」摊开给用户看 ——
 * 这是这套功能里唯一的安全提示位，别省。
 *
 * 用 [Dialog] 而不是 `AlertDialog`：`AlertDialog` 的 text 槽位自带滚动，
 * 里面再放一个能滚的源码区就会互相抢手势。这里整体只有一个滚动区。
 */
@Composable
private fun StoreDetailDialog(
    item: StorePlugin,
    installedPlugin: MessagePlugin?,
    installing: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (enableDeps: Boolean) -> Unit
) {
    val muted = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
    val missing = item.dependencyKeys().filter { PmfManager.pmfStateMap[it] == false }
    val updatable = installedPlugin != null && installedPlugin.versionCode < item.versionCode

    var source by remember(item.key) { mutableStateOf("") }
    var sourceLoading by remember(item.key) { mutableStateOf(true) }
    var sourceError by remember(item.key) { mutableStateOf("") }

    // 打开弹窗就把源码拉下来。走和安装同一套归一化，保证「看到的就是装进去的」
    LaunchedEffect(item.key) {
        val result = withContext(Dispatchers.IO) {
            runCatching { PluginInstaller.normalize(PluginStore.fetchFile(item.file)) }
        }
        sourceLoading = false
        result.onSuccess { source = it }
            .onFailure { sourceError = it.message ?: it.toString() }
    }

    val actionLabel = when {
        installing -> LangUtil.lang(R.string.plugin_store_installing)
        installedPlugin == null || updatable ->
            if (missing.isEmpty()) {
                LangUtil.lang(
                    if (installedPlugin == null) R.string.plugin_store_install else R.string.plugin_store_update
                )
            } else {
                LangUtil.lang(R.string.plugin_store_enable_and_install)
            }

        else -> LangUtil.lang(R.string.plugin_store_installed)
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier
                .padding(horizontal = 20.dp, vertical = 40.dp)
                .fillMaxWidth()
                .fillMaxHeight(0.86f)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = item.name.ifBlank { item.key },
                    fontSize = 16.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(Modifier.height(10.dp))

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(
                        text = "${LangUtil.lang(R.string.plugin_store_version)}: ${item.version}",
                        fontSize = 12.sp,
                        color = muted
                    )

                    if (item.description.isNotBlank()) {
                        Spacer(Modifier.height(8.dp))
                        Text(text = item.description, fontSize = 13.sp, lineHeight = 18.sp)
                    }

                    if (item.dependencies.isNotEmpty()) {
                        Spacer(Modifier.height(10.dp))
                        // 标题和标签同一行，标签多了自己换行 —— 少占两行高度
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = LangUtil.lang(R.string.plugin_store_deps),
                                fontSize = 12.sp
                            )
                            Spacer(Modifier.width(6.dp))
                            FlowRow(
                                modifier = Modifier.weight(1f),
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                item.dependencies.forEach { dep ->
                                    val key = dep.removePrefix("PMF.").trim()
                                    val enabled = PmfManager.pmfStateMap[key] != false
                                    TagChip(text = dep, warn = !enabled)
                                }
                            }
                        }
                    }

                    if (item.env.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = LangUtil.lang(R.string.plugin_store_env),
                                fontSize = 12.sp
                            )
                            Spacer(Modifier.width(6.dp))
                            FlowRow(
                                modifier = Modifier.weight(1f),
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                item.env.forEach { key -> TagChip(text = key, mono = true) }
                            }
                        }
                    }

                    // 编辑页允许改商店插件，改过之后更新会静默覆盖 —— 必须先说清楚
                    if (installedPlugin != null && updatable && PluginInstaller.isModified(installedPlugin)) {
                        Spacer(Modifier.height(10.dp))
                        Text(
                            text = LangUtil.lang(R.string.plugin_store_local_modified),
                            fontSize = 11.sp,
                            lineHeight = 15.sp,
                            color = MaterialTheme.colorScheme.error
                        )
                    }

                    Spacer(Modifier.height(12.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = LangUtil.lang(R.string.plugin_store_source),
                            fontSize = 12.sp
                        )
                        if (source.isNotBlank()) {
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = LangUtil.lang(
                                    R.string.plugin_store_source_info,
                                    source.lines().size
                                ),
                                fontSize = 9.sp,
                                color = muted
                            )
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    when {
                        sourceLoading -> Text(
                            text = LangUtil.lang(R.string.plugin_store_source_loading),
                            fontSize = 10.sp,
                            color = muted
                        )

                        sourceError.isNotEmpty() -> Text(
                            text = LangUtil.lang(R.string.plugin_store_source_failed, sourceError),
                            fontSize = 10.sp,
                            lineHeight = 14.sp,
                            color = MaterialTheme.colorScheme.error
                        )

                        else -> CodeView(
                            code = source,
                            scrollable = false,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                Spacer(Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text(text = LangUtil.lang(R.string.cancel), fontSize = 12.sp)
                    }
                    TextButton(
                        enabled = !installing && (installedPlugin == null || updatable),
                        onClick = { onConfirm(missing.isNotEmpty()) }
                    ) {
                        Text(text = actionLabel, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

/** 行内小按钮，尺寸照项目既有约定（`HomeScreen.PermissionButton` / `DrawerItemButton`） */
@Composable
private fun SmallActionButton(text: String, onClick: () -> Unit) {
    Button(
        modifier = Modifier.defaultMinSize(minWidth = 1.dp, minHeight = 1.dp),
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
        onClick = onClick
    ) {
        Text(text = text, fontSize = 10.sp, lineHeight = 10.sp)
    }
}
