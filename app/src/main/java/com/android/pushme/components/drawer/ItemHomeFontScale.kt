package com.android.pushme.components.drawer

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Slider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.android.pushme.LocalAppViewModel
import com.android.pushme.R
import com.android.pushme.utils.LangUtil

@Composable
fun DrawerItemHomeFontScale(modifier: Modifier = Modifier, expandShow: Boolean) {
    val appViewModel = LocalAppViewModel.current
    val fontScale = appViewModel.fontScale

    DrawerItemMore(modifier = modifier, expandShow, itemTitle = {
        DrawerItemTitle(LangUtil.lang(R.string.list_font_size)+": ", titleValue = "${fontScale.value}", expandShow = expandShow)
    }) {
        Column(Modifier.fillMaxWidth()) {
            DrawerItemLine()
            Slider(
                value = fontScale.value,
                onValueChange = {
                    appViewModel.updateFontScale(it)
                },
                valueRange = 0.5f..1.5f,
                steps = 19
            )
        }
    }
}