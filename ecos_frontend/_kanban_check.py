import subprocess, json
r = subprocess.run(["hermes", "kanban", "--board", "ecos", "list", "--json"],
                   capture_output=True, text=True, timeout=30)
ts = json.loads(r.stdout)
active = [t for t in ts if t.get("status") in ("running", "todo", "blocked")]
print(f"Total active: {len(active)}")
for t in active[:20]:
    print("  " + str(t.get('task_id','?')).ljust(12) + " " + t['status'].ljust(10) + "  " + t.get('title','')[:70])
