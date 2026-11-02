package com.android.pushme.components

import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.android.pushme.LocalAppViewModel
import com.android.pushme.showToast
import kotlin.random.Random

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import com.android.pushme.MainActivity
import com.android.pushme.R
import com.android.pushme.components.drawer.Line
import com.android.pushme.components.drawer.VLine
import com.android.pushme.utils.AppDownloadFrom
import com.android.pushme.utils.AppUpdateDownloader
import com.android.pushme.utils.LangUtil
import com.android.pushme.utils.Permission.checkInstallApkPermission
import com.android.pushme.utils.Permission.checkStoragePermission
import com.android.pushme.utils.Permission.goInstallApkPermission
import com.android.pushme.utils.Permission.goStoragePermission
import kotlinx.coroutines.launch
import java.io.File
import kotlin.math.sqrt

@Composable
fun AppUpdateDialog() {
    val appViewModel = LocalAppViewModel.current
    val appUpdateState = appViewModel.appUpdateState

    val context = LocalContext.current
    val mainActivity = LocalActivity.current as MainActivity
    val updateDownloader = remember { AppUpdateDownloader(context) }
    val coroutineScope = rememberCoroutineScope()

    var isDownloading by remember { mutableStateOf(false) }
    var downloadedFile by remember { mutableStateOf<File?>(null) }
    var downloadProgress by remember { mutableFloatStateOf(0f) }
    var downloadMsg by remember { mutableStateOf("") }

    var noRemindToday by remember { mutableStateOf(false) }

    fun installApk() {
        // 检查安装权限
        if(!checkInstallApkPermission(context)) {
            context.showToast(LangUtil.lang(context, R.string.install_apk_permission_tips))
            goInstallApkPermission(mainActivity) {
                if(it) {
                    Handler(Looper.getMainLooper()).postDelayed({
                        installApk()
                    }, 1000)
                } else {
                    context.showToast(LangUtil.lang(context, R.string.install_permission_open_fail))
                }
            }
            return
        }

        updateDownloader.installApk(downloadedFile!!)
    }

    fun startDownload(from: AppDownloadFrom = AppDownloadFrom.Gitee) {
        // 权限存储检查
        if(!checkStoragePermission(context)) {
            context.showToast(LangUtil.lang(context, R.string.storage_permission_tips))
            goStoragePermission(mainActivity) {
                if(it) {
                    Handler(Looper.getMainLooper()).postDelayed({
                        startDownload()
                    }, 1000)
                } else {
                    context.showToast(LangUtil.lang(context, R.string.storage_permission_open_fail))
                }
            }
            return
        }

        isDownloading = true
        downloadProgress = 0f
        downloadMsg = LangUtil.lang(context, R.string.download_start)

        coroutineScope.launch {
            updateDownloader.downloadApk(
                version = appUpdateState.newVersion,
                from = from,
                onProgress = { it ->
                    downloadProgress = it / 100f
                    downloadMsg = LangUtil.lang(context, R.string.download_from)+": ${from.toText()}  "+LangUtil.lang(context, R.string.progress)+": $it%"
                },
                onSuccess = { file ->
                    downloadedFile = file
                    installApk()
                },
                onError = {
                    if(from == AppDownloadFrom.Gitee) {
                        startDownload(AppDownloadFrom.Github)
                    } else {
                        isDownloading = false
                        downloadMsg = it.message.toString()
                    }
                }
            )
        }
    }

    fun onDismiss() {
        isDownloading = false
        downloadedFile = null
        downloadProgress = 0f
        downloadMsg = ""

        if(noRemindToday) {
            appViewModel.setNoRemindToday()
        }
        appViewModel.setDialogHide()
    }

    AppUpdateDialog(
        showDialog = appUpdateState.dialogShow,
        onDismiss = { onDismiss() },
        newVersion = appUpdateState.newVersion,
        updateContent = appUpdateState.updateLog.split("\n").toList(),
        isDownloading = isDownloading,
        progress = downloadProgress,
        downloadMsg = downloadMsg,
        onDownloadClick = {
            if(downloadedFile != null) {
                installApk()
                return@AppUpdateDialog
            }
            if(isDownloading) {
                return@AppUpdateDialog
            }

            startDownload()
        },
        onGiteeClick = {
            if(isDownloading) {
                return@AppUpdateDialog
            }
            updateDownloader.goGiteePage(context)
        },
        onGithubClick = {
            if(isDownloading) {
                return@AppUpdateDialog
            }
            updateDownloader.goGithubPage(context)
        },
        onNoRemindToday = {
            noRemindToday = it
        },
    )
}

