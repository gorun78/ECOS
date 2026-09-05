#!/bin/bash
# P99 压测脚本：对单个端点发 N 个请求，输出 p50/p90/p99
# 用法: p99_bench.sh <name> <method> <path> [json_body] [N]
set -u
NAME="$1"
METHOD="$2"
PATH_URL="$3"
BODY="${4:-}"
N="${5:-50}"

TOKEN="$(cat /tmp/ecos_token.txt 2>/dev/null | tr -d '\n')"
BASE="http://localhost:8080"

# 组装 curl 参数
if [ "$METHOD" = "POST" ] || [ "$METHOD" = "PUT" ] || [ "$METHOD" = "PATCH" ]; then
  if [ -n "$BODY" ]; then
    curl_args=(-s -o /dev/null -w "%{time_total}" -X "$METHOD" -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" -d "$BODY" -m 90)
  else
    curl_args=(-s -o /dev/null -w "%{time_total}" -X "$METHOD" -H "Authorization: Bearer $TOKEN" -m 90)
  fi
else
  curl_args=(-s -o /dev/null -w "%{time_total}" -X "$METHOD" -H "Authorization: Bearer $TOKEN" -m 90)
fi

tmp_out="/tmp/p99_$(echo "$NAME" | tr -c 'a-zA-Z0-9' '_').times"
: > "$tmp_out"
for i in $(seq 1 "$N"); do
  t=$(curl "${curl_args[@]}" "$BASE$PATH_URL" 2>/dev/null)
  echo "$t" >> "$tmp_out"
done

awk -v name="$NAME" '{
  a[NR]=$1*1000
} END {
  n=NR
  # 排序 a 数组
  for (i=1;i<=n;i++) for(j=i+1;j<=n;j++) if (a[j]<a[i]) {tmp=a[i];a[i]=a[j];a[j]=tmp}
  p50=a[int(n/2)]; if (p50=="" ) p50=a[1]
  h90=int(n*0.90); if(h90<1)h90=1
  h99=int(n*0.99); if(h99<1)h99=1
  printf "%s n=%d p50=%.1fms p90=%.1fms p99=%.1fms\n", name, n, p50, a[h90], a[h99]
}' "$tmp_out"
