import os, re
from collections import defaultdict
root = "src"
zh = re.compile(r'[\u4e00-\u9fff]')
slate = re.compile(r'slate-\d|text-white|bg-white|slate-[0-9]|#(?:[0-9a-fA-F]{3}|[0-9a-fA-F]{6})\b')

rows = []
for dp, _, fns in os.walk(root):
    if "__tests__" in dp:
        continue
    for fn in fns:
        if not fn.endswith((".tsx", ".ts")):
            continue
        p = os.path.join(dp, fn)
        t = open(p, encoding="utf-8", errors="ignore").read()
        zhc = 0
        s = 0
        for l in t.splitlines():
            code = l.split('//', 1)[0]
            zhc += len(zh.findall(code))
            if slate.search(l):
                s += 1
        if zhc > 50 or s > 20:
            rows.append((zhc, s, p.replace('\\', '/')))

rows.sort(reverse=True)
print("files with zhCJ>50 or slate>20:", len(rows))
d = defaultdict(lambda: [0, 0, 0])
for zhc, s, p in rows:
    parts = p.split('/')
    key = '/'.join(parts[:3]) if len(parts) >= 3 else '/'.join(parts[:2])
    d[key][0] += 1
    d[key][1] += zhc
    d[key][2] += s
print("%-44s %4s %8s %6s" % ("dir", "#f", "zhCJK", "slate"))
for k, v in sorted(d.items(), key=lambda x: -x[1][1]):
    print("%-44s %4d %8d %6d" % (k, v[0], v[1], v[2]))
print("\nTOP 25 files:")
for zhc, s, p in rows[:25]:
    print("%6d %5d  %s" % (zhc, s, p))
