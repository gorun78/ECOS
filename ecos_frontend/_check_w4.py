import subprocess, json
r = subprocess.run(["bash","-lc","export PATH=\"$HOME/.local/bin:/usr/local/bin:/usr/bin:$PATH\" && hermes kanban --board ecos show t_2fcd5b9a --json"], capture_output=True, text=True, timeout=20)
d = json.loads(r.stdout)
t = d["task"]
print("Status:", t["status"])
for e in d.get("events", [])[-5:]:
    kind = e.get("kind")
    payload = e.get("payload", {})
    if kind == "blocked":
        print(f"\n[blocked] trigger_outcome={payload.get('trigger_outcome')}")
        print(f"  error={payload.get('error','')[:500]}")
    elif kind == "expanded":
        wm = payload.get("worker_message", "")[:800]
        print(f"\n[expanded:last] {wm}")
