package com.android.pushme.components.drawer

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.android.pushme.MainActivity
import com.android.pushme.R
import com.android.pushme.Utils
import com.android.pushme.components.ColorPickerDialog
import com.android.pushme.sendServiceBroadcast
import com.android.pushme.ui.theme.ThemeManager
import com.android.pushme.utils.LangUtil
import com.android.pushme.utils.LocaleUtil
import com.android.pushme.utils.Settings
import io.mhssn.colorpicker.ext.toHex
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun DrawerItemAppSet(modifier: Modifier = Modifier, expandShow: Boolean) {
    DrawerItemMore(modifier = modifier, expandShow, itemTitle = {
        DrawerItemTitle(LangUtil.lang(R.string.app_setting), expandShow = expandShow)
    }) {
        val activity = LocalActivity.current
        val scope = rememberCoroutineScope()

        Column(modifier = modifier.fillMaxWidth()) {
            DrawerItem(hasExpand = true) {
                DrawerItemRadioList(
                    title = stringResource(R.string.app_language),
                    keys = listOf("系统", "中文", "英文"),
                    labelsResId = R.array.app_language_options,
                    storeValue = Settings.appLanguage,
                    expandShow = it
                ) { key ->
                    Settings.appLanguage = key
                    LangUtil.current = key
                    scope.launch {
                        delay(200) // 这样试试
                        LocaleUtil.activityRecreate(activity)
                    }
                }
            }
            DrawerItem(hasExpand = true) {
                DrawerItemRadioList(
                    title = LangUtil.lang(R.string.app_theme),
                    keys = listOf("系统", "暗色", "亮色"),
                    labelsResId = R.array.app_theme_options,
                    storeValue = Settings.appTheme,
                    expandShow = it
                ) { value ->
                    Settings.appTheme = value
                    ThemeManager.init()
                }
            }
            DrawerItem(hasExpand = true) {
                var showColorPicker by remember { mutableStateOf(false) }
                DrawerItemRadioList(
                    title = LangUtil.lang(R.string.app_color),
                    keys = listOf("系统", "PushMe", "自定义"),
                    labelsResId = R.array.app_color_options,
                    storeValue = Settings.appColor,
                    expandShow = it,
                    contentMore = {
                        if(ThemeManager.appColor == "自定义") {
                            DrawerItemTitle(LangUtil.lang(R.string.current_color)+": ", titleValue = ThemeManager.selfColor, modifier = modifier) {
                                DrawerItemButton(LangUtil.lang(R.string.select_color), {
                                    showColorPicker = true
                                }, Modifier.width(52.dp))
                            }

                            ColorPickerDialog(
                                show = showColorPicker,
                                onDismissRequest = {
                                    showColorPicker = false
                                },
                                onPickedColor = { newColor ->
                                    showColorPicker = false
                                    ThemeManager.setCustomColor(newColor.toHex(true))
                                }
                            )
                        }
                    }
                ) { value ->
                    Settings.appColor = value
                    ThemeManager.init()
                }
            }
            DrawerItem(hasExpand = true) {
                val context = LocalContext.current
                DrawerItemRadioList(
                    title = LangUtil.lang(R.string.recent_task),
                    keys = listOf("显示", "隐藏"),
                    labelsResId = R.array.show_hide_options,
                    storeValue = Settings.recentTask,
                    expandShow = it
                ) { value ->
                    Settings.recentTask = value
                    Utils.setAppFromRecents(context, value == "隐藏")
                }
            }
            DrawerItem(hasExpand = true) {
                DrawerItemStatusNotificationSet(expandShow = it)
            }
            DrawerItem(hasExpand = true) {
                DrawerItemRadioList(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    title = LangUtil.lang(R.string.speak_content),
                    keys = listOf("标题", "内容", "标题+内容"),
                    labelsResId = R.array.speak_fields_options,
                    storeValue = Settings.speakFields,
                    expandShow = it
                ) { value ->
                    Settings.speakFields = value
                }
            }
            DrawerItem(hasExpand = true) {
                LeftItemRepeatTime(expandShow = it)
            }
            DrawerItem(hasExpand = true) {
                val mainActivity = activity as MainActivity
                DrawerItemRadioList(
                    title = LangUtil.lang(R.string.message_action),
                    keys = listOf("显示", "隐藏"),
                    labelsResId = R.array.show_hide_options,
                    storeValue = Settings.messageAction,
                    expandShow = it
                ) { value ->
                    Settings.messageAction = value
                    mainActivity.sendServiceBroadcast("refresh_message_set")
                }
            }
        }
    }
}