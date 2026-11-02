package com.android.pushme.plugin

import android.content.Context
import android.util.Base64
import android.webkit.JavascriptInterface
import android.widget.Toast
import com.android.pushme.showToast
import com.quickjs.JSArray
import com.quickjs.JSObject

class Base64ObjExt(val context: Context): ObjExt() {
    override fun setup(jsObject: JSObject) {
        jsObject.addObjInterface(this, "base64")
    }

    override fun close(jsObject: JSObject) {}

    @JavascriptInterface
    fun encode(args: JSArray): String {
        val text = args.getString(0) ?: ""
        val flags = if(args.getString(1) != null) args.getInteger(1) else Base64.NO_WRAP
        return try {
            String(Base64.encode(text.toByteArray(), flags))
        } catch (e: Exception) {
            context.showToast("BASE64编码失败: ${e.message}", Toast.LENGTH_LONG)
            ""
        }
    }

    @JavascriptInterface
    fun decode(args: JSArray): String {
        val text = args.getString(0) ?: ""
        return try {
            String(Base64.decode(text, args.getInteger(1)))
        } catch (e: Exception) {
            context.showToast("BASE64解码失败: ${e.message}", Toast.LENGTH_LONG)
            ""
        }
    }
}