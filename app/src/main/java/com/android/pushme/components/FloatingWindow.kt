package com.android.pushme.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.android.pushme.R
import com.android.pushme.utils.CacheUtil
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged

enum class AttachedEdge(val value: String) {
    NONE("none"),
    LEFT("left"),
    RIGHT("right"),
    TOP("top"),
    BOTTOM("bottom")
}

data class NearestEdge(
    val attachedEdge: AttachedEdge = AttachedEdge.NONE,
    val isMinimized: Boolean = false
)

open class FloatingState(
    open val defaultPosition: Offset = Offset.Zero,
    open val defaultSize: DpSize = DpSize(100.dp, 100.dp),
    val defaultRadius: Dp = 10.dp,
    val defaultOpacity: Float = 0.6f,
    val defaultAttachedEdge: AttachedEdge = AttachedEdge.NONE,
    val defaultIsMinimized: Boolean = false
) {
    open var position: Offset = defaultPosition
    open var size: DpSize = defaultSize
    open var radius: Dp = defaultRadius
    open var opacity: Float = defaultOpacity
    open var attachedEdge: AttachedEdge = defaultAttachedEdge
    open var isMinimized: Boolean = defaultIsMinimized

    fun reset() {
        position = defaultPosition
        size = defaultSize
        radius = defaultRadius
        opacity = defaultOpacity
        attachedEdge = defaultAttachedEdge
        isMinimized = defaultIsMinimized
    }
}

