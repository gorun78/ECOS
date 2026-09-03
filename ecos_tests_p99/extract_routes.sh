#!/bin/bash
# 从 actuator mappings 提取 Spring MVC 端点
docker exec ecos-postgres psql -U postgres -d sys_man -t -c "select 1;" >/dev/null 2>&1 && echo "PG OK" || echo "PG FAIL"

# 拿 mappings
curl -s -m 15 "http://localhost:8080/actuator/mappings" -o /tmp/mappings.json
echo "mappings size=$(wc -c < /tmp/mappings.json)"

python3 - <<'PY'
import json,re
d=json.load(open('/tmp/mappings.json'))
ctx=d.get('contexts',{})
paths=[]
def walk(o):
    if isinstance(o,dict):
        # pattern -> details
        if 'url' in o and 'handler' in o:
            paths.append((o.get('url'), o.get('handler','')))
        for v in o.values(): walk(v)
    elif isinstance(o,list):
        for v in o: walk(v)
walk(d)
print("total handler mappings:", len(paths))
# 展示与目标相关的
targets=['source','pipeline','compliance','search','reasoning','worldmodel','demo','explor']
for url,h in paths:
    if any(t in url.lower() for t in targets):
        print(f"  {url}  -> {h[-80:]}")
PY
