package com.android.pushme.api

import android.content.Context
import android.util.Log
import com.android.pushme.MessageInfo
import com.android.pushme.TAG
import com.android.pushme.utils.Settings
import com.google.gson.Gson
import com.google.gson.JsonParser
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * PushMe API 服务
 * 提供 HTTP 接口，兼容 PushMe 官方接口
 * 支持 GET/POST 请求，支持表单和 JSON 格式
 */
class APIService(private val context: Context) {
    private val tag = "$TAG[API SERVICE]"
    private var serverSocket: ServerSocket? = null
    private val executor = Executors.newFixedThreadPool(4)
    private val gson = Gson()
    private val running = AtomicBoolean(false)

    /**
     * 消息处理回调，由 MQTTService 设置
     * 参数为原始 JSON 消息字符串，返回处理结果
     */
    var onMessageReceived: ((String) -> String)? = null

    var isRunning = false
        private set

    /**
     * 启动 API 服务
     * @param host 监听地址，支持 IPv4/IPv6，空值或 "0.0.0.0" 监听所有接口
     * @param port 监听端口，默认 3010
     */
    fun start(host: String = "0.0.0.0", port: Int = 3010) {
        if (isRunning) {
            Log.w(tag, "API服务已在运行中")
            return
        }

        try {
            // 处理监听地址
            val bindAddress = when {
                host.isEmpty() || host == "0.0.0.0" -> {
                    // 监听所有接口（同时支持 IPv4 和 IPv6）
                    InetSocketAddress(port)
                }
                host.startsWith("[") && host.endsWith("]") -> {
                    // IPv6 带中括号，去掉中括号
                    val ipv6 = host.substring(1, host.length - 1)
                    InetSocketAddress(ipv6, port)
                }
                host.contains(":") && !host.contains(".") -> {
                    // IPv6 不带中括号（包含冒号但不包含点号）
                    InetSocketAddress(host, port)
                }
                else -> {
                    // IPv4 地址
                    InetSocketAddress(host, port)
                }
            }

            serverSocket = ServerSocket()
            serverSocket?.reuseAddress = true
            serverSocket?.bind(bindAddress)

            running.set(true)
            isRunning = true

            // 启动接收线程
            executor.execute {
                while (running.get()) {
                    try {
                        val clientSocket = serverSocket?.accept() ?: break
                        executor.execute { handleClient(clientSocket) }
                    } catch (e: Exception) {
                        if (running.get()) {
                            Log.e(tag, "接受连接失败: ", e)
                        }
                    }
                }
            }

            Log.d(tag, "API服务已启动: $bindAddress")
        } catch (e: Exception) {
            Log.e(tag, "API服务启动失败: ", e)
            isRunning = false
        }
    }

    /**
     * 停止 API 服务
     */
    fun stop() {
        if (!isRunning) return

        try {
            running.set(false)
            serverSocket?.close()
            serverSocket = null
            isRunning = false
            Log.d(tag, "API服务已停止")
        } catch (e: Exception) {
            Log.e(tag, "API服务停止失败: ", e)
        }
    }

    /**
     * 处理客户端连接
     */
    private fun handleClient(socket: Socket) {
        try {
            socket.use { s ->
                val input = BufferedReader(InputStreamReader(s.getInputStream()))
                val output = PrintWriter(s.getOutputStream(), true)

                // 读取请求行
                val requestLine = input.readLine() ?: return
                val parts = requestLine.split(" ")
                if (parts.size < 2) return

                val method = parts[0].uppercase()
                val path = parts[1]

                // 读取请求头
                val headers = mutableMapOf<String, String>()
                var line = input.readLine()
                while (line != null && line.isNotEmpty()) {
                    val colonIndex = line.indexOf(":")
                    if (colonIndex > 0) {
                        val key = line.substring(0, colonIndex).trim().lowercase()
                        val value = line.substring(colonIndex + 1).trim()
                        headers[key] = value
                    }
                    line = input.readLine()
                }

                // 解析 GET 参数
                val params = mutableMapOf<String, String>()
                val queryIndex = path.indexOf("?")
                if (queryIndex >= 0) {
                    val query = path.substring(queryIndex + 1)
                    parseQueryString(query, params)
                }

                // 读取请求体（POST）
                if (method == "POST") {
                    val contentLength = headers["content-length"]?.toIntOrNull() ?: 0
                    // 限制最大 body 大小，防止 OOM（1MB）
                    if (contentLength in 1..MAX_BODY_SIZE) {
                        try {
                            val body = CharArray(contentLength)
                            var totalRead = 0
                            while (totalRead < contentLength) {
                                val read = input.read(body, totalRead, contentLength - totalRead)
                                if (read == -1) break
                                totalRead += read
                            }
                            val bodyStr = String(body, 0, totalRead)

                            val contentType = headers["content-type"] ?: ""
                            when {
                                contentType.contains("application/json") -> {
                                    parseJsonBody(bodyStr, params)
                                }
                                contentType.contains("multipart/form-data") -> {
                                    val boundary = extractBoundary(contentType)
                                    if (boundary != null) {
                                        parseMultipartBody(bodyStr, boundary, params)
                                    } else {
                                        Log.w(tag, "multipart/form-data 缺少 boundary")
                                    }
                                }
                                else -> {
                                    parseQueryString(bodyStr, params)
                                }
                            }
                        } catch (e: Exception) {
                            Log.e(tag, "读取请求体失败: ", e)
                        }
                    }
                }

                // 处理请求
                val result = processApiRequest(params)

                // 返回响应
                val responseBytes = result.toByteArray(Charsets.UTF_8)
                val responseHeader = "HTTP/1.1 200 OK\r\n" +
                        "Content-Type: text/plain; charset=utf-8\r\n" +
                        "Content-Length: ${responseBytes.size}\r\n" +
                        "Connection: close\r\n" +
                        "\r\n"

                output.print(responseHeader)
                output.flush()
                s.getOutputStream().write(responseBytes)
                s.getOutputStream().flush()
            }
        } catch (e: Exception) {
            Log.e(tag, "处理客户端连接失败: ", e)
        }
    }

