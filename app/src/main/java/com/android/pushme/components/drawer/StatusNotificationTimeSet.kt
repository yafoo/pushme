package com.android.pushme.components.drawer

import android.app.TimePickerDialog
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.android.pushme.R
import com.android.pushme.utils.Settings

@Composable
fun StatusNotificationTimeSet(modifier: Modifier = Modifier) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Text(text = stringResource(R.string.mute_time_limit) + "：", fontSize = 14.sp)
        TimeSet(time = Settings.statusNotifyMuteStartTime) {
            Settings.statusNotifyMuteStartTime = it
        }
        Spacer(modifier = Modifier
            .padding(horizontal = 3.dp)
            .width(3.dp)
            .height(1.dp)
            .background(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))

        )
        TimeSet(time = Settings.statusNotifyMuteEndTime) {
            Settings.statusNotifyMuteEndTime = it
        }
    }
}

@Composable
fun TimeSet(modifier: Modifier = Modifier, time: Int = 0, onChange: (newTime: Int) -> Unit = {}) {
    val context = LocalContext.current
    var storeHour = 0
    var storeMinute = time
    if(time > 60) {
        storeHour = (time / 100)
        storeMinute = time % 100
    }
    var timeHour by remember { mutableIntStateOf(storeHour) }
    var timeMinute by remember { mutableIntStateOf(storeMinute) }

    val timeSetListener = TimePickerDialog.OnTimeSetListener { _, hourOfDay, minute ->
        run {
            timeHour = hourOfDay
            timeMinute = minute
            onChange(hourOfDay * 100 + minute)
        }
    }
    val timePickerDialog = TimePickerDialog(context, timeSetListener, timeHour, timeMinute, true)

    OutlinedButton(
        shape = RoundedCornerShape(3.dp),
        contentPadding = PaddingValues(5.dp),
        modifier = modifier.defaultMinSize(minWidth = 1.dp, minHeight = 1.dp),
        onClick = {
            timePickerDialog.show()
        }) {
        Icon(ImageVector.vectorResource(R.drawable.date_range), contentDescription = null, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(1.dp))

        val strHour: String = if(timeHour < 10) "0$timeHour" else timeHour.toString()
        val strMinute: String = if(timeMinute < 10) "0$timeMinute" else timeMinute.toString()
        Text(text = "$strHour:$strMinute", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.9f))
    }
}