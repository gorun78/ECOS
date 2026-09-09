# 数据管道构建器与调试 — 试点文档（Wave 4 T2）

> **架构铁律**: 必须遵循 [.trae/rules/架构铁律.md](../rules/架构铁律.md) §2.4 安全接入 / §2.5 runtime 公共基础 / §4.8 前后端契约
> 编写: Wave 4 QA + 文档工程师 | 日期: 2026-09-09
> 配套验证: 本仓 Wave 4 后端 5 个测试类 (42 用例) + 前端 3 个 vitest 文件 (43 用例) + 冒烟 smoke (10 阶段)

---

## 1. 功能说明（CRUD / 含 JOIN·SINK·UDF 节点 / 断点调试四 Tab 数据流）

### 1.1 CRUD 四种动作（与既有 API 同契约）

| 操作 | 端点 | 说明 |
|------|------|------|
| 创建 | `POST /api/v1/pipeline/definitions` | 入参 `PipelineSaveDTO { name, description, status, nodes[], edges[] }`，返回 `PipelineVO` 含 `id`。节点带 `nodeId + type + config`；edges 推导 `depends_on` 持久化。 |
| 列表 | `GET /api/v1/pipeline/definitions` | 返回 `List<PipelineVO>` **摘要**（无 nodes），仅供卡片区渲染 |
| 详情 | `GET /api/v1/pipeline/definitions/{id}` | 返回 `PipelineVO` **全量**（含 nodes + config，敏感字段已脱敏）—— 架构铁律 §4.8.1「列表/详情配对」：画布渲染必须用此端点，禁止直接消费列表摘要 |
| 删除 | `DELETE /api/v1/pipeline/definitions/{id}` | **逻辑删除**：把定义 `status` 写为 `ARCHIVED`，物理保留以便审计恢复（§4.8.4）。已 `ARCHIVED` 的定义再 GET `/executions` 被当 404（"Pipeline 定义已被删除"） |
| 执行 | `POST /api/v1/pipeline/definitions/{id}/execute` | 走 runtime-task `ITaskManagementService.submitTask + executeTask`；执行前置 ABAC 裁决（`security-engine` 不可用 → 403 / DENY） |

### 1.2 九种节点类型（前后端枚举同源 §4.8.2）

| 类别 | 类型 | 执行器 | 必填字段 | 可用版本 |
|------|------|--------|---------|---------|
| Source | `SOURCE_JDBC` | `JdbcConnector` | `datasourceId, sql` | 全版本 |
| Source | `SOURCE_CSV` | `CsvConnector` | `filePath` | 全版本 |
| Source | `SOURCE_REST` | `RestApiConnector` | `url` | 全版本 |
| Source | `SOURCE_CDC` | 未实现 | — | **仅 ultimate（flagship）**，standard/enterprise 执行时抛 `BusinessException("SOURCE_CDC 仅旗舰版支持")` |
| Transform | `TRANSFORM_SQL` | 系统 JdbcTemplate（系统库内允许） | `sql` | 全版本 |
| Transform | `TRANSFORM_UDF` | UdfSandbox（内存白名单） | `udfId` | 全版本 |
| Join | `JOIN` | 内存合并（按 `joinKeys`） | `joinKeys`（建议） | 全版本 |
| Sink | `SINK` | `ConnectorFactory(JdbcConnector)` | `table, datasourceId` | 全版本 |
| Output | `OUTPUT_OBJECT` | 系统 JdbcTemplate（INSERT … SELECT） | `targetTable` | 全版本 |

> **权威来源**：后端 `PipelineNodeTypesCatalog.SUPPORTED` (9 类)，前端 `REQUIRED_FIELDS_BY_TYPE` (`pipelineValidation.ts`)，二者必须同集（PMO-46 规范）。

### 1.3 断点调试四 Tab 数据流

调试会话由 `PipelineDebugService` 内存态管理（`ConcurrentHashMap<String, Session>`，TTL 30min），**会话状态全部不落库**——执行只 INSERT 一条 `ecos_pipeline_execution` 用于 `listExecutions` 同源列表。

| Tab | 主导 | 数据源 | 关键字段 |
|-----|------|--------|---------|
| **Overview** | 总览会话 + 进度 | `GET /sessions/{id}` | `state`, `completedNodes/totalNodes`, `currentNodeId`, `variableSnapshot`（含 `rowsProcessed/index/breakpointCondition` 等断点命中刷新） |
| **Steps** | 节点执行步骤 | `steps[]`（按拓扑序，含 QUEUED 占位） | `nodeId, type, status ∈ {QUEUED, AWAITING, RUNNING, SUCCEEDED, FAILED, BROKEN}`, `rowsProcessed, elapsedMs, finishedAt, errorMsg` |
| **Data Preview** | 单节点输入/输出列 + 前 100 行 | `GET /sessions/{id}/preview/{nodeId}` | `columnsIn[]`, `columnsOut[]`, `sampleRows[<=100]`, `snapshot`（含 `rowCount/updatedRows/insertedRows` 等） |
| **Debug Console** | 日志 + 命中记录 | `GET /executions/{executionId}/logs`（回放）+ SSE 实时推送 | `NodeLog { seq, nodeId, level, message, atMs }`、`hitRecords[]`（最近 50 条断点命中 + 变量快照） |

