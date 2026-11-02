package com.android.pushme.components.drawer

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.android.pushme.R
import com.android.pushme.utils.LangUtil
import com.android.pushme.utils.Settings

@Composable
fun DrawerItemMoreSet(modifier: Modifier = Modifier, expandShow: Boolean, onClick: () -> Unit) {
    DrawerItemMore(modifier = modifier, expandShow, itemTitle = {
        DrawerItemTitle(LangUtil.lang(R.string.other_setting), expandShow = expandShow)
    }) {
        Column(modifier = modifier.fillMaxWidth()) {
            DrawerItem {
                DrawerItemGoPage(title = LangUtil.lang(R.string.file_manage), text = LangUtil.lang(R.string.open), page = "file", onClick = onClick)
            }
            DrawerItem(hasExpand = true) {
                LeftItemTempKey(expandShow = it)
            }
            DrawerItem {
                LeftItemDashboardCount()
            }
            DrawerItem(hasExpand = true) {
                DrawerItemRadioList(
                    title = LangUtil.lang(R.string.crash_log),
                    keys = listOf("发送", "不发送", "关闭"),
                    storeValue = Settings.appLogSend,
                    labelsResId = R.array.crash_log_options,
                    expandShow = it
                ) { value ->
                    Settings.appLogSend = value
                }
            }
        }
    }
}