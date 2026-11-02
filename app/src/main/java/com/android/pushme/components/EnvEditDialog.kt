package com.android.pushme.components

import android.view.KeyEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.android.pushme.R
import com.android.pushme.model.EnvData
import com.android.pushme.utils.LangUtil

@Composable
fun EnvEditDialog(
    modifier: Modifier = Modifier,
    envData: EnvData,
    show: Boolean,
    onDismissRequest: () -> Unit,
    properties: DialogProperties = DialogProperties(
        dismissOnBackPress = false,
        dismissOnClickOutside = false,
        usePlatformDefaultWidth = true
    ),
    onDelete: (EnvData) -> Unit,
    onSave: (EnvData) -> Unit
) {
    if (show) {
        Dialog(onDismissRequest = {
            onDismissRequest()
        }, properties = properties) {
            val focusManager = LocalFocusManager.current
            val focusRequester = remember { FocusRequester() }
            var key by remember { mutableStateOf(envData.key) }
            var value by remember { mutableStateOf(envData.value) }
            val title = LangUtil.lang(if(envData.order == 0) R.string.env_add else R.string.env_edit)

            Box(
                modifier = modifier
                    .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) {
                        focusManager.clearFocus()
                    }
                    .width(IntrinsicSize.Max)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(10.dp)
            ) {

                Column {
                    Text(
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center,
                        text = title,
                        lineHeight = 30.sp
                    )
                    Spacer(modifier = Modifier.height(15.dp))

                    TextField(
                        label = {Text(text = LangUtil.lang(R.string.env_name) )},
                        value = key,
                        onValueChange = {
                            key = it
                        },
                        colors = TextFieldDefaults.colors(
                            unfocusedContainerColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05F)
                        ),
                        placeholder = {
                            Text(
                                text = LangUtil.lang(R.string.env_name),
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            imeAction = ImeAction.Next
                        )
                    )
                    TextField(
                        label = {Text(text = LangUtil.lang(R.string.env_value))},
                        value = value,
                        onValueChange = {
                            value = it
                        },
                        colors = TextFieldDefaults.colors(
                            unfocusedContainerColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05F)
                        ),
                        textStyle = LocalTextStyle.current.copy(
                            fontFamily = FontFamily.Monospace,
                            fontSize = MaterialTheme.typography.bodySmall.fontSize,
                            lineHeight = MaterialTheme.typography.bodySmall.lineHeight,
                            letterSpacing = 0.sp
                        ),
                        placeholder = {
                            Text(
                                text = LangUtil.lang(R.string.env_value),
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                            )
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(focusRequester)
                            .onKeyEvent {
                                if (it.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_ENTER) {
                                    focusRequester.requestFocus()
                                    true
                                } else {
                                    false
                                }
                            },
                        singleLine = false,
                        maxLines = 20,
                        minLines = 3
                    )
                    Spacer(modifier = Modifier.height(15.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        if(envData.order > 0) {
                            val confirmShow = remember{mutableStateOf(false)}

                            Button(
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.secondary,
                                    contentColor = MaterialTheme.colorScheme.onSecondary
                                ),
                                onClick = {
                                    confirmShow.value = true
                                },
                                modifier = Modifier.defaultMinSize(minWidth = 60.dp, minHeight = 34.dp),
                                contentPadding = PaddingValues(horizontal = 0.dp, vertical = 0.dp),
                            ) {
                                Text(text = LangUtil.lang(R.string.delete), color = MaterialTheme.colorScheme.onPrimary)
                            }

                            MeConfirm(show = confirmShow, content = LangUtil.lang(R.string.confirm_delete)) {
                                onDelete(envData)
                            }

                            Spacer(Modifier.width(30.dp))
                        }

                        Button(
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            ),
                            onClick = {
                                onSave(envData.copy(key = key, value = value))
                            },
                            modifier = Modifier.defaultMinSize(minWidth = 60.dp, minHeight = 34.dp),
                            contentPadding = PaddingValues(horizontal = 0.dp, vertical = 0.dp),
                        ) {
                            Text(text = LangUtil.lang(R.string.save), color = MaterialTheme.colorScheme.onPrimary)
                        }
                    }

                }

                Icon(
                    imageVector = ImageVector.vectorResource(R.drawable.clear),
                    contentDescription = null,
                    modifier = Modifier.padding(20.dp).size(30.dp).align(Alignment.TopEnd).offset(25.dp, (-25).dp).clickable {
                        onDismissRequest()
                    }
                )
            }
        }
    }

}