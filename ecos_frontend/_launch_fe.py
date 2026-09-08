import subprocess, time
# 双 parts：先 launch 短 timeout 返回，不依赖子进程保活
r = subprocess.run(["bash","-lc","export PATH=/usr/bin:$PATH && cd /home/guorongxiao/ECOS/ecos_frontend && nohup npx tsx server.ts > /tmp/fw4.log 2>&1 & disown; for i in $(seq 1 12); do sleep 3; if ss -tlnp | grep -q :3000; then echo PORT_OK taken ${[1]}; break; fi; if [ $i -eq 12 ]; then echo TIMEOUT36s_after_launch; fi; done && ss -tlnp | grep :3000"], capture_output=True, text=True, timeout=60)
print("OUT:", r.stdout.strip())
print("ERR:", (r.stderr or "(none)")[:200])
