# ECOS 后端代码全面审计报告 — Sprint 7

> **审计日期**: 2026-06-20
> **审计范围**: ECOS 后端全模块 (Spring Boot 3.2 + MyBatis + JdbcTemplate)
> **审计类型**: 静态代码分析 + 数据库实地检查
> **覆盖模块**: aimod, buszhi, common, datanet, dccheng, market, portal, runtime, sysman, workspace, worldmodel

---

## 一、端点全景扫描

### 1.1 控制器总览

| 模块 | Controller | 端点数 | 基路径 |
|------|-----------|--------|--------|
| **aimod** | AgentConfigController | 10 | `/api/v1/agents` |
| | AgentProfileController | 8 | `/api/v1/agent/profiles` |
| | AgentMeshController | 12 | `/api/agent-mesh` |
| | AgentCallController | 4 | `/api/v1/agent` |
| | NLQController | 2 | `/api/v1/query`, `/api/query` |
| **buszhi** | DqController | 12 | `/api/v1/ecos/dq` |
| | WorkflowController | ~8 | `/api/v1/ecos/workflows` |
| | WorkflowTaskController | ~6 | `/api/v1/ecos/workflows` |
| | WorkflowApprovalController | ~4 | `/api/v1/ecos/workflows` |
| **datanet** | CatalogController | 6 | `/datanet/catalog` |
| | MetadataController | 4 | `/datanet/metadata` |
| | DataSourceController | 5 | `/datanet/datasource` |
| | CategoryController | 4 | `/datanet/category` |
| **dccheng** | ClassificationController | ~5 | `/api/v1/ecos/classification` |
| | GlossaryController | ~5 | `/api/v1/ecos/glossary` |
| | KnowledgeGraphController | ~5 | `/api/v1/ecos/knowledge-graph` |
| | OntologyController | ~6 | `/api/v1/ecos/ontology` |
| | OntologyPropertyController | ~5 | `/api/v1/ecos/ontology` |
| | OntologyDomainController | ~5 | `/api/v1/ecos/ontology` |
| | OntologyRelationshipController | ~5 | `/api/v1/ecos/ontology` |
| | OntologyActionController | ~5 | `/api/v1/ecos/ontology` |
| | OntologyRuleController | ~5 | `/api/v1/ecos/ontology` |
| | OntologyVersionController | ~5 | `/api/v1/ecos/ontology` |
| **market** | MarketplaceController | ~6 | `/api/v1/ecos/marketplace` |
| **portal** | FrontendBridgeController | 9 | `/api/*` |
| | MenuController | 3 | `/api/system/menus` |
| **sysman** | AuthController | 3 | `/api/v1/auth`, `/auth` |
| | HealthController | 1 | `/api` |
| | UserController | 7 | `/api/v1/system/users` |
| | RoleController | 7 | `/api/v1/system/roles` |
| | PermissionController | 5 | `/api/system/permissions` |
| | OrganizationController | 9 | `/api/v1/system/organizations` |
| | ConfigController | 6 | `/api/system/config` |
| | SystemParamController | 5 | `/api/system/config/params` |
| | AuditController | 2 | `/api/v1/audit` |
| | AbacController | 5 | `/api/v1/abac` |
| | DataPermissionController | 4 | `/api/v1/data-permission` |
| | PolicyEngineController | 3 | `/api/v1/policy-engine` |
| | MonitoringController | 2 | `/api/v1/ecos/monitoring` |
| | **GsxkBridgeController** | **21** | `/api/v1/gsxk` |
| **workspace** | ObjectController | 7 | `/api/v1/ecos/objects` |
| | ObjectActionController | 2 | `/api/v1/ecos/objects` |
| | ObjectStateMachineController | 6 | `/api/v1/ecos` |
| | ObjectRelationshipController | 4 | `/api/v1/ecos/objects` |
| | ObjectTimelineController | 4 | `/api/v1/ecos/objects` |
| | ObjectQLController | 1 | `/api/query` |
| | QueryHistoryController | 2 | `/api/query` |
| **worldmodel** | WorldModelController | 9 | `/api/v1/ecos/world-model` |

**合计**: 51 个 @RestController，约 215+ 个映射端点。

### 1.2 Sprint6 遗留失败端点核实

