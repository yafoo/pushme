package com.android.pushme.components.drawer

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.android.pushme.MainActivity
import com.android.pushme.utils.Permission.goPermission
import com.android.pushme.utils.PermissionName

@Composable
fun DrawerItemGoAction(modifier: Modifier = Modifier, title: String, titleValue: String = "", text: String = "", action: PermissionName? = null) {
    val mainActivity = LocalActivity.current as MainActivity

    DrawerItemTitle(title, titleValue = titleValue, modifier = modifier) {
        if(action != null) {
            DrawerItemButton(text, {
                goPermission(mainActivity, action, titleValue == "已设置")
            }, Modifier.width(42.dp))
        }
    }
}