package com.android.pushme.screen

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.android.pushme.LocalAppViewModel
import com.android.pushme.LocalNavController
import com.android.pushme.MainActivity
import com.android.pushme.R
import com.android.pushme.Utils
import com.android.pushme.components.MeContent
import com.android.pushme.components.MeTopBarWithBack
import com.android.pushme.components.RadioListNew
import com.android.pushme.sendServiceBroadcast
import com.android.pushme.showToast
import com.android.pushme.utils.LangUtil
import com.android.pushme.utils.Settings

@Composable
fun ApiScreen(modifier: Modifier = Modifier) {
    val navController = LocalNavController.current
    val mainActivity = LocalActivity.current as MainActivity
    var saveClick by remember { mutableStateOf({}) }

    Scaffold(
        modifier = modifier.clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) {
            // 点击空白处收起键盘
        },
        topBar = {
            MeTopBarWithBack(
                title = LangUtil.lang(R.string.api_service),
                actions = {
                    IconButton(onClick = saveClick) {
                        Icon(
                            imageVector = androidx.compose.ui.graphics.vector.ImageVector.vectorResource(R.drawable.save),
                            contentDescription = null,
                            modifier = Modifier.size(26.dp)
                        )
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
            var state by remember { mutableStateOf(Settings.apiState) }
            var verifyKey by remember { mutableStateOf(Settings.apiVerifyKey) }
            var host by remember { mutableStateOf(Settings.apiHost) }
            var port by remember { mutableStateOf(Settings.apiPort.toString()) }

            val appViewModel = LocalAppViewModel.current

            fun saveData() {
                if (port.isEmpty()) {
                    port = "3010"
                }
                Settings.apiState = state
                Settings.apiVerifyKey = verifyKey
                Settings.apiHost = host
                Settings.apiPort = port.toInt()
                appViewModel.serviceApiState.value = state == "开启"
                mainActivity.showToast(LangUtil.lang(mainActivity, R.string.save_success))
                mainActivity.sendServiceBroadcast("refresh_api_service")
            }

            LaunchedEffect(Unit) {
                saveClick = {
                    if (host.isEmpty()) {
                        mainActivity.showToast(LangUtil.lang(mainActivity, R.string.api_host_empty_tips))
                    } else if (port.isEmpty()) {
                        mainActivity.showToast(LangUtil.lang(mainActivity, R.string.api_port_empty_tips))
                    } else {
                        saveData()
                        navController.popBackStack()
                    }
                }
            }

            Column(
                modifier = Modifier
                    .verticalScroll(ScrollState(0))
                    .padding(15.dp)
                    .fillMaxSize(),
            ) {
                // 监听地址
                var ipDropdownExpanded by remember { mutableStateOf(false) }
                val localIPs = remember {
                    val commonIPs = listOf("0.0.0.0", "127.0.0.1", "::1")
                    val allIPs = commonIPs + Utils.getLocalIPs()
                    allIPs.distinct()
                }

                Row(modifier = Modifier.fillMaxWidth()) {
                    TextField(
                        label = { Text(text = LangUtil.lang(R.string.api_host)) },
                        value = host,
                        onValueChange = { host = it },
                        colors = TextFieldDefaults.colors(
                            unfocusedContainerColor = MaterialTheme.colorScheme.surface
                        ),
                        placeholder = { Text(text = LangUtil.lang(R.string.api_host_placeholder), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)) },
                        modifier = Modifier.fillMaxWidth().weight(1f),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            imeAction = ImeAction.Next
                        ),
                        trailingIcon = {
                            IconButton(onClick = { ipDropdownExpanded = true }) {
                                Icon(
                                    imageVector = Icons.Filled.ArrowDropDown,
                                    contentDescription = null,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            DropdownMenu(
                                expanded = ipDropdownExpanded,
                                onDismissRequest = { ipDropdownExpanded = false }
                            ) {
                                if (localIPs.isEmpty()) {
                                    DropdownMenuItem(
                                        text = { Text("无可用IP") },
                                        onClick = { ipDropdownExpanded = false }
                                    )
                                } else {
                                    localIPs.forEach { ip ->
                                        DropdownMenuItem(
                                            text = { Text(ip) },
                                            onClick = {
                                                host = ip
                                                ipDropdownExpanded = false
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    )
                }

                // 监听端口
                TextField(
                    label = { Text(text = LangUtil.lang(R.string.api_port)) },
                    value = port,
                    onValueChange = { newText ->
                        val filtered = newText.filter { it.isDigit() }
                        port = if (filtered != newText) filtered else newText
                    },
                    colors = TextFieldDefaults.colors(
                        unfocusedContainerColor = MaterialTheme.colorScheme.surface
                    ),
                    placeholder = { Text(text = LangUtil.lang(R.string.api_port_placeholder), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        imeAction = ImeAction.Done,
                        keyboardType = KeyboardType.Number
                    )
                )

                Spacer(Modifier.height(10.dp))

                // 验证 push_key
                val verifyKeyList = Settings.getOnOffOptions()
                RadioListNew(
                    modifier = Modifier.height(50.dp),
                    modifierItem = Modifier.padding(start = 2.dp, end = 3.dp),
                    title = LangUtil.lang(R.string.api_verify_key),
                    list = verifyKeyList,
                    selectedValue = verifyKeyList.entries.find { it.key == verifyKey } ?: verifyKeyList.entries.first(),
                    onClick = {
                        verifyKey = it.key
                    }
                )

                // 服务状态
                val stateList = Settings.getOnOffOptions()
                RadioListNew(
                    modifier = Modifier.height(50.dp),
                    modifierItem = Modifier.padding(start = 2.dp, end = 3.dp),
                    title = LangUtil.lang(R.string.api_service_state),
                    list = stateList,
                    selectedValue = stateList.entries.find { it.key == state } ?: stateList.entries.first(),
                    onClick = {
                        state = it.key
                    }
                )

                Spacer(Modifier.height(10.dp))

                // 使用说明
                Text(
                    text = LangUtil.lang(R.string.api_usage_tips),
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

