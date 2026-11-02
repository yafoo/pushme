package com.android.pushme.components.drawer

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Slider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.android.pushme.MainActivity
import com.android.pushme.R
import com.android.pushme.sendServiceBroadcast
import com.android.pushme.utils.LangUtil
import com.android.pushme.utils.Settings
import kotlin.math.roundToInt

/**
 * MQTT 心跳间隔设置（滑块，界面单位**分钟**，1~20 分钟，步进 1 分钟）。
 *
 * 对应 Paho 的 `keepAliveInterval`，是**连接参数**（随 CONNECT 报文发给服务端），
 * 所以改完必须重新连接才生效 —— 在滑动结束（onValueChangeFinished）时广播
 * `refresh_mqtt_host`，让服务把客户端整个重建一遍、重发 CONNECT。
 *
 * 为什么不在 onValueChange 里重连：拖动过程中每跳一档都会触发，会疯狂重连。
 * 所以重连只放在 onValueChangeFinished（一次手势只触发一次）。
 *
 * ⚠️ 单位：**界面按分钟，`Settings.mqttHeartbeat` 存的仍是秒**。
 * 因为 `keepAliveInterval` 的原生单位就是秒，存秒可以直接映射、不用换算层，
 * 也不会出现「旧值被当成新单位误读」的迁移问题。换算只发生在本文件里（×60）。
 *
 * ⚠️ 滑块驱动的是「档位下标」而不是真实数值：
 * 分钟数 = [MIN_MINUTES] + 下标 * [STEP_MINUTES]，全程 Int 运算，
 * 浮点数只用来表示下标（0..N 这种小整数，Float 精确表示），**结果不可能是小数**。
 * 若反过来拿分钟数当滑块值（valueRange = 1f..20f），Slider 的档位是
 * fraction*(max-min)+min 算出来的，会出现 19.99999… 这类值，就得靠 roundToInt 事后修正。
 *
 * 想改档位密度只动下面三个常量即可，[STEP_COUNT] / [SLIDER_STEPS] 会自动跟着变。
 */
@Composable
fun DrawerItemMqttHeartbeat(modifier: Modifier = Modifier, expandShow: Boolean) {
    val mainActivity = LocalActivity.current as MainActivity

    // 存储的秒 → 分钟 → 档位下标（越界值先夹紧，再取整到最近一档）
    var index by remember {
        val minutes = (Settings.mqttHeartbeat / SECONDS_PER_MINUTE)
            .coerceIn(MIN_MINUTES, MAX_MINUTES)
        mutableIntStateOf(((minutes - MIN_MINUTES) / STEP_MINUTES).coerceIn(0, STEP_COUNT))
    }
    val minutes = MIN_MINUTES + index * STEP_MINUTES

    DrawerItemMore(modifier = modifier, expandShow, itemTitle = {
        DrawerItemTitle(
            LangUtil.lang(R.string.mqtt_heartbeat) + ": ",
            titleValue = "${minutes}min",
            expandShow = expandShow
        )
    }) {
        Column(Modifier.fillMaxWidth()) {
            DrawerItemLine()
            Slider(
                value = index.toFloat(),
                onValueChange = { raw ->
                    // raw 是 0..STEP_COUNT 之间的浮点数，roundToInt 后必然是合法下标；
                    // 分钟数由整数乘加得出，不存在小数。
                    index = raw.roundToInt().coerceIn(0, STEP_COUNT)
                    Settings.mqttHeartbeat = (MIN_MINUTES + index * STEP_MINUTES) * SECONDS_PER_MINUTE
                },
                onValueChangeFinished = {
                    // 心跳是连接参数，改完必须重连才生效
                    mainActivity.sendServiceBroadcast("refresh_mqtt_host")
                },
                valueRange = 0f..STEP_COUNT.toFloat(),
                steps = SLIDER_STEPS
            )
            DrawerItemTips(tips = LangUtil.lang(R.string.mqtt_heartbeat_tips))
        }
    }
}

// ==================== 档位参数（改这里就能调整密度） ====================
private const val SECONDS_PER_MINUTE = 60
private const val MIN_MINUTES = 1
private const val MAX_MINUTES = 20
private const val STEP_MINUTES = 1
// 档位个数（含首尾）：1/2/…/20 共 20 档 → 下标范围 0..19
private const val STEP_COUNT = (MAX_MINUTES - MIN_MINUTES) / STEP_MINUTES
// Material3 Slider 的 steps 是「中间档位数」，总取值 = steps + 2，所以这里要减 1
private const val SLIDER_STEPS = STEP_COUNT - 1
