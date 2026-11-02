package com.android.pushme.components.messagelist

import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProgressIndicatorDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.android.pushme.LocalHomeMessageViewModel
import com.android.pushme.LocalScreen
import com.android.pushme.LocalUserMessageViewModel
import com.android.pushme.R
import com.android.pushme.utils.LangUtil

/**
 * 加载更多按钮
 */
@Composable
fun MessageListMore(modifier: Modifier = Modifier) {
    val viewModel = if(LocalScreen.current == "user") LocalUserMessageViewModel.current else LocalHomeMessageViewModel.current
    val resultValue = viewModel.searchResult.value
    if(resultValue.hasMore) {
        Button(
            onClick = {viewModel.getListMore()},
            modifier = modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 1.dp),
            shape = RoundedCornerShape(corner = CornerSize(3.dp))
        ) {
            if(resultValue.isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.padding(end = 1.dp).size(10.dp),
                    color = MaterialTheme.colorScheme.onPrimary,
                    strokeWidth = 1.6.dp,
                    trackColor = ProgressIndicatorDefaults.circularIndeterminateTrackColor,
                )
            }
            val text = if(resultValue.isLoading) LangUtil.lang(R.string.loading) else LangUtil.lang(R.string.load_more)
            Text(text = text)
        }
    }
}