    /**
     * 解析查询字符串
     */
    private fun parseQueryString(query: String, params: MutableMap<String, String>) {
        try {
            query.split("&").forEach { pair ->
                val parts = pair.split("=", limit = 2)
                if (parts.size == 2) {
                    val key = URLDecoder.decode(parts[0], "UTF-8")
                    val value = URLDecoder.decode(parts[1], "UTF-8")
                    params[key] = value
                }
            }
        } catch (e: Exception) {
            Log.e(tag, "解析查询字符串失败: ", e)
        }
    }

    /**
     * 从 Content-Type 中提取 multipart boundary
     */
    private fun extractBoundary(contentType: String): String? {
        val regex = Regex("""boundary=([^\s;]+)""", RegexOption.IGNORE_CASE)
        val match = regex.find(contentType) ?: return null
        var boundary = match.groupValues[1].trim()
        // 去掉可能的引号
        if (boundary.startsWith("\"") && boundary.endsWith("\"")) {
            boundary = boundary.substring(1, boundary.length - 1)
        }
        return boundary
    }

    /**
     * 解析 multipart/form-data 请求体
     */
    private fun parseMultipartBody(body: String, boundary: String, params: MutableMap<String, String>) {
        try {
            val delimiter = "--$boundary"
            val endDelimiter = "--$boundary--"
            val parts = body.split(delimiter)

            for (part in parts) {
                val trimmed = part.trimEnd('\r', '\n')
                // 跳过结束标记和空部分
                if (trimmed.isEmpty() || trimmed.trimStart('\r', '\n').startsWith("--")) continue

                // 去掉开头的换行
                val content = trimmed.removePrefix("\r\n").removePrefix("\n")

                // 分离头部和正文（以双换行分隔）
                val headerEndIndex = content.indexOf("\r\n\r\n")
                if (headerEndIndex < 0) continue

                val headerSection = content.substring(0, headerEndIndex)
                val value = content.substring(headerEndIndex + 4).let {
                    // 去掉末尾的 \r\n
                    if (it.endsWith("\r\n")) it.substring(0, it.length - 2) else it
                }

                // 从 Content-Disposition 中提取 name
                val nameRegex = Regex("name=\"([^\"]+)\"", RegexOption.IGNORE_CASE)
                val nameMatch = nameRegex.find(headerSection) ?: continue
                val name = nameMatch.groupValues[1]
                params[name] = value
            }
        } catch (e: Exception) {
            Log.e(tag, "解析multipart请求体失败: ", e)
        }
    }

    /**
     * 解析 JSON 请求体
     */
    private fun parseJsonBody(body: String, params: MutableMap<String, String>) {
        try {
            val json = JsonParser.parseString(body).asJsonObject
            json.entrySet().forEach { (key, value) ->
                if (value.isJsonPrimitive) {
                    params[key] = value.asString
                }
            }
        } catch (e: Exception) {
            Log.e(tag, "JSON解析失败: ", e)
        }
    }

    /**
     * 处理 API 请求
     * @return 返回结果字符串
     */
    private fun processApiRequest(params: Map<String, String>): String {
        return try {
            val pushKey = params["push_key"] ?: ""
            val title = params["title"] ?: ""
            val content = params["content"] ?: ""
            val date = params["date"] ?: ""
            val type = params["type"] ?: ""

            // 验证 push_key
            if (Settings.apiVerifyKey == "开启") {
                val expectedKey = if (Settings.mqttState == "自建") Settings.selfKey else Settings.pushKey
                if (pushKey.isEmpty() || pushKey != expectedKey) {
                    return "push_key 错误"
                }
            }

            // 验证 title 和 content
            if (title.isEmpty() && content.isEmpty()) {
                return "title 和 content 至少填一项"
            }

            // 构建 JSON 消息
            val messageInfo = MessageInfo(
                title = title,
                content = content,
                date = date,
                type = type,
                push_key = pushKey
            )
            val messageJson = gson.toJson(messageInfo)

            // 通过回调处理消息
            onMessageReceived?.invoke(messageJson) ?: "API服务未初始化"
        } catch (e: Exception) {
            Log.e(tag, "处理API请求失败: ", e)
            "服务器内部错误"
        }
    }

    companion object {
        private const val MAX_BODY_SIZE = 1024 * 1024 // 1MB
    }
}
