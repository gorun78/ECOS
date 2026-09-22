# PMO-21: data-engine后端补齐 — 血缘+质量调度+Schema检测+大表保护

> **架构铁律**: 必须遵循 [ECOS架构铁律](../../ARCHITECTURE-RULES.md)
> 来源: 肖国荣 | 日期: 2026-08-07 | 优先级: 🔴 P0
> **引擎**: data-engine:18082 | **依赖**: PostgreSQL, runtime-task
> **工期**: 4天 | **协同**: ECOS-BE

---

## §背景

data-engine有16个Controller，CRUD完备。但三条执行链路为stub/空壳：
1. **血缘**：DataLineageController存在，但Service返回空（前端的DataLineage.tsx走mock数据）
2. **DQ调度**：QualityController的evaluate可手动触发，但未注册runtime-task定时执行
3. **Schema检测**：无

---

## §禁止清单

1. ❌ 不操作其他引擎的表（铁律3.3）
2. ❌ 管道不执行>30min的同步任务
3. ❌ 血缘不追踪Neo4j内关系（那是kb-engine的事）
4. ❌ 不跨Phase预创建文件（铁律5.1 #1）
5. ❌ 不自建`ScheduledExecutorService` — 走runtime-task全局调度（铁律2.3）

---

## §Task

### T1: JSqlParser字段级血缘解析（2天）

**新建文件**: `engine/data-engine/data-engine-impl/src/main/java/com/chinacreator/gzcm/engine/data/service/SqlLineageParser.java`

**重写文件**: `engine/data-engine/data-engine-impl/src/main/java/com/chinacreator/gzcm/engine/data/service/DataLineageServiceImpl.java`

**现状**: 血缘查询返回空列表

**目标**: JSqlParser解析SQL→字段级血缘DAG

**实现要求**：
1. 引入`com.github.jsqlparser:jsqlparser`（版本与Spring Boot 3.2兼容，推荐4.9）
2. 解析INSERT/SELECT/JOIN/CTE/子查询，提取字段级映射
3. 输出格式：
```json
{
  "nodes": [{"id":"sales.amount","type":"field","table":"sales"},...],
  "edges": [{"source":"orders.total","target":"sales.amount","transform":"SUM","sql":"INSERT INTO sales SELECT SUM(total) FROM orders"}]
}
```
4. 国产DB(SQLServer/达梦/金仓)方言降级处理：解析失败→回退正则匹配表级血缘
5. 解析深度上限5层（通过`DataEngineConfig`可配）

**curl验收**:
```bash
TOKEN=$(curl -s -X POST http://localhost:8080/api/v1/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"admin","password":"admin123"}' \
  | grep -o '"accessToken":"[^"]*"' | cut -d'"' -f4)

# 血缘查询
curl -s -H "Authorization: Bearer $TOKEN" \
  -X GET "http://localhost:8080/api/v1/engine/data/lineage?datasourceId=1&tableName=sales" \
  | python3 -c "import sys,json; d=json.load(sys.stdin); \
     nodes=d.get('data',{}).get('nodes',[]); edges=d.get('data',{}).get('edges',[]); \
     print(f'PASS: {len(nodes)}节点/{len(edges)}边' if len(nodes)>0 else 'FAIL: 空结果')"
# 期望: PASS: ≥1节点
```

---

### T2: DQ定时调度注册（1.5天）

**修改文件**: `engine/data-engine/data-engine-impl/src/main/java/com/chinacreator/gzcm/engine/data/service/QualityServiceImpl.java`

**新建文件**: `engine/data-engine/data-engine-impl/src/main/java/com/chinacreator/gzcm/engine/data/scheduler/DqScheduledTask.java`

**目标**: 所有启用的DQ规则每天凌晨8:00全量执行

**实现要求**：
1. `DqScheduledTask`实现`ITaskStatusCallback`接口，注册到runtime-task的`TaskSchedulerService`
2. 每天8:00触发→遍历所有`status=ENABLED`的规则→逐条evaluate→结果写`dq_evaluation_results`表
3. Cron表达式：`0 0 8 * * ?`
4. 执行超时120s（10表×12s/表），超时→标记FAILED+继续下一条
5. 执行完成后更新`sys_config`中的`dq_last_run_time`

**curl验收**:
```bash
# 手动触发全量巡检（不走cron，直接调evaluate-all端点）
curl -s -H "Authorization: Bearer $TOKEN" \
  -X POST http://localhost:8080/api/v1/engine/data/quality/evaluate-all \
  | python3 -c "import sys,json; d=json.load(sys.stdin); print('PASS: 触发成功' if d.get('success') else 'FAIL')"

# 确认runtime-task注册
curl -s -H "Authorization: Bearer $TOKEN" \
  -X GET http://localhost:8080/api/v1/runtime/task/list?type=SCHEDULED \
  | python3 -c "import sys,json; d=json.load(sys.stdin); \
     tasks=d.get('data',{}).get('items',[]); \
     dq=[t for t in tasks if 'DQ' in t.get('name','')]; \
     print(f'PASS: DQ任务已注册' if dq else 'FAIL: 未注册')"
```