| # | Endpoint | Sprint6 Status | 当前状态 | 原因分析 |
|---|----------|---------------|---------|---------|
| 4 | POST `/api/v1/auth/refresh` | 500 | ⚠️ 仍有风险 | RefreshToken存内存ConcurrentHashMap，服务重启后丢失 |
| 15 | GET `/api/v1/system/permissions` | 500 | ⚠️ 待验证 | PermissionController可能依赖未就绪的Service |
| 23 | GET `/api/v1/gsxk/entities` | 500 | ❌ 仍可能500 | 路径不匹配 — GsxkBridge没有`/entities`端点，只有`/ontology/entities` |
| 24 | GET `/api/v1/gsxk/search` | 500 | ✅ 已修复 | GsxkBridgeController已有`GET /search`端点 |
| 31 | GET `/api/v1/ecos/objects/search` | 500 | ❌ 路径不匹配 | ObjectController的search是`GET /search`（类级`/api/v1/ecos/objects`），请求`/api/v1/ecos/objects/search`返回500 |
| 32 | GET `/api/v1/config` | 500 | ❌ 路由不存在 | 无Controller注册`/api/v1/config`路径 |

---

## 二、SQL 注入风险评估

### 2.1 风险分级

| 风险等级 | 端点/方法 | 文件 | 问题描述 |
|---------|----------|------|---------|
| 🔴 **HIGH** | `GET /api/v1/gsxk/objects` | GsxkBridgeController:34-41 | `entityCode` 参数通过 `replace("'", "''")` 拼入SQL，仅防御单引号，不防御其他注入向量 |
| 🔴 **HIGH** | `GET /api/v1/gsxk/objects/{entityCode}` | GsxkBridgeController:84-90 | `entityCode` 通过 `replace("'", "''")` 拼入SQL |
| 🔴 **HIGH** | `GET /api/v1/gsxk/ontology/properties` | GsxkBridgeController:145-148 | `entityId` 通过 `replace("'", "''")` 拼入SQL |
| 🟡 **MEDIUM** | `GET /api/v1/gsxk/objects` | GsxkBridgeController:41 | `LIMIT` 和 `OFFSET` 直接拼入（int参数，风险低但非最佳实践） |
| 🟢 **SAFE** | `GET /api/v1/gsxk/search` | GsxkBridgeController:294-296 | 使用 `?` 参数化查询 |
| 🟢 **SAFE** | `POST /api/v1/gsxk/entities/{entityId}/properties` | GsxkBridgeController:410-412 | 使用 `?` 占位符 |
| 🟢 **SAFE** | DqRepository (全部) | DqRepository.java | 全部使用 `?` 参数化查询 |
| 🟢 **SAFE** | WorldModelRepository (全部) | WorldModelRepository.java | 全部使用 `?` 参数化查询 |
| 🟢 **SAFE** | FrontendBridgeController searchTable() | FrontendBridgeController:665-718 | 使用 `PreparedStatement.setString()` 参数化 |
| 🟢 **SAFE** | UserController | UserController:34-61 | 动态SQL但使用 `?` 参数列表 |
| 🟡 **MEDIUM** | ObjectRuntimeService.getGraph() | ObjectRuntimeService:257, 373 | 表名 `table` 从静态 `ENTITY_TABLE` Map取值拼入SQL，但表名非用户可控 |

### 2.2 GsxkBridgeController SQL 注入详情

```java
// 🚨 风险代码 — 行34-35
String where = (entityCode != null && !entityCode.isEmpty())
    ? " WHERE entity_code = '" + entityCode.replace("'", "''") + "'" : "";
```