@Composable
fun FloatingWindow(
    floatingState: FloatingState,
    onPositionChange: (Offset) -> Unit,
    onSizeChange: (Dp) -> Unit,
    onRadiusChange: (Dp) -> Unit,
    onOpacityChange: (Float) -> Unit,
    onReset: () -> Unit,
    onClick: () -> Unit,
    backgroundColor: Color? = null,
    content: @Composable () -> Unit
) {
    var editMode by remember { mutableStateOf(false) }
    // 动画化尺寸和圆角变化
    val animatedSize by animateDpAsState(targetValue = floatingState.size.width, label = "sizeAnimation")
    val animatedRadius by animateDpAsState(targetValue = floatingState.radius, label = "radiusAnimation")

    val density = LocalDensity.current

    // 状态栏(后面再研究)
    val statusBarsHeight = remember(Unit) { CacheUtil.statusBarHeight }
    // 屏幕信息
    val configuration = LocalConfiguration.current
    val containerSize = LocalWindowInfo.current.containerSize

    // 屏幕尺寸（考虑状态栏和导航栏）
    val screenWidth = containerSize.width
    val screenHeight = containerSize.height + statusBarsHeight

    // 最小化时尺寸
    val leftRightDpSize = DpSize(18.dp, 80.dp)
    val topBottomDpSize = DpSize(leftRightDpSize.height, leftRightDpSize.width)
    val getMinimizedSize: (AttachedEdge) -> DpSize = { attachedEdge ->
        when (attachedEdge) {
            AttachedEdge.LEFT, AttachedEdge.RIGHT -> leftRightDpSize
            AttachedEdge.TOP, AttachedEdge.BOTTOM -> topBottomDpSize
            else -> floatingState.size
        }
    }

    // 计算吸附位置
    val onDragEnd: () -> Unit = fun() {
        val newPosition = floatingState.position
        val pxSize = with(density) {floatingState.size.width.toPx()}
        val threshold = with(density) {18.dp.toPx()}

        // 计算最近的边缘
        val edge = calculateNearestEdge(
            position = newPosition,
            size = Size(pxSize, pxSize),
            screenWidth = screenWidth.toFloat(),
            screenHeight = screenHeight.toFloat(),
            threshold = threshold,
            minimizedThreshold = threshold
        )

        if(edge.attachedEdge == AttachedEdge.NONE || (edge.attachedEdge == AttachedEdge.TOP && floatingState.size.width < 80.dp)) {
            floatingState.attachedEdge = AttachedEdge.NONE
            floatingState.isMinimized = false
            return
        }

        val finalSize = if(edge.isMinimized) getMinimizedSize(edge.attachedEdge) else floatingState.size
        val finalDpSize = with(density) { finalSize.toSize() }
        val finalPosition =  when (edge.attachedEdge) {
            AttachedEdge.LEFT -> Offset(0f, newPosition.y)
            AttachedEdge.RIGHT -> Offset(screenWidth - finalDpSize.width, newPosition.y)
            AttachedEdge.TOP -> Offset(newPosition.x, 0f)
            AttachedEdge.BOTTOM -> Offset(newPosition.x, screenHeight - finalDpSize.height)
            else -> newPosition
        }

        // 更新状态
        floatingState.position = finalPosition
        floatingState.attachedEdge = edge.attachedEdge
        floatingState.isMinimized = edge.isMinimized
    }

    // 恢复最小化
    val onRestore: () -> Unit = fun() {
        val currentPosition = floatingState.position
        val finalDpSize = with(density) { floatingState.size.toSize()}
        val finalPosition =  when (floatingState.attachedEdge) {
            AttachedEdge.LEFT -> Offset(0f, currentPosition.y)
            AttachedEdge.RIGHT -> Offset(screenWidth - finalDpSize.width, currentPosition.y)
            AttachedEdge.TOP -> Offset(currentPosition.x, 0f)
            AttachedEdge.BOTTOM -> Offset(currentPosition.x, screenHeight - finalDpSize.height)
            else -> currentPosition
        }

        // 更新状态
        floatingState.position = finalPosition
        floatingState.isMinimized = false
    }

    val windowModifier by remember(floatingState.isMinimized, floatingState.attachedEdge) {
        derivedStateOf {
            val finalSize = if (floatingState.isMinimized) {
                getMinimizedSize(floatingState.attachedEdge)
            } else {
                DpSize(animatedSize, animatedSize)
            }
            val paddingTop = if(floatingState.attachedEdge == AttachedEdge.TOP && floatingState.isMinimized) {
                with(density) { statusBarsHeight.toDp() - topBottomDpSize.height - 3.dp }.coerceAtLeast(0.dp)
            } else 0.dp
//            val offsetDpSize = if (floatingState.isMinimized) {
//                when (floatingState.attachedEdge) {
//                    AttachedEdge.TOP, AttachedEdge.BOTTOM -> {
//                        val offsetX = (floatingState.size.width - topBottomDpSize.width) / 2
//                        DpSize(offsetX, 0.dp)
//                    }
//                    AttachedEdge.LEFT, AttachedEdge.RIGHT -> {
//                        val offsetY = (floatingState.size.height - leftRightDpSize.height) / 2
//                        DpSize( 0.dp, offsetY)
//                    }
//                    else -> DpSize(0.dp, 0.dp)
//                }
//            } else {
//                DpSize(0.dp, 0.dp)
//            }
            Modifier
                .padding(top = paddingTop)
                .size(finalSize)
//                .offset(offsetDpSize.width, offsetDpSize.height)
        }
    }
    Box(
        modifier = Modifier
            .then(windowModifier)
            .clip(RoundedCornerShape(animatedRadius))
            .background((backgroundColor ?: MaterialTheme.colorScheme.surface).copy(alpha = floatingState.opacity))
            .border(
                width = if (editMode) 2.dp else 0.dp,
                color = if (editMode) MaterialTheme.colorScheme.primary.copy(alpha = floatingState.opacity) else Color.Transparent,
                shape = RoundedCornerShape(animatedRadius)
            )
            .pointerInput(editMode) {
                detectDragGestures(
                    onDrag = { change, dragAmount ->
                        change.consume()
                        floatingState.position += dragAmount
                        onPositionChange(floatingState.position)
                    },
                    onDragEnd = {
                        onDragEnd()
                        onPositionChange(floatingState.position)
                    }
                )
            }
            .combinedClickable(
                onClick = {
                    if (editMode) {
                        editMode = false
                    } else if(floatingState.isMinimized) {
                        onRestore()
                        onPositionChange(floatingState.position)
                    } else {
                        onClick()
                    }
                },
                onLongClick = {
                    if (!editMode && !floatingState.isMinimized) {
                        editMode = true
                    }
                },
                onLongClickLabel = "进入编辑模式",
                role = Role.Button
            ),
        contentAlignment = Alignment.Center
    ) {
        content()

        if (editMode) {
            val density = LocalDensity.current
            val fontSize = (animatedSize.value / 100 * 11f).coerceIn(6f, 14f).sp
            val lineHeight = (fontSize.value * 1.06).sp

            // 显示尺寸和圆角信息
            val text = "透明: ${floatingState.opacity.format(2)}\n尺寸: ${animatedSize.value.toInt()}dp\n圆角: ${animatedRadius.value.toInt()}dp"
            Text(
                text = text,
                style = TextStyle(
                    color = MaterialTheme.colorScheme.surface,
                    fontSize = fontSize,
                    lineHeight = lineHeight,
                    shadow = Shadow(
                        color = MaterialTheme.colorScheme.onSurface,
                        offset = Offset(0f, 0f),
                        blurRadius = 3f
                    )
                ),
                modifier = Modifier.align(Alignment.Center)
            )

            // 缩放控制
            ResizeHandle(
                modifier = Modifier.align(Alignment.BottomCenter)
            ) { delta ->
                val deltaDp = with(density) { delta.x.toDp() }
                val newSize = (floatingState.size.width + deltaDp).coerceAtLeast(50.dp) // 最小50dp
                floatingState.size = DpSize(newSize, newSize)
                onSizeChange(newSize)
            }

            // 圆角控制
            CornerHandle(
                modifier = Modifier.align(Alignment.CenterEnd)
            ) { delta ->
                val deltaDp = with(density) { -delta.x.toDp() }
                val maxRadius = minOf(animatedSize / 2, 500.dp) // 最大半径为宽度/2或100dp
                val newRadius = (floatingState.radius + deltaDp).coerceIn(0.dp, maxRadius)
                floatingState.radius = newRadius
                onRadiusChange(newRadius)
            }

            // 透明度控制
            OpacityHandle(
                modifier = Modifier.align(Alignment.TopCenter)
            ) { delta ->
                val deltaDp = with(density) { delta.x.toDp() }
                val newOpacity = (floatingState.opacity + deltaDp.value/150).coerceIn(0f, 1f) // 最小50dp
                floatingState.opacity = newOpacity
                onOpacityChange(newOpacity)
            }

            // 重置控制
            ResetHandle(modifier = Modifier.align(Alignment.CenterStart)) {
                floatingState.reset()
                onPositionChange(floatingState.position)
                onReset()
            }
        }
    }
}

