package com.android.pushme.components.drawer

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.dp
import com.android.pushme.R

@Composable
fun DrawerItem(modifier: Modifier = Modifier, hasExpand: Boolean = false, content: @Composable (expandShow: Boolean) -> Unit) {
    var expandShow by remember{ mutableStateOf(false) }

    Column(modifier.fillMaxWidth()) {
        DrawerItemLine()
        Box(
            Modifier
                .fillMaxWidth(),
            Alignment.TopEnd
        ) {
            content(expandShow)

            if(hasExpand) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .clickable {
                            expandShow = !expandShow
                        },
                    Alignment.TopEnd
                ) {
                    val imageVector: ImageVector = if(expandShow) ImageVector.vectorResource(R.drawable.expand_less)
                        else ImageVector.vectorResource(R.drawable.expand_more)
                    Icon(
                        imageVector,
                        contentDescription = null,
                        Modifier
                            .padding(top = 13.dp, end = 6.dp)
                            .size(24.dp),
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                    )
                }
            }
        }
    }
}