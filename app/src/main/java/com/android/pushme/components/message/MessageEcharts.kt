package com.android.pushme.components.message

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.android.pushme.components.MeWebView
import com.android.pushme.components.WebViewState
import com.android.pushme.components.rememberWebViewState

@Composable
fun Echarts(
    modifier: Modifier = Modifier,
    options: String = ""
) {
    Box(modifier = modifier.fillMaxSize().defaultMinSize(10.dp, 10.dp)) {
        val webViewState: WebViewState = rememberWebViewState("file:///android_asset/echarts.html")
        var funName by remember { mutableStateOf("") }
        MeWebView(webViewState, invoke = { webView, callFun ->
            run {
                funName = callFun;
                webView.post {
                    webView.evaluateJavascript("$callFun('$options');") {}
                }
            }
        })
        LaunchedEffect(options) {
            //第一次刚好funName为空
            if(options != "" && funName != "") {
                webViewState.jsCode = "$funName('$options');"
            }
        }
    }
}

