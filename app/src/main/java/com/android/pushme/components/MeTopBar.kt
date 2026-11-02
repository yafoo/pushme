package com.android.pushme.components

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.android.pushme.LocalNavController
import com.android.pushme.R

@Composable
fun MeTopBarWithBack(modifier: Modifier = Modifier, title: String = "PushMe", titleContent: @Composable (() -> Unit)? = null, onIconClick: () -> Unit = {}, actions: @Composable RowScope.() -> Unit = {}) {
    val navController = LocalNavController.current
    var canBack by remember { mutableStateOf(true) }

    BackHandler {
        if(canBack) {
            canBack = false
            navController.popBackStack()
        }
        onIconClick()
    }

    MeTopBar(
        modifier = modifier,
        title = title,
        titleContent = titleContent,
        icon = R.drawable.arrow_back,
        onIconClick = {
            if(canBack) {
                canBack = false
                navController.popBackStack()
            }
            onIconClick()
        },
        actions = actions
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MeTopBar(modifier: Modifier = Modifier, title: String = "PushMe", titleContent: @Composable (() -> Unit)? = null, onIconClick: () -> Unit = {}, icon: Int, actions: @Composable RowScope.() -> Unit = {}) {
    TopAppBar(
        modifier = modifier.height(40.dp),
        title = {
            titleContent?.invoke() ?: run {
                MeTopBarTitle(title)
            }
        },
        navigationIcon = {
            IconButton(onClick = {
                onIconClick()
            }) {
                Icon(ImageVector.vectorResource(icon), contentDescription = null, Modifier.size(24.dp))
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.primary,
            navigationIconContentColor = MaterialTheme.colorScheme.onPrimary,
            titleContentColor = MaterialTheme.colorScheme.onPrimary,
            actionIconContentColor = MaterialTheme.colorScheme.onPrimary
        ),
        actions = actions
    )
}

@Composable
fun MeTopBarTitle(title: String = "PushMe", badge: String = "", url: String = "", onTitleDbClick: () -> Unit = {}, tooltip: (@Composable (Modifier) -> Unit)? = null, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxSize().pointerInput(Unit) {
            detectTapGestures(
                onDoubleTap = { onTitleDbClick() }
            )
        },
        contentAlignment = Alignment.CenterStart
    ) {
        val titleSize = 18.sp
        val textStyle = LocalTextStyle.current
        val titleHeight = with(LocalDensity.current) { textStyle.lineHeight.toDp() }
        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = title, fontSize = titleSize, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Box(
                Modifier.height(height = titleHeight)
            ) {
                if(badge != "") {
                    MeBadge(text = badge)
                }
                if(tooltip != null) {
                    val tooltipOffsetY = with(LocalDensity.current) { ((textStyle.lineHeight.value - titleSize.value) / 2 + 2).sp.toDp() }
                    Box(
                        Modifier.align(Alignment.BottomStart).padding(start = 4.dp, bottom = tooltipOffsetY)
                    ) {
                        tooltip(Modifier)
                    }
                }
            }
        }

        if(url != "") {
            Text(text = url, fontSize = 10.sp, lineHeight = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 27.dp, start = 1.dp))
        }
    }
}
