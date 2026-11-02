package com.android.pushme.screen

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProgressIndicatorDefaults
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import com.android.pushme.utils.PushKey.updatePushKey
import com.android.pushme.utils.PushMeApi.selfHost
import com.android.pushme.utils.PushMeServer.getSelfCert
import com.android.pushme.utils.Settings
import com.android.pushme.utils.getCertInfo

@Composable
fun HostScreen(modifier: Modifier = Modifier) {
    val focusManager = LocalFocusManager.current
    val navController = LocalNavController.current
    val mainActivity = LocalActivity.current as MainActivity
    var saveClick by remember { mutableStateOf({}) }

    Scaffold(
        modifier = modifier.clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) {
            focusManager.clearFocus()
        },
        topBar = {
            MeTopBarWithBack(
                title = LangUtil.lang(R.string.self_host),
                actions = {
                    IconButton(onClick = {
                        Utils.openUrl("https://github.com/yafoo/pushme-server", mainActivity, "blank")
                    }) {
                        Icon(ImageVector.vectorResource(R.drawable.deployed_code), contentDescription = null, Modifier.size(26.dp))
                    }
                    Spacer(Modifier.width(1.dp).height(24.dp).background(LocalContentColor.current.copy(alpha = 0.7f)))

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
            var key by remember { mutableStateOf(if(Settings.selfKey != "") Settings.selfKey else Settings.pushKey) }
            var keyVisible by remember { mutableStateOf(false) }
            var host by remember { mutableStateOf(Settings.selfHost) }
            var port by remember { mutableStateOf(Settings.selfPort.toString()) }
            val protocolList = Settings.getSettingOptions(listOf("TCP", "WebSocket"), R.array.self_protocol_options)
            var protocol by remember { mutableStateOf(Settings.selfProtocol) }
            val tlsList = Settings.getSettingOptions(listOf("无证书", "公共证书", "自签名证书"), R.array.self_tls_options)
            var tls by remember { mutableStateOf(Settings.selfTls) }
            var cert by remember { mutableStateOf(Settings.selfCert) }

            var certStringInfo by remember { mutableStateOf("") }

            fun saveSelfHostData() {
                if(port == "") {
                    port = "3100"
                }
                Settings.selfKey = key
                Settings.selfHost = host
                Settings.selfPort = port.toInt()
                Settings.selfProtocol = protocol
                Settings.selfTls = tls
                Settings.selfCert = cert
                mainActivity.showToast(LangUtil.lang(mainActivity, R.string.save_success))
                mainActivity.sendServiceBroadcast("refresh_self_host")
                selfHost()
            }

            LaunchedEffect(Unit) {
                saveClick = {
                    if(key == "") {
                        mainActivity.showToast(LangUtil.lang(mainActivity, R.string.push_key_empty_tips))
                    } else if(host == "") {
                        mainActivity.showToast(LangUtil.lang(mainActivity, R.string.self_host_empty_tips))
                    } else if(host.startsWith("http") || host.startsWith("mqtt")) {
                        mainActivity.showToast(LangUtil.lang(mainActivity, R.string.self_host_error_tips))
                    } else if(port == "") {
                        mainActivity.showToast(LangUtil.lang(mainActivity, R.string.self_port_empty_tips))
                    } else if(tls == "自签名证书" && cert == "") {
                        mainActivity.showToast(LangUtil.lang(mainActivity, R.string.self_tls_empty_tips))
                    } else {
                        saveSelfHostData()
                        navController.popBackStack()
                    }
                }
            }

            LaunchedEffect(cert) {
                certStringInfo = getCertInfo(cert) ?: ""
            }

            Column (
                modifier = Modifier
                    .verticalScroll(ScrollState(0))
                    .padding(15.dp)
                    .fillMaxSize(),
            ) {
                TextField(
                    label = {Text(text = "push_key")},
                    value = key,
                    visualTransformation = if (keyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    onValueChange = {
                        key = it },
                    colors = TextFieldDefaults.colors(
                        unfocusedContainerColor = MaterialTheme.colorScheme.surface
                    ),
                    placeholder = { Text(text = LangUtil.lang(R.string.push_key_placeholder), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        imeAction = ImeAction.Next,
                        keyboardType = KeyboardType.Password
                    ),
                    suffix = {
                        if(Settings.pushKey == "") {
                            keyVisible = true
                            Text(text = LangUtil.lang(R.string.click_get), modifier = Modifier.clickable {
                                updatePushKey { error, value ->
                                    run {
                                        if(error != "") {
                                            mainActivity.runOnUiThread {
                                                mainActivity.showToast(LangUtil.lang(mainActivity, R.string.get_success)+ error)
                                            }
                                        } else {
                                            key = value

                                            mainActivity.runOnUiThread {
                                                mainActivity.showToast(LangUtil.lang(mainActivity, R.string.get_success))
                                            }
                                            mainActivity.sendServiceBroadcast("refresh_push_key")
                                        }
                                    }
                                }
                            })
                        } else {
                            val imageVector: ImageVector = if(keyVisible) ImageVector.vectorResource(R.drawable.visibility)
                                else ImageVector.vectorResource(R.drawable.visibility_off)
                            Icon(
                                imageVector,
                                contentDescription = null,
                                Modifier.size(24.dp).clickable {
                                    keyVisible = !keyVisible
                                }
                            )
                        }
                    }
                )

                TextField(
                    label = {Text(text = LangUtil.lang(R.string.self_host_address))},
                    value = host,
                    onValueChange = {
                        host = it },
                    colors = TextFieldDefaults.colors(
                        unfocusedContainerColor = MaterialTheme.colorScheme.surface
                    ),
                    placeholder = { Text(text = LangUtil.lang(R.string.self_host_placeholder), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        imeAction = ImeAction.Next
                    )
                )

                TextField(
                    label = {Text(text = LangUtil.lang(R.string.self_port))},
                    value = port,
                    onValueChange = {newText ->
                        // 过滤非数字字符
                        val filtered = newText.filter { it.isDigit() }
                        port = if (filtered != newText) filtered else newText
                    },
                    colors = TextFieldDefaults.colors(
                        unfocusedContainerColor = MaterialTheme.colorScheme.surface
                    ),
                    placeholder = { Text(text = LangUtil.lang(R.string.self_port_placeholder), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        imeAction = ImeAction.Next,
                        keyboardType = KeyboardType.Number
                    )
                )

                Spacer(Modifier.height(10.dp))

                RadioListNew(
                    modifier = Modifier.height(50.dp).horizontalScroll(rememberScrollState()),
                    modifierItem = Modifier.padding(start = 2.dp, end = 3.dp),
                    title = LangUtil.lang(R.string.self_protocol) + "：",
                    list = protocolList,
                    selectedValue = protocolList.entries.find { it.key == protocol }?:protocolList.entries.first(),
                    onClick = {
                        protocol = it.key
                    }
                )

                RadioListNew(
                    modifier = Modifier.height(50.dp).horizontalScroll(rememberScrollState()),
                    modifierItem = Modifier.padding(start = 2.dp, end = 3.dp),
                    title = LangUtil.lang(R.string.self_tls) + "：",
                    list = tlsList,
                    selectedValue = tlsList.entries.find { it.key == tls }?:tlsList.entries.first(),
                    onClick = {
                        tls = it.key
                    }
                )

                Spacer(Modifier.height(10.dp))

                if(tls == "自签名证书") {
                    Box(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        TextField(
                            label = {Text(text = LangUtil.lang(R.string.self_cert))},
                            value = cert,
                            onValueChange = {
                                cert = it
                            },
                            colors = TextFieldDefaults.colors(
                                unfocusedContainerColor = MaterialTheme.colorScheme.surface
                            ),
                            placeholder = { Text(text = LangUtil.lang(R.string.self_cert_placeholder), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)) },
                            modifier = Modifier.fillMaxWidth(),
                            textStyle = LocalTextStyle.current.copy(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 9.sp,
                                lineHeight = 12.sp,
                                letterSpacing = 0.sp,
                            )
                        )

                        var isLoading by remember { mutableStateOf(false) }

                        Button(
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            ),
                            onClick = {
                                if(isLoading) return@Button
                                isLoading = true
                                val domain = if(host.contains(":")) "[$host]" else host
                                val url = "https://$domain:$port/certs/cert.crt"
                                getSelfCert(url) {state,result ->
                                    isLoading = false
                                    if(state) cert = result
                                    else mainActivity.showToast(LangUtil.lang(mainActivity, R.string.get_cert_fail_before) +" $url " +LangUtil.lang(mainActivity, R.string.get_cert_fail_after))
                                }
                            },
                            modifier = Modifier.defaultMinSize(minWidth = 60.dp, minHeight = 28.dp).align(Alignment.TopEnd).padding(top = 3.dp, end = 10.dp).offset(y = (if (certStringInfo == "") 0.dp else (-8).dp)),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                        ) {
                            if(isLoading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.padding(end = 1.dp).size(10.dp),
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    strokeWidth = 1.6.dp,
                                    trackColor = ProgressIndicatorDefaults.circularIndeterminateTrackColor,
                                )
                            }
                            val text = if(isLoading) LangUtil.lang(R.string.get_cert_ing) else LangUtil.lang(R.string.get_cert)
                            Text(text = text, fontSize = 12.sp, lineHeight = 16.sp)
                        }
                    }

                    if(certStringInfo != "") {
                        Text(
                            modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                            text = certStringInfo,
                            style = LocalTextStyle.current.copy(
                                fontFamily = FontFamily.Monospace,
                                fontSize = MaterialTheme.typography.bodySmall.fontSize,
                                lineHeight = MaterialTheme.typography.bodySmall.lineHeight,
                                letterSpacing = 0.sp
                            ),
                        )
                    }
                }
            }
        }
    }
}