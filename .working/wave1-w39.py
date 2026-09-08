#!/usr/bin/env python3
import json, subprocess
r = subprocess.run(["/home/guorongxiao/.hermes/hermes-agent/venv/bin/hermes",
                    "kanban", "--board", "ecos", "show", "t_b40d584f", "--json"],
                   capture_output=True, text=True)
d = json.loads(r.stdout)
t = d.get('task', {})
print("STATUS:", t.get('status'))
print("TITLE:", t.get('title'))
print("EVENTS (last 8):")
for e in (d.get('events') or [])[-8:]:
    print(" ", e.get('kind'), ":", str(e.get('payload'))[:300])
