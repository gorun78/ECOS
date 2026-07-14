# ECOS-BE 后端审视报告

**审视者**: ECOS-BE（高级后端工程师）
**审视日期**: 2026-06-16
**工作目录**: `/mnt/d/JavaProjects/databridge-v2/`

---

## 1. API 全景

### 1.1 控制器总览 (共 23 个 Controller)

| 分组 | 控制器 | 基路径 | 状态 |
|------|--------|--------|------|
| **对象运行时** | ObjectController | `/api/v1/ecos/objects` | ✅ 已实现 |
| **本体设计器** | OntologyController | `/api/v1/ecos/ontologies` | ✅ 已实现 |
| **ObjectQL 查询** | ObjectQLController | `/api/query/objectql` | ⚠️ 路径不一致 |
| **自然语言查询** | NLQController | `/api/query/nlq` | ⚠️ 路径不一致 |
| **查询历史** | QueryHistoryController | — | ✅ |
| **工作流** | WorkflowController | `/api/v1/ecos/workflows` | ✅ |
| **数据质量** | DqController | `/api/v1/ecos/dq` | ✅ |
| **世界模型** | WorldModelController | `/api/v1/ecos/world-model` | ✅ |
| **知识图谱** | KnowledgeGraphController | — | ✅ |
| **分类** | ClassificationController | — | ✅ |
| **术语表** | GlossaryController | — | ✅ |
| **数据市场** | MarketplaceController | — | ✅ |
| **Agent 资料** | AgentProfileController | `/api/v1/agent/profiles` | ⚠️ 路径不一致 |
| **Agent 调用** | AgentCallController | — | ✅ |
| **Agent Mesh** | AgentMeshController | `/api/agent-mesh` | ⚠️ 无 `/v1` |
| **ABAC 权限** | AbacController | `/api/v1/abac` | ✅ |
| **权限管理** | PermissionController | — | ✅ |
| **用户管理** | UserController | `/api/system/users` | ⚠️ 路径不一致 |
| **角色管理** | RoleController | — | ✅ |
| **组织管理** | OrganizationController | — | ✅ |
| **菜单** | MenuController | — | ✅ |
| **审计日志** | AuditController | `/api/v1/audit` | ✅ |
| **系统参数** | SystemParamController | — | ✅ |
| **配置管理** | ConfigController | — | ✅ |
| **策略引擎** | PolicyEngineController | — | ✅ |
| **数据权限** | DataPermissionController | — | ✅ |
| **认证** | AuthController (boot) | `/auth` | ⚠️ 无 `/api` |
| **健康检查** | HealthController (boot) | `/api/health` | ✅ |

### 1.2 API 路径不一致问题 (P1)

当前存在 **4 种不同风格的 API 路径**：

```
风格 A (推荐): /api/v1/{domain}/...  — ObjectController, WorkflowController, DqController, WorldModelController
风格 B:         /api/{domain}/...     — ObjectQLController, NLQController, HealthController
风格 C:         /api/system/...      — UserController, RoleController, OrganizationController
风格 D:         /auth/...            — AuthController (无 /api 前缀)
风格 E:         /api/agent-mesh/...  — AgentMeshController (无版本号)
```

**问题**: 前端需要维护多个 baseURL 模式，增加 SDK 和网关配置复杂度。

### 1.3 响应格式不一致 (P2)

- **AuthController**: 直接返回 `ResponseEntity<Map>`，手动构造 `code/message/data` 字段
- **其他 Controller**: 使用 `ApiResponse` 统一包装
- **HealthController**: 返回裸 `Map`，无 `ApiResponse` 包装

**建议**: 全局统一使用 `ApiResponse`，Auth 控制器也应该通过 `ApiResponse` + 全局异常处理器返回。

### 1.4 HTTP 方法使用

| 操作 | 方法 | 评价 |
|------|------|------|
| CRUD 列表 | GET | ✅ |
| CRUD 详情 | GET /{id} | ✅ |
| CRUD 创建 | POST | ✅ |
| CRUD 更新 | PUT /{id} | ✅ |
| CRUD 删除 | DELETE /{id} | ✅ |
| 状态变更 | PUT /{id}/status | ⚠️ 应使用 PATCH |
| 发布 | PATCH /{id}/publish | ✅ |

**问题**: `PUT /{id}/status` 应为 `PATCH`（部分更新语义）。

---

## 2. 代码质量评估

### 2.1 总体评价

```
整体质量: ★★☆☆☆ (中等偏下)
```

