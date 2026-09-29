---
name: pushme-send-message
description: 向 PushMe 客户端发送消息。推送"通知"用通知消息类（text/markdown/html/url），推送"数据大屏/实时指标"用数据消息类（data/markdata/chart/echarts/svg）——数据消息在手机上不弹通知，选错类型等于没发。支持官方服务、自建服务、局域网直连三种地址。触发词：PushMe、push_key、temp_key、推送消息、发通知、发推送、告警通知、消息提醒、push.i-i.me、自建推送服务。
agent_created: true
---

# PushMe 发送消息

## 什么时候用它

- 要在脚本、CI、定时任务、监控告警里**给设备发一条消息**
- 要写/调试调用 PushMe 接口的代码（curl、Python、Node、Shell）
- 已有企业微信/钉钉/飞书群机器人代码，想**原样改成 PushMe**（接口兼容）
- 要配置自建服务地址、排查「发了但收不到」

---

## 配置文件

技能目录下的 `pushme.config.json` 用于预设密钥和接口地址，避免每次手填：

```json
{
  "push_key": "在 App 内获取的永久密钥",
  "temp_key": "",
  "url": ""
}
```

| 字段 | 说明 |
|---|---|
| `push_key` | 永久推送密钥 |
| `temp_key` | 临时密钥；与 `push_key` **同时填写时优先使用 `temp_key`** |
| `url` | 接口地址。**留空则使用官方接口 `https://push.i-i.me`**；自建服务填 `http://服务器:3010`；局域网直达填 `http://设备IP:端口` |

**取值优先级：环境变量 > `pushme.config.json` > 默认值**

| 环境变量 | 覆盖字段 |
|---|---|
| `PUSHME_URL` | `url` |
| `PUSHME_KEY` | `push_key` |

`url` 留空、为空字符串、或只填了空格，都会**自动回退到官方接口**。只填 `IP:端口`（不带 `http://`）会自动补全协议头。

> ⚠️ **分享技能时不要把密钥填进 `pushme.config.json`** —— 它已被 `.gitignore` 忽略，仅本地生效。仓库内保留的是 `pushme.config.example.json` 模板。
>
> 作为 AI：若当前任务需要发送消息，先读 `pushme.config.json` 取密钥与地址；**若该文件不存在或密钥为空，不要去猜、也不要写死密钥**，应提示用户填写配置或提供环境变量。

### 配套脚本

`send.py` 已实现上述全部逻辑，可直接调用：

```bash
python send.py "标题" "内容"
python send.py "[s]任务完成" "构建耗时 42s"
python send.py "在线人数" "999" --type data      # 数据消息，不弹通知
python send.py "[w]告警" "CPU 95%" --type markdown
```

---

## ⚠️ 第一步：判断用户到底要「通知」还是「数据展示」

**这是最容易出错的地方，必须先判断再动手。**

PushMe 的消息类型实际分为**两大类**，用途完全不同：

| 类别 | 包含类型 | 手机会不会弹通知 | 用途 |
|---|---|---|---|
| **通知消息** | `text` / `markdown` / `html` / `url` | ✅ **会**弹通知、响铃、震动 | 给人看的消息：告警、提醒、任务完成、验证码 |
| **数据消息** | `data` / `markdata` / `chart` / `echarts` / `svg` | ❌ **不会**弹通知 | 给手机「数据小屏」渲染的实时数值、图表、仪表盘 |

> 官方原文：**「数据消息不会发出状态栏通知」**

### 判断规则

**用户的意图里出现这些词 → 用通知消息（默认 `text`）**：
「通知我」「提醒我」「告警」「告诉我」「跑完了叫我」「发给我」「推给我」「监控报警」

**用户的意图里出现这些词 → 才用数据消息**：
「数据大屏」「实时显示」「手机上看图表」「监控面板」「仪表盘」「展示 CPU/在线人数的实时数值」「数据小屏」

**意图不明确时，一律用 `text`（通知消息）。** 因为在用户想「通知」的场景里发数据消息，手机上**静默无提示**，用户会以为推送坏了 —— 这是最严重的错误。宁可多发一条通知，也不要静默不提醒。

**如果用户既要通知又要看数字**：先用 `text` 发一条通知，再用 `data`/`chart` 把数值同步到数据屏，两条消息配合使用。

---

## push_key 从哪来

**无需登录注册。** 安装 App 后，在 App 内获取：

```
App 左上角菜单 → 获取 push_key → 复制
```

| 密钥 | 来源 | 说明 |
|---|---|---|
| `push_key` | App 内获取 | 永久有效，日常首选 |
| `temp_key` | App 内获取 / 自建服务后台 | 临时密钥。**用 temp_key 时不要传 push_key** |

