package com.android.pushme.screen

import android.view.KeyEvent
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.background
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LocalContentColor
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.android.pushme.LocalNavController
import com.android.pushme.MainActivity
import com.android.pushme.components.MeConfirm
import com.android.pushme.components.MeContent
import com.android.pushme.components.MeTopBarWithBack
import com.android.pushme.plugin.PLUGIN_DEMO_CODE
import com.android.pushme.R
import com.android.pushme.components.JSEditor
import com.android.pushme.data.MessagePlugin
import com.android.pushme.sendServiceBroadcast
import com.android.pushme.showToast
import com.android.pushme.utils.LangUtil
import com.android.pushme.utils.PushMeApi.setPlugin

@Composable
fun PluginSaveScreen(modifier: Modifier = Modifier, pluginId: Int = 0) {
    val mainActivity = LocalActivity.current as MainActivity
    val navController = LocalNavController.current
    val viewModel = mainActivity.pluginViewModel
    val plugin = viewModel.getPlugin(pluginId)
    val focusManager = LocalFocusManager.current
    var saveClick by remember { mutableStateOf({}) }


    Scaffold(
        modifier = modifier.clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) {
            focusManager.clearFocus()
        },
        topBar = {
            MeTopBarWithBack(
                title = LangUtil.lang(R.string.plugin_edit),
                actions = {
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
                    Spacer(Modifier.width(1.dp).height(24.dp).background(LocalContentColor.current.copy(alpha = 0.7f)))

                    if(pluginId > 0) {
                        val confirmShow = remember{mutableStateOf(false)}

                        IconButton(onClick = {confirmShow.value = true}) {
                            Icon(ImageVector.vectorResource(R.drawable.delete), contentDescription = null, Modifier.size(24.dp))
                        }

                        MeConfirm(show = confirmShow, content = LangUtil.lang(R.string.confirm_delete), confirm = {
                            viewModel.delPlugin(plugin!!)
                            mainActivity.sendServiceBroadcast("refresh_plugins")
                            confirmShow.value = false
                            navController.popBackStack()
                            mainActivity.showToast(LangUtil.lang(mainActivity, R.string.delete_success))
                        })
                    }

                    IconButton(onClick = saveClick) {
                        Icon(ImageVector.vectorResource(R.drawable.save), contentDescription = null, Modifier.size(26.dp))
                    }
                }
            ) },
    ) { innerPadding -> MeContent(
        modifier = Modifier
            .padding(innerPadding)
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05F))
        ) {
            val focusRequester = remember { FocusRequester() }
            var title by remember { mutableStateOf(plugin?.title ?: "") }
            var content by remember { mutableStateOf(plugin?.content ?: PLUGIN_DEMO_CODE) }

            LaunchedEffect(Unit) {
                saveClick = {
                    if(title == "") {
                        mainActivity.showToast(LangUtil.lang(mainActivity, R.string.plugin_title_empty_tips))
                    } else {
                        // ⚠️ 编辑已有插件时必须用 copy，不能 new 一个 MessagePlugin：
                        // 直接构造会把 pluginKey / versionCode / sha256 这些「商店身份」字段全丢掉，
                        // 结果就是商店以为这个插件没装过，而且「本地已修改」的判据也一起没了。
                        val data = plugin?.copy(title = title, content = content)
                            ?: MessagePlugin(null, title, content, 0, 1)
                        viewModel.addPlugin(data)
                        mainActivity.sendServiceBroadcast("refresh_plugins")
                        setPlugin(viewModel.getPluginCount())
                        navController.popBackStack()
                        mainActivity.showToast(LangUtil.lang(mainActivity, if(pluginId == 0) R.string.save_success else R.string.edit_success))
                    }
                }
            }

            Column (
                modifier = Modifier
                    .padding(15.dp)
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                TextField(
                    value = title,
                    onValueChange = { title = it },
                    colors = TextFieldDefaults.colors(
                        unfocusedContainerColor = MaterialTheme.colorScheme.surface
                    ),
                    placeholder = { Text(text = LangUtil.lang(R.string.plugin_title), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        imeAction = ImeAction.Next
                    )
                )
                JSEditor(modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .focusRequester(focusRequester)
                    .onKeyEvent {
                        if (it.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_ENTER) {
                            focusRequester.requestFocus()
                            true
                        } else {
                            false
                        }
                    },
                    code = content,
                    onCodeChange = {
                        content = it
                    },
                    colors = TextFieldDefaults.colors(
                        unfocusedContainerColor = MaterialTheme.colorScheme.surface
                    ),
                )
            }
        }
    }
}