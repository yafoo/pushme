package com.android.pushme.components.drawer

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProgressIndicatorDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.android.pushme.LocalAppViewModel
import com.android.pushme.R
import com.android.pushme.model.AppUpdateMode
import com.android.pushme.utils.LangUtil
import com.android.pushme.utils.Settings

@Composable
fun DrawerItemAppVersion(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val version = Settings.appVersion
    val appViewModel = LocalAppViewModel.current
    val appUpdateState = appViewModel.appUpdateState

    DrawerItemTitle(LangUtil.lang(R.string.app_version)+": ", titleValue = version, modifier = modifier) {
        DrawerItemButton(click = {
            appViewModel.appUpdateCheck(context, AppUpdateMode.HAND)
        }) {
            if(appUpdateState.isChecking) {
                CircularProgressIndicator(
                    modifier = Modifier
                        .padding(end = 1.dp)
                        .size(10.dp),
                    color = MaterialTheme.colorScheme.onPrimary,
                    strokeWidth = 1.6.dp,
                    trackColor = ProgressIndicatorDefaults.circularIndeterminateTrackColor,
                )
            }
            Text(
                text = if(!appUpdateState.isChecking) LangUtil.lang(R.string.check_update) else LangUtil.lang(R.string.checking),
                fontSize = 10.sp,
                lineHeight = 10.sp
            )
        }
    }
}