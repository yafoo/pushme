package com.android.pushme.components.drawer

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.android.pushme.LocalHomeMessageViewModel
import com.android.pushme.LocalUserMessageViewModel
import com.android.pushme.R
import com.android.pushme.utils.LangUtil
import com.android.pushme.utils.Settings

@Composable
fun DrawerItemMessageSet(modifier: Modifier = Modifier, expandShow: Boolean) {
    DrawerItemMore(modifier = modifier, expandShow, itemTitle = {
        DrawerItemTitle(LangUtil.lang(R.string.message_setting), expandShow = expandShow)
    }) {
        Column(modifier = modifier.fillMaxWidth()) {
            val viewModel = LocalHomeMessageViewModel.current
            val userViewModel = LocalUserMessageViewModel.current
            DrawerItem(hasExpand = true) {
                DrawerItemRadioList(
                    title = LangUtil.lang(R.string.pin_group_message_top),
                    storeValue = Settings.msgUserTop,
                    expandShow = it
                ) { value ->
                    Settings.msgUserTop = value
                    viewModel.msgUserTop.value = value
                    userViewModel.msgUserTop.value = value
                    viewModel.refreshList()
                }
            }
            DrawerItem(hasExpand = true) {
                DrawerItemRadioList(
                    title = LangUtil.lang(R.string.no_group_to_group),
                    storeValue = Settings.msgUserEmpty,
                    expandShow = it
                ) { value ->
                    Settings.msgUserEmpty = value
                    viewModel.msgUserEmpty.value = value
                    userViewModel.msgUserEmpty.value = value
                    viewModel.refreshList()
                }
            }
            DrawerItem(hasExpand = true) {
                DrawerItemRadioList(
                    title = LangUtil.lang(R.string.click_group_message_to),
                    keys = listOf("分组", "对话", "详情"),
                    labelsResId = R.array.click_group_message_options,
                    storeValue = Settings.msgUserClick,
                    expandShow = it
                ) { value ->
                    Settings.msgUserClick = value
                }
            }
            DrawerItem(hasExpand = true) {
                DrawerItemHomeFontScale(expandShow = it)
            }
            DrawerItem(hasExpand = true) {
                DrawerItemRadioList(
                    title = LangUtil.lang(R.string.auto_load_more),
                    storeValue = Settings.msgLoadMore,
                    expandShow = it
                ) { value ->
                    Settings.msgLoadMore = value
                    val autoLoadMore = value == "开启"
                    viewModel.updateResult(viewModel.searchResult.value.copy(autoLoadMore = autoLoadMore))
                    userViewModel.updateResult(userViewModel.searchResult.value.copy(autoLoadMore = autoLoadMore))
                }
            }
            DrawerItem(hasExpand = true) {
                DrawerItemRadioList(
                    title = LangUtil.lang(R.string.link_open_method),
                    keys = listOf("系统", "PushMe"),
                    labelsResId = R.array.link_open_options,
                    storeValue = Settings.linkClickMethod,
                    tips = LangUtil.lang(R.string.tips)+" "+LangUtil.lang(R.string.link_tips),
                    expandShow = it
                ) { value ->
                    Settings.linkClickMethod = value
                }
            }
            DrawerItem(hasExpand = true) {
                DrawerItemRadioList(
                    title = LangUtil.lang(R.string.html_js_support),
                    keys = listOf("开启", "禁用"),
                    storeValue = Settings.htmlJs,
                    expandShow = it
                ) { value ->
                    Settings.htmlJs = value
                }
            }
            DrawerItem(hasExpand = true) {
                DrawerItemRadioList(
                    title = LangUtil.lang(R.string.message_default_state),
                    keys = listOf("已读", "未读"),
                    labelsResId = R.array.read_unread_options,
                    storeValue = Settings.msgReadState,
                    expandShow = it
                ) { value ->
                    Settings.msgReadState = value
                }
            }
        }
    }
}