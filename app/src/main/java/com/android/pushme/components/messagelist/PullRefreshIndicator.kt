package com.android.pushme.components.messagelist

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.pullrefresh.PullRefreshState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProgressIndicatorDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.android.pushme.R
import com.android.pushme.utils.LangUtil
import kotlin.math.roundToInt

/**
 * 下拉刷新指示器
 */
@OptIn(ExperimentalMaterialApi::class)
@Composable
fun PullRefreshIndicator(modifier: Modifier = Modifier, pullRefreshState: PullRefreshState, refreshText: MutableState<String>) {
    val pullRefreshModifier = when (refreshText.value) {
        LangUtil.lang(R.string.pull_to_refresh) -> {
            modifier.height(
                (pullRefreshState.progress * 100).roundToInt().coerceAtMost(100).dp,
            )
        }
        LangUtil.lang(R.string.release_refresh) -> modifier.height(100.dp)
        LangUtil.lang(R.string.refreshing) -> modifier.wrapContentHeight()
        else -> modifier.height(0.dp)
    }

    val context = LocalContext.current
    LaunchedEffect(pullRefreshState.progress) {
        when {
            pullRefreshState.progress >= 1 -> {
                refreshText.value = LangUtil.lang(context, R.string.release_refresh)
            }
            pullRefreshState.progress > 0 -> {
                refreshText.value = LangUtil.lang(context, R.string.pull_to_refresh)
            }
        }
    }

    Column(
        pullRefreshModifier
            .padding(top = 10.dp)
            .fillMaxWidth(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CircularProgressIndicator(
            progress = { pullRefreshState.progress },
            modifier = Modifier.size(16.dp),
            color = MaterialTheme.colorScheme.onSurface.copy(0.6f),
            strokeWidth = 3.dp,
            trackColor = ProgressIndicatorDefaults.circularIndeterminateTrackColor,
        )

        Text(
            text = refreshText.value,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(0.8f),
        )
    }
}
