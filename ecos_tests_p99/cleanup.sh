#!/bin/bash
# 清理 bench 创建的 compliance_rules (name=p99-bench-rule / probe-rule / p99-bench-conc)
# WAVe4 bench 没怎么写入,留 probe-rule 1 条给样例
docker exec ecos-postgres psql -U postgres -d sys_man -c "DELETE FROM sys_compliance_rule WHERE name IN ('p99-bench-rule','p99-bench-conc');"
echo "--- 剩余 compliance_rules 名 pairs ---"
docker exec ecos-postgres psql -U postgres -d sys_man -c "SELECT name, COUNT(*) FROM sys_compliance_rule GROUP BY name ORDER BY name LIMIT 10"

echo ""
echo "=== pg_stat 重置 stats 以看 after-push seq_scan ==="
docker exec ecos-postgres psql -U postgres -d sys_man -c "SELECT * FROM pg_stat_reset_single("ecos_pipeline_task"); " 2>&1
docker exec ecos-postgres psql -U postgres -d sys_man -c "SELECT relname, seq_scan, seq_tup_read, idx_scan FROM pg_stat_user_tables WHERE relname IN ('ecos_pipeline_task','sys_compliance_rule','ecos_knowledge.graph_node','td_datasource') ORDER BY relname;"
