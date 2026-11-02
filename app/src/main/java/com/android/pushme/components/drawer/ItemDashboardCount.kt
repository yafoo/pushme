package com.android.pushme.components.drawer

import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.android.pushme.LocalHomeMessageViewModel
import com.android.pushme.R
import com.android.pushme.components.MeConfirm
import com.android.pushme.sendServiceBroadcast
import com.android.pushme.showToast
import com.android.pushme.utils.CacheUtil
import com.android.pushme.utils.LangUtil

@Composable
fun LeftItemDashboardCount(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val viewModel = LocalHomeMessageViewModel.current
    val total = viewModel.dashboardCount
    val confirmShow = remember{ mutableStateOf(false) }

    DrawerItemTitle(LangUtil.lang(R.string.data_messages)+": ", titleValue = "${total.value}", modifier = modifier) {
        DrawerItemButton(LangUtil.lang(R.string.clear), {
            confirmShow.value = true
        }, Modifier.width(42.dp))

        val successTips = LangUtil.lang(R.string.clear_success_tips)
        MeConfirm(show = confirmShow, title = LangUtil.lang(R.string.clear_confirm_title), content = LangUtil.lang(R.string.clear_confirm_content), confirm = {
            viewModel.clearDashboard()
            confirmShow.value = false
            if(CacheUtil.floatingMessageId > 0) {
                context.sendServiceBroadcast("refresh_floating_message")
            }
            context.showToast(successTips)
        })
    }
}