---

### T3: Schema变更检测（1.5天）

**新建文件**:
- `engine/data-engine/data-engine-impl/src/main/java/com/chinacreator/gzcm/engine/data/scheduler/SchemaChangeDetector.java`
- `engine/data-engine/data-engine-impl/src/main/java/com/chinacreator/gzcm/engine/data/controller/SchemaChangeController.java`

**目标**: 定时对比`information_schema`，检测到变更→写通知表+API可查

**实现要求**：
1. **检测逻辑**：每小时对比`information_schema.columns`快照，识别新增列/删除列/类型变更
2. **快照存储**：`schema_snapshots`表(id, datasource_id, table_name, column_hash, snapshot_at)
3. **变更记录**：`schema_changes`表(id, datasource_id, table_name, change_type[NEW_COLUMN/DROP_COLUMN/TYPE_CHANGE], detail_json, detected_at, acknowledged[bool])
4. **API端点**：
   - `GET /api/v1/engine/data/schema/changes?acknowledged=false` — 未确认变更列表
   - `POST /api/v1/engine/data/schema/changes/{id}/acknowledge` — 确认变更
5. 注册到runtime-task，每小时执行

**Controller三滤波器**（铁律1.2）:
- `/api/v1/engine/data/schema/*` → SecurityConfig permitAll + ClearanceInterceptor豁免

**curl验收**:
```bash
curl -s -H "Authorization: Bearer $TOKEN" \
  -X GET "http://localhost:8080/api/v1/engine/data/schema/changes" \
  | python3 -c "import sys,json; d=json.load(sys.stdin); print('PASS' if d.get('success')!=None else 'FAIL')"
# 期望: PASS (空列表也算成功)
```

---

### T4: 大表查询保护（1天）

**修改文件**: 
- `engine/data-engine/data-engine-impl/src/main/java/com/chinacreator/gzcm/engine/data/controller/QueryController.java`
- `engine/data-engine/data-engine-impl/src/main/java/com/chinacreator/gzcm/engine/data/service/QueryExecutionServiceImpl.java`

**目标**: 所有查询强制LIMIT+超时+行数上限

**实现要求**：
1. **强制LIMIT注入**：用户SQL不带LIMIT→自动追加`LIMIT 10000`
2. **结果行数上限**：>10000行自动截断，Response加`truncated: true`标记
3. **超时保护**：Controller层30s超时→超时返回已查到的部分结果+`timeout: true`
4. **配置项**（`DataEngineConfig`）：
   - `query.timeout.seconds`: 30
   - `query.max.rows`: 10000

**curl验收**:
```bash
# 大表查询（无LIMIT → 自动加LIMIT 10000）
curl -s -H "Authorization: Bearer $TOKEN" \
  -X POST http://localhost:8080/api/v1/engine/data/query \
  -H 'Content-Type: application/json' \
  -d '{"sql":"SELECT * FROM large_table"}' \
  | python3 -c "import sys,json; d=json.load(sys.stdin); \
     rows=len(d.get('data',{}).get('rows',[])); \
     print(f'PASS: {rows}行(≤10000)' if rows<=10000 else 'FAIL: 超限')"
```

---

## §验证门禁

```bash
# V1: 文件生存
find engine/data-engine -name "*.java" -newer /home/guorongxiao/ECOS/docs/3-data/PMO-21-data-engine后端补齐.md -type f | sort

# V2: 关键类/注解
grep -rn "SqlLineageParser\|DqScheduledTask\|SchemaChangeDetector\|ITaskStatusCallback" engine/data-engine/ --include="*.java"

# V3: 编译
env -i HOME=/home/guorongxiao PATH=/usr/bin:/usr/local/bin:/home/guorongxiao/.local/bin:/home/guorongxiao/.local/apache-maven-3.9.11/bin JAVA_HOME=/home/guorongxiao/.local/jdk/jdk-17.0.19+10 bash -c 'cd /home/guorongxiao/ECOS/ecos_backend && mvn install -DskipTests -Dmaven.test.skip=true -q 2>&1 | tail -5'

# V4: Gateway curl验证 (T1-T4)
```

## §工时

| Task | 工期 | 依赖 |
|:--|:--:|------|
| T1 JSqlParser血缘 | 2天 | — |
| T2 DQ定时调度 | 1.5天 | — |
| T3 Schema检测 | 1.5天 | — |
| T4 大表保护 | 1天 | — |
