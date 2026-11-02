package com.android.pushme.utils

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

fun <T> createDebouncedAction(
    delayMillis: Long = 500L,
    coroutineScope: CoroutineScope,
    action: (T) -> Unit
): (T) -> Unit {
    var job: Job? = null

    return { value: T ->
        job?.cancel()
        job = coroutineScope.launch {
            delay(delayMillis)
            action(value)
        }
    }
}

@Composable
fun <T> rememberDebouncedAction(
    delayMillis: Long = 500L,
    action: (T) -> Unit
): (T) -> Unit {
    val scope = LocalLifecycleOwner.current.lifecycleScope
    val updatedAction by rememberUpdatedState(action)

    return remember(delayMillis) {
        createDebouncedAction(delayMillis, scope) { value ->
            updatedAction(value)
        }
    }
}