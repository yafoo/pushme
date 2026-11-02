package com.android.pushme.components.message

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import com.android.pushme.components.MeWebView
import com.android.pushme.components.WebViewState
import com.android.pushme.components.rememberWebViewState
import com.android.pushme.utils.Settings

@Composable
fun Html(
    modifier: Modifier = Modifier,
    content: String = ""
) {
    val enabled = Settings.htmlJs == "开启"
    val webViewState: WebViewState =
        rememberWebViewState(content, jsEnabled = enabled, isBlock = true)
    MeWebView(webViewState, modifier)
    LaunchedEffect(content) {
        if(content != "") {
            webViewState.url = content
        }
    }
}