@Composable
fun AppUpdateDialog(
    showDialog: Boolean,
    onDismiss: () -> Unit,
    newVersion: String,
    updateContent: List<String>,
    progress: Float = 0f, // 小于等于1
    isDownloading: Boolean = false,
    downloadMsg: String = "",
    onDownloadClick: () -> Unit,
    onGiteeClick: () -> Unit,
    onGithubClick: () -> Unit,
    onNoRemindToday: (Boolean) -> Unit,
) {
    if (!showDialog) return

    var noRemindToday by remember { mutableStateOf(false) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false
        )
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .height(400.dp)
                .padding(horizontal = 16.dp),
            shape = RoundedCornerShape(16.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                // 顶部装饰区域
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(110.dp)
                        .background(color = MaterialTheme.colorScheme.primary)
                ) {
                    CloudEdgeWave(
                        modifier = Modifier.fillMaxWidth().offset(y = 10.dp),
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.3f)
                    )
                    CloudEdgeWave(
                        modifier = Modifier.fillMaxWidth().offset(y = 30.dp),
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.3f),
                        seed = 4909L
                    )
                    CloudEdgeWave(
                        modifier = Modifier.fillMaxWidth().offset(y = 30.dp),
                        color = MaterialTheme.colorScheme.surface,
                        seed = 5555L
                    )
                    Icon(
                        ImageVector.vectorResource(R.drawable.rocket),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
                        modifier = Modifier.size(45.dp).align(Alignment.TopEnd).offset(x = (-20).dp, y = 15.dp).rotate(-45f)
                    )

                    // 更新内容标题
                    Column(Modifier.padding(top = 18.dp, start = 24.dp)
                    ) {
                        Text(
                            text = LangUtil.lang(R.string.app_update_title),
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.surface,
                        )
                        Text(
                            text = newVersion,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.surface,
                        )
                    }
                }

                // 更新内容列表
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(horizontal = 24.dp).padding(top = 8.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    updateContent.forEachIndexed { index, content ->
                        Text(
                            text = "${index + 1}. $content",
                            fontSize = 14.sp,
                            lineHeight = 16.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                    }
                }

                // 今日不再提示
                if(!isDownloading) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(start = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = noRemindToday,
                            onCheckedChange = {
                                noRemindToday = it
                                onNoRemindToday(it)
                            },
                        )
                        Text(
                            text = LangUtil.lang(R.string.today_no_tips),
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                        )
                    }
                } else { // 下载进度条（仅在下载时显示）
                    Column(
                        modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 8.dp)
                    ) {
                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp),
                        )
                        Text(
                            text = downloadMsg,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                        )
                    }
                }

                // 底部按钮区域
                Line()
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(45.dp),
                ) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        val downloadButtonText = if(progress == 1f) LangUtil.lang(R.string.apk_install) else if (isDownloading && progress < 1f) LangUtil.lang(R.string.apk_downloading) else LangUtil.lang(R.string.apk_download)
                        DialogTextButton(
                            modifier = Modifier.weight(1f),
                            text = downloadButtonText,
                            color = MaterialTheme.colorScheme.surface,
                            backgroundColor = MaterialTheme.colorScheme.primary.copy(if (isDownloading && progress < 1f) 0.6f else 1f),
                            onClick = onDownloadClick
                        )

                        VLine()
                    }

                    DialogTextButton(
                        modifier = Modifier.weight(1f),
                        text = "Gitee",
                        onClick = onGiteeClick
                    )

                    VLine()

                    DialogTextButton(
                        modifier = Modifier.weight(1f),
                        text = "Github",
                        onClick = onGithubClick
                    )

                    VLine()

                    DialogTextButton(
                        modifier = Modifier.weight(1f),
                        text = LangUtil.lang(R.string.cancel),
                        onClick = onDismiss
                    )
                }
            }
        }
    }
}


