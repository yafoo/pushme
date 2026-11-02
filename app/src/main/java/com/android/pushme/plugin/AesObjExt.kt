package com.android.pushme.plugin

import android.content.Context
import android.webkit.JavascriptInterface
import android.widget.Toast
import com.android.pushme.showToast
import com.android.pushme.utils.AESUtil
import com.quickjs.JSArray
import com.quickjs.JSObject

class AesObjExt(val context: Context): ObjExt() {
    private val aes = AESUtil()

    override fun setup(jsObject: JSObject) {
        jsObject.addObjInterface(this, "aes")
    }

    override fun close(jsObject: JSObject) {}

    @JavascriptInterface
    fun encrypt(args: JSArray): String {
        return try {
            aes.encrypt(
                args.getString(0) ?: "",
                args.getString(1) ?: "",
                args.getString(2),
            )
        } catch (e: Exception) {
            context.showToast("AES加密失败: ${e.message}", Toast.LENGTH_LONG)
            ""
        }
    }

    @JavascriptInterface
    fun decrypt(args: JSArray): String {
        return try {
            aes.decryptToString(
                args.getString(0) ?: "",
                args.getString(1) ?: "",
            )
        } catch (e: Exception) {
            context.showToast("AES解密失败: ${e.message}", Toast.LENGTH_LONG)
            ""
        }
    }

    @JavascriptInterface
    fun setTransformation(args: JSArray) {
        try {
            val transformation = args.getString(0)
            val aesTransformation = when(transformation) {
                "CBC_PKCS5" -> AESUtil.CBC_PKCS5
                "CBC_PKCS7" -> AESUtil.CBC_PKCS7
                "CTR" -> AESUtil.CTR
                "GCM" -> AESUtil.GCM
                "ECB" -> AESUtil.ECB
                else -> throw Exception("AES加密模式不存在")
            }
            aes.setTransformation(aesTransformation)
        } catch (e: Exception) {
            context.showToast(e.message?:"Exception")
        }
    }

    @JavascriptInterface
    fun setKeySize(args: JSArray) {
        try {
            aes.setKeySize(args.getInteger(0))
        } catch (e: Exception) {
            context.showToast(e.message?:"Exception")
        }
    }

    @JavascriptInterface
    fun getRandomKey(): String {
        return try {
            aes.getRandomKey()
        } catch (e: Exception) {
            context.showToast(e.message?:"Exception")
            ""
        }
    }

    @JavascriptInterface
    fun getRandomIV(): String {
        return try {
            aes.getRandomIV()
        } catch (e: Exception) {
            context.showToast(e.message?:"Exception")
            ""
        }
    }
}