### 1.4 节点执行错误处理铁律（架构 §3.2 / §5.1）

- 节点执行失败 → `PipelineExecutionService.executePipeline` 用 `try-catch` 包裹 `executeNode`；不向 Controller 抛，状态落 `FAILED` + `errorMessage`
- 单节点失败 → `repository.updateExecutionStatus(executionId, "FAILED", msg, 0L)`（`PipelineTopologyValidationTest.singleNodeFailureSetsFailedStatus` 覆盖）
- 必填字段缺失（如 SOURCE_JDBC 缺 `sql`）→ `executeNode` 入口抛 `ValidationException`，被 `executePipeline` 吞 → `FAILED` + message 含字段名
- 环检测（A↔B 互依赖）→ `topologicalSort` 抛 `BusinessException("Pipeline DAG 存在循环依赖")`，被 `executePipeline` 吞 → `FAILED`（与前端 `pipelineValidation` 同源环检测 §4.8.2）

### 1.5 执行历史（分页）

- `GET /api/v1/pipeline/definitions/{id}/executions?page=1&pageSize=20`
- 返回 `PipelineExecutionPageVO { page, pageSize, total, items[] }`，`items[i]` 含 `id / status / startedAt / completedAt / rowsProcessed / errorMessage`
- 页参数边界：`pageSize` 上限 200，`page < 1` 自动纠正为 1（`PipelineServiceImplTest` 覆盖）
- `ARCHIVED` 定义：`executions` 返回 404（"Pipeline 定义已被删除"）

---

## 2. 调试指南（断点 + 数据预览 + 变量快照）

### 2.1 创建调试会话（two ways）

**方式 A — 画布已保存**（`definitionId` 引用）：
```json
POST /api/v1/pipeline/debug/sessions
{
  "definitionId": "p-xxxxxxxx",
  "breakpoints": [
    { "nodeId": "n-src", "condition": "rows > 100" },
    { "nodeId": "n-tr" }
  ]
}
```

**方式 B — 画布未保存 ad-hoc**（`definition` 内联）：
```json
{
  "definition": {
    "name": "adhoc-pipeline",
    "nodes": [
      { "nodeId": "d-a", "type": "SOURCE_JDBC", "config": { "sql": "SELECT 1", "datasourceId": "ws" } },
      { "nodeId": "d-b", "type": "TRANSFORM_SQL", "config": { "sql": "SELECT 2" } }
    ]
  },
  "breakpoints": [{ "nodeId": "d-a" }]
}
```

返回 `PipelineDebugSessionVO`：`sessionId`、`state`（小写）、`totalNodes`、`breakpoints`、`steps[]`、`hitRecords[]`、`variableSnapshot`。

### 2.2 六类会话控制

| 端点 | 行为 | 注 |
|------|------|----|
| `GET /sessions/{id}` | 查当前状态 + 进度 | 不推进 |
| `POST /sessions/{id}/step` | 单步执行（1 节点） | 命中下一个断点 → `state=BROKEN` |
| `POST /sessions/{id}/continue` | 继续执行（直到下一断点或结束） | 命中 → `BROKEN`；无断点 → `COMPLETED` |
| `POST /sessions/{id}/stop` | 强制终止 | `state=STOPPED`（落 `CANCELLED`） |
| `POST /sessions/{id}/reset` | 重置回 `CREATED`（清空 queueIndex/steps/logs，保留断点配置） | 会话可复用 |
| `DELETE /sessions/{id}` | 删除内存会话 | 不落库 |

### 2.3 SSE 日志（节点级）

后端 `PipelineDebugLogController`：`GET /api/v1/pipeline/debug/executions/{executionId}/logs`
- 首次调用回放全部缓存（`ExecLog(lines)`，按 seq 升序）
- 组件端 `EventSource` 增量拉取（每行 `NodeLog { seq, nodeId, level, message, atMs }`）
- 内存保留策略：单 session 500 行；单 `ExecLog` 500 行；过期 60min 清理（`@Scheduled fixedDelay=60_000`）

### 2.4 断点命中变量快照（BROKEN 状态专属）

