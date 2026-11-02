package com.android.pushme.components.messagelist

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * 消息列表状态的 CompositionLocal
 */
val LocalMessageListState = staticCompositionLocalOf<LazyListState> {
    error("No MessageListState provided")
}
