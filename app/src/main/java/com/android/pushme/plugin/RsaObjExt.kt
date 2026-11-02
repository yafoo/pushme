package com.android.pushme.plugin

import android.content.Context
import android.webkit.JavascriptInterface
import android.widget.Toast
import com.android.pushme.showToast
import com.android.pushme.utils.RSAUtil
import com.quickjs.JSArray
import com.quickjs.JSObject
import java.security.KeyPair

class RsaObjExt(val context: Context): ObjExt() {
    private val rsa = RSAUtil()
    private lateinit var keyPair: KeyPair

    override fun setup(jsObject: JSObject) {
        jsObject.addObjInterface(this, "rsa")
    }

    override fun close(jsObject: JSObject) {}

    @JavascriptInterface
    fun encrypt(args: JSArray): String {
        return try {
            rsa.encrypt(args.getString(0)?:"", args.getString(1)?:"")
        } catch (e: Exception) {
            context.showToast("RSA加密失败: ${e.message}", Toast.LENGTH_LONG)
            ""
        }
    }

    @JavascriptInterface
    fun decrypt(args: JSArray): String {
        return try {
            rsa.decrypt(args.getString(0)?:"", args.getString(1)?:"")
        } catch (e: Exception) {
            context.showToast("RSA解密失败: ${e.message}", Toast.LENGTH_LONG)
            ""
        }
    }

    @JavascriptInterface
    fun setTransformation(args: JSArray) {
        try {
            val transformation = args.getString(0)
            val rsaTransformation = when(transformation) {
                "ECB_PKCS1PADDING" -> RSAUtil.ECB_PKCS1PADDING
                "ECB_OAEP_WITH_SHA1" -> RSAUtil.ECB_OAEP_WITH_SHA1
                "ECB_OAEP_WITH_SHA256" -> RSAUtil.ECB_OAEP_WITH_SHA256
                "NONE_OAEP_WITH_SHA1" -> RSAUtil.NONE_OAEP_WITH_SHA1
                "NONE_OAEP_WITH_SHA256" -> RSAUtil.NONE_OAEP_WITH_SHA256
                else -> throw Exception("RSA加密模式不存在")
            }
            rsa.setTransformation(rsaTransformation)
        } catch (e: Exception) {
            context.showToast(e.message?:"Exception")
        }
    }

    @JavascriptInterface
    fun setKeySize(args: JSArray) {
        try {
            rsa.setKeySize(args.getInteger(0))
        } catch (e: Exception) {
            context.showToast(e.message?:"Exception")
        }
    }

    @JavascriptInterface
    fun generateKey(): Boolean {
        return try {
            keyPair = rsa.generateKeyPair()
            true
        } catch (e: Exception) {
            context.showToast("RSA密钥生成失败: ${e.message}")
            false
        }
    }

    @JavascriptInterface
    fun getPublicKey(): String {
        if(!::keyPair.isInitialized) {
            generateKey()
        }
        return try {
            rsa.getPublicKey(keyPair)
        } catch (e: Exception) {
            context.showToast("RSA公钥获取失败: ${e.message}")
            ""
        }
    }

    @JavascriptInterface
    fun getPrivateKey(): String {
        if(!::keyPair.isInitialized) {
            generateKey()
        }
        return try {
            rsa.getPrivateKey(keyPair)
        } catch (e: Exception) {
            context.showToast("RSA私钥获取失败: ${e.message}")
            ""
        }
    }
}