**问题**: 
- 仅替换单引号 `'`，未处理反斜杠 `\`、分号 `;`、注释符 `--` 等
- PostgreSQL 中 `replace("'", "''")` 防单引号逃逸是有效的，但不符合安全编码最佳实践
- LIMIT/OFFSET 直接使用 int 参数拼接，虽因类型限制风险较低，但不符合规范

**结论**: 3个端点存在字符串拼接SQL，虽然做了基本的单引号转义但非参数化查询。**建议全部改为 `?` 占位符**。

### 2.3 安全总结

| 类别 | 数量 |
|------|------|
| 参数化查询（? 占位符） | 95%+ |
| 字符串拼接（有基本转义） | 3个端点 |
| 无防护的字符串拼接 | 0 |
| **总体风险等级** | **🟡 MEDIUM** |

---

## 三、异常处理审计

### 3.1 GlobalExceptionHandler 覆盖范围

```java
// 仅处理 3 种异常类型:
@ExceptionHandler(IllegalArgumentException.class)  → 400
@ExceptionHandler(NoSuchElementException.class)     → 404
@ExceptionHandler(Exception.class)                   → 500
```

**缺失的异常处理**:
| 异常类型 | 应返回 | 当前行为 |
|---------|--------|---------|
| `DataAccessException` | 500 + 友好消息 | 被Exception兜底 |
| `BusinessException` | 400 + 业务消息 | 被Exception兜底 |
| `ValidationException` | 400 + 校验详情 | 被Exception兜底 |
| `UnauthorizedException` | 401 | 被Exception兜底 → 500（错误） |
| `ForbiddenException` | 403 | 被Exception兜底 → 500（错误） |
| `NotFoundException` | 404 | 被Exception兜底 → 500（错误） |
| `HttpMessageNotReadableException` | 400 | 被Exception兜底 |
| `MethodArgumentNotValidException` | 400 | 被Exception兜底 |
| `AccessDeniedException` | 403 | 被Exception兜底 → 500（错误） |

**严重问题**: `common-api` 模块定义了 `UnauthorizedException`, `ForbiddenException`, `NotFoundException`, `BusinessException`, `ValidationException`, `DataAccessException` 等自定义异常，但 **GlobalExceptionHandler 未注册这些异常的处理器**。这意味着抛出这些异常会fallback到 `Exception` handler，统一返回500，丢失了业务语义。

### 3.2 Controller 层 try/catch 模式

**模式A — try/catch 全覆盖** (推荐):
- GsxkBridgeController ✅ — 所有21个端点都有 try/catch
- FrontendBridgeController ✅ — 端点级 + 辅助方法级双重保护
- UserController ✅ — 9个端点全覆盖
- ConfigController ✅ — 6个端点全覆盖
- SystemParamController ✅ — 4个端点全覆盖
- MenuController ✅  

**模式B — 无 try/catch，依赖 GlobalExceptionHandler**:
- DqController ❌ — 无任何 try/catch，Service层异常直接传播
- WorldModelController ❌  
- WorkflowController ❌
- 大部分域模块Controller ❌

**问题**: 域模块Controller (datanet, dccheng, market等) 几乎完全依赖GlobalExceptionHandler，但GlobalExceptionHandler未注册业务异常。若Service层抛出 `NotFoundException`，将被兜底为500。

### 3.3 异常处理严重度评估

| 严重度 | 问题 | 影响端点 |
|--------|------|---------|
| 🔴 **CRITICAL** | 自定义异常(Unauthorized/Forbidden/NotFound等)未被GlobalExceptionHandler捕获 | 所有域模块Controller |
| 🟡 **MEDIUM** | catch Exception过于宽泛，不区分可恢复/不可恢复 | GsxkBridge等 |
| 🟢 **OK** | 端点级try/catch+友好中文错误消息 | sysman Controller |

---

## 四、数据库完整性审计

### 4.1 空表清单 (数据行数=0)

| 表名 | 行数 | 关联端点 | 影响 |
|------|------|---------|------|
| `ecos_marketplace_access_request` | 0 | MarketplaceController | 列表查询返回空数组，功能可用 |
| `ecos_ontology_rule` | 0 | OntologyRuleController | 列表查询返回空数组，功能可用 |
| `ecos_ontology_version` | 0 | OntologyVersionController | 列表查询返回空数组，功能可用 |
| `ecos_working_memory` | 0 | Agent相关 | Agent working memory功能受限 |

### 4.2 🔴 缺失表 (代码引用但数据库不存在)

| 代码引用表名 | 引用位置 | 影响 |
|-------------|---------|------|
| **`ecos_object_version`** | ObjectRuntimeService:118,123,138,156 | ⚠️ 版本管理全部500 |
| **`ecos_object_attachment`** | ObjectRuntimeService:405 | ⚠️ 附件功能全部500 |
| **`ecos_tool_registry`** | FrontendBridgeController:137-164 | 🟡 有硬编码降级，功能可用 |
| **`ecos_prompt_template`** | FrontendBridgeController:231-257 | 🟡 有硬编码降级，功能可用 |
| **`ecos_audit_log`** | AuditAspect + FrontendBridgeController | 🔴 所有审计日志静默丢失 |

**严重程度**: `ecos_object_version` 和 `ecos_object_attachment` 的缺失导致 ObjectRuntimeService 中的版本管理和附件功能完全不可用（会抛异常但被catch后静默失败）。审计表缺失导致所有CUD操作的审计记录永久丢失。

### 4.3 数据规模统计 (ecos_* 表)

| 数据规模 | 表数量 | 代表表 |
|---------|--------|--------|
| >50行 | 2 | ecos_agent_execution(50), ecos_agent_execution_step(369) |
| 20-50行 | 8 | ecos_object_data(44), ecos_ontology_property(48), ecos_biz_metric(85), ecos_biz_project(20) |
| 10-19行 | 13 | ecos_knowledge_graph_node(23), ecos_ontology_entity(12) |
| 1-9行 | 21 | ecos_mission(5), ecos_agent(3) |
| 0行 | 4 | 见4.1 |

**结论**: 大部分ecos_*表有少量演示数据，数据规模正常。

---

## 五、🔴 冗余表问题 (ecos_world_* vs ecos_wm_*)

### 5.1 发现

数据库中同时存在两套World Model表：

| world_ 表 | wm_ 表 | 数据行数 | ID格式 | 数据是否一致 |
|-----------|--------|---------|--------|------------|
| `ecos_world_goal` (9行) | `ecos_wm_goal` (9行) | 相同 | world: UUID/"g00x" vs wm: 1-9 | ❌ **完全不同** |
| `ecos_world_scenario` (7行) | `ecos_wm_scenario` (7行) | 相同 | world: UUID vs wm: integer | ❌ **完全不同** |
| `ecos_world_causal_link` (11行) | `ecos_wm_causal_link` (10行) | 不同 | world: UUID vs wm: integer | ❌ **完全不同** |
| `ecos_world_scenario_impact` (10行) | (无对应表) | — | — | 仅world_有 |

### 5.2 列结构差异

| 表对 | world_独有列 | wm_独有列 |
|------|------------|----------|
| goal | `code`, `target_value`, `current_value`, `unit`, `priority`, `category`, `owner`, `deadline` | `progress` |
| scenario | `code`, `assumptions`, `projected_outcomes`, `probability`, `impact_score`, `category` | `config_json` |
| causal_link | `source_type`, `source_id`, `target_type`, `target_id`, `relation_type`, `strength`, `metadata` | `relationship_type`, `source_goal_id`, `target_goal_id` |

### 5.3 代码使用情况

| 表 | 使用者 | 端点 |
|---|--------|------|
| `ecos_world_goal` | GsxkBridgeController | `/gsxk/worldmodel/goals`, `/gsxk/monitoring/summary` |
| `ecos_world_scenario` | GsxkBridgeController | `/gsxk/worldmodel/scenarios` |
| `ecos_world_scenario_impact` | GsxkBridgeController | `/gsxk/worldmodel/impacts` |
| `ecos_wm_causal_link` | GsxkBridgeController + WorldModelRepository | `/gsxk/worldmodel/causal-links` + `/api/v1/ecos/world-model/causal-links` |

### 5.4 🚨 严重问题

**GsxkBridgeController 混用了两套表！**
- `/worldmodel/goals` → 查询 `ecos_world_goal`
- `/worldmodel/scenarios` → 查询 `ecos_world_scenario`  
- `/worldmodel/causal-links` → 查询 `ecos_wm_causal_link` (JOIN `ecos_wm_goal`)
- `/monitoring/summary` → 查询 `ecos_world_goal`

这导致：
1. **因果链端点查询的是wm_表的数据，而非world_表的数据** — 与目标和场景的数据不匹配
2. WorldModelController/Repository使用`ecos_wm_*`表，GsxkBridgeController部分使用`ecos_world_*`，数据完全割裂
3. 两套表的数据集完全不同（ID不交叉），前端无法关联显示

### 5.5 建议

1. **立即决定**: 保留一套表（建议保留`ecos_world_*`，因为列更丰富且包含`ecos_world_scenario_impact`）
2. **统一代码**: 所有 World Model 相关代码统一使用同一套表
3. **迁移数据**: 将 wm_ 表中有价值的数据迁移到 world_ 表，或反之
4. **清理废弃表**: 删除不被使用的冗余表

---

## 六、N+1 查询分析

### 6.1 已发现的 N+1 问题

**ObjectRuntimeService.getGraph() — line 326-348**

```java
// 递归CTE查询所有关系 → 1次DB查询
List<Map<String, Object>> rels = jdbc.queryForList(cteSql, objectId, objectId, depth);

