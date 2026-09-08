#!/usr/bin/env python3
"""Launch ECOS gateway + frontend (tsx server.ts) as detached WSL processes."""
import subprocess, os

BE = "/home/guorongxiao/ECOS/ecos_backend"
FE = "/home/guorongxiao/ECOS/ecos_frontend"
JAVA = "/home/guorongxiao/.local/jdk/jdk-17.0.19+10/bin/java"
NODE = "/home/guorongxiao/.local/bin/node"

env = dict(os.environ)
env["PATH"] = "/home/guorongxiao/.local/bin:/home/guorongxiao/.local/jdk/jdk-17.0.19+10/bin:" + env.get("PATH", "/usr/bin:/sbin:/usr/sbin:/bin")

subprocess.Popen(
    [JAVA, "-jar", BE + "/gateway/target/gateway-1.0.0-SNAPSHOT.jar",
     "--server.port=8080"],
    stdout=open("/tmp/ecos_be.log", "a"),
    stderr=subprocess.STDOUT,
    start_new_session=True,
    env=env,
)
print("[OK] backend -> :8080")

subprocess.Popen(
    [NODE, FE + "/node_modules/tsx/dist/cli.mjs", FE + "/server.ts"],
    cwd=FE,
    stdout=open("/tmp/ecos_fe.log", "a"),
    stderr=subprocess.STDOUT,
    start_new_session=True,
    env=env,
)
print("[OK] frontend -> :3000")
