#!/usr/bin/env python3
import json, subprocess
HERMES = "/home/guorongxiao/.hermes/hermes-agent/venv/bin/hermes"
BOARD = "ecos"
r = subprocess.run([HERMES, "kanban", "--board", BOARD, "list", "--json"],
                  capture_output=True, text=True)
data = json.loads(r.stdout)
ids = ['t_d45a9f20','t_b40d584f','t_260940f4','t_448ed2bb','t_64e6e69e','t_8986e024','t_b1abcc91','t_acbf4a05','t_3ccd06e8','t_d1ad3bca','t_6d63b733','t_c14e22ca','t_eececa8a','t_e284cfcd','t_a1ec47dd','t_7d597404','t_5d803bd9','t_48efbdbe']
layer = {
 't_d45a9f20':'W-38','t_b40d584f':'W-39','t_260940f4':'W-40',
 't_448ed2bb':'W-41','t_64e6e69e':'W-43','t_8986e024':'W-44',
 't_b1abcc91':'V-38','t_acbf4a05':'V-39','t_3ccd06e8':'V-40',
 't_d1ad3bca':'V-41','t_6d63b733':'V-43','t_c14e22ca':'V-44',
 't_eececa8a':'S-38','t_e284cfcd':'S-39','t_a1ec47dd':'S-40',
 't_7d597404':'S-41','t_5d803bd9':'S-43','t_48efbdbe':'S-44',
}
tagged = []
for t in data:
    if t.get('id') in ids:
        tagged.append(t)
tagged.sort(key=lambda t: list(layer.keys()).index(t['id']))
done = 0
for t in tagged:
    s = t.get('status','?')
    title = (t.get('title','') or '')[:48].replace('\n',' ')
    body = (t.get('body','') or '')[:80].replace('\n','|')
    icon = '✅' if s == 'done' else '⏳' if s == 'running' else '⛔' if s == 'blocked' else '·'
    if s == 'done':
        done += 1
    print(f"{icon} {s:10} {layer[t['id']]:5} {t['id']}  {title:48}  body={body}")
print()
print(f"DONE: {done} / {len(tagged)}")
