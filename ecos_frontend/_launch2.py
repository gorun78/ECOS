import subprocess
r = subprocess.run(["bash","-lc","export PATH=/usr/bin:$PATH && cd /home/guorongxiao/ECOS/ecos_frontend && nohup npx tsx server.ts > /tmp/fw4.log 2>&1 & echo PID=$! && disown"], capture_output=True, text=True, timeout=5)
pid_out = r.stdout.strip()
print("stdout:", pid_out)
print("stderr:", (r.stderr or '(none)')[:200])
import time, os
for attempt in range(10):
    time.sleep(3)
    ss = subprocess.run(["bash","-lc","ss -tlnp | grep 3000 || true"], capture_output=True, text=True, timeout=5)
    line = ss.stdout.strip()
    print(f"[t+{attempt*3+3}s]", line)
    if "LISTEN" in line or "3000" in line:
        print("PORT3000 UP, node is detached")
        break
