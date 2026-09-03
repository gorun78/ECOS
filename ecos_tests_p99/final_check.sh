#!/bin/bash
echo "=== 1. 当前 index 列表 (确认 4 索引 + trgm 扩展) ==="
docker exec ecos-postgres psql -U postgres -d sys_man -c "
SELECT indexname FROM pg_indexes
WHERE indexname IN ('idx_ecos_pipeline_task_updated_at','idx_sys_compliance_rule_domain','idx_sys_compliance_rule_status','idx_td_datasource_create_time','idx_td_datasource_create_time_data','idx_graph_node_label_trgm')
ORDER BY indexname"
docker exec ecos-postgres psql -U postgres -d sys_man -c "SELECT extname FROM pg_extension WHERE extname='pg_trgm'"

echo ""
echo "=== 2. Java cache 编译产物 ==="
ls -la /home/guorongxiao/.m2/repository/com/chinacreator/gzcm/kb-engine-impl/1.0.0-SNAPSHOT/kb-engine-impl-1.0.0-SNAPSHOT.jar

echo ""
echo "=== 3. gateway alive (smoke 1 条) ==="
TOKEN="$(cat /tmp/ecos_token.txt | tr -d '\n')"
code=$(curl -s -o /dev/null -w "%{http_code}" -m 5 -H "Authorization: Bearer $TOKEN" "http://localhost:8080/api/v1/knowledge/search?q=cost")
echo "knowledge/search: $code"

echo ""
echo "=== 4. Wave5 报告文件 ==="
ls -la "/home/guorongxiao/ECOS/docs/08-产品化重构方案/17-Wave5.2-T20-P99-optimization.md"

echo ""
echo "=== 5. KGS 源码 cache 字段 ==="
grep -n "searchCache\|Caffeine.newBuilder" /home/guorongxiao/ECOS/ecos_backend/engine/kb-engine/kb-engine-impl/src/main/java/com/chinacreator/gzcm/engine/kb/service/KnowledgeGraphServiceImpl.java

echo ""
echo "=== 6. V107 migration ==="
ls -la /home/guorongxiao/ECOS/ecos_backend/gateway/src/main/resources/db/migration/V107__ecos_wave5_2_p99_indexes.sql

echo ""
echo "ALL TOOLS RUNNING OK"
