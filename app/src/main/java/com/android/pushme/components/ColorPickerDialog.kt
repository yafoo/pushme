package com.android.pushme.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.fastForEachIndexed
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.mhssn.colorpicker.ColorPicker
import io.mhssn.colorpicker.ColorPickerType
import io.mhssn.colorpicker.ext.toHex
import io.mhssn.colorpicker.ext.transparentBackground

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun ColorPickerDialog(
    show: Boolean,
    onDismissRequest: () -> Unit,
    properties: DialogProperties = DialogProperties(),
    onPickedColor: (Color) -> Unit
) {
    var showDialog by remember(show) {
        mutableStateOf(show)
    }
    var color by remember {
        mutableStateOf(Color.White)
    }

    var selectedIndex by remember { mutableIntStateOf(0) }

    if (showDialog) {
        val list = listOf("Default", "Ring", "Circle", "Classic")
        var colorPickerType by remember {
            mutableStateOf<ColorPickerType>(ColorPickerType.SimpleRing())
        }

        LaunchedEffect(selectedIndex) {
            colorPickerType = when(selectedIndex) {
                1 -> ColorPickerType.Ring()
                2 -> ColorPickerType.Circle()
                3 -> ColorPickerType.Classic()
                else -> ColorPickerType.SimpleRing()
            }
        }

        Dialog(onDismissRequest = {
            onDismissRequest()
            showDialog = false
        }, properties = properties) {
            Box(
                modifier = Modifier
                    .width(IntrinsicSize.Max)
                    .clip(RoundedCornerShape(32.dp))
                    .background(Color.White)
            ) {
                Box(modifier = Modifier.padding(32.dp)) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        SingleChoiceSegmentedButtonRow {
                            list.fastForEachIndexed { index,label ->
                                SegmentedButton(
                                    shape = SegmentedButtonDefaults.itemShape(index = index, count = list.size),
                                    onClick = { selectedIndex = index },
                                    selected = index == selectedIndex,
                                    enabled = true
                                ) {
                                    Text(text = label, softWrap = false)
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        ColorPicker(type = colorPickerType, onPickedColor = {
                            color = it
                        })
                        Spacer(modifier = Modifier.height(16.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(50.dp, 30.dp)
                                    .clip(RoundedCornerShape(50))
                                    .border(0.3.dp, Color.LightGray, RoundedCornerShape(50))
                                    .transparentBackground(verticalBoxesAmount = 4)
                                    .background(color)
                            )
                            Text(
                                text = buildAnnotatedString {
                                    withStyle(SpanStyle(color = Color.Gray)) {
                                        append("#")
                                    }
                                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                                        append(color.toHex())
                                    }
                                },
                                fontSize = 14.sp,
                                fontFamily = FontFamily.Monospace,
                            )
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        OutlinedButton(
                            modifier = Modifier.fillMaxWidth(),
                            onClick = {
                                onPickedColor(color)
                                showDialog = false
                            },
                            shape = RoundedCornerShape(50)
                        ) {
                            Text(text = "确定")
                        }
                    }
                }
            }
        }
    }
}