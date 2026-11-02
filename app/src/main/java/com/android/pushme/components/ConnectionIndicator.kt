import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.android.pushme.LocalAppViewModel
import com.android.pushme.mqtt.MQTTStatus

@Composable
fun ConnectionIndicator(
    modifier: Modifier = Modifier
) {
    val appViewModel = LocalAppViewModel.current
    val status = appViewModel.serviceStatus
    val protocol by appViewModel.serviceProtocol

    // 基础颜色和透明度初始值
    val (baseColor, initialAlpha) = when (status) {
        MQTTStatus.CONNECTING -> MaterialTheme.colorScheme.onPrimary to 0.5f
        MQTTStatus.CONNECTED -> MaterialTheme.colorScheme.onPrimary to 1f
        else -> MaterialTheme.colorScheme.onPrimary to 0.5f
    }

    val alpha = remember { Animatable(initialAlpha) }

    LaunchedEffect(status) {
        when (status) {
            MQTTStatus.CONNECTING -> {
                // 呼吸：0.3f ~ 1.0f 循环
                alpha.animateTo(
                    targetValue = 1f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(durationMillis = 1000),
                        repeatMode = RepeatMode.Reverse
                    )
                )
            }
            MQTTStatus.CONNECTED -> {
                alpha.stop()
                alpha.snapTo(1f) // 常亮
            }
            else -> {
                alpha.stop()
                alpha.snapTo(0.5f) // 灰色半透明
            }
        }
    }

    // 最终颜色 = 基础颜色 * alpha（透明度）
    val finalColor = baseColor.copy(alpha = alpha.value)

    Row(
        modifier = modifier.wrapContentHeight(),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if(status != MQTTStatus.CLOSE) {
            if(appViewModel.serviceIsSelf.value) {
                ConnectionTag(text = "SELF", bgColor = MaterialTheme.colorScheme.onPrimary, color = MaterialTheme.colorScheme.primary)
            }

            ConnectionTag(text = protocol, bgColor = finalColor, color = MaterialTheme.colorScheme.primary)
        }

        if(appViewModel.serviceApiState.value) {
            ConnectionTag(text = "API", bgColor = MaterialTheme.colorScheme.onPrimary, color = MaterialTheme.colorScheme.primary)
        }

        // 前台服务模式下额外亮一个 FGS 标签（与 MQTT 连接状态无关，只看服务运行方式）
        if(appViewModel.serviceForeground.value) {
            ConnectionTag(text = "FGS", bgColor = MaterialTheme.colorScheme.onPrimary, color = MaterialTheme.colorScheme.primary)
        }

        // 通知监听生效中（开关开启 + 已授权）：亮一个 NFY 标签。
        // 状态由 MainActivity 回填（见 AppViewModel.serviceNotifyListen），
        // 与 FGS 同理，开关开了但没授权时不亮 —— 那种情况功能其实没在工作。
        if(appViewModel.serviceNotifyListen.value) {
            ConnectionTag(text = "NFY", bgColor = MaterialTheme.colorScheme.onPrimary, color = MaterialTheme.colorScheme.primary)
        }

        // 定时任务生效中（总开关开启 + 至少一个启用中的任务）：亮一个 TSK 标签。
        // 同样是「真实在跑」才算（见 MainActivity.refreshTaskTag）：
        // 开关开着但一条任务都没有、或任务全部停用时，到点不会有任何东西触发。
        if(appViewModel.serviceTask.value) {
            ConnectionTag(text = "TSK", bgColor = MaterialTheme.colorScheme.onPrimary, color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
fun ConnectionTag(
    modifier: Modifier = Modifier,
    text: String,
    bgColor: Color = MaterialTheme.colorScheme.primary,
    color: Color = MaterialTheme.colorScheme.onPrimary
) {
    Text(
        text = text,
        fontFamily = FontFamily.Monospace, // 使用系统默认等宽字体
        fontSize = 6.sp,
        lineHeight = 8.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.5.sp,
        color = color,
        modifier = modifier
            .defaultMinSize(minWidth = 8.dp)
            .background(color = bgColor, shape = RoundedCornerShape(1.dp))
            .padding(horizontal = 0.5.dp).padding(top = 0.5.dp)
    )
}