package com.android.pushme.components.drawer

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.android.pushme.MainActivity
import com.android.pushme.R
import com.android.pushme.components.MeConfirm
import com.android.pushme.sendServiceBroadcast
import com.android.pushme.showToast
import com.android.pushme.utils.LangUtil
import com.android.pushme.utils.PushKey.updatePushKey
import com.android.pushme.utils.Settings

@Composable
fun DrawerItemPushKey(modifier: Modifier = Modifier, expandShow: Boolean) {
    DrawerItemMore(modifier = modifier, expandShow, itemTitle = {
        DrawerItemTitle("push_key", expandShow = expandShow)
    }) {
        Column(
            modifier = modifier
                .fillMaxWidth()
        ) {
            val mainActivity = LocalActivity.current as MainActivity

            var pushKeyStr by remember { mutableStateOf(Settings.pushKey) }
            var buttonStr by remember { mutableStateOf("") }

            val confirmShow = remember { mutableStateOf(false) }
            var titleStr by remember { mutableStateOf("") }
            var contentStr by remember { mutableStateOf("") }

            LaunchedEffect(Settings.pushKey) {
                if (Settings.pushKey == "") {
                    pushKeyStr = LangUtil.lang(mainActivity, R.string.push_key_empty)
                    buttonStr = LangUtil.lang(mainActivity, R.string.get)
                    titleStr = LangUtil.lang(mainActivity, R.string.get_confirm)
                    contentStr = LangUtil.lang(mainActivity, R.string.get_confirm_content)
                } else {
                    buttonStr = LangUtil.lang(mainActivity, R.string.replace)
                    titleStr = LangUtil.lang(mainActivity, R.string.replace_confirm)
                    contentStr = LangUtil.lang(mainActivity, R.string.replace_confirm_content)
                }
            }

            DrawerItemLine()

            DrawerItemTitle(pushKeyStr) {
                DrawerItemButton(buttonStr, {
                    confirmShow.value = true
                }, Modifier.width(42.dp))
            }

            MeConfirm(show = confirmShow, title = titleStr, content = contentStr, confirm = {
                confirmShow.value = false

                updatePushKey { error, value ->
                    run {
                        if(error != "") {
                            mainActivity.runOnUiThread {
                                mainActivity.showToast(LangUtil.lang(mainActivity, R.string.get_fail) + error)
                            }
                        } else {
                            Settings.pushKey = value
                            pushKeyStr = value

                            mainActivity.runOnUiThread {
                                mainActivity.showToast(LangUtil.lang(mainActivity, R.string.get_success))
                            }
                            mainActivity.sendServiceBroadcast("refresh_push_key")
                        }
                    }
                }
            })
        }
    }
}