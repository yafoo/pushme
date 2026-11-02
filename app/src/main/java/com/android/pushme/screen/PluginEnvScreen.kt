package com.android.pushme.screen

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.android.pushme.MainActivity
import com.android.pushme.R
import com.android.pushme.components.EnvEditDialog
import com.android.pushme.components.MeContent
import com.android.pushme.components.MeTopBarWithBack
import com.android.pushme.components.SmallSwitch
import com.android.pushme.model.EnvData
import com.android.pushme.model.EnvManager
import com.android.pushme.sendServiceBroadcast
import com.android.pushme.showToast
import com.android.pushme.utils.LangUtil

@Composable
fun PluginEnvScreen(modifier: Modifier = Modifier) {
    val mainActivity = LocalActivity.current as MainActivity
    var showEnvEditDialog by remember { mutableStateOf(false) }
    var envData by remember { mutableStateOf(EnvData()) }

    Scaffold(
        modifier = modifier,
        topBar = {
            MeTopBarWithBack(
                title = LangUtil.lang(R.string.plugin_env),
                actions = {
                    IconButton(onClick = {
                        envData = EnvData()
                        showEnvEditDialog = true
                    }) {
                        Icon(ImageVector.vectorResource(R.drawable.add), contentDescription = null, Modifier.size(26.dp))
                    }
                }
            ) },
    ) { innerPadding -> MeContent(
        modifier = Modifier
            .padding(innerPadding)
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05F))
    ) {
        val envList = EnvManager.envDataList

        LazyColumn(
            contentPadding = PaddingValues(10.dp)
        ) {
            items(envList.size, { envList[it].key }) {
                Card(
                    shape = RoundedCornerShape(3.dp),
                    modifier = Modifier
                        .padding(bottom = 10.dp)
                        .fillMaxWidth().animateItem(fadeInSpec = null, fadeOutSpec = null),
                    backgroundColor = MaterialTheme.colorScheme.surface
                ) {
                    EnvItem(
                        index = it + 1,
                        length = envList.size,
                        env = envList[it],
                        sort = {
                            i, sort -> EnvManager.sortEnvData(i, sort)
                        },
                        update = { update ->
                            EnvManager.updateEnvData(update)
                            mainActivity.sendServiceBroadcast("refresh_plugins_env")
                            mainActivity.showToast(LangUtil.lang(mainActivity, if(update.enabled) R.string.enable_success else R.string.disable_success))
                        },
                        view = {editEnvData ->
                            envData = editEnvData
                            showEnvEditDialog = true
                        }
                    )
                }
            }
        }

        // 变量编辑框
        EnvEditDialog(
            modifier = Modifier,
            show = showEnvEditDialog,
            envData = envData,
            onDismissRequest = { showEnvEditDialog = false },
            onDelete = {
                EnvManager.delEnvData(it)
                showEnvEditDialog = false
                mainActivity.showToast(LangUtil.lang(mainActivity, R.string.delete_success))
                mainActivity.sendServiceBroadcast("refresh_plugins_env")
            }
        ) {
            if(it.order == 0) {
                if(EnvManager.addEnvData(it)) {
                    showEnvEditDialog = false
                    mainActivity.showToast(LangUtil.lang(mainActivity, R.string.add_success))
                    mainActivity.sendServiceBroadcast("refresh_plugins_env")
                } else {
                    mainActivity.showToast(LangUtil.lang(mainActivity, R.string.plugin_env_name_exists))
                }
            } else {
                if(EnvManager.updateEnvDataByOrder(it)) {
                    showEnvEditDialog = false
                    mainActivity.showToast(LangUtil.lang(mainActivity, R.string.save_success))
                    mainActivity.sendServiceBroadcast("refresh_plugins_env")
                } else {
                    mainActivity.showToast(LangUtil.lang(mainActivity, R.string.plugin_env_name_exists))
                }
            }

        }
    }

    }
}

@Composable
fun EnvItem(index: Int, length: Int, modifier: Modifier = Modifier, env: EnvData, sort: (i: Int, sort: String) -> Unit, update: (env: EnvData) -> Unit, view: (env: EnvData) -> Unit) {
    val textColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8F)
    val disableColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3F)
    val upColor = if(index == 1) disableColor else textColor
    val downColor = if(index == length) disableColor else textColor

    Row(
        modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            Modifier
            .weight(1f)
            .clickable(
                onClick = {
                    view(env)
                }
            )
            .padding(vertical = 10.dp)
            .padding(start = 10.dp, end = 5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "ENV.",
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.primary,
                fontSize = 10.sp,
                lineHeight = 10.sp,
                modifier = Modifier.padding(top = 4.dp),
            )
            Text(
                text = env.key,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "=",
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2F),
                modifier = Modifier.padding(horizontal = 3.dp)
            )
            Text(
                text = env.value,
                modifier = Modifier.weight(1f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                fontSize = 10.sp,
                lineHeight = 10.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5F)
            )
        }

        Spacer(Modifier.width(2.dp))
        IconButton(
            modifier = Modifier.size(40.dp),
            onClick = {if(index != 1) sort(index - 1, "up")}
        ) {
            Icon(ImageVector.vectorResource(R.drawable.keyboard_arrow_up), tint = upColor, modifier = Modifier.size(28.dp), contentDescription = null)
        }

        Spacer(Modifier.width(2.dp))
        IconButton(
            modifier = Modifier.size(40.dp),
            onClick = {if(index != length) sort(index - 1, "down")}
        ) {
            Icon(ImageVector.vectorResource(R.drawable.keyboard_arrow_down), tint = downColor, modifier = Modifier.size(28.dp), contentDescription = null)
        }

        Spacer(Modifier.width(2.dp))
        var state by remember {
            mutableStateOf(env.enabled)
        }
        LaunchedEffect(env.enabled) {
            state = env.enabled
        }
        SmallSwitch(
            modifier = Modifier.padding(end = 8.dp),
            checked = state,
            onCheckedChange = {
                val newState = !state
                update(env.copy(enabled = newState))
                state = newState
            }
        )

    }
}