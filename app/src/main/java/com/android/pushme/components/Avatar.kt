package com.android.pushme.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.rememberAsyncImagePainter
import coil.request.ImageRequest
import com.android.pushme.utils.FaceManager
import java.util.Locale
import kotlin.code

@Composable
fun Avatar(
    modifier: Modifier = Modifier,
    user: String = "",
    face: String = "",
    size: Dp = 42.dp,
    textSize: TextUnit? = null,
    elevation: Dp = 0.dp,
    contentDescription: String = "Avatar"

) {
    val context = LocalContext.current
    val localFile = FaceManager.getFaceFile(context, user)
    val bgColor = if(localFile != null || face.startsWith("http")) MaterialTheme.colorScheme.surface else generateColorFromUser(user)
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(size)
            .then(
                if (elevation > 0.dp) Modifier.shadow(elevation, CircleShape) else Modifier
            )
            .clip(CircleShape)
            .background(bgColor)
    ) {
        when {
            localFile != null -> {
                // 优先显示本地缓存头像
                Image(
                    painter = rememberAsyncImagePainter(
                        model = ImageRequest.Builder(context)
                            .data(localFile)
                            .crossfade(true)
                            .build()
                    ),
                    contentDescription = contentDescription,
                    modifier = Modifier
                        .size(size)
                        .clip(CircleShape),
                    contentScale = ContentScale.Crop
                )
            }
            face.startsWith("http") -> {
                // 本地没有缓存，显示网络图片
                Image(
                    painter = rememberAsyncImagePainter(
                        model = ImageRequest.Builder(context)
                            .data(face)
                            .crossfade(true)
                            .build()
                    ),
                    contentDescription = contentDescription,
                    modifier = Modifier
                        .size(size)
                        .clip(CircleShape),
                    contentScale = ContentScale.Crop
                )
            }
            else -> {
                val words = if(face != "") face else getFirstCharacter(user)
                val wordsLength = words.codePoints().count()
                var fontSize = textSize ?: (size.value / 2.4).sp
                var maxLines = 1
                val with = (fontSize.value + 2).dp
                when {
                    wordsLength > 1 && wordsLength <=4 -> {
                        fontSize = (fontSize.value / 2).sp
                        maxLines = 2
                    }
                    wordsLength > 4 -> {
                        fontSize = (fontSize.value / 3).sp
                        maxLines = 3
                    }
                }
                Text(
                    modifier = Modifier.width(with),
                    text = words,
                    color = Color.White,
                    fontSize = fontSize,
                    lineHeight = fontSize,
                    fontWeight = FontWeight.Bold,
                    maxLines = maxLines,
                    overflow = TextOverflow.Clip,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

private fun getFirstCharacter(user: String): String {
    return if (user.isNotEmpty()) {
        // 获取第一个完整的 Unicode 代码点
        val firstCodePoint = user.codePointAt(0)
        // 将代码点转换为字符串
        String(Character.toChars(firstCodePoint)).uppercase(Locale.getDefault())
    } else {
        "?"
    }
}

//private fun generateColorFromUser(user: String): Color {
//    val colors = listOf(
//        Color(0xFFF44336), // Red
//        Color(0xFFE91E63), // Pink
//        Color(0xFF9C27B0), // Purple
//        Color(0xFF673AB7), // Deep Purple
//        Color(0xFF3F51B5), // Indigo
//        Color(0xFF2196F3), // Blue
//        Color(0xFF03A9F4), // Light Blue
//        Color(0xFF00BCD4), // Cyan
//        Color(0xFF009688), // Teal
//        Color(0xFF4CAF50), // Green
//        Color(0xFF8BC34A), // Light Green
//        Color(0xFFCDDC39), // Lime
//        Color(0xFFFFC107), // Amber
//        Color(0xFFFF9800), // Orange
//        Color(0xFFFF5722), // Deep Orange
//        Color(0xFF795548), // Brown
////        Color(0xFF9E9E9E), // Grey
//        Color(0xFF607D8B)  // Blue Grey
//    )
//
//    // 更复杂的哈希算法确保均匀分布
//    var hash = 0
//    for (i in user.indices) {
//        hash = user[i].code + ((hash shl 5) - hash) + (i * 31)
//        // 防止溢出，限制在合理范围内
//        hash = hash and 0x7FFFFFFF
//    }
//
//    val index = (hash % colors.size).absoluteValue()
//    return colors[index]
//}

//private fun generateColorFromUser(user: String): Color {
//    val colors = listOf(
//        Color(0xFFEF5350), // 红
//        Color(0xFFF57C00), // 橙
//        Color(0xFFFFA726), // 黄
//        Color(0xFF66BB6A), // 绿
//        Color(0xFF26A69A), // 青
//        Color(0xFF42A5F5), // 蓝
//        Color(0xFF5C6BC0), // 靛
//        Color(0xFF7E57C2), // 紫
//        Color(0xFFEC407A), // 粉
//        Color(0xFF8D6E63), // 棕
//        Color(0xFF78909C), // 灰
//        Color(0xFF43A047)  // 深绿
//    )
//
//    // 更复杂的哈希算法确保均匀分布
//    var hash = 0
//    for (i in user.indices) {
//        hash = user[i].code + ((hash shl 5) - hash) + (i * 31)
//        // 防止溢出，限制在合理范围内
//        hash = hash and 0x7FFFFFFF
//    }
//
//    val index = (hash % colors.size).absoluteValue()
//    return colors[index]
//}

private fun generateColorFromUser(user: String): Color {
    val colors = listOf(
        // 粉色系 - 加深
        Color(0xFFEC407A), // 玫红
        Color(0xFFD81B60), // 深粉
        Color(0xFFC2185B), // 酒红

        // 紫色系 - 加深
        Color(0xFFAB47BC), // 紫罗兰
        Color(0xFF8E24AA), // 深紫
        Color(0xFF7B1FA2), // 皇家紫

        // 蓝色系 - 加深
        Color(0xFF42A5F5), // 亮蓝
        Color(0xFF1E88E5), // 钴蓝
        Color(0xFF1976D2), // 宝蓝
        Color(0xFF0097A7), // 青蓝

        // 绿色系 - 加深
        Color(0xFF66BB6A), // 草绿
        Color(0xFF43A047), // 森林绿
        Color(0xFF388E3C), // 深绿
        Color(0xFF00897B), // 翡翠绿

        // 暖色调 - 加深
        Color(0xFFFFA726), // 橙黄
        Color(0xFFFB8C00), // 橙色
        Color(0xFFF57C00), // 深橙

        // 其他清新色调 - 加深
        Color(0xFF7E57C2), // 薰衣草紫
        Color(0xFF26C6DA), // 湖蓝
        Color(0xFF26A69A), // 碧绿
        Color(0xFF9CCC65), // 黄绿
        Color(0xFFFFCA28), // 金黄
        Color(0xFF8D6E63), // 咖啡棕

        // 更多加深的清新色调
        Color(0xFF5C6BC0), // 矢车菊蓝
        Color(0xFFEF5350), // 珊瑚红
        Color(0xFF78909C), // 石板灰（柔和的深色）
        Color(0xFF7CB342), // 苹果绿
        Color(0xFFFFB300), // 琥珀色
        Color(0xFF546E7A)  // 钢蓝
    )

    // 更复杂的哈希算法确保均匀分布
    var hash = 0
    for (i in user.indices) {
        hash = user[i].code + ((hash shl 5) - hash) + (i * 31)
        // 防止溢出，限制在合理范围内
        hash = hash and 0x7FFFFFFF
    }

    val index = (hash % colors.size).absoluteValue()
    return colors[index]
}

private fun Int.absoluteValue(): Int {
    return if (this < 0) -this else this
}