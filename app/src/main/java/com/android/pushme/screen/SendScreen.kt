package com.android.pushme.screen

import android.view.KeyEvent
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.android.pushme.MainActivity
import com.android.pushme.R
import com.android.pushme.components.MeContent
import com.android.pushme.components.MeTopBarWithBack
import com.android.pushme.components.RadioListNew
import com.android.pushme.data.Message
import com.android.pushme.showToast
import com.android.pushme.utils.LangUtil
import com.android.pushme.utils.PushMeApi.sendMessage
import com.android.pushme.utils.Settings

@Composable
fun SendScreen(modifier: Modifier = Modifier) {
    val focusManager = LocalFocusManager.current
    var sendClick by remember { mutableStateOf({}) }

    Scaffold(
        modifier = modifier.clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) {
            focusManager.clearFocus()
        },
        topBar = {
            MeTopBarWithBack(
                title = LangUtil.lang(R.string.send_message),
                actions = {
                    IconButton(onClick = sendClick) {
                        Icon(ImageVector.vectorResource(R.drawable.send), contentDescription = null, Modifier.size(26.dp))
                    }
                }
            ) },
    ) { innerPadding -> MeContent(
            modifier = Modifier
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05F))
        ) {
            var title by remember { mutableStateOf("") }
            var content by remember { mutableStateOf("") }
            val typeList = Settings.getSettingOptions(Message.allTypes, Message.allTypes)
            var selectedType by remember { mutableStateOf(typeList.entries.first()) }
            val mainActivity = LocalActivity.current as MainActivity

            val focusRequester = remember { FocusRequester() }

            val mqttState = Settings.mqttState
            var selfApi = Settings.selfApi
            if(mqttState == "自建" && selfApi == "") {
                val http = if(Settings.selfTls == "无证书") "http" else "https"
                selfApi = "$http://${Settings.selfHost}:3010"
            }
            var api by remember { mutableStateOf(selfApi) }

            LaunchedEffect(Unit) {
                sendClick = {
                    val pushKey = if(mqttState == "自建") Settings.selfKey else Settings.pushKey
                    if(pushKey == "") {
                        mainActivity.showToast(LangUtil.lang(mainActivity, R.string.send_empty_push_key_tips))
                    } else if(title == "" && content == "") {
                        mainActivity.showToast(LangUtil.lang(mainActivity, R.string.send_empty_title_content_tips))
                    } else if(mqttState == "自建" && api == "") {
                        mainActivity.showToast(LangUtil.lang(mainActivity, R.string.send_empty_api_url_tips))
                    } else {
                        if(mqttState == "自建") {
                            Settings.selfApi = api
                        }
                        sendMessage(title, content, "", selectedType.key, pushKey, onSend = { result ->
                            if(result.contains("成功")) {
                                title = ""
                                content = ""
                                selectedType = typeList.entries.first()
                            }
                            mainActivity.runOnUiThread {
                                mainActivity.showToast(LangUtil.lang(mainActivity, if(result.contains("成功")) R.string.send_success else R.string.send_fail))
                            }
                        })
                    }
                }
            }

            Column (
                modifier = Modifier
                    .verticalScroll(ScrollState(0))
                    .padding(15.dp)
                    .fillMaxSize(),
            ) {
                if(mqttState == "自建") {
                    TextField(
                        label = {Text(text = LangUtil.lang(R.string.api_url))},
                        value = api,
                        onValueChange = {
                            api = it
                        },
                        colors = TextFieldDefaults.colors(
                            unfocusedContainerColor = MaterialTheme.colorScheme.surface
                        ),
                        placeholder = {Text(text = LangUtil.lang(R.string.api_url), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))},
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            imeAction = ImeAction.Next
                        )
                    )
                }

                TextField(
                    label = {Text(text = LangUtil.lang(R.string.message_title))},
                    value = title,
                    onValueChange = {
                    title = it },
                    colors = TextFieldDefaults.colors(
                        unfocusedContainerColor = MaterialTheme.colorScheme.surface
                    ),
                    placeholder = {Text(text = LangUtil.lang(R.string.title), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))},
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        imeAction = ImeAction.Next
                    )
                )
                TextField(
                    label = {Text(text = LangUtil.lang(R.string.message_content))},
                    value = content,
                    onValueChange = {
                    content = it },
                    colors = TextFieldDefaults.colors(
                        unfocusedContainerColor = MaterialTheme.colorScheme.surface
                    ),
                    placeholder = {Text(text = LangUtil.lang(R.string.content), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))},
                    modifier = Modifier
                        .fillMaxWidth()
                        .defaultMinSize(
                            minHeight = 150.dp
                        )
                        .focusRequester(focusRequester)
                        .onKeyEvent {
                            if (it.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_ENTER) {
                                focusRequester.requestFocus()
                                true
                            } else {
                                false
                            }
                        },
                    maxLines = 5
                )

                Spacer(modifier = Modifier.height(20.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = LangUtil.lang(R.string.type) + "：", fontSize = 16.sp)
                    RadioListNew(
                        modifier = Modifier.horizontalScroll(
                            rememberScrollState()
                        ),
                        modifierItem = Modifier.padding(end = 8.dp),
                        list = typeList,
                        selectedValue = selectedType,
                        onClick = {
                            selectedType = it
                        }
                    )
                }

                Spacer(modifier = Modifier.height(15.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = LangUtil.lang(R.string.samples) + "：", fontSize = 16.sp)
                    Row(
                        modifier = Modifier.horizontalScroll(
                            rememberScrollState()
                        )
                    ) {
                        Message.allTypes.forEach { type ->
                            Button(
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.secondary,
                                    contentColor = MaterialTheme.colorScheme.onSecondary
                                ),
                                onClick = {
                                    title = "hello $type"
                                    content = when(type) {
                                        "markdown" -> "## PushMe\n#### 一个简单轻量的Android消息通知客户端！\n官网：https://push.i-i.me/"
                                        "html" -> "<div style='border: 10px solid #46bc99; text-align: center; font-size: 30px;'>HTML</div>"
                                        "url" -> "https://push.i-i.me?from=type_url"
                                        "data" -> (100..999).random().toString()
                                        "markdata" -> "|A\\B|D1|D2\n-|-|-\n" +
                                                "今日|${(100..999).random()}|${(100..999).random()}\n" +
                                                "昨日|${(100..999).random()}|${(100..999).random()}"
                                        "note" -> "- [x] 完成项目需求文档\n- [x] 设计数据库结构\n- [ ] 开发用户模块\n- [ ] 开发消息推送模块\n- [ ] 编写单元测试\n- [ ] 部署到生产环境"
                                        "chart" -> (100..999).random().toString()
                                        "echarts" -> "{\"series\":[{\"type\":\"pie\",\"data\":[{\"value\":${(10..99).random()},\"name\":\"A\"},{\"value\":${(10..99).random()},\"name\":\"B\"},{\"value\":${(10..99).random()},\"name\":\"C\"}]}]}"
                                        "svg" -> """<svg xmlns="http://www.w3.org/2000/svg" width="400" height="200" viewBox="0 0 400 200">
    <defs>
        <!-- 方格纹理图案 -->
        <pattern id="grid" width="20" height="20" patternUnits="userSpaceOnUse">
            <rect width="20" height="20" fill="#f0f0f0"/>
            <rect width="10" height="10" fill="#e0e0e0"/>
        </pattern>
        
        <!-- 心形渐变 -->
        <linearGradient id="heartGradient" x1="0%" y1="0%" x2="100%" y2="100%">
            <stop offset="0%" style="stop-color:#FF6B9D;stop-opacity:1" />
            <stop offset="50%" style="stop-color:#E91E63;stop-opacity:1" />
            <stop offset="100%" style="stop-color:#C2185B;stop-opacity:1" />
        </linearGradient>
        
        <!-- 阴影 -->
        <filter id="shadow" x="-30%" y="-30%" width="160%" height="160%">
            <feDropShadow dx="0" dy="2" stdDeviation="3" flood-color="#00000040"/>
        </filter>
    </defs>
    
    <!-- 方格背景 -->
    <rect width="400" height="200" fill="url(#grid)" />
    
    <!-- 心形主体 -->
    <g filter="url(#shadow)">
        <path 
            d="M100,130 C100,130 65,108 65,86 C65,73 74,64 85,64 C92,64 96,69 100,76 C104,69 108,64 115,64 C126,64 135,73 135,86 C135,108 100,130 100,130Z" 
            fill="url(#heartGradient)" 
            stroke="#AD1457" 
            stroke-width="1.5"
        />
    </g>
    
    <!-- 高光效果 -->
    <path 
        d="M85,78 C87,72 92,70 94,74 C96,78 91,82 85,78Z" 
        fill="white" 
        opacity="0.5"
    />
</svg>""".trimIndent()
                                        else -> {"PushMe，一个简单轻量的Android消息通知客户端！"}
                                    }
                                    selectedType = typeList.entries.find { it.key == type }?:typeList.entries.first()
                                },
                                contentPadding = PaddingValues(vertical = 5.dp, horizontal = 8.dp),
                                shape = RoundedCornerShape(2.dp),
                                modifier = modifier.defaultMinSize(minWidth = 1.dp, minHeight = 1.dp).padding(end = 8.dp)
                            ) {
                                Text(text = "hello $type", fontSize = 10.sp, lineHeight = 10.sp)
                            }
                        }
                    }
                }

            }
        }
    }
}