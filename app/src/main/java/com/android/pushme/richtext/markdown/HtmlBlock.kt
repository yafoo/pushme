package com.android.pushme.richtext.markdown

import android.text.Html
import android.util.TypedValue
import android.widget.TextView
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.text.HtmlCompat

/**
 * Android and JVM can have different WebView or HTML rendering implementations.
 * We are leaving HTML rendering to platform side.
 */
@Composable
internal fun HtmlBlock(content: String, modifier: Modifier = Modifier) {
    val textColor = MaterialTheme.colorScheme.onSurface.toArgb()
    AndroidView(
        modifier = modifier.fillMaxWidth(),
        factory = { context ->
            TextView(context).apply {
                setTextColor(textColor)
                textSize = 16.72f
                setTextIsSelectable(true)
            }
        },
        update = {
            it.text = Html.fromHtml(content, 0)
        }
    )
}

//@Composable
//internal fun HtmlBlock(content: String, modifier: Modifier = Modifier) {
//    val htmlText = remember(content) {
//        HtmlCompat.fromHtml(content, HtmlCompat.FROM_HTML_MODE_COMPACT).toString()
//    }
//    SelectionContainer(modifier = modifier) {
//        Text(
//            text = htmlText,
//            fontSize = 16.72.sp,
//            color = MaterialTheme.colorScheme.onSurface
//        )
//    }
//}