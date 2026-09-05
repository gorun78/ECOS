#!/bin/bash
echo "=== mappings raw ==="
cat /tmp/mappings.json
echo ""
echo "=== source-scan: 提取 7 个目标 controller 路径 ==="
cd /home/guorongxiao/ECOS/ecos_backend
# 1. DataSourceController -> /api/v1/datanet/datasource
echo "[DataSourceController@list]"
grep -A3 '@RequestMapping({"\?/api/v1/datanet/datasource' engine/data-engine/data-engine-impl/src/main/java/com/chinacreator/gzcm/engine/data/controller/DataSourceController.java 2>/dev/null || \
grep -B1 -A5 '@GetMapping' engine/data-engine/data-engine-impl/src/main/java/com/chinacreator/gzcm/engine/data/controller/DataSourceController.java | head -20
echo ""
# 2. compliance rules -> /api/v1/knowledge/compliance-rules
echo "[ComplianceRuleController]"
grep -n 'Mapping' engine/kb-engine/kb-engine-impl/src/main/java/com/chinacreator/gzcm/engine/kb/controller/ComplianceRuleController.java
echo ""
# 3. knowledge search
echo "[knowledge/search]"
grep -rn '"/search"\|/search\b' engine/kb-engine/kb-engine-impl/src/main/java/com/chinacreator/gzcm/engine/kb/controller/ | head -10
