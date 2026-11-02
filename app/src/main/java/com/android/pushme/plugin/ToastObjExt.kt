package com.android.pushme.plugin

import android.content.Context
import android.widget.Toast
import com.android.pushme.showToast
import com.quickjs.JSObject

class ToastObjExt(val context: Context) : ObjExt() {
    override fun setup(jsObject: JSObject) {
        jsObject.registerJavaMethod({ _, args ->
            val toast = args.getString(0) ?: ""
            val type = if (args.getInteger(1) == 1) Toast.LENGTH_LONG else Toast.LENGTH_SHORT
            Thread {
                context.showToast(toast, type)
            }.start()
        }, "toast")
    }

    override fun close(jsObject: JSObject) {}
}