package com.android.pushme.components.messagelist

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.DefaultShadowColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.android.pushme.LocalHomeMessageViewModel
import com.android.pushme.LocalScreen
import com.android.pushme.LocalUserMessageViewModel
import com.android.pushme.MainActivity
import com.android.pushme.R
import com.android.pushme.components.MeConfirm
import com.android.pushme.components.RadioListNew
import com.android.pushme.components.drawer.Line
import com.android.pushme.showToast
import com.android.pushme.ui.theme.FailureColor
import com.android.pushme.utils.LangUtil
import com.android.pushme.utils.Settings
import kotlinx.coroutines.launch

/**
 * 消息列表搜索框
 */
@Composable
fun MessageListSearch(modifier: Modifier = Modifier) {
    val lazyListState = LocalMessageListState.current
    val scope = rememberCoroutineScope()
    val viewModel = if(LocalScreen.current == "user") LocalUserMessageViewModel.current else LocalHomeMessageViewModel.current
    val homeViewModel = LocalHomeMessageViewModel.current
    val search by viewModel.search
    val box by viewModel.searchBox
    val context = LocalContext.current

    Column(modifier = modifier
        .fillMaxWidth()
        .padding(10.dp)
        .clip(RoundedCornerShape(3.dp))
        .shadow(
            elevation = 1.dp,
            spotColor = DefaultShadowColor.copy(alpha = 0.6F),
            ambientColor = DefaultShadowColor.copy(alpha = 0.6F),
            clip = false
        )
        .background(MaterialTheme.colorScheme.surface)
    ) {
        TextField(
            modifier = Modifier.fillMaxWidth(),
            value = search.keyword,
            onValueChange = {
                viewModel.updateKeyword(it)
                viewModel.refreshList {
                    scope.launch {
                        lazyListState.animateScrollToItem(0)
                    }
                }
            },
            colors = TextFieldDefaults.colors(
                unfocusedContainerColor = MaterialTheme.colorScheme.surface
            ),
            placeholder = { Text(text = LangUtil.lang(R.string.search_placeholder), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                imeAction = ImeAction.Next
            ),
            leadingIcon = {
                IconToggleButton(checked = box.filterShow, onCheckedChange = {
                    viewModel.setSearchFilter(it)
                    if(!it) {
                        viewModel.refreshList()
                    }
                }) {
                    Icon(ImageVector.vectorResource(R.drawable.filter), contentDescription = null)
                }
            },
            trailingIcon = {
                if(search.keyword != "") {
                    Icon(
                        ImageVector.vectorResource(R.drawable.clear),
                        contentDescription = null,
                        modifier = Modifier.size(24.dp).clickable{
                            viewModel.updateKeyword("")
                            viewModel.refreshList()
                        }
                    )
                }
            }
        )

        if(box.filterShow) {
            Column(modifier = Modifier
                .padding(top = 10.dp, bottom = 5.dp, start = 15.dp)
            ) {
                val listTheme = Settings.getSettingOptions(listOf("全部", "信息", "成功", "警告", "失败"), R.array.search_theme_options)
                RadioListNew(
                    modifier = Modifier.padding(vertical = 5.dp).horizontalScroll(rememberScrollState()),
                    modifierItem = Modifier.padding(start = 2.dp, end = 3.dp),
                    title = LangUtil.lang(R.string.theme) + ":",
                    list = listTheme,
                    selectedValue = listTheme.entries.find { it.key == search.theme } ?: listTheme.entries.first(),
                    onClick = {
                        viewModel.setSearchFilterTheme(it.key)
                        viewModel.refreshList {
                            scope.launch {
                                lazyListState.animateScrollToItem(0)
                            }
                        }
                    }
                )
                val listFields = Settings.getSettingOptions(listOf("标题", "内容", "标题+内容"), R.array.search_fields_options)
                RadioListNew(
                    modifier = Modifier.padding(vertical = 5.dp).horizontalScroll(rememberScrollState()),
                    modifierItem = Modifier.padding(start = 2.dp, end = 3.dp),
                    title = LangUtil.lang(R.string.field) + ":",
                    list = listFields,
                    selectedValue = listFields.entries.find { it.key == search.field } ?: listFields.entries.first(),
                    onClick = {
                        viewModel.setSearchFilterField(it.key)
                        viewModel.refreshList {
                            scope.launch {
                                lazyListState.animateScrollToItem(0)
                            }
                        }
                    }
                )
                val listStatus = Settings.getSettingOptions(listOf("全部", "已读", "未读", "收藏", "未藏"), R.array.search_status_options)
                RadioListNew(
                    modifier = Modifier.padding(vertical = 5.dp).horizontalScroll(rememberScrollState()),
                    modifierItem = Modifier.padding(start = 2.dp, end = 3.dp),
                    title = LangUtil.lang(R.string.status) + ":",
                    list = listStatus,
                    selectedValue = listStatus.entries.find { it.key == search.state } ?: listStatus.entries.first(),
                    onClick = {
                        viewModel.setSearchFilterState(it.key)
                        viewModel.refreshList {
                            scope.launch {
                                lazyListState.animateScrollToItem(0)
                            }
                        }
                    }
                )

                Line(Modifier.padding(top = 10.dp, bottom = 5.dp, end = 15.dp), MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))

                // 批量操作
                if(box.actionShow) {
                    val mainActivity = LocalActivity.current as MainActivity
                    val actionList = Settings.getSettingOptions(listOf("read", "unread", "star", "unstar", "delete"), R.array.search_action_list)
                    val actionValue = remember{ mutableStateOf("") }
                    val actionTips = remember{ mutableStateOf("") }
                    val confirmShow = remember{ mutableStateOf(false) }
                    val screen = LocalScreen.current
                    Row {
                        actionList.forEach {(key, value) ->
                            val buttonColor = when(key) {
                                "read", "star" -> MaterialTheme.colorScheme.primary
                                "unread", "unstar" -> MaterialTheme.colorScheme.secondary
                                else -> FailureColor
                            }
                            Button(
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = buttonColor,
                                    contentColor = if(key == "delete") MaterialTheme.colorScheme.surface else contentColorFor(buttonColor)
                                ),
                                onClick = {
                                    actionValue.value = key
                                    actionTips.value = if(key == "delete") LangUtil.lang(context, R.string.delete_tips) else LangUtil.lang(context, R.string.action_tips) + "${actionList[key]}？"
                                    confirmShow.value = true
                                },
                                contentPadding = PaddingValues(vertical = 5.dp, horizontal = 8.dp),
                                shape = RoundedCornerShape(2.dp),
                                modifier = Modifier.defaultMinSize(minWidth = 1.dp, minHeight = 1.dp).padding(end = 8.dp)
                            ) {
                                Text(text = value, fontSize = 10.sp, lineHeight = 10.sp)
                            }
                        }
                    }

                    MeConfirm(show = confirmShow, title = LangUtil.lang(context, R.string.action_title), content = actionTips.value, confirm = {
                        viewModel.updateMessageAll(actionValue.value)
                        confirmShow.value = false
                        mainActivity.showToast(LangUtil.lang(context, R.string.action_success))
                        if(screen == "user") {
                            homeViewModel.updateUserUnreadList()
                            homeViewModel.refreshList()
                        }
                        if(actionValue.value != "star" && actionValue.value != "unstar") {
                            homeViewModel.updateUserUnreadList()
                        }
                        if(actionValue.value == "delete") {
                            homeViewModel.updateMessageCount()
                        }
                    })

                    Line(Modifier.padding(top = 5.dp, bottom = 5.dp, end = 15.dp), MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
                }

                Row(Modifier.fillMaxWidth().padding(end = 15.dp)) {
                    Text(
                        modifier = Modifier.weight(1f),
                        text = LangUtil.lang(context, R.string.search_results_prev)+" ${viewModel.searchResult.value.total} "+LangUtil.lang(context, R.string.search_results_next),
                        fontSize = 10.sp,
                        lineHeight = 16.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(0.6f)
                    )
                    Text(
                        modifier = Modifier.clickable {
                            viewModel.setSearchFilterAction(!box.actionShow)
                        },
                        text = LangUtil.lang(context, R.string.action_all),
                        fontSize = 10.sp,
                        lineHeight = 16.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(0.6f),
                        textDecoration = TextDecoration.Underline
                    )
                }
            }
        }
    }
}
