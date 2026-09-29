#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
PushMe 发送消息 —— 读取同目录 pushme.config.json 的配置并发送消息。

配置读取优先级：环境变量 > pushme.config.json > 默认值
  PUSHME_KEY : 覆盖 push_key（推荐，避免把密钥写进文件）
  PUSHME_URL : 覆盖 url

用法：
  python send.py "标题" "内容"
  python send.py "[s]任务完成" "构建耗时 42s"
  python send.py "在线人数" "999" --type data      # 数据消息，不弹通知
  python send.py "[w]告警" "CPU 95%" --type markdown
"""

import argparse
import json
import os
import sys
import urllib.parse
import urllib.request

DEFAULT_URL = "https://push.i-i.me"  # url 留空时使用的官方接口
CONFIG_NAME = "pushme.config.json"


def load_config():
    """读取 pushme.config.json，不存在则返回空配置。"""
    path = os.path.join(os.path.dirname(os.path.abspath(__file__)), CONFIG_NAME)
    if not os.path.exists(path):
        return {}
    try:
        with open(path, "r", encoding="utf-8") as f:
            return json.load(f)
    except (OSError, ValueError) as e:
        print(f"配置文件读取失败，将使用默认配置：{e}", file=sys.stderr)
        return {}


def resolve(config):
    """按 环境变量 > 配置文件 > 默认值 的优先级解析出 (url, key_param, key_value)。"""
    # 接口地址：留空或未配置时回退官方接口
    url = os.environ.get("PUSHME_URL") or config.get("url") or ""
    url = url.strip()
    if not url:
        url = DEFAULT_URL
    if not url.startswith(("http://", "https://")):
        url = "http://" + url  # 兼容只填 IP:端口 的写法

    # 密钥：temp_key 优先于 push_key，二选一
    temp_key = (config.get("temp_key") or "").strip()
    push_key = (os.environ.get("PUSHME_KEY") or config.get("push_key") or "").strip()

    if temp_key:
        return url, "temp_key", temp_key
    if push_key:
        return url, "push_key", push_key
    return url, None, None


def send(title="", content="", msg_type="text", date=""):
    """发送消息，返回 (是否成功, 服务端返回文本)。"""
    url, key_param, key_value = resolve(load_config())
    if not key_value:
        return False, "未配置密钥：请在 pushme.config.json 填写 push_key，或设置环境变量 PUSHME_KEY"

    payload = {key_param: key_value}
    if title:
        payload["title"] = title
    if content:
        payload["content"] = content
    if msg_type and msg_type != "text":
        payload["type"] = msg_type
    if date:
        payload["date"] = date

    data = urllib.parse.urlencode(payload).encode("utf-8")
    req = urllib.request.Request(url, data=data, method="POST")
    try:
        with urllib.request.urlopen(req, timeout=30) as resp:
            body = resp.read().decode("utf-8", "replace").strip()
    except Exception as e:
        return False, f"请求失败：{e}"

    return body == "success", body


def main():
    p = argparse.ArgumentParser(description="通过 PushMe 发送消息")
    p.add_argument("title", help="消息标题")
    p.add_argument("content", nargs="?", default="", help="消息内容")
    p.add_argument(
        "--type",
        default="text",
        choices=["text", "markdown", "html", "url",
                 "data", "markdata", "chart", "echarts", "svg", "note"],
        help="消息类型，默认 text（通知消息）；data/chart 等为数据消息，不弹通知",
    )
    p.add_argument("--date", default="", help='消息时间，格式 "YYYY-mm-dd HH:ii:ss"，默认当前时间')
    args = p.parse_args()

    ok, msg = send(args.title, args.content, args.type, args.date)
    if ok:
        print("推送成功")
        return 0
    print(f"推送失败：{msg}", file=sys.stderr)
    return 1


if __name__ == "__main__":
    sys.exit(main())
