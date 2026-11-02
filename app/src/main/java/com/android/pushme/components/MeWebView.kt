package com.android.pushme.components

import android.graphics.Bitmap
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.LocalTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.android.pushme.MainActivity
import com.android.pushme.Utils
import com.android.pushme.showToast
import java.util.regex.Pattern

@Composable
fun MeWebView(
    webViewState: WebViewState = rememberWebViewState(),
    modifier: Modifier = Modifier.fillMaxSize(),
    onCreated: (WebView) -> Unit = {},
    onFinished: (WebView) -> Unit = {},
    invoke: (webView: WebView, callFun: String) -> Unit = fun (_: WebView, _: String){},
    onReady: (WebView) -> Unit = {},
) {
//    val density = LocalDensity.current
//    var width by remember { mutableIntStateOf(0) }
//    var height by remember { mutableIntStateOf(1) }
//    var heightDp = remember(height) { with(density){ height.toDp() } }

    // 兼容悬浮窗
    val mainActivity = if(LocalActivity.current != null) {
        LocalActivity.current as MainActivity
    } else {
        null
    }
    val context = LocalContext.current
    val protocolPattern = Pattern.compile("^[a-zA-Z][a-zA-Z0-9+\\-.]{0,63}://", Pattern.CASE_INSENSITIVE)
    val webView = remember { WebView(context).apply {
        setBackgroundColor(0)
        settings.javaScriptEnabled = webViewState.javaScriptEnabled

        webViewClient = object: WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val url = request.url.toString()
                if(!Utils.isValidUrl(url) && protocolPattern.matcher(url).find()) {
                    mainActivity?.let {
                        Utils.openUrl(url, it, "blank")
                    }
                } else if(webViewState.isBlock) { // html消息组件，强制新窗口打开链接
                    mainActivity?.let {
                        Utils.openUrl(url, it)
                    }
                } else { // WebScreen或正常webview组件
                    if(url == webViewState.url) {
                        view.loadUrl(url)
                    } else { // 通过LaunchedEffect触发view.loadUrl
                        webViewState.url = request.url.toString()
                    }
                }
                clearFocus()
                return true
            }

            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                super.onPageStarted(view, url, favicon)
            }

            override fun onPageFinished(view: WebView, url: String) {
                onFinished(view)
//                if(webViewState.autoHeight) {
//                    view.evaluateJavascript("window.Android.autoHeight(document.documentElement.clientWidth, document.documentElement.scrollHeight);"){}
//                }
            }
        }

        webChromeClient = object: WebChromeClient() {
            override fun onReceivedTitle(view: WebView, title: String) {
                webViewState.title = title
                view.evaluateJavascript("document.addEventListener('DOMContentLoaded', function() {window.Android.documentReady();});"){}
            }

            override fun onReceivedIcon(view: WebView?, icon: Bitmap?) {
                super.onReceivedIcon(view, icon)
            }

            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                super.onProgressChanged(view, newProgress)
            }
        }

        addJavascriptInterface(
            object {
                @JavascriptInterface
                fun documentReady() {
                    onReady(this@apply)
                }

                @JavascriptInterface
                fun invoke(callFun: String = "") {
                    invoke(this@apply, callFun)
                }

//                @JavascriptInterface
//                fun autoHeight(clientWidth: Float, scrollHeight: Float) {
//                    val rate: Float = width / clientWidth
//                    height = (scrollHeight * rate).toInt()
//                }

                @JavascriptInterface
                fun showToast(text: String = "") {
                    context.showToast(text)
                }
            },
            "Android"
        )
    } }
    if(!webViewState.isInit) {
        webViewState.isInit = true
        onCreated(webView)
    }


    LaunchedEffect(webViewState.javaScriptEnabled) {
        webView.settings.javaScriptEnabled = webViewState.javaScriptEnabled
    }
    val fontColor = if(isSystemInDarkTheme()) "#eee" else "#333"
    val textStyle = LocalTextStyle.current
    val fontSize = textStyle.fontSize.value
    val lineHeight = textStyle.lineHeight.value
    LaunchedEffect(webViewState.url) {
        if(webViewState.url != "") {
            if(Utils.isValidUrl(webViewState.url) || webViewState.url.startsWith("file:///android_asset/")) {
                if(!webViewState.url.startsWith("file:///android_asset/")) {
                    webView.setBackgroundColor(0xFFFFFFFF.toInt())
                }
                webView.loadUrl(webViewState.url)
            } else if(protocolPattern.matcher(webViewState.url).find()) {
                mainActivity?.let {
                    Utils.openUrl(webViewState.url, it, "blank")
                }
            } else {
                val content = if(webViewState.url.contains("<body") && webViewState.url.contains("</body>")) {
                    "<style>html::-webkit-scrollbar{width:0;height:0;}html{overflow-y:hidden;}</style>" + webViewState.url
                } else {
                    "<style>html::-webkit-scrollbar{width:0;height:0;}html{overflow-y:hidden;}body{padding:0;margin:0;color:$fontColor;font-size:${fontSize}px;line-height:${lineHeight}px;}</style>\n<body>" + webViewState.url + "</body>"
                }
                webView.loadDataWithBaseURL("", content, "text/html", "utf-8", null)
            }
        }
    }
    LaunchedEffect(webViewState.jsCode) {
        if(webViewState.jsCode != "") {
            webView.evaluateJavascript(webViewState.jsCode) {}
        }
    }

//    modifier.onSizeChanged {
//        width = it.width
//    }
    AndroidView(factory = {
        webView
    }, modifier = modifier)
}

@Composable
fun rememberWebViewState(url: String = "", title: String = "网页", jsEnabled: Boolean = true, autoHeight: Boolean = false, isBlock: Boolean = false): WebViewState {
    return remember {
        WebViewState(url, title, jsEnabled, autoHeight, isBlock)
    }
}

@Stable
class WebViewState(url: String = "", title: String = "网页", jsEnabled: Boolean = true, autoHeight: Boolean = false, isBlock: Boolean = false) {
    var url: String by mutableStateOf(url)
    var title: String by mutableStateOf(title)
    var jsCode: String by mutableStateOf("")
    var javaScriptEnabled: Boolean by mutableStateOf(jsEnabled)
    var autoHeight: Boolean by mutableStateOf(autoHeight)
    var isBlock: Boolean by mutableStateOf(isBlock) // 是否html组件
    var isInit: Boolean = false
}