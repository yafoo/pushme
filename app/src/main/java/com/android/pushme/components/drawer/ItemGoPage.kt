package com.android.pushme.components.drawer

import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.android.pushme.LocalNavController

@Composable
fun DrawerItemGoPage(modifier: Modifier = Modifier, title: String, titleValue: String = "", text: String, page: String, onClick: () -> Unit) {
    val navController = LocalNavController.current
    DrawerItemTitle(title, titleValue = titleValue, modifier = modifier) {
        DrawerItemButton(text, {
            onClick()
            navController.navigate(page)
        }, Modifier.width(42.dp))
    }
}