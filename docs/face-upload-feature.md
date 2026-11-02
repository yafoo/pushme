# 头像上传功能实现文档

## 功能概述
在文件管理页面的右上角添加了上传功能，当用户处于 `faces` 目录时，可以上传图片并自动处理为头像格式。

## 修改内容

### 1. FaceManager.kt
**文件路径**: `app/src/main/java/com/android/pushme/utils/FaceManager.kt`

**新增方法**: `processAndCacheFace()`
- 功能：处理本地图片文件为头像格式
- 参数：
  - `context`: 上下文
  - `user`: 分组名称
  - `imageFile`: 源图片文件
  - `onComplete`: 处理完成回调（主线程执行）
- 处理流程：
  1. 读取源图片文件
  2. 压缩到最大 256px（保持宽高比）
  3. 转换为 WEBP 格式（质量 85）
  4. 保存到 faces 目录
  5. 在主线程回调结果

**复用逻辑**: 与 `downloadAndCacheFace()` 共用 `compressBitmap()` 方法

### 2. FileScreen.kt
**文件路径**: `app/src/main/java/com/android/pushme/screen/FileScreen.kt`

**新增功能**:
1. **上传按钮**: 在 TopBar 右上角添加 "+" 按钮，仅在 faces 目录显示
2. **图片选择器**: 使用 `ActivityResultContracts.GetContent()` 选择图片
3. **分组名称输入对话框**: `FaceNameInputDialog` 组件
   - 输入分组名称
   - 检查名称是否已存在
   - 确认后调用 `FaceManager.processAndCacheFace()` 处理图片

**关键代码**:
```kotlin
// 判断是否在 faces 目录
val isInFacesDir = remember(currentPath) {
    currentPath.name.equals("faces", ignoreCase = true)
}

// 上传按钮
if (isInFacesDir) {
    IconButton(onClick = { imagePickerLauncher.launch("image/*") }) {
        Icon(
            imageVector = ImageVector.vectorResource(R.drawable.add),
            contentDescription = LangUtil.lang(R.string.upload_file),
            tint = MaterialTheme.colorScheme.onSurface
        )
    }
}
```

### 3. 字符串资源
**文件路径**: 
- `app/src/main/res/values/strings.xml` (英文)
- `app/src/main/res/values-zh/strings.xml` (中文)

**新增字符串**:
- `upload_file`: 上传文件 / Upload File
- `upload_success`: 上传成功！ / Upload successful!
- `upload_fail`: 上传失败 / Upload failed
- `select_image`: 选择图片 / Select Image
- `input_face_name`: 请输入分组名称 / Please input group name
- `face_name_exists`: 分组名称已存在 / Group name already exists

## 使用流程

1. 用户在文件管理页面进入 `faces` 目录
2. 点击右上角 "+" 按钮
3. 从相册选择图片
4. 输入分组名称
5. 系统自动处理图片：
   - 压缩到 256px
   - 转换为 WEBP 格式
   - 保存到 faces 目录
6. 显示成功/失败提示
7. 自动刷新文件列表

## 技术要点

1. **逻辑复用**: 头像处理逻辑与 `FaceManager.downloadAndCacheFace()` 共用压缩算法
2. **异步处理**: 图片处理在后台线程执行，回调在主线程
3. **临时文件**: 使用 `cacheDir` 存储临时文件，处理完成后自动删除
4. **错误处理**: 完善的异常捕获和用户提示
5. **UI 一致性**: 使用现有的 `MeTopBar` 和 `AlertDialog` 组件

## 文件结构

```
app/src/main/
├── java/com/android/pushme/
│   ├── screen/FileScreen.kt (修改)
│   └── utils/FaceManager.kt (修改)
└── res/values/
    ├── strings.xml (修改)
    └── values-zh/strings.xml (修改)
```

## 测试建议

1. 测试在 faces 目录外不显示上传按钮
2. 测试在 faces 目录显示上传按钮
3. 测试选择图片后输入分组名称
4. 测试重复分组名称的提示
5. 测试图片压缩和格式转换
6. 测试上传成功后的文件列表刷新
7. 测试异常情况（无效图片、权限问题等）
