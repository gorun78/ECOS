import subprocess, time, os

script = r'''
#!/usr/bin/env bash
export PATH=/usr/bin:$PATH
cd /home/guorongxiao/ECOS/ecos_frontend
nohup npx tsx server.ts > /tmp/fw4.log 2>&1 &
NODPID=$!
disown
echo "NODE_PID=$NODPID"
sleep 1
echo "PORT3000_AT_START=$(ss -tln | grep -c 3000)"
'''
# 写在 WSL 可执行文件
script_path = "/tmp/fw4_launch.sh"
r = subprocess.run(["bash","-lc",f"cat > {script_path} <<'EOS'\n{script}\nEOS\nchmod +x {script_path} && timeout 40 bash {script_path}"], capture_output=True, text=True, timeout=60)
print("OUT:", r.stdout.strip())
print("ERR:", (r.stderr or '(none)')[:300])
# 轮询确认端口
import time
for i in range(10):
    time.sleep(3)
    ss = subprocess.run(["bash","-lc","ss -tlnp | grep 3000 || true"], capture_output=True, text=True, timeout=8)
    out = ss.stdout.strip()
    print(f"[t+{(i+1)*3}s]", out)
    if "LISTEN" in out:
        print("READY TO GO")
        break
