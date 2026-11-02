package com.android.pushme.components

import android.annotation.SuppressLint
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

@SuppressLint("SuspiciousModifierThen")
fun Modifier.leftBorder(strokeWidth: Dp, color: Color): Modifier = this.then(
    drawWithContent {
        drawContent()
        val startX = strokeWidth.toPx() / 2
        drawLine(
            color = color,
            start = Offset(startX, 0f),
            end = Offset(startX, size.height),
            strokeWidth = strokeWidth.toPx()
        )
    }
)

@Composable
fun MeContent(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(
        modifier = modifier.fillMaxSize()
    ) {
        content()
    }
}

@Composable
fun rememberLifecycleState(): Lifecycle.State {
    val lifecycleOwner = LocalLifecycleOwner.current
    var state by remember { mutableStateOf(lifecycleOwner.lifecycle.currentState) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, _ ->
            state = lifecycleOwner.lifecycle.currentState
        }

        lifecycleOwner.lifecycle.addObserver(observer)

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    return state
}

@Composable
fun OnResumeEffect(action: () -> Unit) {
    val lifecycleState = rememberLifecycleState()

    LaunchedEffect(lifecycleState) {
        if (lifecycleState.isAtLeast(Lifecycle.State.RESUMED)) {
            action()
        }
    }
}