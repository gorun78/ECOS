import subprocess
script = r'''
#!/usr/bin/env bash
export PATH=/usr/bin:$PATH
cd /home/guorongxiao/ECOS/ecos_frontend
exec npx tsx server.ts >> /tmp/fw4.log 2>&1
'''
r = subprocess.run(["bash","-lc","cat > /tmp/fw4_wd.sh <<'EOS'\n"+script+f"""
EOS
chmod +x /tmp/fw4_wd.sh
nohup timeout 43200 python3 -u /home/guorongxiao/ECOS/ecos_frontend/_fe_watchdog.py > /tmp/wd.log 2>&1 &
echo WD_PID=$!
disown
sleep 5
ss -tlnp | grep 3000
"""], capture_output=True, text=True, timeout=20)
print("OUT:", r.stdout)
print("ERR:", (r.stderr or '(none)')[:200])
