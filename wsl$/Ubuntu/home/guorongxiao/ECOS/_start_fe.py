import subprocess
r = subprocess.run(["bash","-lc","export PATH=/usr/bin:$PATH && cd /home/guorongxiao/ECOS/ecos_frontend && nohup node server.js > /tmp/fw4.log 2>&1 & sleep 5 && netstat -tlnp 2>/dev/null | grep :3000 || ss -tlnp | grep 3000"], capture_output=True, text=True, timeout=30)
print("OUT:", r.stdout)
print("ERR:", r.stderr[:300] if r.stderr else "(none)")
