package com.android.pushme.components.drawer

import androidx.annotation.ArrayRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.android.pushme.R
import com.android.pushme.components.RadioListNew
import com.android.pushme.utils.Settings

@Composable
fun DrawerItemRadioList(modifier: Modifier = Modifier, title: String, keys: List<String>? = null, @ArrayRes labelsResId: Int? = null, storeValue: String, expandShow: Boolean, contentMore: @Composable () -> Unit = {}, tips: String = "", onSelect: (value: String) -> Unit) {
    val list = Settings.getSettingOptions(keys?:listOf("开启", "关闭"), labelsResId?:R.array.on_off_options)
    var selected by remember { 
        mutableStateOf(list.entries.find{it.key == storeValue} ?: list.entries.firstOrNull()) 
    }

    DrawerItemMore(modifier = Modifier, expandShow, itemTitle = {
        DrawerItemTitle("$title: ", titleValue = selected?.value ?: "", expandShow = expandShow)
    }) {
        Column(Modifier.fillMaxWidth()) {
            DrawerItemLine()
            RadioListNew(
                modifier = modifier.height(50.dp),
                modifierItem = Modifier.padding(start = 2.dp, end = 2.dp),
                list = list,
                selectedValue = selected!!,
                spaceWidth = 2.dp,
                onClick = {
                    selected = it
                    onSelect(it.key)
                }
            )
            contentMore()
            if(tips != "") {
                DrawerItemTips(tips = tips)
            }
        }
    }
}