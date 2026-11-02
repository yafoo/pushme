package com.android.pushme.screen

import androidx.activity.compose.LocalActivity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.android.pushme.MainActivity
import com.android.pushme.R
import com.android.pushme.components.MeContent
import com.android.pushme.components.MeTopBarWithBack
import com.android.pushme.components.SmallSwitch
import com.android.pushme.components.drawer.DrawerItemLine
import com.android.pushme.model.PmfData
import com.android.pushme.model.PmfList
import com.android.pushme.model.PmfManager
import com.android.pushme.sendServiceBroadcast
import com.android.pushme.showToast
import com.android.pushme.utils.LangUtil

@Composable
fun PluginPmfScreen(modifier: Modifier = Modifier) {
    val mainActivity = LocalActivity.current as MainActivity

    Scaffold(
        modifier = modifier,
        topBar = {
            MeTopBarWithBack(
                title = LangUtil.lang(R.string.plugin_method),
            ) },
    ) { innerPadding -> MeContent(
        modifier = Modifier
            .padding(innerPadding)
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05F))
        ) {
            val pmfStateMap = PmfManager.pmfStateMap
            val pmfList = PmfList().pmfList

            LazyColumn(
                contentPadding = PaddingValues(10.dp)
            ) {
                items(pmfList.size, { pmfList[it].key }) {
                    Card(
                        shape = RoundedCornerShape(3.dp),
                        modifier = Modifier
                            .padding(bottom = 10.dp)
                            .fillMaxWidth().animateItem(fadeInSpec = null, fadeOutSpec = null),
                        backgroundColor = MaterialTheme.colorScheme.surface
                    ) {
                        PmfItem(
                            pmf = pmfList[it],
                            state = pmfStateMap[pmfList[it].key]?:false,
                            updateState = { key, state ->
                                PmfManager.updatePmfState(key, state)
                                mainActivity.sendServiceBroadcast("refresh_plugins_pmf")
                                mainActivity.showToast(LangUtil.lang(mainActivity, if(state) R.string.enable_success else R.string.disable_success))
                            }
                        )
                    }
                }
            }

        }

    }
}

@Composable
fun PmfItem(modifier: Modifier = Modifier, pmf: PmfData, state: Boolean, updateState: (key: String, state: Boolean) -> Unit) {
    var showContent by remember { mutableStateOf(false) }

    Column {
        Row(
            modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                Modifier
                    .weight(1f)
                    .clickable(
                        onClick = {
                            showContent = !showContent
                        }
                    )
                    .padding(vertical = 10.dp)
                    .padding(start = 10.dp, end = 5.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "PMF.",
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 10.sp,
                    lineHeight = 10.sp,
                    modifier = Modifier.padding(top = 4.dp),
                )
                Text(
                    text = pmf.key,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Spacer(Modifier.width(2.dp))
            var selected by remember {
                mutableStateOf(state)
            }
            LaunchedEffect(state) {
                selected = state
            }
            SmallSwitch(
                modifier = Modifier.padding(end = 8.dp),
                checked = selected,
                onCheckedChange = {
                    val newState = !state
                    updateState(pmf.key, newState)
                    selected = newState
                }
            )

        }
        AnimatedVisibility(
            modifier = Modifier.fillMaxWidth(),
            visible = showContent
        ) {
            DrawerItemLine()
            SelectionContainer{
                Text(
                    text = pmf.content,
                    fontFamily = FontFamily.Monospace,
                    fontSize = MaterialTheme.typography.bodySmall.fontSize,
                    lineHeight = MaterialTheme.typography.bodySmall.lineHeight,
                    letterSpacing = 0.sp,
                    modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.03f)).padding(horizontal = 10.dp, vertical = 14.dp)
                )
            }
        }
    }
}