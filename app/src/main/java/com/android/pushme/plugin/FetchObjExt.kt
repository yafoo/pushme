package com.android.pushme.plugin

import android.content.Context
import android.util.Log
import android.webkit.JavascriptInterface
import com.android.pushme.TAG
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.quickjs.JSArray
import com.quickjs.JSObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

class FetchObjExt(val context: Context) : ObjExt() {
    private val tag = "${TAG}[FETCH]"

    override fun setup(jsObject: JSObject) {
        jsObject.addObjInterface(this, "fetch")
    }

    override fun close(jsObject: JSObject) {}

    @JavascriptInterface
    fun fetch(args: JSArray): String {
        val urlStr = args.getString(0) ?: ""
        val optionsStr = args.getString(1)
        return doRequest(urlStr, optionsStr)
    }

    @JavascriptInterface
    fun get(args: JSArray): String {
        val urlStr = args.getString(0) ?: ""
        val optionsStr = args.getString(1)
        return doRequest(urlStr, mergeMethod(optionsStr, "GET"))
    }

    @JavascriptInterface
    fun post(args: JSArray): String {
        val urlStr = args.getString(0) ?: ""
        val optionsStr = args.getString(1)
        return doRequest(urlStr, mergeMethod(optionsStr, "POST"))
    }

    @JavascriptInterface
    fun asyncFetch(args: JSArray): String {
        val urlStr = args.getString(0) ?: ""
        val optionsStr = args.getString(1)
        Thread({ doRequest(urlStr, optionsStr) }, "AsyncFetch").apply { isDaemon = true }.start()
        return "{\"status\":0,\"statusText\":\"async\",\"headers\":{},\"body\":\"\"}"
    }

    @JavascriptInterface
    fun asyncGet(args: JSArray): String {
        val urlStr = args.getString(0) ?: ""
        val optionsStr = args.getString(1)
        Thread({ doRequest(urlStr, mergeMethod(optionsStr, "GET")) }, "AsyncFetch").apply { isDaemon = true }.start()
        return "{\"status\":0,\"statusText\":\"async\",\"headers\":{},\"body\":\"\"}"
    }

    @JavascriptInterface
    fun asyncPost(args: JSArray): String {
        val urlStr = args.getString(0) ?: ""
        val optionsStr = args.getString(1)
        Thread({ doRequest(urlStr, mergeMethod(optionsStr, "POST")) }, "AsyncFetch").apply { isDaemon = true }.start()
        return "{\"status\":0,\"statusText\":\"async\",\"headers\":{},\"body\":\"\"}"
    }

    private fun mergeMethod(optionsStr: String?, method: String): String {
        return try {
            val obj = if (!optionsStr.isNullOrBlank())
                JsonParser.parseString(optionsStr).asJsonObject
            else
                JsonObject()
            obj.addProperty("method", method)
            obj.toString()
        } catch (e: Exception) {
            JsonObject().apply { addProperty("method", method) }.toString()
        }
    }

    private fun doRequest(urlStr: String, optionsStr: String?): String {
        if (urlStr.isBlank()) {
            return buildErrorResponse("URL不能为空")
        }

        return try {
            // 解析options JSON
            var method = "GET"
            var headers = mapOf<String, String>()
            var body: String? = null
            var bodyType = "json"
            var cookies: String? = null
            var timeout = 30000

            if (!optionsStr.isNullOrBlank()) {
                try {
                    val options = JsonParser.parseString(optionsStr).asJsonObject
                    method = options.get("method")?.let {
                        if (it.isJsonNull) "GET" else it.asString.uppercase()
                    } ?: "GET"
                    body = options.get("body")?.let {
                        if (it.isJsonNull) null else it.asString
                    }
                    bodyType = options.get("bodyType")?.let {
                        if (it.isJsonNull) "json" else it.asString
                    } ?: "json"
                    cookies = options.get("cookies")?.let {
                        if (it.isJsonNull) null else it.asString
                    }
                    timeout = options.get("timeout")?.let {
                        if (it.isJsonNull) 30000 else it.asInt
                    } ?: 30000

                    // 解析headers
                    options.get("headers")?.let {
                        if (!it.isJsonNull && it.isJsonObject) {
                            val map = mutableMapOf<String, String>()
                            it.asJsonObject.entrySet().forEach { (key, value) ->
                                if (!value.isJsonNull) {
                                    map[key] = value.asString
                                }
                            }
                            headers = map
                        }
                    }
                } catch (e: Exception) {
                    Log.w(tag, "options解析失败: ${e.message}")
                }
            }

            // 构建请求
            val url = URL(urlStr)
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = method
                connectTimeout = timeout
                readTimeout = timeout
                doInput = true
            }

            try {
                // 设置自定义headers
                headers.forEach { (key, value) ->
                    conn.setRequestProperty(key, value)
                }

                // 设置Cookie
                if (!cookies.isNullOrBlank()) {
                    conn.setRequestProperty("Cookie", cookies)
                }

                // 设置body
                if (body != null && (method == "POST" || method == "PUT" || method == "PATCH")) {
                    conn.doOutput = true
                    val contentType = when (bodyType) {
                        "form" -> "application/x-www-form-urlencoded"
                        "json" -> "application/json; charset=utf-8"
                        "text" -> "text/plain; charset=utf-8"
                        else -> bodyType
                    }
                    // 不覆盖用户手动设置的Content-Type
                    if (!headers.containsKey("Content-Type") && !headers.containsKey("content-type")) {
                        conn.setRequestProperty("Content-Type", contentType)
                    }
                    OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use {
                        it.write(body)
                        it.flush()
                    }
                }

                // 读取响应
                val responseCode = conn.responseCode
                val responseMessage = conn.responseMessage

                val responseBody = try {
                    val stream = if (responseCode in 200..299) conn.inputStream else conn.errorStream
                    BufferedReader(InputStreamReader(stream, Charsets.UTF_8)).use { it.readText() }
                } catch (e: Exception) {
                    Log.w(tag, "响应体读取失败: ${e.message}")
                    ""
                }

                // 读取响应headers
                val responseHeaders = JsonObject()
                conn.headerFields?.forEach { (key, values) ->
                    if (key != null && !values.isNullOrEmpty()) {
                        responseHeaders.addProperty(key, values.joinToString("; "))
                    }
                }

                // 提取Set-Cookie
                val setCookies = conn.headerFields?.get("Set-Cookie")

                // 构建响应JSON
                val result = JsonObject().apply {
                    addProperty("status", responseCode)
                    addProperty("statusText", responseMessage ?: "")
                    add("headers", responseHeaders)
                    addProperty("body", responseBody)
                    if (!setCookies.isNullOrEmpty()) {
                        addProperty("cookies", setCookies.joinToString("; "))
                    }
                }

                result.toString()
            } finally {
                conn.disconnect()
            }
        } catch (e: Exception) {
            Log.e(tag, "fetch失败: ${e.message}", e)
            buildErrorResponse(e.message ?: "未知错误")
        }
    }

    private fun buildErrorResponse(error: String): String {
        return try {
            JsonObject().apply {
                addProperty("status", -1)
                addProperty("statusText", error)
                add("headers", JsonObject())
                addProperty("body", "")
            }.toString()
        } catch (e: Exception) {
            """{"status":-1,"statusText":"构建错误响应失败","headers":{},"body":""}"""
        }
    }
}
