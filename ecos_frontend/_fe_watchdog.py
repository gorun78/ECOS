"""看门狗： wenn 3000 端口死了就重启 nohup tsx server.ts"""
import subprocess, time, os

def is_alive():
    r = subprocess.run(["bash","-lc","ss -tln | grep -c 3000 || true"], capture_output=True, text=True, timeout=8)
    try:
        return int(r.stdout.strip()) > 0
    except:
        return False

def launch():
    subprocess.Popen(["bash","-c","export PATH=/mnt/c/Program\\ Files/nodejs:/usr/bin:/usr/local/bin && cd /home/guorongxiao/ECOS/ecos_frontend && exec npx tsx server.ts"],
                     stdout=open("/tmp/fw4.log","a"), stderr=subprocess.STDOUT,
                     start_new_session=True, env={**os.environ, "PATH": "/mnt/c/Program Files/nodejs:/usr/bin:/usr/local/bin"})

if not is_alive():
    print("port 3000 dead at start, launching...")
    launch()

# 守护循环：每 15s 检查一次
for i in range(2880):  # 2880 * 15s = 12h
    time.sleep(15)
    if not is_alive():
        print(f"[watchdog t={i*15}s] port3000 dead, relaunch...")
        launch()
        time.sleep(3)
        if is_alive():
            print(f"[watchdog t={(i+1)*15}s] back up")
print("watchdog stopped (12h)")
