package com.android.pushme.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.graphics.scale
import com.android.pushme.TAG
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * 管理分组头像的下载、缓存和加载
 */
object FaceManager {
    private val tag = "$TAG[FaceManager]"
    private const val FACE_DIR = "faces"
    private const val MAX_SIZE = 256 // 最大尺寸（像素）
    private const val COMPRESS_QUALITY = 85 // JPEG压缩质量
    private val mainHandler = Handler(Looper.getMainLooper())

    /**
     * 获取头像目录
     */
    private fun getFaceDir(context: Context): File {
        val dir = File(context.filesDir, FACE_DIR)
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    /**
     * 根据用户名获取本地头像文件
     * @param create 如果文件不存在，是否返回文件路径（用于下载）
     */
    fun getFaceFile(context: Context, user: String, create: Boolean = false): File? {
        if (user.isBlank()) return null
        val safeName = user.replace(Regex("[^a-zA-Z0-9\u4e00-\u9fa5_-]"), "_")
        val file = File(getFaceDir(context), "$safeName.webp")
        return if (file.exists() || create) file else null
    }

    /**
     * 加载本地缓存的头像 Bitmap
     */
    fun loadFaceBitmap(context: Context, user: String): Bitmap? {
        val file = getFaceFile(context, user) ?: return null
        return try {
            BitmapFactory.decodeFile(file.absolutePath)
        } catch (e: Exception) {
            Log.e(tag, "加载头像失败: $user", e)
            null
        }
    }

    /**
     * 下载并缓存头像（异步）
     * @param context 上下文
     * @param user 分组名
     * @param faceUrl 头像URL
     * @param onComplete 下载完成回调（在主线程执行），参数为是否成功下载了新头像
     */
    fun downloadAndCacheFace(context: Context, user: String, faceUrl: String, onComplete: ((success: Boolean) -> Unit)? = null) {
        if (user.isBlank() || faceUrl.isBlank()) {
            onComplete?.invoke(false)
            return
        }

        // 如果本地已有缓存，跳过下载
        if (getFaceFile(context, user) != null) {
            Log.d(tag, "头像已缓存: $user")
            onComplete?.invoke(false)
            return
        }

        Thread {
            var success = false
            try {
                Log.d(tag, "开始下载头像: $user -> $faceUrl")
                val url = URL(faceUrl)
                val connection = url.openConnection() as HttpURLConnection
                connection.connectTimeout = 10000
                connection.readTimeout = 10000
                connection.doInput = true

                if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                    val inputStream = connection.inputStream
                    val originalBitmap = BitmapFactory.decodeStream(inputStream)
                    inputStream.close()
                    connection.disconnect()

                    if (originalBitmap != null) {
                        val compressed = compressBitmap(originalBitmap)
                        if (originalBitmap != compressed) {
                            originalBitmap.recycle()
                        }
                    
                        val faceFile = getFaceFile(context, user, create = true)!!
                    
                        FileOutputStream(faceFile).use { fos ->
                            compressed.compress(Bitmap.CompressFormat.WEBP, COMPRESS_QUALITY, fos)
                        }
                    
                        if (originalBitmap != compressed && !compressed.isRecycled) {
                            compressed.recycle()
                        }
                    
                        Log.d(tag, "头像下载成功: $user -> ${faceFile.absolutePath}")
                        success = true
                    }
                } else {
                    Log.w(tag, "头像下载失败: HTTP ${connection.responseCode}")
                    connection.disconnect()
                }
            } catch (e: Exception) {
                Log.e(tag, "头像下载异常: $user", e)
            }
            
            // 在主线程回调
            if (onComplete != null) {
                mainHandler.post { onComplete(success) }
            }
        }.start()
    }

    /**
     * 处理图片文件为头像格式（压缩、转WEBP）
     * @param context 上下文
     * @param user 分组名
     * @param imageFile 源图片文件
     * @param onComplete 处理完成回调（在主线程执行），参数为是否成功
     */
    fun processAndCacheFace(context: Context, user: String, imageFile: File, onComplete: ((success: Boolean) -> Unit)? = null) {
        if (user.isBlank() || !imageFile.exists()) {
            onComplete?.invoke(false)
            return
        }

        Thread {
            var success = false
            try {
                Log.d(tag, "开始处理头像: $user -> ${imageFile.absolutePath}")
                val originalBitmap = BitmapFactory.decodeFile(imageFile.absolutePath)

                if (originalBitmap != null) {
                    val compressed = compressBitmap(originalBitmap)
                    if (originalBitmap != compressed) {
                        originalBitmap.recycle()
                    }

                    val faceFile = getFaceFile(context, user, create = true)!!

                    FileOutputStream(faceFile).use { fos ->
                        compressed.compress(Bitmap.CompressFormat.WEBP, COMPRESS_QUALITY, fos)
                    }

                    if (originalBitmap != compressed && !compressed.isRecycled) {
                        compressed.recycle()
                    }

                    Log.d(tag, "头像处理成功: $user -> ${faceFile.absolutePath}")
                    success = true
                }
            } catch (e: Exception) {
                Log.e(tag, "头像处理异常: $user", e)
            }

            // 在主线程回调
            if (onComplete != null) {
                mainHandler.post { onComplete(success) }
            }
        }.start()
    }

    /**
     * 压缩 Bitmap 到合理尺寸
     */
    private fun compressBitmap(source: Bitmap): Bitmap {
        val width = source.width
        val height = source.height

        if (width <= MAX_SIZE && height <= MAX_SIZE) {
            return source
        }

        val ratio = MAX_SIZE.toFloat() / maxOf(width, height)
        val newWidth = (width * ratio).toInt()
        val newHeight = (height * ratio).toInt()

        return source.scale(newWidth, newHeight)
    }
}
