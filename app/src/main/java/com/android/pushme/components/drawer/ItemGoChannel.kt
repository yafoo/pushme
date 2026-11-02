package com.android.pushme.components.drawer

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.android.pushme.R
import com.android.pushme.STATE_CHANNEL
import com.android.pushme.model.ChannelDetail
import com.android.pushme.ui.theme.FailureColor
import com.android.pushme.utils.LangUtil
import com.android.pushme.utils.Settings

@Composable
fun LeftItemGoChannel(modifier: Modifier = Modifier, channel: ChannelDetail, onDelete: () -> Unit, onAction: () -> Unit, expandShow: Boolean) {
    DrawerItemMore(
        modifier = modifier,
        expandShow = expandShow,
        itemTitle = {
            DrawerItemTitle(channel.name, expandShow = expandShow)
        }
    ) {
        Column(Modifier.fillMaxWidth()) {
            DrawerItemTitle(LangUtil.lang(R.string.channel_set)) {
                Row {
                    DrawerItemButton(LangUtil.lang(R.string.delete), onDelete, Modifier.width(42.dp), colors = ButtonDefaults.buttonColors(
                        containerColor = FailureColor,
                        contentColor = MaterialTheme.colorScheme.surface
                    ))
                    Spacer(Modifier.width(10.dp))
                    DrawerItemButton(LangUtil.lang(R.string.set), onAction, Modifier.width(42.dp))
                }
            }

            if(channel.id != STATE_CHANNEL) {
                DrawerItem(hasExpand = true) {
                    var speakSet by Settings.speakSet(channel.id)
                    DrawerItemRadioList(
                        title = LangUtil.lang(R.string.speak_set),
                        storeValue = speakSet,
                        expandShow = it
                    ) { value ->
                        speakSet = value
                    }
                }

                DrawerItem(hasExpand = true) {
                    var repeatSet by Settings.repeatSet(channel.id)
                    DrawerItemRadioList(
                        title = LangUtil.lang(R.string.repeat_set),
                        storeValue = repeatSet,
                        expandShow = it
                    ) { value ->
                        repeatSet = value
                    }
                }
            }
        }
    }

}