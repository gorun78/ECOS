#!/usr/bin/env python3
"""后端自动部署脚本：编译 → 端口归属校验 → 强杀旧进程 → 启动 jar → 验证。

复用 kanban-dispatch-json-format/references/deploy-java-process-force-kill.md 的强杀模式。
退出码约定：0=成功，2=编译失败，3=端口被其他项目占用，4=启动失败，5=验证失败。
"""
import argparse
import json
import subprocess
import sys
import time
import urllib.error
import urllib.request


def run(cmd, cwd=None, timeout=None):
    """执行 shell 命令，返回 (returncode, stdout, stderr)。"""
    r = subprocess.run(
        cmd, shell=True, cwd=cwd, capture_output=True, text=True, timeout=timeout
    )
    return r.returncode, r.stdout.strip(), r.stderr.strip()


def check_port_owner(port):
    """返回监听指定端口的进程命令行，无进程返回 None。"""
    rc, out, _ = run(f"lsof -ti :{port}")
    if rc != 0 or not out:
        return None
    pids = out.split()
    for pid in pids:
        rc2, cmdline, _ = run(f"ps -p {pid} -o args= --no-headers")
        if rc2 == 0 and cmdline:
            return cmdline
    return None


def is_own_process(cmdline, project_dir):
    """判断进程命令行是否属于本项目（jar 名或项目目录匹配）。"""
    if not cmdline:
        return False
    return project_dir in cmdline or "ainative-factory" in cmdline


def build(backend_dir):
    print("[1/4] 编译后端 mvn clean package -DskipTests ...")
    rc, out, err = run("mvn clean package -DskipTests", cwd=backend_dir, timeout=600)
    if rc != 0:
        print(f"  ✗ 编译失败：{err[-2000:]}")
        return False
    print("  ✓ 编译通过")
    return True


def kill_old_process(jar_name):
    print("[2/4] 强杀旧进程 pkill -9 -f ...")
    run(f"pkill -9 -f '{jar_name}' 2>/dev/null")
    time.sleep(2)


def start(jar_path, port, log_file):
    print("[3/4] 启动新 jar ...")
    subprocess.Popen(
        ["java", "-jar", jar_path, f"--server.port={port}"],
        stdout=open(log_file, "a"),
        stderr=subprocess.STDOUT,
        start_new_session=True,
    )
    time.sleep(15)


def verify(server_ip, port, health_path, expected_codes):
    print("[4/4] 验证端口 + HTTP ...")
    rc, out, _ = run(f"lsof -ti :{port}")
    if rc != 0 or not out:
        print("  ✗ 端口未监听")
        return False

    url = f"http://{server_ip}:{port}{health_path}"
    try:
        req = urllib.request.Request(url, method="GET")
        with urllib.request.urlopen(req, timeout=10) as resp:
            code = resp.getcode()
    except urllib.error.HTTPError as e:
        code = e.code
    except Exception as e:  # noqa: BLE001
        print(f"  ✗ HTTP 请求失败：{e}")
        return False

    expected = {int(c) for c in expected_codes.split(",")}
    print(f"  HTTP {code} @ {url}（期望 {sorted(expected)}）")
    return code in expected


def main():
    p = argparse.ArgumentParser(description="后端自动部署")
    p.add_argument("--project-dir", required=True, help="项目根目录（含 backend/）")
    p.add_argument("--jar", default="ainative-factory-1.0.0.jar")
    p.add_argument("--port", type=int, default=18085)
    p.add_argument("--server-ip", default="127.0.0.1")
    p.add_argument("--health-path", default="/health")
    p.add_argument("--expected-codes", default="200")
    p.add_argument("--skip-build", action="store_true", help="跳过编译，直接部署")
    args = p.parse_args()

    project_dir = args.project_dir.rstrip("/")
    backend_dir = f"{project_dir}/backend"
    jar_path = f"{backend_dir}/target/{args.jar}"
    log_file = "/tmp/backend-auto.log"

    if not args.skip_build:
        if not build(backend_dir):
            sys.exit(2)

    # 端口归属校验（只操作本项目端口）
    owner = check_port_owner(args.port)
    if owner and not is_own_process(owner, project_dir):
        print(json.dumps(
            {"success": False, "error": f"端口 {args.port} 被其他项目占用，拒绝操作"},
            ensure_ascii=False,
        ))
        sys.exit(3)

    kill_old_process(args.jar)
    start(jar_path, args.port, log_file)

    if not verify(args.server_ip, args.port, args.health_path, args.expected_codes):
        print(json.dumps({"success": False, "error": "启动/验证失败"}, ensure_ascii=False))
        sys.exit(5)

    print(json.dumps(
        {"success": True, "port": args.port, "jar": args.jar},
        ensure_ascii=False,
    ))
    return 0


if __name__ == "__main__":
    sys.exit(main())
