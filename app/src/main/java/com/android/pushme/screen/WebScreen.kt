package com.android.pushme.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.dp
import com.android.pushme.R
import com.android.pushme.components.MeContent
import com.android.pushme.components.MeTopBarTitle
import com.android.pushme.components.MeTopBarWithBack
import com.android.pushme.components.MeWebView
import com.android.pushme.components.WebViewState
import com.android.pushme.components.rememberWebViewState
import com.android.pushme.utils.LangUtil

@Composable
fun WebScreen(modifier: Modifier = Modifier, url: String = "") {
    val focusManager = LocalFocusManager.current
    val webViewState: WebViewState = rememberWebViewState(url, LangUtil.lang(R.string.web_default_title))

    Scaffold(
        modifier = modifier.clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) {
            focusManager.clearFocus()
        },
        topBar = {
            MeTopBarWithBack(
                titleContent = {
                    MeTopBarTitle(title = webViewState.title, url = webViewState.url)
                },
                actions = {
                    Spacer(Modifier.width(40.dp))
                }
            ) },
    ) { innerPadding -> MeContent(
            modifier = Modifier.padding(innerPadding)
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                MeWebView(webViewState)
            }
        }
    }
}