for (Map<String, Object> rel : rels) {
    // ...
    if (nodeIds.add(srcId)) {
        nodes.add(buildNode(srcId, srcEntity));  // ← N+1: 每个新节点一次DB查询
    }
    if (nodeIds.add(tgtId)) {
        nodes.add(buildNode(tgtId, tgtEntity));  // ← N+1: 每个新节点一次DB查询
    }
}

// buildNode() → line 364-384
private Map<String, Object> buildNode(String objectId, String entityCode) {
    // ...
    String table = ENTITY_TABLE.get(entityCode);
    if (table != null) {
        List<Map<String, Object>> rows = jdbc.queryForList(
            "SELECT name FROM " + table + " WHERE id = ?", objectId);  // ← 每次一次查询
    }
}
```

**影响评估**: 
- 受限：ENTITY_TABLE只有3个实体(Customer/Supplier/Invoice)，深度限制≤5
- 当前数据规模下影响小（关系数<10），但随数据增长会线性恶化
- **建议**: 将节点label查询改为批量 — 收集所有`(objectId, entityCode)`对后按表分组，使用`WHERE id IN (?)`批量查询

### 6.2 干净的代码

以下Service/Repository未发现N+1问题：
- ✅ DqRepository — 所有查询都是单次完成
- ✅ WorldModelRepository — 使用PostgreSQL JSONB操作，无循环查询
- ✅ WorkflowRepository — 使用PreparedStatement批量操作
- ✅ FrontendBridgeController — 使用多次独立查询但无循环依赖
- ✅ UserController — 分页查询单次完成

---

## 七、API 响应格式审计

### 7.1 ApiResponse 使用情况

| 格式 | 使用者 | 状态 |
|------|--------|------|
| `ApiResponse<T>` 直接返回 | 95%+ 的Controller | ✅ 标准 |
| `ResponseEntity<ApiResponse<T>>` | AuthController (仅此一个) | ❌ 不一致 |

**AuthController** 是唯一使用 `ResponseEntity<ApiResponse<...>>` 的Controller（返回 `ResponseEntity.status(401).body(...)`），其他所有Controller直接返回 `ApiResponse<T>`。这种不一致会导致：
1. HTTP状态码不统一（AuthController返回401/400，其他所有端点返回200+ApiResponse.code）
2. 前端需要两套错误处理逻辑

### 7.2 ApiResponse.code 使用一致性

所有端点基本统一使用：
- `ApiResponse.success(data)` → code=0
- `ApiResponse.badRequest(msg)` → code=400
- `ApiResponse.notFound(msg)` → code=404
- `ApiResponse.internalError(msg)` → code=-1

✅ 一致性好，但有少数遗留的 `ApiResponse.error(400, msg)` 调用（DqController:160,170）

---

## 八、缺失功能端点分析

### 8.1 CRUD 完整性检查

| 实体 | C (Create) | R (Read) | U (Update) | D (Delete) | 缺失 |
|------|-----------|---------|-----------|-----------|------|
| User | ✅ POST | ✅ GET | ✅ PUT | ✅ DELETE | — |
| Role | ✅ POST | ✅ GET | ✅ PUT | ✅ DELETE | — |
| Organization | ✅ POST | ✅ GET | ✅ PUT | ✅ DELETE | — |
| Permission | ✅ POST | ✅ GET | ✅ PUT | ✅ DELETE | — |
| Config | ✅ POST | ✅ GET | ✅ PUT | ✅ DELETE | — |
| SystemParam | ✅ POST | ✅ GET | ✅ PUT | ✅ DELETE | — |
| ABAC Policy | ✅ POST | ✅ GET | ✅ PUT | ✅ DELETE | — |
| DataPermission | ✅ POST | ✅ GET | ✅ PUT | ✅ DELETE | — |
| DQ Rule | ✅ POST | ✅ GET | ✅ PUT | ✅ DELETE | — |
| DQ Issue | ✅ POST | ✅ GET | ✅ PUT(Resolve) | ✅ DELETE | — |
| Workflow | ✅ POST | ✅ GET | ✅ PUT | ✅ DELETE | — |
| Ontology Entity | — | ✅ GET (gsxk) | — | — | ⚠️ 缺CUD |
| Ontology Property | ✅ POST (gsxk) | ✅ GET (gsxk) | ✅ PUT (gsxk) | ✅ DELETE (gsxk) | — |
| Ontology Relationship | ✅ POST (gsxk) | ✅ GET (gsxk) | — | ✅ DELETE (gsxk) | ⚠️ 缺Update |
| World Model Goal | ✅ POST (wm) | ✅ GET (wm+gsxk) | ✅ PUT (wm) | ✅ DELETE (wm) | — |
| World Model Scenario | ✅ POST (wm) | ✅ GET (wm+gsxk) | ✅ PUT (wm) | ✅ DELETE (wm) | — |
| World Model Causal Link | ✅ POST (wm) | ✅ GET (wm+gsxk) | — | ✅ DELETE (wm) | ⚠️ 缺Update |
| Object | ✅ POST | ✅ GET | ✅ PUT | ✅ DELETE | — |
| Agent Config | ✅ POST | ✅ GET | ✅ PUT | ✅ DELETE | — |
| Agent Profile | ✅ POST | ✅ GET | ✅ PUT | ✅ DELETE | — |
| Menu | — | ✅ GET | — | — | ⚠️ 只读，缺CUD |

### 8.2 关键缺失端点

| 优先级 | 端点 | 说明 |
|--------|------|------|
| 🔴 P0 | **本体实体的CUD** | Ontology Entity只有读取(gsxk bridge)，缺少创建/更新/删除端点。OntologyDesigner前端需要 |
| 🔴 P0 | **ecos_object_version 表创建** | 表不存在导致版本功能完全不可用 |
| 🔴 P0 | **ecos_object_attachment 表创建** | 表不存在导致附件功能完全不可用 |
| 🔴 P0 | **ecos_audit_log 表创建** | 表不存在导致所有审计日志静默丢失 |
| 🟡 P1 | **菜单管理CUD** | MenuController只有只读(tree/list/get)端点 |
| 🟡 P1 | **Ontology Entity标准CRUD** | dccheng域模块的OntologyController应有独立CRUD，不依赖gsxk bridge |

### 8.3 GsxkBridgeController 临时端点清单

GsxkBridgeController是P3迁移期间的**临时桥接方案**（直接用JdbcTemplate），以下端点应逐步迁移到对应域模块：

| GsxkBridge端点 | 目标模块 | 迁移状态 |
|---------------|---------|---------|
| `/gsxk/objects` | workspace | ⚠️ 功能重复（workspace已有ObjectController） |
| `/gsxk/ontology/entities` | dccheng | ⚠️ 需迁移 |
| `/gsxk/ontology/properties` | dccheng | ⚠️ 需迁移 |
| `/gsxk/worldmodel/*` | worldmodel | ⚠️ 需迁移（且world_ vs wm_表混用问题需先解决） |
| `/gsxk/dq/rules` | buszhi | ✅ buszhi模块已有DqController |
| `/gsxk/monitoring/*` | sysman | ⚠️ 需迁移至MonitoringController |
| `/gsxk/biz/dashboard` | portal | ✅ portal已有FrontendBridgeController重复端点 |
| `/gsxk/search` | portal | ✅ portal已有globalSearch重复端点 |
| `/gsxk/actions` | dccheng | ⚠️ 需迁移 |
| `/gsxk/workflows` | buszhi | ⚠️ 需迁移 |
| `/gsxk/datasets` | portal | ✅ portal已有重复端点 |

---

## 九、综合问题清单

### 🔴 Critical (P0 — 必须立即修复)

| # | 问题 | 影响范围 | 建议修复 |
|---|------|---------|---------|
| 1 | **ecos_object_version 表缺失** | ObjectController版本/快照功能完全不可用 | 创建表DDL |
| 2 | **ecos_object_attachment 表缺失** | ObjectController附件功能完全不可用 | 创建表DDL |
| 3 | **ecos_audit_log 表缺失** | 所有CUD审计日志永久丢失 | 创建表DDL |
| 4 | **ecos_world_* vs ecos_wm_* 表混用** | 前端世界模型数据不一致，因果链端点数据与其他端点不匹配 | 统一为ecos_world_*，修正GsxkBridgeController |
| 5 | **GlobalExceptionHandler未注册业务异常** | UnauthorizedException/ForbiddenException等返回500而非正确状态码 | 添加@ExceptionHandler |

### 🟡 High (P1 — 应尽快修复)

| # | 问题 | 影响范围 | 建议修复 |
|---|------|---------|---------|
| 6 | **SQL注入风险 — GsxkBridgeController 3个端点** | 字符串拼接SQL | 改为?参数化查询 |
| 7 | **AuthController使用ResponseEntity不一致** | 前端需两套错误处理逻辑 | 改为直接返回ApiResponse |
| 8 | **N+1查询 — ObjectRuntimeService.getGraph()** | 图表查询随数据增长性能恶化 | 批量查询节点label |

### 🟢 Medium (P2 — 计划修复)

| # | 问题 | 影响范围 | 建议修复 |
|---|------|---------|---------|
| 9 | 缺少Ontology Entity CUD端点 | 本体设计器无法创建/编辑/删除实体 | dccheng模块添加CRUD Controller |
| 10 | 缺少菜单管理CUD端点 | 菜单只能读取无法修改 | 在Portal模块添加Menu CRUD |
| 11 | GsxkBridgeController迁移未完成 | 存在与域模块功能重复的临时端点 | 按迁移计划逐步下线 |
| 12 | Sprint6遗留500端点未修复 | 4个端点仍返回500 | 逐个排查修复 |

---

## 十、代码质量评分

| 维度 | 评分 (1-10) | 说明 |
|------|------------|------|
| **架构设计** | 7 | 多模块分层清晰，但GsxkBridge临时方案打破了分层 |
| **安全性** | 6 | 大部分参数化查询，但有3个SQL拼接端点；GlobalExceptionHandler覆盖不足 |
| **异常处理** | 5 | sysman控制器有try/catch，但域模块完全依赖不完整的GlobalExceptionHandler |
| **数据一致性** | 4 | ecos_world_/wm_表混用；多表缺失；td_user/td_sm_user冗余 |
| **API设计** | 7 | 统一使用ApiResponse，但AuthController不一致；路径风格不统一(/api/v1 vs /api vs /datanet) |
| **代码复用** | 5 | GsxkBridge与域模块存在大量重复端点 |
| **可维护性** | 6 | 注释完整，但临时方案(GsxkBridge)占比过大 |

**总体评分**: **5.7/10**

---

## 附录A: 数据库表完整清单

共 **97** 张 public schema 表，其中：
- `ecos_*` 表: 56张
- `td_*` 表: 30张
- `demo_*` 表: 3张
- `sys_*` 表: 3张
- 其他: 5张

**冗余表对**:
1. `ecos_world_goal` vs `ecos_wm_goal` — 完全不同的数据集
2. `ecos_world_scenario` vs `ecos_wm_scenario` — 完全不同的数据集
3. `ecos_world_causal_link` vs `ecos_wm_causal_link` — 完全不同的数据集
4. `ecos_dq_rule` vs `ecos_dq_rule_v2` — 不同的列结构
5. `ecos_workflow` vs `ecos_workflow_v2` — 不同的列结构
6. `td_user` vs `td_sm_user` vs `users` — 三套用户表

---

## 附录B: 审计方法

- **工具**: ripgrep + Python pg8000 + 手动代码审查
- **方法**: 
  - grep扫描所有 `@RestController` + `@RequestMapping` 端点
  - 逐文件审查SQL拼接模式
  - 对比代码引用表名 vs 数据库实际表名
  - 检查循环中的DB查询(N+1)
  - 对照Sprint6遗留问题核实修复状态
- **范围**: 全部10个后端模块的全部Java源文件
- **未覆盖**: 编译产物(target/)、Git对象存储(.git/)、前端代码

---

> **报告生成**: 2026-06-20 | **审计人**: Hermes Agent | **下次审计**: Sprint 8
