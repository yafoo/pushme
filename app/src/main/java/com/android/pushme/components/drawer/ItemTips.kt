package com.android.pushme.components.drawer

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun DrawerItemTips(modifier: Modifier = Modifier, tips: String = "") {
    Column(modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        DrawerItemLine()
        Text(
            modifier = modifier,
            text = tips,
            fontSize = 10.sp,
            lineHeight = 12.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(0.6f)
        )
    }
}