# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

-keep class com.android.pushme.utils.PushKeyInfo {*;}
-keep class com.android.pushme.utils.TempKeyInfo {*;}
-keep class com.android.pushme.MessageInfo {*;}
-keep class com.android.pushme.utils.MqttHostInfo {*;}
-keep class com.android.pushme.utils.MessageRepostInfo {*;}
-keep class com.android.pushme.utils.VersionInfo {*;}
-keep class com.android.pushme.components.message.ChartData {*;}
-keep class com.android.pushme.utils.PushMeLogInfo {*;}
-keep class com.android.pushme.task.TaskInfo {*;}
-keep class com.android.pushme.notify.NotifyEvent {*;}

# 插件商店索引：index.json 是按字段名反序列化的，字段被混淆成 a/b/c 后整份索引解析为空
-keep class com.android.pushme.pluginstore.StoreIndex {*;}
-keep class com.android.pushme.pluginstore.StorePlugin {*;}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile