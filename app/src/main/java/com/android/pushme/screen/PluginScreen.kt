package com.android.pushme.screen

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
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
import com.android.pushme.LocalNavController
import com.android.pushme.MainActivity
import com.android.pushme.R
import com.android.pushme.components.MeContent
import com.android.pushme.components.MeTopBarWithBack
import com.android.pushme.components.OnResumeEffect
import com.android.pushme.components.SmallSwitch
import com.android.pushme.data.MessagePlugin
import com.android.pushme.model.PluginViewModel
import com.android.pushme.sendServiceBroadcast
import com.android.pushme.utils.LangUtil

@Composable
fun PluginScreen(modifier: Modifier = Modifier) {
    val mainActivity = LocalActivity.current as MainActivity
    val navController = LocalNavController.current

    Scaffold(
        modifier = modifier,
        topBar = {
            MeTopBarWithBack(
                title = LangUtil.lang(R.string.message_plugin),
                actions = {
                    IconButton(onClick = {
                        navController.navigate("plugin_store") {
                            launchSingleTop = true
                        }
                    }) {
                        Icon(ImageVector.vectorResource(R.drawable.store), contentDescription = LangUtil.lang(R.string.plugin_store), Modifier.size(26.dp))
                    }
                    Spacer(Modifier.width(1.dp).height(24.dp).background(LocalContentColor.current.copy(alpha = 0.7f)))

                    IconButton(onClick = {
                        navController.navigate("plugin_env")
                    }) {
                        Icon(ImageVector.vectorResource(R.drawable.env), contentDescription = null, Modifier.size(26.dp))
                    }
                    IconButton(onClick = {
                        navController.navigate("plugin_pmf")
                    }) {
                        Icon(ImageVector.vectorResource(R.drawable.function), contentDescription = null, Modifier.size(26.dp))
                    }
                    IconButton(onClick = {
                        navController.navigate("plugin/0")
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
            val viewModel = mainActivity.pluginViewModel
            val pluginList = viewModel.pluginList

            // 从插件商店装完返回时，列表要能立刻看到新插件 ——
            // ViewModel 里的 pluginList 是个快照，只有 addPlugin/delPlugin 才会刷新。
            OnResumeEffect {
                viewModel.getList()
            }

            LazyColumn(
                contentPadding = PaddingValues(10.dp)
            ) {
                items(pluginList.value.size, { pluginList.value[it].id!! }) { it ->
                    Card(
                        shape = RoundedCornerShape(3.dp),
                        modifier = Modifier
                            .padding(bottom = 10.dp)
                            .fillMaxWidth().animateItem(fadeInSpec = null, fadeOutSpec = null),
                        backgroundColor = MaterialTheme.colorScheme.surface
                    ) {
                        PluginItem(
                            index = it + 1,
                            length = pluginList.value.size,
                            plugin = pluginList.value[it],
                            viewModel = viewModel,
                            sort = {i, sort ->
                                viewModel.swapSort(
                                    pluginList.value[i],
                                    pluginList.value[if(sort == "up") i - 1 else i + 1]
                                )
                                mainActivity.sendServiceBroadcast("refresh_plugins")
                            },
                            click = { pluginId ->
                                navController.navigate("plugin/$pluginId") {
                                    launchSingleTop = true
                                }
                            }
                        )
                    }
                }
            }
        }

    }
}

@Composable
fun PluginItem(index: Int, length: Int, modifier: Modifier = Modifier, plugin: MessagePlugin, viewModel: PluginViewModel, sort: (i: Int, sort: String) -> Unit, click: (pluginId: Int?) -> Unit) {
    val mainActivity = LocalActivity.current as MainActivity
    val textColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8F)
    val disableColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3F)
    val upColor = if(index == 1) disableColor else textColor
    val downColor = if(index == length) disableColor else textColor

    Row(
        modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(Modifier
            .weight(1f)
            .clickable(
                onClick = {
                    click(plugin.id)
                }
            )
            .padding(10.dp, 10.dp)
        ) {
            Text(
                text = plugin.title,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
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
            mutableStateOf(plugin.state == 1)
        }
        LaunchedEffect(plugin.state) {
            state = plugin.state == 1
        }
        SmallSwitch(
            modifier = Modifier.padding(end = 8.dp),
            checked = state,
            onCheckedChange = {
                viewModel.switchState(plugin)
                state = !state
                mainActivity.sendServiceBroadcast("refresh_plugins")
            }
        )

    }
}