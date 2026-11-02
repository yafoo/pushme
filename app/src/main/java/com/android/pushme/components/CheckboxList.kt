package com.android.pushme.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun CheckboxList(modifier: Modifier = Modifier, title: String = "", list: List<String>, spaceWidth: Dp = 5.dp, checkedList: MutableList<String>, onCheckedChange: (value: String, checked: Boolean) -> Unit, modifierItem: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if(title != "") {
            Text(text = title, fontSize = 14.sp)
            Spacer(modifier = Modifier.width(2.dp))
        }
        list.forEach {
            CheckboxItem(
                modifier = modifierItem,
                value = it,
                spaceWidth = spaceWidth,
                checked = checkedList.contains(it),
                onCheckedChange = {checked ->
                    onCheckedChange(it, checked)
                }
            )
        }
    }
}

@Composable
fun CheckboxItem(modifier: Modifier = Modifier, value: String = "", spaceWidth: Dp = 5.dp, checked: Boolean = false, onCheckedChange: (checked: Boolean) -> Unit) {
    var isChecked by remember { mutableStateOf(checked) }
    Row(
        modifier.toggleable(
            value = isChecked,
            onValueChange = {
                isChecked = !isChecked
                onCheckedChange(isChecked)
            },
            role = Role.Checkbox
        ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(
            checked = isChecked,           //是否选中
            onCheckedChange = null,
            modifier = Modifier
                .size(width = 20.dp, height = 20.dp),
            enabled = true,
            colors = CheckboxDefaults.colors(
                checkedColor = MaterialTheme.colorScheme.primary,
                uncheckedColor = MaterialTheme.colorScheme.onSurface.copy(
                    alpha = 0.7f
                )
            )
        )
        Spacer(modifier = Modifier.width(spaceWidth))
        Text(text = value, fontSize = 14.sp)
    }
}