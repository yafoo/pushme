package com.android.pushme.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.String
import kotlin.collections.Map

@Composable
fun RadioListNew(modifier: Modifier = Modifier, title: String = "", list: Map<String, String>, selectedValue: Map.Entry<String, String>, spaceWidth: Dp = 5.dp, onClick: (value: Map.Entry<String, String>) -> Unit, modifierItem: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if(title != "") {
            Text(text = title, fontSize = 14.sp)
            Spacer(modifier = Modifier.width(2.dp))
        }
        list.forEach {
            RadioItemNew(
                modifier = modifierItem,
                value = it,
                selected = it == selectedValue,
                spaceWidth = spaceWidth,
                onClick = onClick
            )
        }
    }
}

@Composable
fun RadioItemNew(modifier: Modifier = Modifier, value: Map.Entry<String, String>, selected: Boolean = false, spaceWidth: Dp = 5.dp, onClick: (value: Map.Entry<String, String>) -> Unit) {
    Row(
        modifier.selectable(
            selected = selected,
            onClick = {onClick(value)},
            role = Role.RadioButton
        ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(
            selected = selected,           //是否选中
            onClick = null,                                          //点击事件
            modifier = Modifier
                .size(width = 20.dp, height = 20.dp),            //修饰符
            colors = RadioButtonDefaults.colors(
                selectedColor = MaterialTheme.colorScheme.primary,
                unselectedColor = MaterialTheme.colorScheme.onSurface.copy(
                    alpha = 0.7f
                )
            )
        )
        Spacer(modifier = Modifier.width(spaceWidth))
        Text(text = value.value, fontSize = 14.sp)
    }
}