package com.android.pushme.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.android.pushme.ui.theme.Orange
import com.android.pushme.ui.theme.WhiteColor

@Composable
fun MeBadge(
    text: String,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val height = with(density) { 18.sp.toDp() }
    Box(
        modifier = modifier.height(height),
        contentAlignment = Alignment.TopStart
    ) {
        val lineHeight = with(density) { 12.dp.toSp() }
        Text(
            text = text,
            color = WhiteColor.copy(alpha = 0.8f),
            fontSize = 8.sp,
            lineHeight = lineHeight,
            textAlign = TextAlign.Center,
            maxLines = 1,
            modifier = Modifier
                .defaultMinSize(minWidth = 12.dp)
                .padding(0.dp)
                .height(12.dp)
                .background(
                    color = Orange,
                    shape = RoundedCornerShape(6.dp)
                )
                .padding(horizontal = 2.dp)
        )
    }
}