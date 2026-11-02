package com.android.pushme.screen

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import com.android.pushme.components.CheckboxList
import com.android.pushme.MainActivity
import com.android.pushme.R
import com.android.pushme.Utils
import com.android.pushme.components.MeContent
import com.android.pushme.components.MeTopBarWithBack
import com.android.pushme.components.RadioListNew
import com.android.pushme.components.drawer.TimeSet
import com.android.pushme.sendServiceBroadcast
import com.android.pushme.showToast
import com.android.pushme.utils.LangUtil
import com.android.pushme.utils.Settings

@Composable
fun RepostScreen(modifier: Modifier = Modifier) {
    val focusManager = LocalFocusManager.current
    val mainActivity = LocalActivity.current as MainActivity
    var saveClick by remember { mutableStateOf({}) }

    Scaffold(
        modifier = modifier.clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) {
            focusManager.clearFocus()
        },
        topBar = {
            MeTopBarWithBack(
                title = LangUtil.lang(R.string.message_repost),
                actions = {
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
            var url by remember { mutableStateOf(Settings.repostUrl) }
            var limit by remember { mutableStateOf(Settings.repostLimit) }
            var omit by remember { mutableStateOf(Settings.repostOmit) }
            var weekChecked = remember { Settings.repostWeek.toMutableList() }
            var timeStart = Settings.repostTimeStart
            var timeEnd = Settings.repostTimeEnd

            val postStateList = Settings.getOnOffOptions()
            var repostPushKey by remember { mutableStateOf(Settings.repostPushKey) }
            var postState by remember { mutableStateOf(Settings.repostState) }

            val weekList = listOf("一", "二", "三", "四", "五", "六", "日")

            LaunchedEffect(Unit) {
                saveClick = {
                    if(url != "" && !Utils.isValidUrl(url)) {
                        mainActivity.showToast(LangUtil.lang(mainActivity, R.string.post_url_error_tips))
                    } else {
                        Settings.repostUrl = url
                        Settings.repostWeek = weekChecked
                        Settings.repostTimeStart = timeStart
                        Settings.repostTimeEnd = timeEnd
                        Settings.repostLimit = limit
                        Settings.repostOmit = omit
                        Settings.repostState = postState
                        Settings.repostPushKey = repostPushKey

                        mainActivity.sendServiceBroadcast("refresh_repost_data")
                        mainActivity.showToast(LangUtil.lang(mainActivity, R.string.save_success))
                    }
                }
            }

            Column (
                modifier = Modifier
                    .verticalScroll(ScrollState(0))
                    .padding(15.dp)
                    .fillMaxSize(),
            ) {
                TextField(
                    label = {Text(text = LangUtil.lang(R.string.post_url))},
                    value = url,
                    onValueChange = {
                        url = it },
                    colors = TextFieldDefaults.colors(
                        unfocusedContainerColor = MaterialTheme.colorScheme.surface
                    ),
                    placeholder = { Text(text = LangUtil.lang(R.string.post_url_placeholder), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        imeAction = ImeAction.Next
                    )
                )

                CheckboxList(
                    modifier = Modifier.height(60.dp).horizontalScroll(rememberScrollState()),
                    modifierItem = Modifier.padding(end = 5.dp),
                    title = LangUtil.lang(R.string.week)+"：",
                    list = weekList,
                    checkedList = weekChecked,
                    onCheckedChange = { value, checked ->
                        run {
                            if (checked) {
                                weekChecked.add(value)
                            } else {
                                weekChecked.remove(value)
                            }
                            weekChecked.sort()
                            val newWeekChecked = mutableListOf<String>()
                            newWeekChecked.addAll(weekChecked)
                            weekChecked = newWeekChecked
                        }
                    }
                )

                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(text = LangUtil.lang(R.string.time)+"：", fontSize = 14.sp)
                    TimeSet(time = timeStart) {timeStart = it}
                    Spacer(modifier = Modifier
                        .padding(horizontal = 3.dp)
                        .width(3.dp)
                        .height(1.dp)
                        .background(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))

                    )
                    TimeSet(time = timeEnd) {timeEnd = it}
                }

                TextField(
                    label = {Text(text = LangUtil.lang(R.string.post_limit))},
                    value = limit,
                    onValueChange = {
                        limit = it },
                    colors = TextFieldDefaults.colors(
                        unfocusedContainerColor = MaterialTheme.colorScheme.surface
                    ),
                    placeholder = { Text(text = LangUtil.lang(R.string.post_limit_placeholder), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        imeAction = ImeAction.Next
                    )
                )

                TextField(
                    label = {Text(text = LangUtil.lang(R.string.post_omit))},
                    value = omit,
                    onValueChange = {
                        omit = it },
                    colors = TextFieldDefaults.colors(
                        unfocusedContainerColor = MaterialTheme.colorScheme.surface
                    ),
                    placeholder = { Text(text = LangUtil.lang(R.string.post_omit_placeholder), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        imeAction = ImeAction.Next
                    )
                )

                Spacer(Modifier.height(10.dp))

                RadioListNew(
                    modifier = Modifier.height(50.dp),
                    modifierItem = Modifier.padding(start = 2.dp, end = 3.dp),
                    title = LangUtil.lang(R.string.repost_push_key) + "：",
                    list = postStateList,
                    selectedValue = postStateList.entries.find { it.key == repostPushKey } ?: postStateList.entries.first(),
                    onClick = {
                        repostPushKey = it.key
                    }
                )

                RadioListNew(
                    modifier = Modifier.height(50.dp),
                    modifierItem = Modifier.padding(start = 2.dp, end = 3.dp),
                    title = LangUtil.lang(R.string.status) + "：",
                    list = postStateList,
                    selectedValue = postStateList.entries.find { it.key == postState }?:postStateList.entries.first(),
                    onClick = {
                        postState = it.key
                    }
                )

                Spacer(Modifier.height(10.dp))
            }
        }
    }
}