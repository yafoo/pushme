package com.android.pushme.components.drawer

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.android.pushme.components.leftBorder

@Composable
fun DrawerItemTitle(title: String, titleValue: String = "", modifier: Modifier = Modifier, expandShow: Boolean = false, content: @Composable () -> Unit = {}) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(50.dp)
            .then(if(expandShow) Modifier.background(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f)).leftBorder(2.dp, MaterialTheme.colorScheme.primary).padding(start = 5.dp) else Modifier),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        SelectionContainer(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    fontSize = 14.sp,
                    lineHeight = 14.sp
                )
                if(titleValue != "") {
                    Text(
                        text = titleValue,
                        fontSize = 12.sp,
                        lineHeight = 14.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(0.6f)
                    )
                }
            }
        }
        content()
    }
}