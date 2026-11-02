package com.android.pushme.components.drawer

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.android.pushme.MainActivity
import com.android.pushme.Utils

@Composable
fun LeftItemGoUrl(modifier: Modifier = Modifier, title: String, titleValue: String = "", text: String, url: String) {
    val activity = LocalActivity.current as MainActivity

    DrawerItemTitle(title, titleValue = titleValue, modifier = modifier) {
        DrawerItemButton(text, {
            Utils.openUrl(url, activity, "blank")
        }, Modifier.width(42.dp))
    }
}