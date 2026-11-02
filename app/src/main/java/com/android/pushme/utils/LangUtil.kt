package com.android.pushme.utils

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import com.android.pushme.R
import com.android.pushme.data.AutoSaveState

object LangUtil {
    var current by AutoSaveState(KEY_APP_LANGUAGE, "系统")
    val localeList = mapOf("系统" to null, "中文" to "zh", "英文" to "en")
    val localeCode: String?
        get() = localeList[current]

    @Composable
    fun lang(@StringRes id: Int): String {
        return stringResource(id)
    }

    @Composable
    fun lang(@StringRes id: Int, vararg args: Any): String {
        return stringResource(id, *args)
    }

    fun lang(context: Context, @StringRes id: Int): String {
        return context.getString(id)
    }

    /** 带占位符的版本（如 `安装失败：%1$s`）。Kotlin 会优先选上面那个非 vararg 的重载 */
    fun lang(context: Context, @StringRes id: Int, vararg args: Any): String {
        return context.getString(id, *args)
    }

    @Composable
    fun langOnOff(state: String): String {
        val labels = stringArrayResource(R.array.on_off_options)
        return if(state == "开启") labels[0] else labels[1]
    }
}