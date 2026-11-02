package com.android.pushme.screen

import android.os.Handler
import android.os.Looper
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.android.pushme.LocalNavController
import com.android.pushme.MainActivity
import com.android.pushme.R
import com.android.pushme.components.MeContent
import com.android.pushme.components.MeTopBarWithBack
import com.android.pushme.components.OnResumeEffect
import com.android.pushme.components.RadioListNew
import com.android.pushme.notify.NotifyFilter
import com.android.pushme.sendNotifyConfigBroadcast
import com.android.pushme.showToast
import com.android.pushme.utils.LangUtil
import com.android.pushme.utils.Settings

/**
 * 系统通知过滤设置页。
 *
 * 只负责过滤规则；监听开关在「服务设置 - 系统通知监听」里，
 * 而「监听系统通知」的权限入口在「权限设置」分组里（本页不再重复放权限设置）。
 *
 * ⚠️ 应用名单不在这里编辑：应用选择是独立一页（`notify_app_filter`），
 * 勾选即时落盘。本页只展示「已选 N 个」，并且**不参与保存**，
 * 否则两边各存一份会互相覆盖。
 */
@Composable
fun NotifyFilterScreen(modifier: Modifier = Modifier) {
    val focusManager = LocalFocusManager.current
    val mainActivity = LocalActivity.current as MainActivity
    val navController = LocalNavController.current
    var saveClick by remember { mutableStateOf({}) }

    // 已选应用数量：应用选择页是即时保存的，返回本页后重新读一次
    var selectedCount by remember { mutableStateOf(NotifyFilter.parsePackages(Settings.notifyAppFilterList).size) }
    OnResumeEffect {
        Handler(Looper.getMainLooper()).postDelayed({
            selectedCount = NotifyFilter.parsePackages(Settings.notifyAppFilterList).size
        }, 300)
    }

    val modeOptions = Settings.getSettingOptions(
        listOf(NotifyFilter.MODE_CLOSE, NotifyFilter.MODE_WHITE, NotifyFilter.MODE_BLACK),
        R.array.notify_app_filter_options
    )
    val onOffOptions = Settings.getOnOffOptions()

    var filterMode by remember { mutableStateOf(Settings.notifyAppFilterMode) }
    var limit by remember { mutableStateOf(Settings.notifyLimit) }
    var omit by remember { mutableStateOf(Settings.notifyOmit) }
    var ignoreOngoing by remember { mutableStateOf(Settings.notifyIgnoreOngoing) }
    var ignoreEmpty by remember { mutableStateOf(Settings.notifyIgnoreEmpty) }

    LaunchedEffect(Unit) {
        saveClick = {
            Settings.notifyAppFilterMode = filterMode
            Settings.notifyLimit = limit
            Settings.notifyOmit = omit
            Settings.notifyIgnoreOngoing = ignoreOngoing
            Settings.notifyIgnoreEmpty = ignoreEmpty

            mainActivity.sendNotifyConfigBroadcast()
            mainActivity.showToast(LangUtil.lang(mainActivity, R.string.save_success))
        }
    }

    Scaffold(
        modifier = modifier.clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) {
            focusManager.clearFocus()
        },
        topBar = {
            MeTopBarWithBack(
                title = LangUtil.lang(R.string.notify_filter),
                actions = {
                    IconButton(onClick = saveClick) {
                        Icon(ImageVector.vectorResource(R.drawable.save), contentDescription = null, Modifier.size(26.dp))
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
            TextField(
                label = { Text(text = LangUtil.lang(R.string.notify_limit)) },
                value = limit,
                onValueChange = { limit = it },
                colors = TextFieldDefaults.colors(
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface
                ),
                placeholder = {
                    Text(
                        text = LangUtil.lang(R.string.notify_limit_placeholder),
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
            )

            TextField(
                label = { Text(text = LangUtil.lang(R.string.notify_omit)) },
                value = omit,
                onValueChange = { omit = it },
                colors = TextFieldDefaults.colors(
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface
                ),
                placeholder = {
                    Text(
                        text = LangUtil.lang(R.string.notify_omit_placeholder),
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
            )

            Spacer(Modifier.height(10.dp))

            RadioListNew(
                modifier = Modifier.height(50.dp),
                modifierItem = Modifier.padding(start = 2.dp, end = 3.dp),
                title = LangUtil.lang(R.string.notify_ignore_ongoing) + "：",
                list = onOffOptions,
                selectedValue = onOffOptions.entries.find { it.key == ignoreOngoing } ?: onOffOptions.entries.first(),
                onClick = { ignoreOngoing = it.key }
            )

            RadioListNew(
                modifier = Modifier.height(50.dp),
                modifierItem = Modifier.padding(start = 2.dp, end = 3.dp),
                title = LangUtil.lang(R.string.notify_ignore_empty) + "：",
                list = onOffOptions,
                selectedValue = onOffOptions.entries.find { it.key == ignoreEmpty } ?: onOffOptions.entries.first(),
                onClick = { ignoreEmpty = it.key }
            )

            // 应用过滤模式
            RadioListNew(
                modifier = Modifier.height(50.dp),
                modifierItem = Modifier.padding(start = 2.dp, end = 3.dp),
                title = LangUtil.lang(R.string.notify_app_filter_mode) + "：",
                list = modeOptions,
                selectedValue = modeOptions.entries.find { it.key == filterMode } ?: modeOptions.entries.first(),
                onClick = { filterMode = it.key }
            )

            // 已选应用：勾选在独立的应用选择页里做，这里只显示数量 + 入口
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = LangUtil.lang(R.string.notify_app_filter_list) + ": ",
                    fontSize = 14.sp
                )
                Text(
                    text = if(selectedCount == 0) {
                        LangUtil.lang(R.string.notify_app_filter_none)
                    } else {
                        LangUtil.lang(R.string.notify_app_filter_count, selectedCount)
                    },
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(0.6f)
                )
                Spacer(modifier = Modifier.weight(1f))
                // 小按钮：与 HomeScreen.PermissionButton / DrawerItemButton 同一套写法
                Button(
                    modifier = Modifier.defaultMinSize(minWidth = 1.dp, minHeight = 1.dp),
                    contentPadding = PaddingValues(horizontal = 5.dp, vertical = 3.dp),
                    onClick = { navController.navigate("notify_app_filter") }
                ) {
                    Text(
                        text = LangUtil.lang(R.string.notify_app_filter_select),
                        fontSize = 10.sp,
                        lineHeight = 10.sp
                    )
                }
            }

            Spacer(Modifier.height(10.dp))

            Text(
                text = LangUtil.lang(R.string.notify_app_filter_list_tips),
                fontSize = 10.sp,
                lineHeight = 14.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(0.6f)
            )

            Spacer(Modifier.height(10.dp))

            Text(
                text = LangUtil.lang(R.string.notify_filter_tips),
                fontSize = 10.sp,
                lineHeight = 14.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(0.6f)
            )
        }
    }
    }
}
