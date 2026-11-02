package com.android.pushme.components.drawer

import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.android.pushme.LocalAppViewModel
import com.android.pushme.R
import com.android.pushme.Utils
import com.android.pushme.components.OnResumeEffect
import com.android.pushme.notify.NotifyHelper
import com.android.pushme.utils.LangUtil
import com.android.pushme.utils.PermissionName

@Composable
fun DrawerItemPermission(modifier: Modifier = Modifier, expandShow: Boolean) {
    DrawerItemMore(modifier = modifier, expandShow, itemTitle = {
        DrawerItemTitle(LangUtil.lang(R.string.permission_setting), expandShow = expandShow)
    }) {
        val context = LocalContext.current
        val viewModel = LocalAppViewModel.current

        LaunchedEffect(Unit) {
            viewModel.initPermit(context)
        }
        OnResumeEffect {
            Handler(Looper.getMainLooper()).postDelayed({
                viewModel.initPermit(context)
            }, 1000)
        }

        val setText = LangUtil.lang(R.string.set)
        val enabledText = LangUtil.lang(R.string.enabled)
        val unenabledText = LangUtil.lang(R.string.unenabled)

        Column(modifier = Modifier.fillMaxWidth()) {
            if(Utils.isAndroid12()) {
                DrawerItem {
                    val titleValue = if(viewModel.alarmPermit.value) enabledText else unenabledText
                    DrawerItemGoAction(title = LangUtil.lang(R.string.alarm_reminder) + ": ", titleValue = titleValue, text = setText, action = PermissionName.ALARM)
                }
            }

            DrawerItem {
                DrawerItemGoAction(title = LangUtil.lang(R.string.auto_start)+": ", titleValue = LangUtil.lang(R.string.auto_start_tips), text = setText, action = PermissionName.START)
            }
            DrawerItem {
                val titleValue = if(viewModel.batteryPermit.value) enabledText else unenabledText
                DrawerItemGoAction(title = LangUtil.lang(R.string.battery_ignore)+": ", titleValue = titleValue, text = setText, action = PermissionName.BATTERY)
            }
            DrawerItem { // 安卓13+（Build.VERSION_CODES.TIRAMISU 33）需要，后期再测试
                val titleValue = if(viewModel.notifyPermit.value) enabledText else unenabledText
                DrawerItemGoAction(title = LangUtil.lang(R.string.notify_permission)+": ", titleValue = titleValue, text = setText, action = PermissionName.NOTIFY)
            }
            if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                DrawerItem(hasExpand = true) {
                    DrawerItemChannels(expandShow = it)
                }
            }
            DrawerItem {
                DrawerItemGoAction(title = LangUtil.lang(R.string.app_lock)+": ", titleValue = LangUtil.lang(R.string.app_lock_tips))
            }

            // 监听系统通知（即系统里的「通知使用权」）：读取其它应用的通知并转发给服务。
            // 这是特殊权限，系统不弹授权框，只能跳系统设置页手动开，所以状态实时检测 + 手动跳转。
            DrawerItem {
                var granted by remember { mutableStateOf(NotifyHelper.isGranted(context)) }
                OnResumeEffect {
                    Handler(Looper.getMainLooper()).postDelayed({
                        granted = NotifyHelper.isGranted(context)
                    }, 500)
                }
                DrawerItemTitle(
                    title = LangUtil.lang(R.string.notify_access) + ": ",
                    titleValue = if(granted) enabledText else unenabledText
                ) {
                    DrawerItemButton(LangUtil.lang(R.string.set), {
                        NotifyHelper.goNotificationAccessSetting(context)
                    }, Modifier.width(42.dp))
                }
            }

            DrawerItemTips(tips = LangUtil.lang(R.string.tips)+" "+LangUtil.lang(R.string.permission_tips))
        }
    }
}