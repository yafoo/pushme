package com.android.pushme.components.drawer

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun DrawerItemButton(title: String = "", click: () -> Unit, modifier: Modifier = Modifier, colors: ButtonColors = ButtonDefaults.buttonColors(), content: @Composable (() -> Unit)? = null) {
    Button(
        modifier = modifier.defaultMinSize(minWidth = 1.dp, minHeight = 1.dp),
        contentPadding = PaddingValues(horizontal = 5.dp, vertical = 5.dp),
        colors = colors,
        onClick = click
    ) {
        if(content == null) {
            Text(text = title, fontSize = 10.sp, lineHeight = 10.sp)
        } else {
            content()
        }
    }
}