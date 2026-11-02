package com.android.pushme.components.drawer

import android.os.Handler
import android.os.Looper
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.android.pushme.R
import com.android.pushme.components.OnResumeEffect
import com.android.pushme.deleteNotificationChannel
import com.android.pushme.model.NotificationViewModel
import com.android.pushme.openChannelSettings
import com.android.pushme.showToast
import com.android.pushme.utils.LangUtil
import com.android.pushme.utils.Settings

@Composable
fun DrawerItemChannels(modifier: Modifier = Modifier, expandShow: Boolean) {
    DrawerItemMore(modifier, expandShow, itemTitle = {
        DrawerItemTitle(LangUtil.lang(R.string.notify_channel)+": ", titleValue = LangUtil.lang(R.string.channel_tips), expandShow = expandShow)
    }) {
        val context = LocalContext.current
        val viewModel = viewModel<NotificationViewModel>()

        LaunchedEffect(Unit) {
            viewModel.initChannels(context)
        }
        OnResumeEffect {
            Handler(Looper.getMainLooper()).postDelayed({
                viewModel.initChannels(context)
            }, 1000)
        }

        Column {
            LazyColumn(
                modifier = Modifier.fillMaxWidth().heightIn(max = 500.dp)
            ) {
                items(viewModel.channels, key = { it.id }) { channel ->
                    DrawerItem(
                        Modifier.animateItem(fadeInSpec = null, fadeOutSpec = null),
                        hasExpand = true
                    ) {
                        LeftItemGoChannel(
                            channel = channel,
                            onDelete = {
                                deleteNotificationChannel(context, channel.id)
                                Settings.speakRemove(channel.id)
                                Settings.repeatRemove(channel.id)
                                context.showToast(LangUtil.lang(context, R.string.delete_success))
                                viewModel.initChannels(context)
                            },
                            onAction = {
                                openChannelSettings(context, channel.id)
                            },
                            expandShow = it
                        )
                    }
                }
            }

            DrawerItemTips(tips = LangUtil.lang(R.string.tips)+" "+LangUtil.lang(R.string.notify_tips))
        }
    }
}