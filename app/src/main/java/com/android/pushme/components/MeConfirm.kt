package com.android.pushme.components

import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.android.pushme.R
import com.android.pushme.utils.LangUtil

@Composable
fun MeConfirm(show: MutableState<Boolean>, title: String = LangUtil.lang(R.string.confirm_default_title), content: String = LangUtil.lang(R.string.confirm_default_content), confirm: () -> Unit) {
    if (show.value) {
        AlertDialog(
            shape = RoundedCornerShape(corner = CornerSize(10.dp)),
            onDismissRequest = { show.value = false },
            title = { Text(text = title, fontSize = 16.sp) },
            text = {
                Text(
                    text = content
                )
            },
            confirmButton = {
                TextButton(
                    onClick = confirm
                ) {
                    Text(text = LangUtil.lang(R.string.sure))
                }
            },
            dismissButton = {
                TextButton(onClick = { show.value = false }) {
                    Text(text = LangUtil.lang(R.string.cancel))
                }
            }
        )
    }
}