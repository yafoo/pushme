package com.android.pushme.utils

import android.annotation.SuppressLint
import com.android.pushme.utils.HTTP.callBack
import com.android.pushme.utils.HTTP.getUserAgent
import okhttp3.ConnectionPool
import okhttp3.OkHttpClient
import okhttp3.Request
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

object PushMeServer {
    // 获取自签名证书内容
    fun getSelfCert(url: String, callback: (state: Boolean, result: String) -> Unit = {_,_ ->}) {
        try {
            val request = Request.Builder().url(url).removeHeader("User-Agent").addHeader("User-Agent",
                getUserAgent()).build()
            OkHttpClient.Builder()
                .sslSocketFactory(
                    createSSLContext().socketFactory,
                    createTrustManager()
                )
                .hostnameVerifier { _, _ -> true }
                .connectionPool(ConnectionPool(0, 1, TimeUnit.SECONDS))  // 不保持连接
                .connectTimeout(2, TimeUnit.SECONDS) // 连接超时
                .readTimeout(2, TimeUnit.SECONDS)    // 读取超时
                .writeTimeout(5, TimeUnit.SECONDS)   // 写入超时
                .build().newCall(request).enqueue(callBack(url, callback))
        } catch (e: Exception) {
            callback(false, e.toString())
        }
    }

    @SuppressLint("CustomX509TrustManager")
    private fun createTrustManager(): X509TrustManager {
        return object : X509TrustManager {
            @SuppressLint("TrustAllX509TrustManager")
            override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
            @SuppressLint("TrustAllX509TrustManager")
            override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
            override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
        }
    }

    // 2. 创建 SSLContext
    private fun createSSLContext(): SSLContext {
        return SSLContext.getInstance("TLS").apply {
            val trustManagers = arrayOf<TrustManager>(createTrustManager())
            init(null, trustManagers, null)
        }
    }
}