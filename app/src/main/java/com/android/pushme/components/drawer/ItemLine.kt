package com.android.pushme.components.drawer

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp

@Composable
fun DrawerItemLine(modifier: Modifier = Modifier) {
    Line(modifier, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
}

@Composable
fun Line(modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)) {
    val displayMetrics = LocalContext.current.resources.displayMetrics
    val density = displayMetrics.density
    val pxDp = 1 / density
    Spacer(
        modifier = modifier
            .height(pxDp.dp)
            .background(color)
            .fillMaxWidth()
    )
}

@Composable
fun VLine(modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)) {
    val density = LocalDensity.current
    val pixDp = with(density) {
        1f.toDp()
    }
    Spacer(modifier.fillMaxHeight().width(pixDp).background(color))
}