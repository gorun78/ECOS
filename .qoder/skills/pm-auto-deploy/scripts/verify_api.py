#!/usr/bin/env python3
"""API 功能验证脚本：登录获取 token → 调用验证端点 → 测量响应时间。

退出码约定：0=成功，2=登录失败，3=验证端点失败，4=响应时间超阈值。
"""
import argparse
import json
import sys
import time
import urllib.error
import urllib.request


def post_json(url, payload, timeout=10):
    data = json.dumps(payload).encode("utf-8")
    req = urllib.request.Request(
        url, data=data, headers={"Content-Type": "application/json"}, method="POST"
    )
    with urllib.request.urlopen(req, timeout=timeout) as resp:
        return resp.getcode(), resp.read().decode("utf-8")


def login(base_url, login_path, account, password):
    print(f"[1/3] 登录 {base_url}{login_path} ...")
    url = f"{base_url}{login_path}"
    try:
        code, body = post_json(url, {"account": account, "password": password})
    except urllib.error.HTTPError as e:
        code, body = e.code, e.read().decode("utf-8")
    except Exception as e:  # noqa: BLE001
        print(f"  ✗ 登录请求失败：{e}")
        return None

    if code not in (200, 201):
        print(f"  ✗ 登录失败 HTTP {code}：{body[:500]}")
        return None

    try:
        data = json.loads(body)
    except json.JSONDecodeError:
        data = {"raw": body}

    # 兼容多种 token 结构：data 直接为 token 字符串，或 data.token / data.access_token / 顶层 token
    payload = data.get("data")
    if isinstance(payload, str) and payload:
        token = payload
    elif isinstance(payload, dict):
        token = payload.get("token") or payload.get("access_token")
    else:
        token = None
    token = token or data.get("token") or data.get("access_token")
    if not token:
        print(f"  ✗ 登录响应缺少 token 字段：{body[:500]}")
        return None
    print("  ✓ 登录成功，获取 token")
    return token


def call_verify(base_url, verify_path, token):
    print(f"[2/3] 调用验证端点 {verify_path} ...")
    url = f"{base_url}{verify_path}"
    req = urllib.request.Request(
        url, method="GET", headers={"Authorization": f"Bearer {token}"}
    )
    start = time.time()
    try:
        with urllib.request.urlopen(req, timeout=10) as resp:
            code = resp.getcode()
    except urllib.error.HTTPError as e:
        code = e.code
    except Exception as e:  # noqa: BLE001
        print(f"  ✗ 验证端点请求失败：{e}")
        return None
    elapsed = time.time() - start
    print(f"  ✓ HTTP {code}，耗时 {elapsed:.2f}s")
    return code, elapsed


def main():
    p = argparse.ArgumentParser(description="API 功能验证")
    p.add_argument("--base-url", required=True, help="后端地址，如 http://127.0.0.1:18085")
    p.add_argument("--account", default="admin")
    p.add_argument("--password", default="admin123")
    p.add_argument("--login-path", default="/api/auth/login")
    p.add_argument("--verify-path", required=True, help="本次开发功能的验证端点")
    p.add_argument("--max-response-time", type=float, default=3.0)
    args = p.parse_args()

    base_url = args.base_url.rstrip("/")

    token = login(base_url, args.login_path, args.account, args.password)
    if not token:
        sys.exit(2)

    result = call_verify(base_url, args.verify_path, token)
    if result is None:
        sys.exit(3)

    code, elapsed = result
    if elapsed > args.max_response_time:
        print(json.dumps(
            {"success": False, "error": f"响应时间 {elapsed:.2f}s 超阈值 {args.max_response_time}s"},
            ensure_ascii=False,
        ))
        sys.exit(4)

    print(json.dumps(
        {"success": True, "http_code": code, "response_time": round(elapsed, 3)},
        ensure_ascii=False,
    ))
    return 0


if __name__ == "__main__":
    sys.exit(main())
