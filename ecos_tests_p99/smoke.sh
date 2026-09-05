#!/bin/bash
# 7 个候选端点单请求 smoke，输出每个耗时 + HTTP code
TOKEN="$(cat /tmp/ecos_token.txt | tr -d '\n')"
BASE="http://localhost:8080"

smoke() {
  local name="$1" method="$2" path="$3" body="$4"
  local t code
  if [ "$method" = "POST" ]; then
    local out
    out=$(curl -s -w "\n%{http_code}\n%{time_total}" -o /tmp/smoke_body.txt -X POST -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" ${body:+-d "$body"} -m 90 "$BASE$path")
    # 取 code 和 time：最后一行是 time,倒数第二行是 code
    local lines=($out)
    local tcode="${lines[-2]}" ttime="${lines[-1]}"
    printf "%-45s code=%s t=%.0fms body_head=%s\n" "$name" "$tcode" "$(awk -v t="$ttime" 'BEGIN{printf "%.0f", t*1000}')" "$(head -c 80 /tmp/smoke_body.txt | tr -d '\n')"
  else
    local out
    out=$(curl -s -w "\n%{http_code}\n%{time_total}" -o /tmp/smoke_body.txt -X GET -H "Authorization: Bearer $TOKEN" -m 90 "$BASE$path")
    local lines=($out)
    local tcode="${lines[-2]}" ttime="${lines[-1]}"
    printf "%-45s code=%s t=%.0fms body_head=%s\n" "$name" "$tcode" "$(awk -v t="$ttime" 'BEGIN{printf "%.0f", t*1000}')" "$(head -c 80 /tmp/smoke_body.txt | tr -d '\n')"
  fi
}

smoke "cognitive/demo/wave3"        POST /api/v1/cognitive/demo/wave3 '{"maxClusters":2,"domains":["cx"]}'
smoke "data/sources"                GET  /api/v1/data/sources ""
smoke "data/pipelines"              GET  /api/v1/data/pipelines ""
smoke "kb/compliance-rules(create)" POST /api/v1/kb/compliance-rules '{"name":"perf-test-rule","expression":"x>1","scope":"test","severity":"low"}'
smoke "knowledge/search"            GET  "/api/v1/knowledge/search?query=cost&topK=3" ""
smoke "cognitive/reasoning-path/build" POST /api/v1/cognitive/reasoning-path/build '{"metric":"revenue","seeds":["a","b","c","d"]}'
smoke "worldmodel/domains/cx"       GET  /api/v1/worldmodel/domains/cx ""