`markBreaking` 在命中时写：
```json
{
  "rowsProcessed": 42,
  "completedNodes": 1,
  "totalNodes": 3,
  "nextNodeId": "n-tr",
  "state": "BROKEN",
  "breakpointCondition": "rows > 100"   // 仅当断点有 condition
}
```
前端 `DebugPanel` 在 `state=BROKEN` 时把 `variableSnapshot` 写到「数据预览」Tab 顶部，并显示命中 `hitRecords` 时间线（最多 50 条）。

### 2.5 调试辅助：节点数据预览

`GET /sessions/{id}/preview/{nodeId}` 返回 `PipelineStepStatusVO { nodeId, type, status, columnsIn[], columnsOut[], sampleRows[<=100], snapshot, rowsInput, rowsOutput, errorMsg, finishedAt, elapsedMs }`：
- `columnsIn[]` 来自 `NodeResult.columnsIn`（Source 节点 = 源数据列；Transform 节点 = 上游 `columnsIn`）
- `columnsOut[]` 来自 `NodeResult.columnsOut`（Transform 节点 = SQL 输出列；Sink = target 表列）
- 未执行节点（QUEUED 占位）返回空 `[]`/`null`

---

## 3. 三版本兼容性（standard / enterprise / ultimate）

### 3.1 编译验证（已实测）

