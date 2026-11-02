package com.android.pushme.utils

import android.util.Log
import com.android.pushme.TAG
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okio.buffer
import okio.sink
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

object HTTP {
    const val TAG_HTTP = "${TAG}[HTTP]"
    private val JSON = "application/json; charset=utf-8".toMediaType()
    fun callBack(url: String, callback: (state: Boolean, result: String) -> Unit): Callback {
        return object : Callback {
            override fun onResponse(call: Call, response: Response) {
                // 使用 use 确保资源被正确关闭
                response.use {
                    if(response.isSuccessful) {
                        val body = response.body?.string()?:""
                        Log.d(TAG_HTTP, "RESPONSE: $url BODY: $body")
                        callback(true, body)
                    } else {
                        Log.e(TAG_HTTP, "RESPONSE: $url 请求失败: $response")
                        callback(false, "请求失败！$response")
                    }
                }
            }

            override fun onFailure(call: Call, e: IOException) {
                // e.printStackTrace()
                Log.e(TAG_HTTP,"FAIL: $url $e")
                callback(false, e.toString())
            }
        }
    }
    fun getUserAgent(): String {
        val userAgent = System.getProperty("http.agent")
        val sb = StringBuffer()
        var i = 0
        val length = userAgent?.length ?: 0
        while (i < length) {
            val c = userAgent?.get(i)
            if (c != null) {
                if (c <= '\u001f' || c >= '\u007f') {
                    sb.append(String.format("\\u%04x", c.code))
                } else {
                    sb.append(c)
                }
            }
            i++
        }
        return sb.toString()
    }
    private val client = OkHttpClient.Builder()
        .connectTimeout(2, TimeUnit.SECONDS) // 连接超时
        .readTimeout(2, TimeUnit.SECONDS)    // 读取超时
        .writeTimeout(5, TimeUnit.SECONDS)   // 写入超时
        .build()

    fun getAsync(url: String, callback: (state: Boolean, result: String) -> Unit = {_,_ ->}) {
        Log.d(TAG_HTTP, "GET: $url")

        try {
            val request = Request.Builder().url(url).removeHeader("User-Agent").addHeader("User-Agent",
                getUserAgent()).build()
            client.newCall(request).enqueue(callBack(url, callback))
        } catch (e: Exception) {
            callback(false, e.toString())
        }
    }

    fun postAsync(url: String, json: String = "", callback: (state: Boolean, result: String) -> Unit = {_,_ ->}) {
        Log.d(TAG_HTTP, "POST: $url JSON:$json")

        try {
            val request = Request.Builder().url(url).removeHeader("User-Agent").addHeader("User-Agent",
                getUserAgent()).post(json.toRequestBody(JSON)).build()
            client.newCall(request).enqueue(callBack(url, callback))
        } catch (e: Exception) {
            callback(false, e.toString())
        }
    }

    fun getAsyncWithBak(url: String, bakUrl: String, callback: (state: Boolean, result: String) -> Unit = {_,_ ->}) {
        getAsync(url){ state,result ->
            run {
                if(state)
                    callback(true, result)
                else
                    getAsync(bakUrl, callback)
            }
        }
    }

    fun postAsyncWithBak(url: String, bakUrl: String, json: String = "", callback: (state: Boolean, result: String) -> Unit = {_,_ ->}) {
        postAsync(url, json){ state,result ->
            run {
                if(state)
                    callback(true, result)
                else
                    postAsync(bakUrl, json, callback)
            }
        }
    }

    private val downloadClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .build()
    }

    suspend fun download(
        url: String,
        downloadDir: File,
        fileName: String,
        onProgress: (Int) -> Unit = {_ ->},
        onSuccess: (File) -> Unit = {_ ->},
        onError: (Throwable) -> Unit = {_ ->}
    ) {
        Log.d(TAG_HTTP, "Download: $url")
        withContext(Dispatchers.IO) {
            try {
                val request = Request.Builder()
                    .url(url)
                    .build()

                downloadClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        throw IOException("下载失败，HTTP ${response.code}")
                    }

                    response.body?.let { body ->
                        val contentLength = body.contentLength()

                        // 创建下载目录
                        if (!downloadDir.exists()) {
                            downloadDir.mkdirs()
                        }

                        // 创建临时文件，下载完成后重命名
                        val tempFile = File(downloadDir, "$fileName.temp")
                        val outputFile = File(downloadDir, fileName)

                        // 如果文件已存在，先删除
                        if (tempFile.exists()) tempFile.delete()
                        if (outputFile.exists()) outputFile.delete()

                        // 写入文件
                        tempFile.sink().buffer().use { sink ->
                            body.source().use { source ->
                                var totalBytesRead = 0L
                                val bufferSize = 8192L

                                while (true) {
                                    val bytesRead = source.read(sink.buffer, bufferSize)
                                    if (bytesRead == -1L) break

                                    totalBytesRead += bytesRead
                                    sink.emit()

                                    // 计算并回调进度
                                    if (contentLength > 0) {
                                        val progress = (totalBytesRead * 100 / contentLength).toInt()
                                        withContext(Dispatchers.Main) {
                                            onProgress(progress)
                                        }
                                    }
                                }
                            }
                        }

                        // 重命名文件
                        tempFile.renameTo(outputFile)

                        withContext(Dispatchers.Main) {
                            onSuccess(outputFile)
                        }
                    } ?: run {
                        throw IOException("响应体为空")
                    }
                }

            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    onError(e)
                }
            }
        }
    }

    fun downloadClientDestroy() {
        downloadClient.dispatcher.executorService.shutdown()
        downloadClient.connectionPool.evictAll()
        downloadClient.cache?.close()
    }
}