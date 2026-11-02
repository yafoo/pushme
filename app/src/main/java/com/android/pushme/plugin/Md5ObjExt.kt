package com.android.pushme.plugin

import com.quickjs.JSObject
import com.quickjs.JavaCallback
import java.math.BigInteger
import java.security.MessageDigest

class Md5ObjExt: ObjExt() {
    private val md = MessageDigest.getInstance("MD5")

    override fun setup(jsObject: JSObject) {
        jsObject.registerJavaMethod(JavaCallback { _, args ->
            val str = args.getString(0) ?: ""
            str.md5()
        }, "md5")
    }

    override fun close(jsObject: JSObject) { }

    private fun String.md5(): String {
        return BigInteger(1, md.digest(toByteArray())).toString(16).padStart(32, '0')
    }
}