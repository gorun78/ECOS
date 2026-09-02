#!/usr/bin/env python3
"""前端自动部署脚本：npm build → 端口归属校验 → 杀旧 vite → 启动 vite → 验证。

退出码约定：0=成功，2=构建失败，3=端口被其他项目占用，4=启动失败，5=验证失败。
"""
import argparse
import json
import subprocess
import sys
import time
import urllib.error
import urllib.request


def run(cmd, cwd=None, timeout=None):
    r = subprocess.run(
        cmd, shell=True, cwd=cwd, capture_output=True, text=True, timeout=timeout
    )
    return r.returncode, r.stdout.strip(), r.stderr.strip()


def check_port_owner(port):
    rc, out, _ = run(f"lsof -ti :{port}")
    if rc != 0 or not out:
        return None
    for pid in out.split():
        rc2, cmdline, _ = run(f"ps -p {pid} -o args= --no-headers")
        if rc2 == 0 and cmdline:
            return cmdline
    return None


def is_own_process(cmdline, project_dir):
    if not cmdline:
        return False
    return project_dir in cmdline or "vite" in cmdline


def build(frontend_dir):
    print("[1/3] 前端构建 npm run build ...")
    rc, _, err = run("npm run build", cwd=frontend_dir, timeout=300)
    if rc != 0:
        print(f"  ✗ 构建失败：{err[-2000:]}")
        return False
    print("  ✓ 构建通过")
    return True


def rollback(frontend_dir):
    print("  ↺ 回退前端文件 git checkout HEAD -- frontend/ ...")
    run("git checkout HEAD -- frontend/", cwd=frontend_dir)


def start(frontend_dir, port, log_file):
    print("[2/3] 启动 vite ...")
    subprocess.Popen(
        ["npx", "vite", "--host", "0.0.0.0", f"--port={port}"],
        cwd=frontend_dir,
        stdout=open(log_file, "a"),
        stderr=subprocess.STDOUT,
        start_new_session=True,
    )
    time.sleep(10)


def verify(server_ip, port):
    print("[3/3] 验证端口 + HTTP ...")
    rc, out, _ = run(f"lsof -ti :{port}")
    if rc != 0 or not out:
        print("  ✗ 端口未监听")
        return False
    url = f"http://{server_ip}:{port}"
    try:
        req = urllib.request.Request(url, method="GET")
        with urllib.request.urlopen(req, timeout=10) as resp:
            code = resp.getcode()
    except urllib.error.HTTPError as e:
        code = e.code
    except Exception as e:  # noqa: BLE001
        print(f"  ✗ HTTP 请求失败：{e}")
        return False
    print(f"  HTTP {code} @ {url}")
    return code == 200


def main():
    p = argparse.ArgumentParser(description="前端自动部署")
    p.add_argument("--project-dir", required=True, help="项目根目录（含 frontend/）")
    p.add_argument("--port", type=int, default=18084)
    p.add_argument("--server-ip", default="127.0.0.1")
    args = p.parse_args()

    project_dir = args.project_dir.rstrip("/")
    frontend_dir = f"{project_dir}/frontend"
    log_file = "/tmp/frontend-auto.log"

    if not build(frontend_dir):
        rollback(frontend_dir)
        sys.exit(2)

    owner = check_port_owner(args.port)
    if owner and not is_own_process(owner, project_dir):
        print(json.dumps(
            {"success": False, "error": f"端口 {args.port} 被其他项目占用，拒绝操作"},
            ensure_ascii=False,
        ))
        sys.exit(3)

    run(f"lsof -ti :{args.port} | xargs kill -9 2>/dev/null")
    time.sleep(1)
    start(frontend_dir, args.port, log_file)

    if not verify(args.server_ip, args.port):
        print(json.dumps({"success": False, "error": "启动/验证失败"}, ensure_ascii=False))
        sys.exit(5)

    print(json.dumps(
        {"success": True, "port": args.port},
        ensure_ascii=False,
    ))
    return 0


if __name__ == "__main__":
    sys.exit(main())