@Composable
fun ResizeHandle(
    modifier: Modifier = Modifier,
    onDrag: (Offset) -> Unit,
) {
    Icon(
        imageVector = ImageVector.vectorResource(R.drawable.resize),
        contentDescription = null,
        modifier = modifier
            .size(20.dp)
            .pointerInput(Unit) {
                detectDragGestures(
                    onDrag = { change, dragAmount ->
                        change.consume()
                        onDrag(dragAmount)
                    }
                )
            }
    )
}

@Composable
fun CornerHandle(
    modifier: Modifier = Modifier,
    onDrag: (Offset) -> Unit
) {
    Icon(
        imageVector = ImageVector.vectorResource(R.drawable.corner),
        contentDescription = null,
        modifier = modifier
            .size(20.dp)
            .pointerInput(Unit) {
                detectDragGestures(
                    onDrag = { change, dragAmount ->
                        change.consume()
                        onDrag(dragAmount)
                    }
                )
            }
    )
}

@Composable
fun OpacityHandle(
    modifier: Modifier = Modifier,
    onDrag: (Offset) -> Unit
) {
    Icon(
        imageVector = ImageVector.vectorResource(R.drawable.opacity),
        contentDescription = null,
        modifier = modifier
            .size(20.dp)
            .pointerInput(Unit) {
                detectDragGestures(
                    onDrag = { change, dragAmount ->
                        change.consume()
                        onDrag(dragAmount)
                    }
                )
            }
    )
}

@Composable
fun ResetHandle(
    modifier: Modifier = Modifier,
    onReset: () -> Unit
) {
    Icon(
        imageVector = ImageVector.vectorResource(R.drawable.reset),
        contentDescription = null,
        modifier = modifier
            .size(20.dp)
            .clickable {
                onReset()
            }
    )
}

@OptIn(FlowPreview::class)
@Composable
fun rememberStableStatusBarHeight(): Int {
    val density = LocalDensity.current
    val defaultHeight = with(density) { 24.dp.roundToPx() }
    var heightPx by remember { mutableIntStateOf(defaultHeight) }

    val statusBars = WindowInsets.statusBars
    val systemBars = WindowInsets.systemBars
    val displayCutout = WindowInsets.displayCutout

    LaunchedEffect(Unit) {
        val statusBarsHeight = maxOf(
            statusBars.getTop(density),
            systemBars.getTop(density),
            displayCutout.getTop(density)
        ).takeIf { it > 0 } ?: defaultHeight
        snapshotFlow { statusBarsHeight }
            .distinctUntilChanged()
            .debounce(100) // 防抖，避免频繁跳变
            .collect { newHeight ->
                heightPx = maxOf(newHeight, heightPx)
            }
    }

    return heightPx
}

private fun calculateNearestEdge(
    position: Offset,
    size: Size,
    screenWidth: Float,
    screenHeight: Float,
    threshold: Float = 50f, // 吸附阈值
    minimizedThreshold: Float = 50f
): NearestEdge {
    val leftDistance = position.x
    val rightDistance = screenWidth - (position.x + size.width)
    val topDistance = position.y
    val bottomDistance = screenHeight - (position.y + size.height)

    val distances = mapOf(
        AttachedEdge.LEFT to leftDistance,
        AttachedEdge.RIGHT to rightDistance,
        AttachedEdge.TOP to topDistance,
        AttachedEdge.BOTTOM to bottomDistance
    )

    val minDistance = distances.values.minOrNull() ?: Float.MAX_VALUE
    val attachedEdge = if (minDistance <= threshold) {
        distances.entries.minByOrNull { it.value }?.key ?: AttachedEdge.NONE
    } else {
        AttachedEdge.NONE
    }
    return NearestEdge(attachedEdge, minDistance <= -minimizedThreshold)
}

private fun Float.format(digits: Int) = "%.${digits}f".format(this)