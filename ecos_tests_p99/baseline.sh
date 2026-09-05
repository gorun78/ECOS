#!/bin/bash
# 5 候选端点 baseline 压测（E4 用固定 name，压测后统一清理）
set -u
B=/home/guorongxiao/ECOS/ecos_tests_p99

bash $B/p99_bench.sh "E1_wave3" POST /api/v1/cognitive/demo/wave3 \
  '{"markdown":"## Q3 毛利率下滑\n\n毛利率从 22% 降至 14%，其中差旅费增长最高。\n\n库存周转天数从 45 天升至 78 天。","domain":"finance","maxDepth":4}' 60

bash $B/p99_bench.sh "E2_datasources" GET /api/v1/datanet/datasource "" 50

bash $B/p99_bench.sh "E3_pipelines" GET /api/v1/engine/data/pipeline/tasks "" 50

bash $B/p99_bench.sh "E4_compliance_create" POST /api/v1/knowledge/compliance-rules \
  '{"name":"p99-bench-rule","domain":"finance","ruleType":"EXPRESSION","condition":"cost > 100","action":"flag","priority":1,"enabled":true,"description":"benchmark"}' 50

bash $B/p99_bench.sh "E5_kg_search" GET "/api/v1/knowledge/search?q=cost&topK=3" "" 50
