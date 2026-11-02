# app/libs

本目录存放无法或暂未通过 Maven 坐标引入的二进制依赖。每个文件都必须在此登记来源与协议。

---

## org.eclipse.paho.client.mqttv3-1.2.5.jar

| 项 | 内容 |
|---|---|
| 项目 | Eclipse Paho MQTT Java Client |
| 上游 | https://github.com/eclipse-paho/paho.mqtt.java |
| 版本 | 1.2.5 |
| 协议 | **EPL-2.0 或 EDL-1.0 双许可**，本项目选用 **EDL-1.0**（Eclipse Distribution License 1.0，BSD 3-Clause 型） |
| 版权 | Copyright (c) Eclipse Foundation, Inc. |
| 修改 | 无，原样二进制 |

### 为什么放在这里

项目初始化时以本地 jar 形式引入。该版本在 Maven Central 已有坐标，后续可改为：

```groovy
implementation 'org.eclipse.paho:org.eclipse.paho.client.mqttv3:1.2.5'
```

改造时需同步删除本文件，并更新根目录 `NOTICE` 第 3 节。

### 分发义务

EDL-1.0 要求保留版权声明、条件列表与免责声明。本项目在根目录 `NOTICE` 中列出了该声明；如需完整协议文本，见：

- EDL-1.0：https://www.eclipse.org/org/documents/edl-v10.php
- EPL-2.0：https://www.eclipse.org/legal/epl-2.0/

该 jar 内自带 `about.html`，为 Eclipse 项目标准的归属声明，不要从 jar 中删除。
