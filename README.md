# PushMe
PushMe，一个简单轻量的Android消息客户端！

## 安装

下载地址：在项目github或gitee Releases处下载。

安装提示：系统要求Android10+，因为各安卓系统杀后台严重，所以尽量给与app最大权限：1、允许后台运行 2、允许自启动 3、允许后台高耗电（实际测试，并不高耗电） 3、锁定任务栏 4、允许消息通知

## 接口参数

- 接口地址：https://push.i-i.me
- 请求方式：GET或POST
- 请求参数：
1. push_key：接口密钥，必填，在app上获取
2. title：消息标题，title和content至少填一项
3. content：消息内容，title和content至少填一项
4. date：消息时间，选填，默认为当前日期时间
- 接口返回："success"，表示成功，非"success"即为错误提示内容

所有参数支持GET、POST或混合使用。
## 在线测试

[https://push.i-i.me/](https://push.i-i.me/)