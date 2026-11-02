package com.android.pushme.ui.theme

import android.app.Activity
import android.app.Service
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.core.graphics.toColorInt
import com.android.pushme.utils.Settings

private val LightColorScheme = lightColorScheme(
        primary = Turquoise,
        secondary = Orange,
        tertiary = Turquoise60

        /* Other default colors to override
    background = Color(0xFFFFFBFE),
    surface = Color(0xFFFFFBFE),
    onPrimary = Color.White,
    onSecondary = Color.White,
    onTertiary = Color.White,
    onBackground = Color(0xFF1C1B1F),
    onSurface = Color(0xFF1C1B1F),
    */
)

private val DarkColorScheme = darkColorScheme(
    primary = Turquoise,
    secondary = Orange40,
    tertiary = Turquoise10,
    onPrimary = Color.Black,
    onSecondary = Orange
)

object ThemeManager {
    var appTheme by mutableStateOf(Settings.appTheme)
        private set
    var appColor by mutableStateOf(Settings.appColor)
        private set
    var selfColor by mutableStateOf(Settings.selfColor)
        private set

    fun init() {
        appTheme = Settings.appTheme
        appColor = Settings.appColor
        selfColor = Settings.selfColor
    }

    fun setCustomColor(colorHex: String) {
        selfColor = colorHex
        Settings.selfColor = colorHex
    }
}

@Composable
fun PushMeTheme(
        content: @Composable () -> Unit
) {
    val darkTheme = when (ThemeManager.appTheme) {
        "系统" -> isSystemInDarkTheme()
        "暗色" -> true
        else -> false
    }
    val useDynamicColor = when (ThemeManager.appColor) {
        "系统" -> true
        else -> false
    }
    val useSelfColor = ThemeManager.appColor == "自定义"

    val colorScheme = when {
        useDynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        useSelfColor -> {
            // 使用自定义颜色方案
            if (darkTheme) selfDarkColorScheme() else selfLightColorScheme()
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
//            val window = (view.context as Activity).window
            // 兼容悬浮窗
            val window = when (val context = view.context) {
                is Activity -> context.window
                is Service -> null
                else -> null
            }
            window?.let {
                it.statusBarColor = colorScheme.primary.toArgb()
                WindowCompat.getInsetsController(it, view).isAppearanceLightStatusBars = darkTheme
            }
        }
    }

    MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content
    )
}

// 生成基于自定义主色的亮色方案
fun selfLightColorScheme(): ColorScheme {
    val primaryColor = try {
        Color(ThemeManager.selfColor.toColorInt())
    } catch (_: IllegalArgumentException) {
        Material3Color
    }
    return lightColorScheme(
        primary = primaryColor,
        onPrimary = Color.White,
        primaryContainer = primaryColor.copy(alpha = 0.1f),
        onPrimaryContainer = primaryColor,
        surface = Color.White,
        onSurface = Color.Black,
        background = Color(0xFFFEF7FF),
        onBackground = Color(0xFF1C1B1F)
    )
}

// 生成基于自定义主色的暗色方案
fun selfDarkColorScheme(): ColorScheme {
    val primaryColor = try {
        Color(ThemeManager.selfColor.toColorInt())
    } catch (_: IllegalArgumentException) {
        Material3Color
    }
    return darkColorScheme(
        primary = primaryColor,
        onPrimary = Color.Black,
        primaryContainer = primaryColor.copy(alpha = 0.2f),
        onPrimaryContainer = primaryColor.copy(alpha = 0.8f),
        surface = Color(0xFF1C1B1F),
        onSurface = Color.White,
        background = Color(0xFF141218),
        onBackground = Color(0xFFE6E1E5)
    )
}