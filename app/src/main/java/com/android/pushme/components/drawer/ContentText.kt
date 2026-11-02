package com.android.pushme.components.drawer

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun ContentText(text: String, color: Color = MaterialTheme.colorScheme.onSurface, modifier: Modifier = Modifier) {
    Text(
        fontSize = 14.sp,
        lineHeight = 20.sp,
        text = text,
        color = color,
        modifier = modifier.padding(top = 5.dp, bottom = 5.dp)
    )
}