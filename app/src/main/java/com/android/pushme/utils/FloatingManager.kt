package com.android.pushme.utils

import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.Dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.android.pushme.components.FloatingState
import com.android.pushme.components.FloatingWindow
import com.android.pushme.showToast
import com.android.pushme.ui.theme.PushMeTheme

open class FloatingManager(private val context: Context) {
    private var windowManager: WindowManager? = null
    private var lifecycleOwner: FloatingWindowLifecycleOwner? = null
    private var savedStateRegistryOwner: FloatingWindowSavedStateRegistryOwner? = null
    var composeView: View? = null

    open val floatingState = FloatingState()

    init {
        windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    }

    @Composable
    open fun FloatingContent() {}

    @Composable
    open fun FloatingBackgroundColor(): Color? = null

    open fun onViewClick() {}

    open fun onPositionChange(offset: Offset) { updatePosition() }

    open fun onSizeChange(size: Dp) {}

    open fun onRadiusChange(radius: Dp) {}

    open fun onOpacityChange(opacity: Float) {}

    open fun onReset() {}

    open fun show() {
        if (!checkPermission()) {
            context.showToast("请先设置悬浮窗权限")
            return
        }

        if (composeView != null) return

        runOnMainThread {
            showContent()
        }
    }

    open fun close() {
        if (composeView == null) return

        runOnMainThread {
            try {
                composeView?.let { windowManager?.removeView(it) }
                composeView = null

                lifecycleOwner?.onDestroy()
                savedStateRegistryOwner?.onDestroy()
                lifecycleOwner = null
                savedStateRegistryOwner = null
            } catch (e: Exception) {
                Log.e(FLOATING_WINDOW_TAG, "隐藏失败", e)
            }
        }
    }

    private fun showContent() {
        composeView = ComposeView(context).apply {
            lifecycleOwner = FloatingWindowLifecycleOwner()
            savedStateRegistryOwner = FloatingWindowSavedStateRegistryOwner()
            setViewTreeLifecycleOwner(lifecycleOwner)
            setViewTreeSavedStateRegistryOwner(savedStateRegistryOwner)

            setContent {
                PushMeTheme {
                    FloatingWindow(
                        floatingState = floatingState,
                        backgroundColor = FloatingBackgroundColor(),
                        onPositionChange = { onPositionChange(it) },
                        onSizeChange = { onSizeChange(it) },
                        onRadiusChange = { onRadiusChange(it) },
                        onOpacityChange = { onOpacityChange(it) },
                        onReset = { onReset() },
                        onClick = { onViewClick() }
                    ) {
                        FloatingContent()
                    }
                }
            }
        }

        val params = createWindowParams()

        try {
            windowManager?.addView(composeView, params)
        } catch (e: Exception) {
            Log.e(FLOATING_WINDOW_TAG, "显示失败", e)
        }
    }

    fun updatePosition() {
        if (composeView == null) return

        runOnMainThread {
            try {
                composeView?.let { view ->
                    val params = view.layoutParams as WindowManager.LayoutParams
                    params.x = floatingState.position.x.toInt()
                    params.y = floatingState.position.y.toInt()
                    windowManager?.updateViewLayout(view, params)
                }
            } catch (e: Exception) {
                Log.e(FLOATING_WINDOW_TAG, "更新位置失败", e)
            }
        }
    }

    private fun createWindowParams(): WindowManager.LayoutParams {
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            WindowManager.LayoutParams.TYPE_PHONE
        }

        return WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                    WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = floatingState.position.x.toInt()
            y = floatingState.position.y.toInt()

            // 设置窗口动画
            windowAnimations = android.R.style.Animation_Dialog
        }
    }

    private fun checkPermission(): Boolean {
        return Permission.checkFloatingPermission(context)
    }

    private fun runOnMainThread(action: () -> Unit) {
        if(Looper.myLooper() != Looper.getMainLooper()) {
            Handler(Looper.getMainLooper()).post(action)
        } else {
            action()
        }
    }

    companion object {
        const val FLOATING_WINDOW_TAG = "FloatingWindow"
    }
}

private class FloatingWindowLifecycleOwner : LifecycleOwner {
    private val lifecycleRegistry = LifecycleRegistry(this)

    init {
        onCreate()
        onStart()
    }

    override val lifecycle: Lifecycle
        get() = lifecycleRegistry

    fun onCreate() {
        lifecycleRegistry.currentState = Lifecycle.State.CREATED
    }
    fun onStart() {
        lifecycleRegistry.currentState = Lifecycle.State.STARTED
    }
    fun onResume() {
        lifecycleRegistry.currentState = Lifecycle.State.RESUMED
    }
    fun onDestroy() {
        lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
    }
}

private class FloatingWindowSavedStateRegistryOwner() : SavedStateRegistryOwner, LifecycleOwner {

    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateRegistryController = SavedStateRegistryController.create(this)

    init {
        // 初始化生命周期和状态恢复控制器
        savedStateRegistryController.performAttach()
        savedStateRegistryController.performRestore(null)

        // 设置生命周期为 STARTED
        onCreate()
        onStart()
    }
    override val savedStateRegistry: SavedStateRegistry
        get() = savedStateRegistryController.savedStateRegistry
    override val lifecycle: Lifecycle
        get() = lifecycleRegistry

    fun onCreate() {
        lifecycleRegistry.currentState = Lifecycle.State.CREATED
    }
    fun onStart() {
        lifecycleRegistry.currentState = Lifecycle.State.STARTED
    }
    fun onResume() {
        lifecycleRegistry.currentState = Lifecycle.State.RESUMED
    }
    fun onDestroy() {
        lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
    }
}