@Composable
fun DialogTextButton(
    modifier: Modifier = Modifier,
    text: String,
    onClick: () -> Unit,
    fontSize: TextUnit = 14.sp,
    height: Dp = 45.dp,
    color: Color = MaterialTheme.colorScheme.onSurface,
    backgroundColor: Color = MaterialTheme.colorScheme.surface
) {
    val lineHeight = with(LocalDensity.current) {
        (height.toPx() / fontScale).toSp()
    }
    Text(
        text = text,
        fontSize = fontSize,
        lineHeight = lineHeight,
        color = color,
        textAlign = TextAlign.Center,
        modifier = modifier
            .background(color = backgroundColor)
            .clickable(onClick = onClick)
    )
}


@Composable
fun CloudEdgeWave(
    modifier: Modifier = Modifier,
    height: Dp = 100.dp,
    color: Color = MaterialTheme.colorScheme.primary,
    curvatureRatio: Float = 0.4f,
    arcHeight: Dp = 25.dp,
    segmentCount: Int = 6,     // 弧段数量
    seed: Long = 1234L
) {
    Canvas(
        modifier = modifier.height(height)
    ) {
        val width = size.width
        val height = size.height

        val baseLineY = height / 2
        val yVariation = arcHeight.toPx()

        val segmentWidth = width / segmentCount
        val maxSegmentWidth = 2 * segmentWidth
        val minSegmentWidth = 0.5 * segmentWidth
        val random = Random(seed) // 固定种子

        // 生成随机段宽（归一化到总宽度）
        val rawWidths = List(segmentCount) {
            minSegmentWidth + random.nextFloat() * (maxSegmentWidth - minSegmentWidth)
        }
        val totalRaw = rawWidths.sum()
        val normalizedWidths = rawWidths.map { (it * width / totalRaw).toFloat() }

        val path = Path().apply {
            // 起始点
            var currentX = 0f
            var currentY = baseLineY + random.nextFloat() * yVariation
            moveTo(currentX, currentY)

            normalizedWidths.forEach { segWidth ->
                if (currentX >= width) return@forEach
                val nextX = (currentX + segWidth).coerceAtMost(width)
                if (nextX <= currentX) return@forEach

                val nextY = baseLineY + random.nextFloat() * yVariation

                // === 关键：计算更圆的控制点 ===
                val dx = nextX - currentX
                val dy = nextY - currentY
                val length = sqrt(dx * dx + dy * dy)

                // 拱高：基于水平跨度（或弦长）的比例
                val arcHeight = segWidth * curvatureRatio

                // 中点
                val midX = (currentX + nextX) / 2f
                val midY = (currentY + nextY) / 2f

                // 法向量（指向“上”侧）：垂直于弦，Y 轴向下，所以“上”是 (-dy, dx) 方向
                val nx = dy / length
                val ny = -dx / length

                // 控制点 = 中点 + 法向量 * 拱高
                val controlX = midX + nx * arcHeight
                val controlY = midY + ny * arcHeight

                // ✅ 关键：从 (currentX, currentY) 到 (nextX, nextY)，用控制点控制弧度
                quadraticTo(
                    controlX,
                    controlY,
                    nextX,
                    nextY
                )

                // 更新当前位置为终点
                currentX = nextX
                currentY = nextY
            }

            lineTo(width, height)
            lineTo(0f, height)
            close()
        }

        drawPath(
            path = path,
            color = color,
            style = Fill
        )
    }
}