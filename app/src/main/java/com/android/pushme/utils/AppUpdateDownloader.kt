package com.android.pushme.utils

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import androidx.core.content.FileProvider
import com.android.pushme.Utils
import com.android.pushme.showToast
import java.io.File

enum class AppDownloadFrom {
    Gitee,
    Github,
    None;

    fun toText(): String {
        return when(this) {
            Gitee -> "Gitee"
            Github -> "Github"
            None -> "None"
        }
    }
}

class AppUpdateDownloader(private val context: Context) {
    private val fileName = "app_update.apk"

    /**
     * 下载APK
     */
    suspend fun downloadApk(
        version: String,
        from: AppDownloadFrom = AppDownloadFrom.Gitee,
        onProgress: (Int) -> Unit,
        onSuccess: (File) -> Unit,
        onError: (Throwable) -> Unit
    ) {
        HTTP.download(
            url = getDownloadUrl(version, from),
            downloadDir = getDownloadDir(),
            fileName = fileName,
            onProgress = onProgress,
            onSuccess = onSuccess,
            onError = onError
        )
    }

    /**
     * 安装APK
     */
    fun installApk(apkFile: File) {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)

            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                apkFile
            )
            setDataAndType(uri, "application/vnd.android.package-archive")
        }

        try {
            context.startActivity(intent)
        } catch (e: Exception) {
            e.printStackTrace()
            context.showToast("安装出错：" + e.message.toString())
        }
    }

    /**
     * 清理旧的APK文件
     */
    fun cleanApkFiles() {
        try {
            val downloadDir = getDownloadDir()
            downloadDir.listFiles()?.forEach { file ->
                if (file.name.endsWith(".apk") || file.name.endsWith(".temp")) {
                    file.delete()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun getDownloadUrl(version: String, from: AppDownloadFrom = AppDownloadFrom.Gitee): String {
        val versionCode = version.replace("v", "", true)
        if(versionCode.length > 10) throw Exception("版本号异常")
        val versionType = if(Utils.isProcess64Bit()) "-arm64" else ""
        return when(from) {
            AppDownloadFrom.Gitee -> "https://gitee.com/yafu/pushme/releases/download/v$versionCode/PushMe-$versionCode$versionType.apk"
            AppDownloadFrom.Github -> "https://github.com/yafoo/pushme/releases/download/v$versionCode/PushMe-$versionCode$versionType.apk"
            else -> throw Exception("下载来源未明")
        }
    }

    /**
     * 获取下载文件保存路径
     */
    private fun getDownloadDir(): File {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Android 10及以上，使用应用专属目录
            context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.filesDir
        } else {
            // Android 10以下，使用公共下载目录
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        }
    }

    public fun goGiteePage(context: Context) {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://gitee.com/yafu/pushme/releases/latest")))
    }

    public fun goGithubPage(context: Context) {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/yafoo/pushme/releases/latest")))
    }
}