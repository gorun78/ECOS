# PMO-37 数据源元数据获取策略 — 验收报告

- 任务分支: feature/pmo-37-metadata-strategy
- 验收人: Commander (ecos-pmo)
- 日期: 2026-08-31
- 状态: **BE 完成 ✅ | FE 完成 ✅ | 编译门禁 ✅ | API 运行时验收 ⏸ 受阻**

## 验收标准与进展

| T | 交付物 | 状态 |
|---|--------|------|
| T | 设计文档 `docs/3-data/PMO-37-数据源元数据获取策略.md` | ✅ committed 6adf59d |
| T1 | V105 migration (metadata_config + last_collect_time) | ✅ committed 46b8496 |
| T1 | V106 migration (metadata_collect_log) | ✅ committed 46b8496 |
| T2 | DataSourceDTO 7 策略字段 + metadataConfig JSONB | ✅ committed 46b8496 |
| T2 | DataSourceEntity metadataConfig + lastCollectTime | ✅ committed 46b8496 |
| T2 | DataSourceMapper.xml new column mappings | ✅ committed 46b8496 |
| T3 | MetadataCollectTaskParser + Executor + Registrar | ✅ committed ea10b76 |
| T4 | MetadataController (/catalog /collect-async /collect-status /resources) | ✅ committed ea10b76 |
| T4 | DataSourceRepository lastCollectTime + metadataConfig | ✅ committed 229f1a1 |
| T5 | MetadataAsyncTrigger (ON_SAVE / ON_DEMAND) | ✅ committed ea10b76 |
| T6 | MetadataRowCountService (EXACT / ESTIMATE / OFF) | ✅ committed ea10b76 |
| T6 | AutoCollectScheduler (ON_SCHEDULE) | ✅ committed ea10b76 |
| T7 | @EnableAsync on DataEngineApplication | ✅ committed a3a664b |
| T8 | DataSourceServiceImpl metadataConfig wiring | ✅ committed a3a664b |
| T9a | FE api.ts: triggerMetadataCollect + strategy DTOs | ✅ committed 3340c8d |
| T9b | FE ConnectionsTab.tsx 策略UI (embedded, no new tab) | ✅ committed 350e0ab |
| T10 | FE CatalogTree 分页 + physicalRows (已存在，upstream) | ✅ 无需改动 |

## 编译门禁（已验证）

```
BE: mvn -q compile -Dmaven.test.skip=true -pl \
     common/common-api,engine/data-engine/data-engine-impl,\
     engine/data-engine/data-engine-boot,gateway -am
   → EXIT=0  ✅ (/tmp/pmo37-compile2.log, 2026-08-31)

FE: npm run build  → Done ✅
    npx tsc --noEmit  → data-workbench 0 errors ✅
    (aiworkbench TS1005 系列为前序已知问题，不在本次范围)
```

## API 运行时验收 — ⏸ 受阻

**阻断原因**：Gateway 当前没有运行。新构建的 JAR (gateway-1.0.0-SNAPSHOT.jar, 12:03, EXIT=0)
已就绪于 `ecos_backend/gateway/target/`，但它未在任何正在运行的进程里。
当前无 Java gateway 进程（`fuser 8080/tcp` 空），也无 `ecos-api-gateway` docker container。

**恢复 Gateway** 需要执行团队知道的启动步骤（docker compose? 独立 java -jar?）。
**不应当 PMO 猜测约定。** 这里记录恢复后需要跑的全部 curl 命令，供执行人跑完贴结果。

### 恢复后 curl 清单（顺序执行）

```bash
# A. 登录取 token (Base = http://localhost:8080)
TOKEN=*** -s -X POST http://localhost:8080/api/v1/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"admin","password":"admin12345"}' \
  | python3 -c "import sys,json;d=json.load(sys.stdin);print(d.get('data',{}).get('token') or d.get('data',{}).get('accessToken') or '')")
echo "token_len=${#TOKEN}"

# B. T1/T10: 目录分页 + 行数
curl -s "http://localhost:8080/api/v1/datanet/metadata/catalog?pageNum=1&pageSize=5" \
  -H "Authorization: Bearer $TOKEN" | python3 -m json.tool | head -40
# 验: body.code=0, data.items[].physicalRows∈int, data.totalPages∈int

# C. 找 ds_id
DS_ID=$(curl -s "http://localhost:8080/api/v1/engine/data/datasources" \
  -H "Authorization: Bearer $TOKEN" | python3 -c "
import sys,json; d=json.load(sys.stdin)
items=d.get('data',{}).get('items') or d.get('data',{}).get('list') or []
print(items[0]['datasourceId'] if items else '')")
echo "ds_id=$DS_ID"

# D. T4: 立即触发采集（任务引擎接入验证）
curl -s -X POST "http://localhost:8080/api/v1/datanet/metadata/collect-async/$DS_ID" \
  -H "Authorization: Bearer $TOKEN" | python3 -m json.tool
# 验: body.code=0, data.taskId∈int, data.status=RUNNING

# E. T4: 查状态（TASK_ID 从上一步取）
curl -s "http://localhost:8080/api/v1/datanet/metadata/collect-status/${TASK_ID}" \
  -H "Authorization: Bearer $TOKEN" | python3 -m json.tool

# F. T9: 数据源 metadataConfig 字段回显（FE 地图校验）
curl -s "http://localhost:8080/api/v1/engine/data/datasources" \
  -H "Authorization: Bearer $TOKEN" | python3 -c "
import sys,json
items=json.load(sys.stdin)['data']['items']
first=items[0]
mc=first.get('metadataConfig')
print('metadataConfig:', mc if mc else '(null → default MANUAL)')
print('metadataStrategy:', first.get('metadataStrategy','MANUAL'))
"

# G. DB 直查（V105 列验证）
docker exec ecos-postgres psql -U postgres -d ecos \
  -c "SELECT datasource_id, metadata_config, last_collect_time FROM td_datasource LIMIT 3;"
# 验: metadata_config JSONB 非 NULL
```

## 验收结论

- **TS/BE 代码**: 全部落地，编译通过，commit hash 已记录 ✅
- **API 运行时**: 不能只靠编译 — 必须在恢复 Gateway 后用上面 curl 清单逐项验。
  当前没有 gateway 进程 → 本报告不标 DONE，待 curl A–G 回贴后定。

**Q: 谁知道 Gateway 原来的启动方式？** 请 PMO 或团队补启动步骤，再跑完 curl 清单。
