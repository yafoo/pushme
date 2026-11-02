package com.android.pushme.components.drawer

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.unit.dp
import com.android.pushme.MainActivity
import com.android.pushme.R
import com.android.pushme.components.RadioListNew
import com.android.pushme.sendServiceBroadcast
import com.android.pushme.utils.LangUtil
import com.android.pushme.utils.Settings

@Composable
fun DrawerItemStatusNotificationSet(modifier: Modifier = Modifier, expandShow: Boolean) {
    val options = listOf("通知", "限静", "静音", "限断", "关闭")

    val list = Settings.getSettingOptions(options, R.array.status_notify_options)
    var selected by remember { mutableStateOf(list.entries.find{it.key == Settings.statusNotify}?: list.entries.first()) }

    val tipsLabels = stringArrayResource(R.array.status_notify_tips)
    val tips = remember {
        options.zip(tipsLabels.asList()).toMap()
    }

    val mainActivity = LocalActivity.current as MainActivity

    DrawerItemMore(modifier = modifier, expandShow, itemTitle = {
        DrawerItemTitle(LangUtil.lang(R.string.status_notify)+": ", titleValue = selected.value, expandShow = expandShow)
    }) {
        Column(modifier = modifier.fillMaxWidth()) {
            DrawerItemLine()
            RadioListNew(
                modifier = Modifier
                    .height(50.dp)
                    .horizontalScroll(rememberScrollState()),
                modifierItem = Modifier.padding(start = 2.dp, end = 2.dp),
                list = list,
                selectedValue = selected,
                spaceWidth = 2.dp,
                onClick = {
                    selected = it
                    Settings.statusNotify = it.key
                    mainActivity.sendServiceBroadcast("refresh_channel_set")
                }
            )

            if(Settings.statusNotify == "限静") {
                StatusNotificationTimeSet()
            }

            DrawerItemTips(tips = selected.value + ": " + tips[selected.key])
        }
    }
}