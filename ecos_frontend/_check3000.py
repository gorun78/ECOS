import subprocess
r = subprocess.run(["bash","-lc","export PATH=/usr/bin:$PATH && lsof -ti:3000 && echo PORT3000_OK || echo PORT3000_STILL_DEAD"], capture_output=True, text=True, timeout=10)
print(r.stdout)
