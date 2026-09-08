import subprocess
r = subprocess.run(["bash","-lc","export PATH=/usr/bin:$PATH && cd /home/guorongxiao/ECOS/ecos_frontend && nohup npx tsx server.ts > /tmp/fw4.log 2>&1 & sleep 6 && ss -tlnp | grep 3000"], capture_output=True, text=True, timeout=30)
print("OUT:", r.stdout)
print("ERR:", r.stderr[:300] if r.stderr else "(none)")
