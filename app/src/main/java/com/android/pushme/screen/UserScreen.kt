package com.android.pushme.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.dp
import com.android.pushme.LocalScreen
import com.android.pushme.LocalUserMessageViewModel
import com.android.pushme.R
import com.android.pushme.components.messagelist.MessageListView
import com.android.pushme.components.messagelist.LocalMessageListState
import com.android.pushme.components.MeContent
import com.android.pushme.components.MeTopBarTitle
import com.android.pushme.components.MeTopBarWithBack
import kotlinx.coroutines.launch

@Composable
fun UserScreen(modifier: Modifier = Modifier) {
    val focusManager = LocalFocusManager.current
    val viewModel = LocalUserMessageViewModel.current
    val search by viewModel.search
    val searchResult by viewModel.searchResult

    val lazyListState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) { // 新增副作用
        viewModel.searchBoxAutoHide()
    }

    Scaffold(
        modifier = modifier.clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) {
            focusManager.clearFocus()
        },
        topBar = {
            MeTopBarWithBack(
                onIconClick = {
                    scope.launch {
                        viewModel.clearData()
                    }
                },
                titleContent = {
                    val title = if(search.user == " ") "?" else search.user
                    val badge = if(searchResult.newerCount > 0) searchResult.newerCount.toString() else ""
                    MeTopBarTitle(
                        title = title,
                        badge = badge,
                        onTitleDbClick = {
                            scope.launch {
                                lazyListState.animateScrollToItem(0)
                            }
                        })
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
    ) {
        innerPadding -> MeContent(
            modifier = Modifier
            .padding(innerPadding)
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05F))
        ) {
            CompositionLocalProvider(LocalMessageListState provides lazyListState, LocalScreen provides "user") {
                MessageListView()
            }
        }
    }
}