> 自建服务已支持 `temp_key`（在自建服务的 PushKey 管理里配置）。官方与自建**均支持**两种密钥。

push_key 等同于「向该设备发消息的密码」，属敏感信息。代码里从环境变量读取（如 `PUSHME_KEY`），**不要硬编码进仓库**。

---

## 接口地址（三选一，请求参数完全相同）

| 场景 | 地址 | 说明 |
|---|---|---|
| 官方服务 | `https://push.i-i.me` | **默认**；`pushme.config.json` 的 `url` 留空时用它 |
| 自建服务 | `http://你的服务器:3010` | 自建 API 端口 |
| 局域网直达 | `http://App或Win的局域网IP:端口` | 不经公网直达设备 |

官方服务、自建服务、App API、Windows API **接口规范完全一致**，换地址即可。

### 自建服务端口别搞反

| 端口 | 用途 |
|---|---|
| `3010` | Web 管理 **+ 发送 API**（接口请求用这个） |
| `3100` | MQTT / WebSocket 消息服务（**App 里填这个**） |

首次部署后先访问 `http://你的服务器:3010` 完成初始化并配置 push_key。

---

## 请求方式

**GET 或 POST** 均可。参数值**全部是 string 字符串**。

```bash
# 最简：GET，浏览器直接打开也能发
https://push.i-i.me/?push_key=你的KEY&title=标题&content=内容

# 推荐：POST 表单（内容长、含特殊字符时更稳）
curl -X POST 'https://push.i-i.me/?push_key=你的KEY' \
  -d 'title=服务器告警' \
  -d 'content=CPU 使用率 95%'
```

## 请求参数

| 参数 | 必填 | 说明 |
|---|---|---|
| `push_key` | 是* | 接口密钥；用 `temp_key` 时则不填 |
| `title` | 是* | 消息标题；`title` 与 `content` **至少填一项** |
| `content` | 是* | 消息内容；`title` 与 `content` **至少填一项** |
| `date` | 否 | 消息时间，`YYYY-mm-dd HH:ii:ss`，默认当前时间 |
| `type` | 否 | 消息类型，默认 `text` |
| `temp_key` | 否 | 临时密钥；与 `push_key` 二选一 |

> ⚠️ `date` 用小写 `mm` 表示分钟。写成 `MM` 会得到错误时间。

## 返回值判断

| 返回值 | 含义 |
|---|---|
| `success` | ✅ 发送成功 |
| 其它字符串 | ❌ 失败原因（原文就是错误信息） |
| json 字符串 | 兼容企微/钉钉/飞书模式时返回对应 json |

**必须判断返回值，不能只看 HTTP 200：**

```python
import os, requests

# url 留空时用官方接口；也可直接读 pushme.config.json
url = os.environ.get("PUSHME_URL") or "https://push.i-i.me"
data = {
    "push_key": os.environ["PUSHME_KEY"],
    "title": "[w]服务器告警",
    "content": "CPU 使用率 95%",
}
r = requests.post(url, data=data)
if r.status_code == 200 and r.text.strip() == "success":
    print("推送成功")
else:
    print(f"推送失败：{r.status_code} {r.text}")
```

---

## 通知消息类（会弹通知）

### `text` —— 普通文本（默认，最常用）

```
type=text
content=hello world !
```

### `markdown` —— markdown 文本

链接可在 App 内打开（需在设置中开启）。

```
type=markdown
content=## 构建结果\n- 状态：成功\n- 耗时：42s
```

### `html` —— html 消息

支持 css 样式和 js 运行（js 需开启）。

```
type=html
content=<div style="border:10px solid #46bc99;padding:8px">HTML</div>
```

### `url` —— 链接消息，点击打开指定网址

```
type=url
content=https://push.i-i.me
```

---

## 数据消息类（不弹通知，用于数据小屏）

> 再次强调：这些类型**不会发出状态栏通知**。仅当用户明确要「数据展示/大屏/图表」时使用。

### `data` —— 普通数据消息，建议纯数字

```
type=data
content=999
```

### `markdata` —— markdown 格式数据

```
type=markdata
content=> 在线人数：100\n> 新用户数：10
```

### `chart` —— 图表，支持 bar / line / pie

```
type=chart
content=line|5::888,999
```

### `echarts` —— echarts 图表，content 为 option 的 json 字符串

```
type=echarts
content={"series":[{"type":"pie","data":[...]}]}
```

### `svg` —— SVG 图片消息，content 为完整 SVG 代码

```
type=svg
content=<svg xmlns="...">...</svg>
```

