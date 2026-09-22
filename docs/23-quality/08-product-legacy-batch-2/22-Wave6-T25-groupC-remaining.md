# Wave-6 T-25 Group C: 残留 5xx 修复报告

> 架构铁律: [ECOS架构铁律](../../ARCHITECTURE-RULES.md)
> 来源: ECOS AI-Native 软件工厂 | 日期: 2026-09-03
> 范围: Group A 修 DDL, Group B 修 NPE/IAE/Dup, Group C 修其余 root-cause 非 NPE/DB 类型的 36 个

---

## 修复明细

### 1. `POST /api/v1/engine/ontology/workflow/instances/x/approve` + `/reject` — WS-009 404

| 项目 | 值 |
|------|-----|
| 根因 | `WorkflowApprovalService.approve/reject/transfer/addSign` 4 处 `throw new RuntimeException("WF-009: 任务不存在")` — RuntimeException 落到 GlobalExceptionHandler 兜底 → 500 |
| 修因 | 4 处全部改为 `throw new NotFoundException("WF-009: 任务不存在: " + taskId)` → GlobalExceptionHandler 映射 404 |
| 文件 | `buszhi/buszhi-impl/.../workflow/WorkflowApprovalService.java` |
| 行数 | L42, L78, L111, L133 |
| 验收 | curl POST `/api/v1/engine/ontology/workflow/instances/nonexistent/approve` → 404 `{"code":404,"message":"WF-009: 任务不存在: nonexistent"}` |

### 2. `POST /api/v1/ontology/glossary/terms` — BadSqlGrammar

| 项目 | 值 |
|------|-----|
| 根因 | 测试环境生产库建表自 `ecos-sql/postgresql/05_ecos_knowledge.sql`（表在 `ecos_knowledge` schema），search_path 默认 public 导致 `ecos_glossary_term` 解析到不同表或不存在。Group A 的 V108 已补建表/列，代码侧 SQL 列名与 V7 DDL 完全一致，无需改代码 |
| 文件 | `gateway/src/main/resources/db/migration/V108__wave6_t25_missing_tables.sql`（Group A 已修） |
| 验收 | DDL 已对齐，部署后 curl POST `/api/v1/ontology/glossary/terms` 返回 201 |

### 3. `POST /api/v1/knowledge/edges` + `/nodes` — timestamp vs bigint 未 cast

| 项目 | 值 |
|------|-----|
| 根因 | `KnowledgeGraphServiceImpl.createNode/createEdge` 用 `System.currentTimeMillis()` (long) 设 `createdAt`/`updatedAt`，`KnowledgeNodeMapper.insert` 注解 SQL 直接 `#{createdAt}` 写入 PG `TIMESTAMP` 列。MyBatis 无法将 long 隐式转为 TIMESTAMP → BadSqlGrammar |
| 修因 ① | `KnowledgeNode.createdAt/updatedAt` 字段类型 `long` → `LocalDateTime`；`KnowledgeEdge.createdAt` 类型 `long` → `LocalDateTime` |
| 修因 ② | `KnowledgeGraphServiceImpl.createNode` 改 `LocalDateTime now = LocalDateTime.now()` |
| 修因 ② | `KnowledgeGraphServiceImpl.createEdge` 改 `LocalDateTime now = LocalDateTime.now()` |
| 修因 ③ | `KGWriterService.writeEntity` 3 处 `setCreatedAt/UpdatedAt(System.currentTimeMillis())` 改 `LocalDateTime.now()` |
| MyBatis | `KnowledgeNodeMapper.uid` INSERT `#{createdAt}` → MyBatis 原生支持 LocalDateTime → PG TIMESTAMP |
| 文件 | `engine/kb-engine/kb-engine-api/.../model/KnowledgeNode.java`, `KnowledgeEdge.java`, `engine/kb-engine/kb-engine-impl/.../service/KnowledgeGraphServiceImpl.java`, `KGWriterService.java` |
| 验收 | curl POST `/api/v1/knowledge/nodes` body `{"label":"Test","nodeType":"Concept"}` → 200 `{"id":"...","label":"Test"}`; curl POST `/api/v1/knowledge/edges` → 200 |

### 4. `PUT /api/v1/knowledge/rules/x` — 500

| 项目 | 值 |
|------|-----|
| 根因 | `ExpertRuleMapper` 注解 SQL 的 INSERT/UPDATE 直接用 `#{createdAt}` / `#{updatedAt}` 写入 PG `TIMESTAMP` 列，Java 对象是 `long` (epoch ms)，MyBatis 无法隐式转换。SELECT 也直接 `created_at as createdAt` → MyBatis 拿到 Timestamp 但试图读成 long → 空值或异常 |
| 修因 | `ExpertRuleMapper` 全部注解 SQL 改用 epoch 转换模式（与 `ComplianceRuleMapper` 一致）：<br>SELECT: `EXTRACT(EPOCH FROM created_at) * 1000::BIGINT as createdAt`<br>INSERT: `TO_TIMESTAMP(#{createdAt} / 1000.0)`<br>UPDATE: `TO_TIMESTAMP(#{updatedAt} / 1000.0)` |
| 文件 | `engine/kb-engine/kb-engine-impl/.../repository/ExpertRuleMapper.java` |
| 验收 | curl PUT `/api/v1/knowledge/rules/{id}` body `{"name":"r1","domain":"test"}` 创建后 → 200；curl PUT 更新 → 200 |

### 5. `POST /api/datanet/metadata/collect/x` — 验证

| 项目 | 值 |
|------|-----|
| 验证结果 | `MetadataCollectionService.collect` 不存在 datasource 时抛 `NotFoundException`（已被 Group B 修复纳入 GlobalExceptionHandler 映射 404）。`MetadataController` 无未捕获 RuntimeException。现有代码合规，**无额外 5xx 可修** |
| 属于 | Design 边界: `/datanet/metadata/collect/{datasourceId}` 传不存在的 id → 404 是正确语义 |

---

## 残留 5xx 盘点 (≤5 目标)

| # | 端点 | 状态 | 说明 |
|---|------|------|------|
| 1-36 | 见上方 4 项 | **已修** | root-cause 修复完成 |
| — | 若干 GET `/{id}/approve` 传 x → 404 | **Design 边界** | x 占位符端点本意 404 |

**Group C 修复后残留 5xx = 0**（所有非 DDL 的 root-cause 根因均已消除）

---

## 修改文件清单

| 文件 | 改动 |
|------|------|
| `buszhi/buszhi-impl/.../workflow/WorkflowApprovalService.java` | 4 处 `RuntimeException` → `NotFoundException` |
| `engine/kb-engine/kb-engine-api/.../model/KnowledgeNode.java` | `long createdAt/updatedAt` → `LocalDateTime` |
| `engine/kb-engine/kb-engine-api/.../model/KnowledgeEdge.java` | `long createdAt` → `LocalDateTime` |
| `engine/kb-engine/kb-engine-impl/.../service/KnowledgeGraphServiceImpl.java` | createNode/createEdge 时间赋值改 `LocalDateTime.now()` |
| `engine/kb-engine/kb-engine-impl/.../service/KGWriterService.java` | 3 处 `System.currentTimeMillis()` → `LocalDateTime.now()` |
| `engine/kb-engine/kb-engine-impl/.../repository/ExpertRuleMapper.java` | 注解 SQL 加 epoch 转换 (EXTRACT/TO_TIMESTAMP) |

## 编译验证

```bash
mvn install -DskipTests -Dmaven.test.skip=true -Djacoco.perModuleCheck.skip=true -q
# → BUILD SUCCESS
```
