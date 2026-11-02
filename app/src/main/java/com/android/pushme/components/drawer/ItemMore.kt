package com.android.pushme.components.drawer

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.android.pushme.components.leftBorder

@Composable
fun DrawerItemMore(modifier: Modifier = Modifier, expandShow: Boolean, itemTitle: @Composable () -> Unit, itemMore: @Composable () -> Unit) {
    Column(modifier = modifier.fillMaxWidth()) {
        itemTitle()
        AnimatedVisibility(
            modifier = modifier
                .leftBorder(2.dp, MaterialTheme.colorScheme.primary)
                .padding(start = 5.dp)
                .fillMaxWidth(),
            visible = expandShow
        ) {
            itemMore()
        }
    }
}