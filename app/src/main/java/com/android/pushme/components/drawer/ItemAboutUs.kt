package com.android.pushme.components.drawer

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.android.pushme.R
import com.android.pushme.utils.LangUtil

@Composable
fun DrawerItemAboutUs(modifier: Modifier = Modifier, expandShow: Boolean) {
    DrawerItemMore(modifier = modifier, expandShow, itemTitle = {
        DrawerItemTitle(LangUtil.lang(R.string.about_me), expandShow = expandShow)
    }) {
        Column(modifier = modifier.fillMaxWidth()) {
            DrawerItem {
                LeftItemGoUrl(title = LangUtil.lang(R.string.feedback), text = LangUtil.lang(R.string.write), url = "https://me.i-i.me/article/72.html")
            }
            DrawerItem {
                LeftItemGoUrl(title = LangUtil.lang(R.string.update_log), text = LangUtil.lang(R.string.view), url = "https://push.i-i.me/docs/version")
            }
            DrawerItem {
                DrawerItemAppVersion()
            }
        }
    }
}