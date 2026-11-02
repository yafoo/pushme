package com.android.pushme.components.drawer

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.android.pushme.MainActivity
import com.android.pushme.R
import com.android.pushme.Utils
import com.android.pushme.ui.theme.Orange
import com.android.pushme.utils.LangUtil
import com.android.pushme.utils.Settings

@Composable
fun HomeDrawer(modifier: Modifier = Modifier, onButtonClick: () -> Unit) {
    Box(
        modifier = modifier
            .width(280.dp)
            .fillMaxHeight()
            .background(MaterialTheme.colorScheme.surface)
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() }) {
            }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(15.dp)
        ) {
            Image(
                ImageVector.vectorResource(R.drawable.logo),
                contentDescription = null,
                modifier = Modifier
                    .padding(top = 5.dp)
                    .size(60.dp)
                    .clip(RoundedCornerShape(30.dp))
                    .background(color = MaterialTheme.colorScheme.primary)
            )
            Row (verticalAlignment = Alignment.Bottom) {
                val version = Settings.appVersion

                Text(text = "PushMe")
                Text(
                    text = version,
                    fontSize = 12.sp,
                    lineHeight = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    modifier = Modifier.padding(start = 5.dp, bottom = 3.dp)
                )
            }
            Line(modifier = Modifier.padding(top = 15.dp, bottom = 15.dp))
            SelectionContainer {
                Column {
                    ContentText("PushMe，" + LangUtil.lang(R.string.app_slogan))
                    Row (verticalAlignment = Alignment.Bottom) {
                        ContentText(LangUtil.lang(R.string.app_website) + "：")
                        val activity = LocalActivity.current as MainActivity
                        val url = "https://push.i-i.me/"
                        ContentText(
                            text = url,
                            color = Orange,
                            modifier = Modifier.clickable {
                                Utils.openUrl(url, activity, "blank")
                            }
                        )
                    }
                    Spacer(Modifier
                        .fillMaxWidth()
                        .height(5.dp))
                }
            }

            Box(Modifier.weight(1f))

            DrawerItem {
                DrawerItemMessageCount()
            }
            DrawerItem {
                DrawerItemGoPage(title = LangUtil.lang(R.string.send) + LangUtil.lang(R.string.message), text = LangUtil.lang(R.string.send), page = "send", onClick = onButtonClick)
            }
            DrawerItem(hasExpand = true) {
                DrawerItemPushKey(expandShow = it)
            }
            DrawerItem(hasExpand = true) {
                DrawerItemMessageSet(expandShow = it)
            }
            DrawerItem(hasExpand = true) {
                DrawerItemServiceSet(expandShow = it, onClick = onButtonClick)
            }
            DrawerItem(hasExpand = true) {
                DrawerItemAppSet(expandShow = it)
            }
            DrawerItem(hasExpand = true) {
                DrawerItemPermission(expandShow = it)
            }
            DrawerItem(hasExpand = true) {
                DrawerItemMoreSet(expandShow = it, onClick = onButtonClick)
            }
            DrawerItem(hasExpand = true) {
                DrawerItemAboutUs(expandShow = it)
            }
        }
    }
}