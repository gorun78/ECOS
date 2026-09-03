#!/bin/bash
# 并发压测：5 端点 × 8 并发 × 100 请求 = 每端点 100 req（8 worker × 12.5）
set -u
B=/home/guorongxiao/ECOS/ecos_tests_p99
TOKEN="$(cat /tmp/ecos_token.txt | tr -d '\n')"
BASE="http://localhost:8080"

BODIES=(
  'E1_wave3|POST|/api/v1/cognitive/demo/wave3|{"markdown":"## Q3 毛利率下滑\n\n毛利率从 22% 降至 14%。","domain":"finance","maxDepth":4}'
  'E2_datasources|GET|/api/v1/datanet/datasource|'
  'E3_pipelines|GET|/api/v1/engine/data/pipeline/tasks|'
  'E4_compliance_create|POST|/api/v1/knowledge/compliance-rules|{"name":"p99-bench-conc","domain":"finance","ruleType":"EXPRESSION","condition":"x>1","action":"flag","priority":1,"enabled":false,"description":"conc"}'
  'E5_kg_search|GET|/api/v1/knowledge/search?q=cost&topK=3|'
)

for spec in "${BODIES[@]}"; do
  IFS='|' read -r name method path body <<< "$spec"
  timesfile="/tmp/p99c_$(echo "$name" | tr -c 'a-zA-Z0-9' '_').times"
  : > "$timesfile"

  # 100 请求 / 8 worker = 每 worker 13
  for w in 8; do
    (
      for i in $(seq 1 13); do
        if [ "$method" = "POST" ]; then
          t=$(curl -s -o /dev/null -w "%{time_total}" -X POST -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" -d "$body" -m 90 "$BASE$path" 2>/dev/null)
        else
          t=$(curl -s -o /dev/null -w "%{time_total}" -X GET -H "Authorization: Bearer $TOKEN" -m 90 "$BASE$path" 2>/dev/null)
        fi
        echo "$t" >> "$timesfile"
      done
    ) &
  done
  wait

  awk -v name="$name c8" '{
    a[NR]=$1*1000
  } END {
    n=NR
    for (i=1;i<=n;i++) for(j=i+1;j<=n;j++) if (a[j]<a[i]) {tmp=a[i];a[i]=a[j];a[j]=tmp}
    p50=a[int(n/2)]; if(p50=="")p50=a[1]
    h90=int(n*0.90); if(h90<1)h90=1
    h99=int(n*0.99); if(h99<1)h99=1
    printf "  %-40s n=%d p50=%.1fms p90=%.1fms p99=%.1fms\n", name, n, p50, a[h90], a[h99]
  }' "$timesfile"
done