**正向**:
- 项目有 ArchUnit 架构测试（sysman 模块）
- 使用了 SLF4J 日志（所有 Controller 有 Logger）
- IAM 模块做了接口抽象（IUserService, IAbacPolicyService 等）
- Agent Mesh 使用了 MyBatis Mapper + Entity 分离
- ObjectQLParser 有实体白名单防 SQL 注入

### 2.2 严重问题

#### 2.2.1 SQL 注入风险 — ObjectController 直接拼接表名 (P0)

```java
// ObjectController.java:101
rows = jdbc.queryForList(
    "SELECT * FROM " + table + " ORDER BY id LIMIT ? OFFSET ?", size, offset);

// 创建时直接拼接字段名
sql.append(e.getKey());  // 用户提供的 key 直接拼入 SQL
```

**风险**: `@RequestBody Map<String, Object>` 中的 key 直接作为列名拼入 SQL。虽然 `ENTITY_TABLE` 做了表名映射，但列名完全由客户端控制，恶意请求可注入 `; DROP TABLE ...` 或触发 PostgreSQL 错误泄露信息。

#### 2.2.2 硬编码密码与 Token 方案 (P0)

```java
// AuthController.java
private static final String DEMO_USER = "admin";
private static final String DEMO_PASS = "admin123";
// token = UUID.randomUUID()  // 无 JWT 签名
```

**问题**:
- 硬编码凭据不适合任何非演示环境
- UUID Token 是无状态的纯内存方案，重启后所有登录失效
- 无 Refresh Token 机制
- Token 存储在 `ConcurrentHashMap`，无过期策略 → 内存泄漏

#### 2.2.3 手写 JSON 序列化 (P2)

```java
// NLQController.java:175
private String toJson(Map<String, Object> map) {
    StringBuilder sb = new StringBuilder("{");
    // ... 手动拼接 JSON
}
```

**问题**: 项目中已有 Jackson ObjectMapper，手写 JSON 序列化不仅低效，而且容易出错（未正确处理特殊字符、数字精度、嵌套对象）。

### 2.3 架构与命名问题

#### 2.3.1 泛型 Map 滥用 (P1)

**问题**: 半数以上 Controller 使用 `Map<String, Object>` 作为请求体和响应体。

```java
@RequestBody Map<String, Object> body  // OntologyController, WorkflowController, WorldModelController, DqController 等
```

**后果**:
- 丧失类型安全（Lombok/Bean Validation 无法生效）
- Swagger/OpenAPI 无法自动生成文档（生成类型为 `object`）
- 序列化/反序列化不可控

**例外**: UserController 使用了 `UserAccount` 实体，AgentProfileController 使用了 `ProfileConfig`，AgentMeshController 使用了 `AgentRegistryEntity` / `MissionEntity`。

#### 2.3.2 Controller 层过厚 (P2)

多数 Controller 直接包含业务逻辑：
- `ObjectController.initTables()` — DDL 自动建表 + 种子数据
- `WorkflowController.seedData()` — 种子数据内嵌在代码中
- 所有内存存储 Controller 直接在构造函数中 `seedData()`

**问题**: Controller 应仅做参数校验和路由转发，业务逻辑应下沉到 Service。

#### 2.3.3 硬编码种子数据 (P2)

**问题**: 7 个 Controller 包含硬编码种子数据（Ontology、Workflow、DQ、WorldModel、Object 等）。种子数据和业务逻辑耦合，无法区分环境（dev/test/prod）。

### 2.4 测试覆盖

```
单元测试: 1 个 (ArchitectureTest.java)
集成测试: 0 个
Controller 测试: 0 个
Service 测试: 0 个 (但 runtime-core 模块有 7 个单元测试)
```

**状态**: sysman 模块几乎无业务逻辑测试。`ArchitectureTest` 仅验证包结构约束，不验证功能正确性。

---

## 3. 数据层评估

### 3.1 存储策略分裂

当前后端存在 **3 种数据持久化方式**：

| 方式 | 模块 | 数据 |
|------|------|------|
| **PostgreSQL + JdbcTemplate** | ObjectController, ObjectQLController, NLQController | demo_customer, demo_supplier, demo_invoice |
| **PostgreSQL + MyBatis Mapper** | AgentMeshController (通过 AgentRegistryRepository) | ecos_agent_registry, ecos_mission, ecos_mission_task |
| **内存 ConcurrentHashMap** | OntologyController, WorkflowController, DqController, WorldModelController | 本体设计、工作流、数据质量、世界模型 |

