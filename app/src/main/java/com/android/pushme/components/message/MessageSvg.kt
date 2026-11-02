package com.android.pushme.components.message

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import coil.compose.rememberAsyncImagePainter
import coil.decode.SvgDecoder
import coil.request.ImageRequest
import coil.size.Size

@Composable
fun Svg(
    modifier: Modifier = Modifier,
    content: String = "",
    contentDescription: String? = null
) {
    val context = LocalContext.current

    // 使用 derivedStateOf 避免不必要的重组
    val imageRequest = remember(content) {
        ImageRequest.Builder(context)
            .data(content.toByteArray())
            .decoderFactory(SvgDecoder.Factory())
            .size(Size.ORIGINAL)
            .build()
    }

    val painter = rememberAsyncImagePainter(
        model = imageRequest
    )

    // 监听 painter 的 intrinsic size
    val intrinsicSize = painter.intrinsicSize

    Box(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (intrinsicSize.width > 0 && intrinsicSize.height > 0) {
                    Modifier.aspectRatio(intrinsicSize.width / intrinsicSize.height)
                } else {
                    Modifier.aspectRatio(1f)
                }
            )
    ) {
        Image(
            painter = painter,
            contentDescription = contentDescription,
            modifier = modifier.fillMaxSize()
        )
    }
}