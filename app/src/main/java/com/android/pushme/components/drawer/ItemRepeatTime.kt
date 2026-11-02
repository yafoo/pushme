package com.android.pushme.components.drawer

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Slider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.android.pushme.R
import com.android.pushme.utils.LangUtil
import com.android.pushme.utils.Settings
import kotlin.math.roundToInt

/**
 * 重复通知间隔设置（滑块）。
 *
 * ⚠️ 与 `DrawerItemMqttHeartbeat` 同一套写法：滑块驱动「档位下标」而不是「秒数」，
 * 秒数 = [MIN_SECONDS] + 下标 * [STEP_SECONDS] 全程 Int 运算，结果不可能是小数。
 * 若直接拿秒数当滑块值（valueRange = 10f..90f），档位是 fraction*(max-min)+min 算出来的浮点数，
 * 例如 1/8*80+10 实际是 19.99999…，裸 toInt() 会得到 19 而不是 20。
 */
@Composable
fun LeftItemRepeatTime(modifier: Modifier = Modifier, expandShow: Boolean) {
    var index by remember {
        val seconds = Settings.repeatTime.coerceIn(MIN_SECONDS, MAX_SECONDS)
        mutableIntStateOf(((seconds - MIN_SECONDS) / STEP_SECONDS).coerceIn(0, STEP_COUNT))
    }
    val time = MIN_SECONDS + index * STEP_SECONDS

    DrawerItemMore(modifier = modifier, expandShow, itemTitle = {
        DrawerItemTitle(LangUtil.lang(R.string.repeat_time)+": ", titleValue = "${time}s", expandShow = expandShow)
    }) {
        Column(Modifier.fillMaxWidth()) {
            DrawerItemLine()
            Slider(
                value = index.toFloat(),
                onValueChange = { raw ->
                    index = raw.roundToInt().coerceIn(0, STEP_COUNT)
                    Settings.repeatTime = MIN_SECONDS + index * STEP_SECONDS
                },
                valueRange = 0f..STEP_COUNT.toFloat(),
                steps = SLIDER_STEPS
            )
        }
    }
}

// ==================== 档位参数（改这里就能调整密度） ====================
private const val MIN_SECONDS = 10
private const val MAX_SECONDS = 90
private const val STEP_SECONDS = 10
// 档位个数（含首尾）：10/20/…/90 共 9 档 → 下标范围 0..8
private const val STEP_COUNT = (MAX_SECONDS - MIN_SECONDS) / STEP_SECONDS
// Material3 Slider 的 steps 是「中间档位数」，总取值 = steps + 2，所以这里要减 1
private const val SLIDER_STEPS = STEP_COUNT - 1