### chart 语法

```
类型|长度::value|label,value|label...
```

- 类型：`bar` / `line` / `pie`，默认 `bar`
- 长度：客户端保留的数据个数，默认 10，无 label 时**先进先出**
- 分隔符 `|` `,` `::` 某侧为空时请直接省略

```
999                → bar，长度10，数据 999
5::888             → bar，长度5，数据 888
line|5::888,999    → line，长度5，两个数据
pie|5::888|A,999|B → pie，带标签 A / B
```

### 数据消息的关键特性

- **以 `title` 作为唯一标识**：同一 title 只显示一个模块，内容为**最后一次推送的值**（会被覆盖）。适合做「实时指标」，**不要**用它发流水式日志。
- 支持悬浮、贴边显示、常驻状态栏。
- 另支持 `note`（笔记 / 任务列表）类型：接口已支持，官方文档尚未补充，可直接使用。

### html 的附加行为

- 不含 `<body>` 标签时，系统自动添加并**适配暗黑模式**；含 `<body>` 则原样不处理。
- html 与 echarts 使用 webview 渲染，**性能稍差**，能用 text/markdown 就别用 html。

---

## 消息主题（写在 title 最前面即可，无需额外字段）

| 主题 | 标识 | title 示例 |
|---|---|---|
| 信息 | `[i]` | `[i]收到一条信息` |
| 成功 | `[s]` | `[s]任务执行成功` |
| 警告 | `[w]` | `[w]服务器cpu告警` |
| 失败 | `[f]` | `[f]网站签到失败` |

## 消息分组与头像 `[#分组!头像]`

头像**必须配合分组**，单独设置无效。位置可放在 title 任意位置。

| 效果 | 写法 | 头像 |
|---|---|---|
| 仅分组 | `[#PushMe]hello` | 分组名首字符 `P` |
| 分组 + emoji | `[#PushMe!😄]hello` | 😄 |
| 分组 + 文字 | `hello[#PushMe!百度网]` | 百度网（≤9 字符） |
| 分组 + 图片 | `hello[#PushMe![http地址](https://...jpg)]` | 该图片 |

> App 支持将无分组消息合并为空分组，并支持分组消息置顶。

## 消息通道 `[~通道名]`

支持中文。不同通道可设不同声音、震动、灯光。

- 默认消息通道：**MainChannel**
- 默认状态通道：**StateChannel**（建议开启，便于查看服务连接状态）
- 至少发送过一条通道消息后，手机通知设置里才会出现该通道

## 兼容企业微信 / 钉钉 / 飞书群机器人

PushMe **接口兼容**三家群机器人格式，迁移时**直接替换 URL 即可**，代码基本不用改。

---

## 常见坑

1. **先分「通知」还是「数据」** —— 想通知却发了数据消息，手机上静默无提示，等于没发。
2. **返回值不是 `success` 一律算失败** —— 别只看 HTTP 状态码。
3. **`title` 和 `content` 至少有一个**，两个都空会失败。
4. **`date` 用小写 `mm` 表示分钟**。
5. **`push_key` 与 `temp_key` 二选一**，同时传会出错。
6. **数据消息用 title 去重** —— 同 title 的多次推送只会看到最后一条。
7. **`push_key` 泄露等于任何人可给你的设备发消息** —— 一律走 `pushme.config.json`（已 gitignore）或环境变量。
8. **`pushme.config.json` 的 `url` 留空是有意为之** —— 表示走官方接口，不要误判为「配置未填写」。
9. **收不到消息先查手机权限**，这不是接口问题（见下）。

## 排查「发了但收不到」

按顺序检查：

1. 返回值是不是 `success`？不是就从错误信息入手。
2. **是不是发了数据消息？** 数据消息本就不弹通知，去 App 的「数据小屏」看，而非通知栏。
3. 手机 App 是否在线？开启 **StateChannel** 可实时看连接状态（显示 `Disconnected` 即掉线）。
4. 手机权限：自启动、后台运行、通知权限、电池优化豁免；**Android 12 还需闹钟权限**；并在多任务页面锁定 App。
5. 自建服务：App 里填 **3100**、接口请求 **3010**，别搞反。
6. 自建服务：确认密钥已在 `http://服务器:3010` 的 PushKey 管理中配置好。

---

## 参考

- 官方网站与接口文档：https://push.i-i.me/
- 自建服务端：https://github.com/yafoo/pushme-server （文档 https://push.i-i.me/docs/host）
- 消息插件：https://github.com/yafoo/pushme-plugin
- Windows 客户端：https://github.com/yafoo/pushme-client