**严重问题 (P0)**: 超过 60% 的"业务数据"存储在内存 `ConcurrentHashMap` 中：
- 服务重启后数据全部丢失
- 多实例部署无法共享数据
- 无事务保障
- 无持久化审计

### 3.2 数据库 Schema 缺失

PostgreSQL 表都是通过 `ObjectController.initTables()` 中的 `CREATE TABLE IF NOT EXISTS` 自动创建，无：
- 数据库初始化脚本（`schema.sql` / Flyway / Liquibase）
- 索引定义（当前无任何索引）
- 外键约束（`demo_invoice.customer_id` 无 FK 引用）
- 实体关系图

### 3.3 MyBatis Mapper 层分析

AgentMesh 的 Repository 层是唯一使用 MyBatis 的模块：

**正向**:
- 使用了 `@Mapper` 注解 + 参数化查询
- entity 有独立 POJO 类
- 使用了 `::jsonb` 类型转换

**待改进**:
- SQL 散落在注解中（建议移入 XML Mapper 以便 DBA review）
- 无分页插件（`findAll()` 无分页，大数据量问题）
- `findRecent(int limit)` 的 SQL 实现未见到（可能也是无 limit 的全表查询）

### 3.4 连接池配置

```yaml
hikari:
  minimum-idle: 5
  maximum-pool-size: 20
  idle-timeout: 30000
  max-lifetime: 1800000
```

HikariCP 配置基本合理，但所有 Controller 共享同一个 `JdbcTemplate`/DataSource，无读写分离。

---

## 4. 性能与稳定性

### 4.1 性能隐患

#### 4.1.1 全表搜索 (P1)

```java
// ObjectController.java:187
for (String table : ENTITY_TABLE.values()) {
    rows = jdbc.queryForList("SELECT * ... ILIKE ...", ...);
}
```

**问题**: 搜索接口遍历所有表，每条执行 `ILIKE` 全表扫描。无全文索引，数据量大时（>10万行）将导致 OOM 或超时。

#### 4.1.2 内存分页 (P1)

```java
// ObjectController.java:200-202
int total = allRows.size();  // 先查出全部
int from = Math.min(offset, allRows.size());
List<Map<String, Object>> pageRows = allRows.subList(from, to);
```

**问题**: `search()` 方法先查出 **所有匹配行** 再在内存中分页。当数据量增大时，这会成为严重的内存瓶颈。

#### 4.1.3 全局搜索无 limit 限制 (P2)

```java
rows = jdbc.queryForList("SELECT * ... ILIKE ... LIMIT ?", size);
```

虽然单表查询加了 `LIMIT ?`，但跨表搜索循环中每个表都使用传入的 `size` 作为 limit。如果有 10 个表，实际返回数据可达 `size * 10`。

#### 4.1.4 N+1 查询模式 (P2)

`ObjectController.getDetail()` 查询对象详情后，硬编码返回空 relations/timeline：

```java
result.put("relations", List.of());  // 后续扩展需要额外查询
result.put("timeline", List.of());   // 同上
```

如果未来实现关联加载，需要警惕 N+1 问题。

### 4.2 稳定性风险

#### 4.2.1 缺乏统一异常处理 (P2)

当前模式是每个 Controller 方法内部 `try-catch` 包裹：

```java
try {
    // ... 业务逻辑
} catch (Exception e) {
    log.error("...", e);
    return ApiResponse.internalError("查询失败: " + e.getMessage());
}
```

**问题**: 这种模式在每个方法中重复，没有使用 `@ControllerAdvice` + `@ExceptionHandler`。未来如果某个 `catch` 漏掉，异常将直接暴露给客户端（500 + stacktrace）。

#### 4.2.2 @Autowired(required=false) 模式 (P2)

多个 Controller 使用 `@Autowired(required=false)` 且每次调用都判空：

```java
@Autowired(required = false)
private IAgentProfileService agentProfileService;

if (agentProfileService == null) {
    return ApiResponse.internalError("Agent Profile 服务未就绪");
}
```

**问题**: 如果 Bean 未装配，说明配置有问题，应该启动失败而不是运行时返回错误。`required=false` 隐藏了真正的配置问题。

#### 4.2.3 并发 ID 生成器 (P3)

```java
private static final AtomicInteger ID_SEQ = new AtomicInteger(100);
```

**问题**: 内存 ID 生成器在重启后重置，多个实例可能生成重复 ID。应使用数据库序列或 UUID。

