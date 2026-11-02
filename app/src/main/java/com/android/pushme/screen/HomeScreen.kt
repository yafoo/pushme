package com.android.pushme.screen

import ConnectionIndicator
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Card
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.android.pushme.LocalAppViewModel
import com.android.pushme.LocalHomeMessageViewModel
import com.android.pushme.LocalNavController
import com.android.pushme.LocalScreen
import com.android.pushme.MainActivity
import com.android.pushme.R
import com.android.pushme.components.messagelist.MessageListView
import com.android.pushme.components.messagelist.LocalMessageListState
import com.android.pushme.components.MeContent
import com.android.pushme.components.MeTopBar
import com.android.pushme.components.MeTopBarTitle
import com.android.pushme.components.drawer.HomeDrawer
import com.android.pushme.utils.Permission.goPermission
import com.android.pushme.utils.PermissionName
import kotlinx.coroutines.launch

@Composable
fun HomeScreen(modifier: Modifier = Modifier) {
    val focusManager = LocalFocusManager.current
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val lazyListState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    val viewModel = LocalHomeMessageViewModel.current
    val searchResult by viewModel.searchResult

    val appViewModel = LocalAppViewModel.current
    var permissionTipsShow by remember { mutableStateOf(false) }

    LaunchedEffect(appViewModel.batteryPermit.value, appViewModel.notifyPermit.value) {
        permissionTipsShow = !appViewModel.batteryPermit.value || !appViewModel.notifyPermit.value
    }

    LaunchedEffect(Unit) { // 新增副作用
        viewModel.searchBoxAutoHide()
    }

    BackHandler(enabled = drawerState.isOpen) {
        if(drawerState.isOpen) {
            scope.launch {
                drawerState.close()
            }
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = true,
        drawerContent = {
            HomeDrawer(onButtonClick = {
                if(drawerState.isOpen) {
                    scope.launch {
                        drawerState.close()
                    }
                }
            })
        }
    ) {
        val navController = LocalNavController.current

        Scaffold(
            modifier = modifier.clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) {
                focusManager.clearFocus()
            },
            topBar = {
                MeTopBar(
                    icon = R.drawable.settings,
                    onIconClick = {
                        scope.launch {
                            if(drawerState.isOpen) {
                                drawerState.close()
                            } else {
                                drawerState.open()
                            }
                        }
                    },
                    titleContent = {
                        val badge = if(searchResult.newerCount > 0) searchResult.newerCount.toString() else ""
                        MeTopBarTitle(
                            badge = badge,
                            onTitleDbClick = {
                                scope.launch {
                                    lazyListState.animateScrollToItem(0)
                                }
                            },
                            tooltip = { ConnectionIndicator() }
                        )
                    },
                    actions = {
                        IconButton(onClick = {
                            if(viewModel.searchBox.value.show) {
                                viewModel.setSearchBoxHide()
                                viewModel.refreshList()
                            } else {
                                viewModel.setSearchBoxShow()
                            }
                        }) {
                            Icon(ImageVector.vectorResource(R.drawable.search), contentDescription = null, Modifier.size(26.dp))
                        }
                    }
                )
            },
            floatingActionButton = {
                Button(
                    onClick = {
                        viewModel.setDashboardShow()
                        navController.navigate("dashboard") },
                    shape = CircleShape,
                    contentPadding = PaddingValues(14.dp),
                    modifier = Modifier.defaultMinSize(minWidth = 10.dp),
                    elevation = ButtonDefaults.buttonElevation(4.dp, 10.dp, 0.dp)
                ) {
                    Icon(
                        ImageVector.vectorResource(R.drawable.dashboard),
                        contentDescription = null,
                        Modifier.size(24.dp)
                    )
                }
            }
        ) {
                innerPadding -> MeContent(
                    modifier = Modifier
                        .padding(innerPadding)
                        .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05F))
                ) {
                    if(permissionTipsShow) {
                        PermissionTips()
                    }

                    CompositionLocalProvider(LocalMessageListState provides lazyListState, LocalScreen provides "") {
                        MessageListView(Modifier.padding(top = if(permissionTipsShow) 30.dp else 0.dp) )
                    }
                }
        }
    }
}

@Composable
fun PermissionTips(modifier: Modifier = Modifier) {
    val fontSize = 12.sp
    Card(
        shape = RoundedCornerShape(0.dp),
        backgroundColor = MaterialTheme.colorScheme.surface,
        elevation = 1.dp
    ) {
        Row(
            modifier = Modifier.height(30.dp).fillMaxWidth().padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("权限设置: ", fontSize = fontSize)

            Text("自启动", fontSize = fontSize)
            PermissionButton("去开启", PermissionName.START)

            Spacer(Modifier.width(6.dp))

            Text("电池优化", fontSize = fontSize)
            PermissionButton("去忽略", PermissionName.BATTERY)

            Spacer(Modifier.width(6.dp))

            Text("系统通知", fontSize = fontSize)
            PermissionButton("去开启", PermissionName.NOTIFY)
        }
    }
}

@Composable
fun PermissionButton(title: String = "", action: PermissionName) {
    val mainActivity = LocalActivity.current as MainActivity
    Button(
        modifier = Modifier.defaultMinSize(minWidth = 1.dp, minHeight = 1.dp),
        contentPadding = PaddingValues(horizontal = 5.dp, vertical = 3.dp),
        onClick = {
            goPermission(mainActivity, action, true)
        }
    ) {
        Text(text = title, fontSize = 10.sp, lineHeight = 10.sp)
    }
}