| Profile | DB | 模块聚合 | 编译耗时 | 结果 |
|---------|----|---------|----------|------|
| `standard` (default) | PG only | common, runtime, sysman, workspace, services/*, gateway | ~8 min (增量) | **BUILD SUCCESS** |
| `enterprise` | PG + Neo4j | 同上 (+ Neo4j driver `neo4j-java-driver`) + redis flag | ~8 min | **BUILD SUCCESS** |
| `ultimate` (flagship) | PG + Neo4j + Doris + Doris DB 调度 | 同上 (+ Doris driver/mysql-connector) + redis flag | ~10 min | **BUILD SUCCESS** |

> 三大 profile 共 13 个 Maven 模块（不增不减，架构铁律 §0.4「一套代码三套发布」），pipeline 全部代码无条件进入，按 feature flag 分支。

### 3.2 节点级可用性对照

| 节点 | standard | enterprise | ultimate | 执行器差异 |
|------|:--------:|:---------:|:-------:|-----------|
| SOURCE_JDBC / CSV / REST | ✅ | ✅ | ✅ | 全走 `runtime-access` `ConnectorFactory`（架构 §2.5） |
| TRANSFORM_SQL / OUTPUT_OBJECT | ✅ | ✅ | ✅ | 走系统 JdbcTemplate（PG only） |
| TRANSFORM_UDF / JOIN / SINK | ✅ | ✅ | ✅ | 内存合并 / ConnectorFactory |
| SOURCE_CDC | ❌ 守卫抛 `BusinessException("SOURCE_CDC 仅旗舰版支持")` | ❌ 同 | ✅（future: 基于 Kafka + runtime-task 多消费） | 仅 ultimate 暴露；NodeTypesService 用 `enabled=false` + `disabledReasonKey="dw.pipeline.node.cdcFlagshipOnly"` 让前端灰显 |

### 3.3 守卫机制（profile 切面，无 if-else 硬编码）

1. **编译期 Maven profile** → `ecos.edition` 属性（standard/enterprise/ultimate）
2. **运行期 Bean 委托**：`SOURCE_CDC` 节点在 `PipelineExecutionService.executeNode` 走 `case "SOURCE_CDC" -> throw new BusinessException(...)`，与 profile 无关（源码常驻）
3. **前端一致**：`NodePalette` 通过 `GET /pipeline/node-types` 拉目录（含 `enabled` 与 `disabledReasonKey`），standard/enterprise 节点面板灰显 SOURCE_CDC，最终执行侧面无依赖风险

### 3.4 前后端契约漂移记录（Wave 4 验收指出）

| 漂移点 | 后端 VO | 前端 shape | 状态 |
|--------|--------|-----------|------|
| 调试会话字段 | `PipelineDebugSessionVO.sessionId, state (lower), currentNodeId, variableSnapshot, hitRecords` | `DebugSession` 同 schema | ✅ 对齐 |
| 调试会话 fallback id | 后端无 `id` 字段 | 旧代码曾用 `session.id` | ❌ **已废弃**，前端必须 `session.sessionId` |
| 节点必填字段 | `PipelineNodeTypesService` 用 `meta.requiredFields` | `REQUIRED_FIELDS_BY_TYPE` (`pipelineValidation.ts`) | ✅ 两者已同集 (9 类节点 + 字段集) |
| 节点枚举 | `PipelineNodeTypesCatalog.SUPPORTED` (9 类) | `REQUIRED_FIELDS_BY_TYPE` 9 类 | ✅ 已锁（`PipelineNodeTypesCatalogTest.catalogSizeLocked`） |

> **Wave 5 任务建议**：把 `REQUIRED_FIELDS_BY_TYPE` 与 `PipelineNodeTypesCatalog.SUPPORTED` 都迁到一份静态 i18n（`src/i18n/pipeline.en.json` + `backend/resources/locales`），消除硬编码（前端「不硬编码中文」铁律 §4.3 + 后端「不硬编码节点类型」）；当前 9 类硬编码存在漂移风险。

---

## 4. 安全集成（架构铁律 §2.4 强制卡三连）

> 详细调用格式：[`docs/7-integration/02-security/05-安全能力操作手册.md`](../../../docs/7-integration/02-security/05-安全能力操作手册.md)

| 卡项 | 触发点 | 实现 | 测试覆盖 |
|------|--------|------|---------|
| ⑤ 写操作异步审计 | `createDefinition / updateDefinition / deleteDefinition / executeDefinition` | `security.auditWrite(action, resource, userId)` 走 `POST /api/security/audit/log`；**不阻塞**（try-catch swallow + WARN 级日志） | `PipelineServiceImplTest.updateDefinitionSuccessAudited` + `PipelineSecurityIntegrationTest.auditWriteFailureDoesNotBlock` |
| ② 脱敏 | 节点 `config` 序列化进 VO 前 | `security.parseAndMaskConfig(configJson)` 把 `password/token/secret/apikey/api_key/authorization/credential` 键值替换为 `******`（递归 map） | `PipelineSecurityIntegrationTest.maskNodeConfigMasksAll7SensitiveKeys` + `parseAndMaskConfigParsesJsonAndMasks` |
| ⑥ ABAC 默认 DENY | `executeDefinition` 前置 | `security.evaluateExecute(id)` → 走 `POST /api/v1/security/policy-engine/evaluate`；security 不可用 → `AllowedResult(false, "SECURITY_ENGINE_UNAVAILABLE")` → 抛 `BusinessException(403, ...)` | `PipelineSecurityIntegrationTest.abacDenyWhenSecurityEngineUnavailable` + `PipelineServiceImplTest.abacDenyBlocksExecution` |

> 本波次补记：`PipelineSecurityIntegrationTest` 之前对「ABAC 拒绝以 403 文案透出」的断言错误（`message.contains("403")` 不命中）—— 实际用 `BusinessException.errorCode == 403`，文案为 `"Pipeline 执行被 ABAC 策略拒绝: <reason>"`。已修正测试断言走 `getErrorCode()`。

---

## 5. 冒烟脚本（`ecos-tests/data-workbench-pipeline-smoke.mjs`）

跑法：
```bash
# 1. 起基础设施 (PG 5432 / Neo4j 7474+7687 / MinIO 9000 / OPA 8181)
cd D:\workspace\javaprojects\ECOS\ecos-docker
docker compose up -d
# 2. 起 gateway (profiles=enterprise)
powershell -NoProfile -ExecutionPolicy Bypass -File D:\workspace\javaprojects\ECOS\_win_tasks\start-gateway.ps1
# 3. 起前端 dev
cd D:\workspace\javaprojects\ECOS\ecos_frontend
set DISABLE_HMR=true
npm run dev
# 4. 跑 smoke
cd D:\workspace\javaprojects\ECOS\ecos-tests
node data-workbench-pipeline-smoke.mjs
```

覆盖 10 阶段（详见脚本头注释 A~L 共 12 步，编号重排）：
- A. 登录 → token
- B. `node-types` → 9 类
- C. 创建 3 节点管道（CSV + SQL + OUTPUT_OBJECT）
- D. 拉详情（画布用）→ 节点 ≥3 + 配置回显
- E. execute + E2 轮询 `task status` 收敛
- F. `list executions` → items[]
- G. DELETE → 逻辑归档
- H. 已归档定义 `executions` → 404
- I. 调试会话 `POST /sessions` → `sessionId`
- J~L. Playwright 浏览器冒烟（列表/新建/画布 + console/network 无 error）

预期：所有 10 阶段 ✓，`Total: 12 | Pass: 12 | Fail: 0`。

---

## 6. 自检清单（Wave 4 T2 完成时勾选）

- [x] CRUD 四种动作语义已说明（含逻辑删除 → ARCHIVED 404）
- [x] 9 类节点 + 必填字段表与后端 `PipelineNodeTypesCatalog` 同集
- [x] 断点调试四 Tab（Overview/Steps/Data Preview/Debug Console）数据源 + 关键字段
- [x] 三 profile 编译验证（standard/enterprise/ultimate 全 SUCCESS）
- [x] SOURCE_CDC 守卫（仅 ultimate 暴露）
- [x] 安全卡三连（审计/脱敏/ABAC 默认 DENY）— 含测试类 level 修复
- [x] 冒烟脚本写入 `ecos-tests/`
- [x] 前后端契约漂移记录（Wave 5 待补 i18n）