### 4.3 日志与可观测性

**正向**:
- 所有 Controller 使用 SLF4J + `LoggerFactory`
- 关键操作有 `log.info("...created: {}", id)`
- 异常日志有 stacktrace: `log.error("...", e)`

**待改进**:
- **无 MDC 跟踪 ID**：请求链路无法串联（无 TraceId/RequestId）
- **无 Metrics**：无 Micrometer / Prometheus 端点
- **无健康检查扩展**：`HealthController` 仅返回静态 `UP`，无数据库/LLM 连通性检查
- **日志级别不精确**：大量使用 `log.warn` 而非 `log.error` 记录实际错误
- **缺少 AOP 日志**：无请求耗时、入参/出参的统一记录

---

## 5. 改进建议（按优先级）

### P0 — 必须立即修复

| # | 问题 | 修复方案 |
|---|------|----------|
| 1 | **SQL 注入** — `ObjectController` 拼接表名和列名 | 从硬编码 MAP 读取允许的列名白名单，禁止直接拼接 `e.getKey()` |
| 2 | **硬编码凭据** — `admin/admin123` | 改为从 `application.yml` 读取，环境变量注入，生产环境使用 SSO/LDAP |
| 3 | **Token 方案** — UUID 无签名 Token | 引入 JWT（jjwt 库），支持 `access_token` + `refresh_token`，设置过期时间 |

### P1 — 高优先级

| # | 问题 | 修复方案 |
|---|------|----------|
| 4 | **内存存储** — 60%+ 控制器用 ConcurrentHashMap | 按模块迁移到 PostgreSQL（先加 `schema.sql`，再用 MyBatis + 实体类） |
| 5 | **API 路径统一** — 4 种风格 | 全部统一为 `/api/v1/{domain}`，网关层做路径重写兼容旧版 |
| 6 | **DTO 缺失** — `Map<String, Object>` 滥用 | 为每个 Controller 定义 Request/Response DTO，启用 Bean Validation |
| 7 | **全表搜索/内存分页** | 改为数据库级分页（`OFFSET/LIMIT`），加 GIN 索引支持 `ILIKE` 搜索 |
| 8 | **Controller 层过厚** | 抽取 Service 层，Controller 只做路由和校验 |

### P2 — 中优先级

| # | 问题 | 修复方案 |
|---|------|----------|
| 9 | **缺少单元测试** | 为 Service 层加 JUnit 5 + Mockito 测试，关键 Controller 加 `@WebMvcTest` |
| 10 | **手写 JSON 序列化** | 全部改用 Jackson ObjectMapper |
| 11 | **统一异常处理** | 实现 `@RestControllerAdvice` + `@ExceptionHandler` 全局处理 |
| 12 | **种子数据** | 抽离到 `data.sql` / Flyway migration，按 profile 区分 |
| 13 | **数据库 Schema 管理** | 引入 Flyway，创建初始化迁移脚本，加外键和索引 |
| 14 | **PUT /status 改为 PATCH** | 遵循 RESTful 部分更新语义 |
| 15 | **`@Autowired(required=false)`** | 改为构造器注入 + `@ConditionalOnBean` |

### P3 — 低优先级

| # | 问题 | 修复方案 |
|---|------|----------|
| 16 | **MDC TraceId** | 加 Filter/Interceptor 设置 `MDC.put("traceId", UUID)` |
| 17 | **Metrics 端点** | 引入 Micrometer + Actuator `/actuator/metrics` |
| 18 | **Health 增强** | 实现 `HealthIndicator` 检查 DB 连通性和 LLM API |
| 19 | **AOP 请求日志** | 实现 `@LogExecutionTime` 注解或全局拦截器 |
| 20 | **MyBatis XML Mapper** | 将注解 SQL 迁移到 XML，便于 DBA review 和 SQL 格式化 |

---

## 总结

ECOS 后端整体处于 **MVP 原型阶段**，功能面覆盖广泛（23+ 控制器），但在 **安全性、数据持久化、代码质量** 三方面存在显著短板：

- **最大风险**: SQL 注入（P0）+ 硬编码凭据（P0）+ 内存数据丢失（P1）
- **架构债务**: API 路径不统一、DTO 缺失、Controller-Service 职责混叠
- **可观测性**: 完全缺失 TraceId、Metrics、Health Check

**建议优先完成 P0+P1 的修复（预计 3-5 人日）**，在此基础上追加测试覆盖和数据库迁移，再考虑性能优化和可观测性增强。
