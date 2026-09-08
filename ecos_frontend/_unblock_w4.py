import subprocess, json

def h(*args):
    r = subprocess.run(["bash","-lc",f"export PATH=\"$HOME/.local/bin:/usr/local/bin:/usr/bin:$PATH\" && hermes {' '.join(args)}"],
                       capture_output=True, text=True, timeout=30)
    return r.stdout, r.stderr

out, err = h("kanban","--board","ecos","unblock","t_2fcd5b9a")
print("Unblock:", out, err[:200])

# 检查 git diff
r = subprocess.run(["bash","-lc","export PATH=\"/usr/bin:$PATH\" && cd /home/guorongxiao/ECOS && git diff --stat HEAD 2>&1 | tail -10"], capture_output=True, text=True, timeout=30)
print("git diff:", r.stdout, r.stderr[:200])
