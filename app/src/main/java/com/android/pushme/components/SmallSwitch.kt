package com.android.pushme.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Switch
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.unit.dp

/**
 * 列表行尾的小开关，占位和 `RadioButton(Modifier.size(40.dp))` 完全一致。
 *
 * ## 为什么不能直接 `Switch(Modifier.size(40.dp))`
 * Material3 的 Switch 内部是 `requiredSize(52.dp, 32.dp)` —— 固定尺寸、**无视外部约束**。
 * 外面套 `size(40.dp)` 只会让 52x32 的内容**溢出**到左右相邻控件上，并不会把它变小。
 *
 * 所以这里用 `scale(0.75f)` 做视觉缩放（52*0.75≈39dp、32*0.75=24dp），
 * 外面套一个 40dp 的 Box 保持布局占位 —— 换掉 RadioButton 后行宽不会变。
 *
 * ## 为什么点击在外层 Box 上
 * 内层 Switch 的 `onCheckedChange` 传 null，关掉它自己的点击；
 * 触摸区交给外层 40dp 的 Box。原因：缩放后的 Switch 自身触摸区只有 39x24，
 * 比原来 RadioButton 的 40dp 小一圈，手指不好点。
 *
 * @param checked        当前是否开启
 * @param onCheckedChange 切换回调，参数是**切换后**的目标状态
 */
@Composable
fun SmallSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    Box(
        modifier = modifier
            .size(40.dp)
            .clickable(enabled = enabled) { onCheckedChange(!checked) },
        contentAlignment = Alignment.Center
    ) {
        Switch(
            checked = checked,
            onCheckedChange = null,
            enabled = enabled,
            modifier = Modifier.scale(0.75f)
        )
    }
}
