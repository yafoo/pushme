package com.android.pushme.screen

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.android.pushme.LocalNavController
import com.android.pushme.MainActivity
import com.android.pushme.R
import com.android.pushme.components.MeConfirm
import com.android.pushme.components.MeContent
import com.android.pushme.components.MeTopBar
import com.android.pushme.showToast
import com.android.pushme.utils.FaceManager
import com.android.pushme.utils.LangUtil
import java.io.File
import java.io.FileOutputStream

// 受保护的文件/文件夹列表（系统或框架生成的，不能删除）
private val protectedFiles = setOf(
    "datastore",
    "profileInstalled",
    "profileinstaller",
    "profileinstaller_"
)

// 可以用系统查看器打开的文件后缀
private val viewableExtensions = setOf(
    "jpg", "jpeg", "png", "gif", "bmp", "webp",
    "mp4", "mkv", "avi", "mov",
    "mp3", "wav", "ogg", "m4a",
    "pdf",
    "txt", "log"
)

@Composable
fun FileScreen(modifier: Modifier = Modifier) {
    val focusManager = LocalFocusManager.current
    val mainActivity = LocalActivity.current as MainActivity
    val context = LocalContext.current
    val filesDir = mainActivity.filesDir

    var currentPath by remember { mutableStateOf(filesDir) }
    var fileList by remember { mutableStateOf(listFiles(filesDir)) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var fileToDelete by remember { mutableStateOf<File?>(null) }
    var showUploadDialog by remember { mutableStateOf(false) }
    var pendingImageUri by remember { mutableStateOf<Uri?>(null) }

    val navController = LocalNavController.current

    // 判断当前是否在 faces 目录
    val isInFacesDir = remember(currentPath) {
        currentPath.name.equals("faces", ignoreCase = true)
    }

    // 文件选择器
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            pendingImageUri = it
            showUploadDialog = true
        }
    }

    // 统一的返回处理：如果在子目录，返回上级；否则退出页面
    val onBackClick = {
        if (currentPath != filesDir) {
            val parent = currentPath.parentFile
            if (parent != null) {
                currentPath = parent
                fileList = listFiles(parent)
            }
        } else {
            navController.popBackStack()
        }
    }

    // 处理系统返回键
    BackHandler {
        onBackClick()
    }

    MeConfirm(
        show = remember { mutableStateOf(false) }.apply { value = showDeleteConfirm },
        title = LangUtil.lang(R.string.delete_confirm_title),
        content = LangUtil.lang(R.string.delete_file_confirm)
    ) {
        fileToDelete?.let { file ->
            if (file.deleteRecursively()) {
                mainActivity.showToast(LangUtil.lang(mainActivity, R.string.delete_success))
                fileList = listFiles(currentPath)
            }
        }
        showDeleteConfirm = false
        fileToDelete = null
    }

    // 头像名称输入对话框
    if (showUploadDialog) {
        FaceNameInputDialog(
            onDismiss = {
                showUploadDialog = false
                pendingImageUri = null
            },
            onConfirm = { faceName ->
                pendingImageUri?.let { uri ->
                    // 将 URI 复制到临时文件
                    val tempFile = File(context.cacheDir, "temp_face_upload_${System.currentTimeMillis()}")
                    try {
                        context.contentResolver.openInputStream(uri)?.use { input ->
                            FileOutputStream(tempFile).use { output ->
                                input.copyTo(output)
                            }
                        }
                        // 使用 FaceManager 处理图片
                        FaceManager.processAndCacheFace(context, faceName, tempFile) { success ->
                            if (success) {
                                mainActivity.showToast(LangUtil.lang(context, R.string.upload_success))
                                // 刷新文件列表
                                fileList = listFiles(currentPath)
                            } else {
                                mainActivity.showToast(LangUtil.lang(context, R.string.upload_fail))
                            }
                            // 删除临时文件
                            tempFile.delete()
                        }
                    } catch (e: Exception) {
                        mainActivity.showToast(LangUtil.lang(context, R.string.upload_fail))
                        tempFile.delete()
                    }
                }
                showUploadDialog = false
                pendingImageUri = null
            }
        )
    }

    Scaffold(
        modifier = modifier.clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) {
            focusManager.clearFocus()
        },
        topBar = {
            MeTopBar(
                title = LangUtil.lang(R.string.file_manage),
                icon = R.drawable.arrow_back,
                onIconClick = { onBackClick() },
                actions = {
                    if (isInFacesDir) {
                        IconButton(onClick = {
                            imagePickerLauncher.launch("image/*")
                        }) {
                            Icon(ImageVector.vectorResource(R.drawable.add), contentDescription = null, Modifier.size(26.dp))
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        MeContent(
            modifier = Modifier
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05F))
        ) {
            if (fileList.isEmpty() && currentPath == filesDir) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = LangUtil.lang(R.string.empty_directory),
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                        fontSize = 14.sp
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize()
                ) {
                    // 添加".."返回上级目录
                    if (currentPath != filesDir) {
                        item {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        val parent = currentPath.parentFile
                                        if (parent != null) {
                                            currentPath = parent
                                            fileList = listFiles(parent)
                                        }
                                    }
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = ImageVector.vectorResource(R.drawable.folder),
                                    contentDescription = null,
                                    modifier = Modifier.size(24.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = "..",
                                    fontSize = 14.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                    items(fileList) { file ->
                        val notSupportedText = LangUtil.lang(R.string.file_not_supported)
                        val openFailText = LangUtil.lang(R.string.open_fail)
                        FileItem(
                            file = file,
                            onClick = {
                                if (file.isDirectory) {
                                    currentPath = file
                                    fileList = listFiles(file)
                                } else {
                                    // 尝试用系统查看器打开文件
                                    openFile(context, file, mainActivity, notSupportedText, openFailText)
                                }
                            },
                            onDelete = {
                                fileToDelete = file
                                showDeleteConfirm = true
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun FileItem(
    file: File,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    val isDirectory = file.isDirectory
    val iconRes = if (isDirectory) R.drawable.folder else R.drawable.file
    val fileSize = if (isDirectory) "" else formatFileSize(file.length())
    val isProtected = isProtectedFile(file)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = ImageVector.vectorResource(iconRes),
            contentDescription = null,
            modifier = Modifier.size(24.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = file.name,
                fontSize = 14.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (fileSize.isNotEmpty()) {
                Text(
                    text = fileSize,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }
        }
        // 只显示非目录且非受保护文件的删除按钮
        if (!isDirectory && !isProtected) {
            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = ImageVector.vectorResource(R.drawable.delete),
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

private fun isProtectedFile(file: File): Boolean {
    val path = file.absolutePath.lowercase()
    return protectedFiles.any { protected ->
        val protectedLower = protected.lowercase()
        // 检查路径中是否包含受保护的目录或文件
        // 如果以下划线结尾，则匹配前缀（如 profileinstaller_）
        if (protectedLower.endsWith("_")) {
            path.contains("/$protectedLower")
        } else {
            path.contains("/$protectedLower/") || path.endsWith("/$protectedLower")
        }
    }
}

private fun openFile(context: android.content.Context, file: File, activity: MainActivity, notSupportedText: String, openFailText: String) {
    val extension = file.extension.lowercase()
    if (extension !in viewableExtensions) {
        activity.showToast(notSupportedText)
        return
    }

    try {
        val uri: Uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, getMimeType(extension))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(intent)
    } catch (e: Exception) {
        activity.showToast("$openFailText: ${e.message}")
    }
}

private fun getMimeType(extension: String): String {
    return when (extension) {
        "jpg", "jpeg" -> "image/jpeg"
        "png" -> "image/png"
        "gif" -> "image/gif"
        "bmp" -> "image/bmp"
        "webp" -> "image/webp"
        "mp4" -> "video/mp4"
        "mkv" -> "video/x-matroska"
        "avi" -> "video/x-msvideo"
        "mov" -> "video/quicktime"
        "mp3" -> "audio/mpeg"
        "wav" -> "audio/wav"
        "ogg" -> "audio/ogg"
        "m4a" -> "audio/mp4"
        "pdf" -> "application/pdf"
        "txt", "log" -> "text/plain"
        else -> "*/*"
    }
}

private fun listFiles(dir: File): List<File> {
    return dir.listFiles()?.sortedWith(compareByDescending<File> { it.isDirectory }.thenBy { it.name.lowercase() })?.toList() ?: emptyList()
}

private fun formatFileSize(size: Long): String {
    return when {
        size < 1024 -> "$size B"
        size < 1024 * 1024 -> "${size / 1024} KB"
        size < 1024 * 1024 * 1024 -> "${size / (1024 * 1024)} MB"
        else -> "${size / (1024 * 1024 * 1024)} GB"
    }
}

@Composable
private fun FaceNameInputDialog(
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var faceName by remember { mutableStateOf("") }
    val context = LocalContext.current

    AlertDialog(
        shape = RoundedCornerShape(10.dp),
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = LangUtil.lang(R.string.input_face_name),
                fontSize = 16.sp
            )
        },
        text = {
            OutlinedTextField(
                value = faceName,
                onValueChange = { faceName = it },
                label = { Text(LangUtil.lang(R.string.input_face_name)) },
                singleLine = true
            )
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (faceName.isNotBlank()) {
                        // 检查是否已存在
                        val faceFile = FaceManager.getFaceFile(context, faceName)
                        if (faceFile != null && faceFile.exists()) {
                            (context as? MainActivity)?.showToast(LangUtil.lang(context, R.string.face_name_exists))
                        } else {
                            onConfirm(faceName)
                        }
                    }
                }
            ) {
                Text(LangUtil.lang(R.string.sure))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(LangUtil.lang(R.string.cancel))
            }
        }
    )
}
