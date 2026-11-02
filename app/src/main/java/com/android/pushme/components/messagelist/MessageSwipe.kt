package com.android.pushme.components.messagelist

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Card
import androidx.compose.material.DismissDirection
import androidx.compose.material.DismissValue
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.FractionalThreshold
import androidx.compose.material.SwipeToDismiss
import androidx.compose.material.rememberDismissState
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.dp
import com.android.pushme.R
import com.android.pushme.data.Message
import com.android.pushme.ui.theme.FailureColor

/**
 * 通用 Swipe 手势容器（Home/User/Chat 页面共用）
 */
@OptIn(ExperimentalMaterialApi::class)
@Composable
fun MessageSwipe(
    modifier: Modifier = Modifier,
    message: Message,
    cardShape: RoundedCornerShape = RoundedCornerShape(3.dp),
    vibrateAction: (duration: Long) -> Unit,
    onDismiss: (action: String) -> Unit,
    content: @Composable () -> Unit
) {
    var currentProgress by remember {
        mutableFloatStateOf(0f)
    }
    val dismissState = rememberDismissState(
        confirmStateChange = {
            if (it == DismissValue.DismissedToStart) {
                if (currentProgress >= 0.4f && currentProgress < 1) {
                    onDismiss("delete")
                    return@rememberDismissState true
                }
            } else if (it == DismissValue.DismissedToEnd) {
                if (currentProgress >= 0.5f && currentProgress < 1) {
                    onDismiss("star")
                } else if(currentProgress >= 0.2f && currentProgress < 0.4f) {
                    onDismiss("read")
                }
            }
            false
        }
    )

    // 缩小重组范围
    ForUpdateData {
        currentProgress = dismissState.progress.fraction
    }

    SwipeToDismiss(
        modifier = modifier,
        state = dismissState,
        directions = setOf(DismissDirection.StartToEnd, DismissDirection.EndToStart),
        dismissThresholds = { direction ->
            FractionalThreshold(if (direction == DismissDirection.StartToEnd) 0.2f else 0.4f)
        },
        background = {
            val direction = dismissState.dismissDirection ?: return@SwipeToDismiss
            val step = when (direction) {
                DismissDirection.EndToStart -> when {
                    currentProgress < 0.4f -> "delete"
                    else -> "delete2"
                }
                DismissDirection.StartToEnd -> when {
                    currentProgress < 0.2f -> "read"
                    currentProgress >= 0.2f && currentProgress < 0.4f -> "read2"
                    currentProgress >= 0.4f && currentProgress < 0.5f -> "star"
                    currentProgress >= 0.5f && currentProgress < 1f -> "star2"
                    else -> ""
                }
            }
            val color by animateColorAsState(
                when (dismissState.targetValue) {
                    DismissValue.Default -> MaterialTheme.colorScheme.surface
                    DismissValue.DismissedToStart -> FailureColor
                    DismissValue.DismissedToEnd -> when(step) {
                        "read2" -> MaterialTheme.colorScheme.primary
                        "star2" -> MaterialTheme.colorScheme.secondary
                        else -> MaterialTheme.colorScheme.surface
                    }
                },
                label = "SwipeToDismissExampleColor",
            )
            val alignment = when (direction) {
                DismissDirection.StartToEnd -> Alignment.CenterStart
                DismissDirection.EndToStart -> Alignment.CenterEnd
            }
            val scale by animateFloatAsState(
                if (step == "delete2" || step == "read2" || step == "star2") 1f else 0.8f,
                label = "SwipeToDismissExampleColorScale"
            )
            val icon = when (direction) {
                DismissDirection.EndToStart -> R.drawable.delete
                DismissDirection.StartToEnd -> when(step) {
                    "read" -> if(message.read) R.drawable.read else R.drawable.unread
                    "read2" -> if(message.read) R.drawable.unread else R.drawable.read
                    "star" -> if(message.star) R.drawable.star else R.drawable.unstar
                    "star2" -> if(message.star) R.drawable.unstar else R.drawable.star
                    else -> R.drawable.logo
                }
            }

            // 当 icon 变化时触发震动
            var preStep by remember { mutableStateOf("") }
            LaunchedEffect(step) {
                if(preStep != "") {
                    vibrateAction(30) // 短震动
                }
                preStep = step
            }

            Box(
                Modifier
                    .fillMaxSize()
                    .background(color)
                    .padding(horizontal = 20.dp),
                contentAlignment = alignment
            ) {
                Icon(
                    ImageVector.vectorResource(icon),
                    contentDescription = null,
                    Modifier
                        .size(24.dp)
                        .scale(scale),
                    tint = contentColorFor(color)
                )
            }
        }
    ) {
        Card(
            shape = cardShape,
            backgroundColor = MaterialTheme.colorScheme.surface,
            elevation = animateDpAsState(
                if (dismissState.dismissDirection != null) 4.dp else 1.dp,
                label = "SwipeToDismissExampleColorElevation"
            ).value
        ) {
            content()
        }
    }
}

@Composable
private fun ForUpdateData(onUpdate: () -> Unit) {
    onUpdate()
}
