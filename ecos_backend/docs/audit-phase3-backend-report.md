# ECOS-BE Phase 3 后端代码质量审计报告

> **审计日期**: 2026-06-25  
> **审计范围**: Phase 3 7个关键服务 + 安全配置  
> **审计人员**: Hermes Agent (ECOS PMO)  
> **代码分支**: databridge-v2 (gateway 统一入口)

---

## 一、审计概览

| 维度 | 评级 | 关键问题数 |
|------|:----:|:---------:|
| 代码质量（异常处理/资源管理/线程安全） | ⚠️ 中等 | 8 |
| API 契约一致性 | ❌ 较差 | 4 |
| 安全配置（白名单覆盖） | ⚠️ 中等 | 5 |
| 性能（连接池/超时/内存） | ⚠️ 中等 | 6 |
| **合计** | | **23** |

---

## 二、审计文件清单

| # | 任务描述 | 实际文件 |
|---|---------|---------|
| 1 | DuckDbService | `gateway/.../service/DuckDBQueryService.java` |
| 2 | WorkbookService | `workspace/.../workbook/WorkbookController.java` |
| 3 | Nsga2Service | `worldmodel/.../controller/ParetoController.java` |
| 4 | DigitalTwinController | `gateway/.../controller/TwinController.java` + `DigitalTwinService.java` |
| 5 | TelemetryInterceptor | `gateway/.../interceptor/TelemetryInterceptor.java` |
| 6 | QuotaFilter | `gateway/.../filter/QuotaFilter.java` |
| 7 | SysConfigController | `sysman/.../controller/SysConfigController.java` |
| 8 | SecurityConfig | `sysman/.../security/SecurityConfig.java` |

---

## 三、已知 API 问题根因分析

### 3.1 `/api/datalake/query` 和 `/api/datalake/export` → 405 (Method Not Allowed)

**根因**: 这两个端点定义为 `@PostMapping`，只能用 POST 请求。若前端或测试用例使用 GET 调用，Spring MVC 返回 405。

```java
// DataLakeController.java — 正确使用 POST，但文档/前端可能不知情
@PostMapping("/query")   // ❌ GET 请求 → 405
@PostMapping("/export")  // ❌ GET 请求 → 405
```

**修复建议**:
- 在 API 文档中明确标注 HTTP 方法
- 可选：添加一个简单的 GET `/api/datalake/query` 重定向说明端点

---

### 3.2 `/api/twins/telemetry` → 404 (Not Found)

**根因**: 遥测端点需要 `{deviceId}` 路径变量。`/api/twins/telemetry` 缺少该变量，Spring 找不到匹配的 handler。

```java
// TwinController.java:50-56
@GetMapping("/{deviceId}/telemetry")  // ✅ 正确路径: /api/twins/sensor-01/telemetry
public ApiResponse<...> getTelemetry(@PathVariable String deviceId, ...)
//                            ^^^^^^^^ 必需路径变量
// ❌ /api/twins/telemetry → 没有匹配的handler → 404
```

**修复建议**:
```java
// 添加聚合遥测端点（所有设备最新一条）
@GetMapping("/telemetry")
public ApiResponse<List<Map<String, Object>>> getAllTelemetry() {
    // 返回所有设备的最新遥测数据
}
```

---

### 3.3 `/api/traces` 和 `/api/traces/{id}` → 403 (Forbidden)

**根因**: 
1. 实际端点路径是 `/api/telemetry/traces`（前缀 `/api/telemetry`）
2. SecurityConfig 白名单包含 `/api/telemetry/**`，但 **不包含** `/api/traces/**`
3. 因此 `/api/traces` 不在白名单中 → `anyRequest().authenticated()` → 无 Token → 403

```java
// SecurityConfig.java — 白名单
"/api/telemetry/**",  // ✅ 正确路径在名单中
// ❌ 缺少 "/api/traces/**" 
```

```java
// TelemetryController.java — 实际映射
@RestController
@RequestMapping("/api/telemetry")  // ← 前缀
public class TelemetryController {
    @GetMapping("/traces")          // → 完整路径: /api/telemetry/traces
    @GetMapping("/traces/{traceId}") // → 完整路径: /api/telemetry/traces/{id}
}
```

**修复建议**: 
- **方案A（推荐）**: 前端统一使用正确路径 `/api/telemetry/traces`
- **方案B**: 在 SecurityConfig 中添加别名路由白名单 `/api/traces/**`，并添加重定向 Controller

---

## 四、代码质量审计详情

### 4.1 DuckDBQueryService.java — 🔴 严重: SQL 注入

```java
// ❌ SQL 注入 — tableName 直接拼接到 SQL
// exportInfo() line 75
List<Map<String, Object>> countResult = query("SELECT count(*) AS cnt FROM \"" + tableName + "\"");

// exportInfo() line 80
"SELECT column_name, data_type FROM information_schema.columns WHERE table_name='" + tableName + "'"

// registerParquetView() line 103
String sql = "CREATE OR REPLACE VIEW \"" + viewName + "\" AS SELECT * FROM read_parquet('" + parquetPath + "')";
```

**修复**:
```java
// 对表名进行白名单校验
private static final Pattern SAFE_IDENTIFIER = Pattern.compile("^[a-zA-Z_][a-zA-Z0-9_]*$");

private void validateIdentifier(String name, String fieldName) {
    if (name == null || !SAFE_IDENTIFIER.matcher(name).matches()) {
        throw new DataBridgeException("非法的 " + fieldName + ": " + name);
    }
}
```

**其他问题**:
- ⚠️ `query()` 方法抛出 `RuntimeException` 而非 `DataBridgeException`，破坏统一异常体系
- ⚠️ 每次 `getConnection()` 创建新连接，无连接池，高并发时性能差
- ⚠️ 目录创建失败直接忽略 `catch (IOException ignored)`，可能导致后续连接失败时无上下文

---

### 4.2 DataLakeExportService.java — 🔴 严重: SQL 注入 + 内存风险

```java
// ❌ SQL注入 — tableName直接拼接
// exportTable() line 60
List<Map<String, Object>> rows = pgJdbc.queryForList("SELECT * FROM \"" + tableName + "\"");

// ❌ writeParquetViaDuckDB() — 构建 INSERT VALUES 时直接拼接值
insertSql.append("'").append(s.replace("'", "''")).append("'");
//                        ^^ 仅转义单引号，未处理反斜杠、NULL字节等
```

**内存风险**:
```java
// ❌ 大数据量表一次性加载到内存，OOM风险
List<Map<String, Object>> rows = pgJdbc.queryForList("SELECT * FROM \"" + tableName + "\"");
// 若表有100万行 × 20列 → 内存可能溢出
```

**修复**: 使用流式读取 + 分批写入 DuckDB。

---

### 4.3 DigitalTwinService.java — ⚠️ 线程安全

```java
// ⚠️ deviceList 使用 ArrayList（非线程安全）
private final List<Map<String, Object>> deviceList = new ArrayList<>();

// ✅ 以下使用并发安全集合
private final Map<String, Map<String, Object>> deviceShadows = new ConcurrentHashMap<>();
private final Map<String, ConcurrentLinkedDeque<Map<String, Object>>> telemetryStore = new ConcurrentHashMap<>();
```

**修复**:
```java
// 使用 CopyOnWriteArrayList 或同步包装
private final List<Map<String, Object>> deviceList = new CopyOnWriteArrayList<>();
```

---

### 4.4 TelemetryInterceptor.java — ⚠️ 资源泄漏风险

```java
// ⚠️ ExecutorService 未注册关闭钩子，Spring 容器关闭时可能丢失未写入的 Span
private final ExecutorService writer = Executors.newFixedThreadPool(2, r -> {
    Thread t = new Thread(r, "telemetry-writer");
    t.setDaemon(true);  // daemon 线程在 JVM 退出时直接终止
    return t;
});
```

**修复**:
```java
@PreDestroy
public void shutdown() {
    writer.shutdown();
    try {
        if (!writer.awaitTermination(5, TimeUnit.SECONDS)) {
            writer.shutdownNow();
        }
    } catch (InterruptedException e) {
        writer.shutdownNow();
        Thread.currentThread().interrupt();
    }
}
```

---

### 4.5 QuotaFilter.java — ⚠️ 竞态条件

```java
// ❌ check-then-act 竞态条件
if (usedCount >= dailyLimit && dailyLimit > 0) {  // ← 检查
    sendQuotaExceeded(...);
    return;
}
// ... 放行 ...
jdbc.update(  // ← 递增（非原子）
    "INSERT INTO ... ON CONFLICT ... DO UPDATE SET used_count = used_count + 1"
);
```

高并发下两个请求同时通过检查，都递增后实际用量可能超过配额。

**修复**:
```java
// 使用 PostgreSQL 的原子 UPDATE + RETURNING 在数据库层面保证一致性
String sql = """
    INSERT INTO ecos_tenant_usage (tenant_id, usage_date, quota_type, used_count, updated_at)
    VALUES (?, ?::date, 'API_CALLS', 1, NOW())
    ON CONFLICT (tenant_id, usage_date, quota_type)
    DO UPDATE SET used_count = ecos_tenant_usage.used_count + 1, updated_at = NOW()
    RETURNING used_count
    """;
Long newUsedCount = jdbc.queryForObject(sql, Long.class, tenantId, today);

// 在 DB 层面检查（先查配额→写→验证）
if (newUsedCount > dailyLimit) {
    sendQuotaExceeded(...);
    return;
}
```

---

### 4.6 SysConfigController.java — ✅ 基本合格

**优点**:
- 使用 `ApiResponse` 统一返回格式
- 输入校验（`configValue` 非空检查）
- `jdbcTemplate` 可选注入有 null guard

**小问题**:
- `@Autowired(required = false)` 使数据源可选，但语义上配置管理不应在无数据源时降级

---

### 4.7 WorkbookController.java — ⚠️ 内存泄漏风险

```java
// ❌ 执行历史无限增长，无上限控制、无过期清理
private final ConcurrentHashMap<String, List<Map<String, Object>>> history = new ConcurrentHashMap<>();

history.computeIfAbsent(sessionId, k -> new ArrayList<>()).add(record);
```

**修复**:
```java
// 方案1: 限制每会话最大记录数
private static final int MAX_HISTORY_PER_SESSION = 100;
history.compute(sessionId, (k, v) -> {
    if (v == null) v = new ArrayList<>();
    if (v.size() >= MAX_HISTORY_PER_SESSION) v.remove(0);
    v.add(record);
    return v;
});

// 方案2: 使用 Caffeine Cache 自动过期
private final Cache<String, List<Map<String, Object>>> history = 
    Caffeine.newBuilder()
        .maximumSize(1000)
        .expireAfterAccess(1, TimeUnit.HOURS)
        .build();
```

---

### 4.8 ParetoController.java — ⚠️ 内存泄漏风险

```java
// ❌ 优化结果无限缓存，永不过期
private final Map<String, Map<String, Object>> results = new LinkedHashMap<>();
private final Map<String, Map<String, Object>> problems = new LinkedHashMap<>();

// ❌ HashMap 非线程安全（Controller 默认 Singleton，多线程并发写）
```

**修复**: 改用 `ConcurrentHashMap` + 设置最大容量或定时清理。

---

## 五、API 契约一致性

### 5.1 Swagger/OpenAPI 缺失

❌ 项目中**完全没有** Swagger/OpenAPI 注解（无 `@Operation`, `@ApiResponse`, `@Schema` 等）。前端开发者无法从代码自动生成 API 文档。

**修复**: 添加 `springdoc-openapi-starter-webmvc-ui` 依赖，为 Controller 添加注解。

### 5.2 路径命名不一致

| SecurityConfig 白名单 | 实际 Controller Mapping | 一致性 |
|----------------------|------------------------|:-----:|
| `/api/twins/**` | `@RequestMapping("/api/twins")` | ✅ |
| `/api/datalake/**` | `@RequestMapping("/api/datalake")` | ✅ |
| `/api/workbook/**` | `@RequestMapping("/api/workbook")` | ✅ |
| `/api/pareto/**` | `@RequestMapping("/api/pareto")` | ✅ |
| `/api/telemetry/**` | `@RequestMapping("/api/telemetry")` | ✅ |
| `/api/tenants/**` | `TenantBillingController` | ✅ |
| ❌ 缺少 `/api/traces/**` | 不存在该路径 | ❌ |

### 5.3 两套白名单冲突

```yaml
# application.yml — auth.whitelist.paths (7条)
paths:
  - "/api/health"
  - "/api/v1/auth/**"
  - "/error"
  - "/api/agent/**"
  - "/api/v1/ecos/**"
  - "/api/v1/gsxk/**"
```

```java
// SecurityConfig.java — .requestMatchers(...).permitAll() (30+条)
.requestMatchers(
    "/auth/**", "/api/v1/auth/**", "/api/v1/ecos/**", "/api/datalake/**",
    "/api/workbook/**", "/api/pareto/**", "/api/twins/**", "/api/telemetry/**", ...
).permitAll()
```

**两套配置不一致**。yml配置仅7条路径，SecurityConfig有30+条。需要在两者中选择一个作为权威来源。

---

## 六、错误码和响应格式审计

### 6.1 ApiResponse 设计

```java
// ✅ 优点：
// - 统一封装：code/message/data/timestamp
// - 工厂方法链清晰：success() / badRequest() / notFound() / internalError()
// - JSON序列化就绪

// ⚠️ 问题：code 语义重叠
// code = 0    → 成功
// code = 400  → 既是HTTP状态码又是业务错误码 → 容易混淆
// code = -1   → 系统错误 → 不能用HTTP状态码判断成功/失败
```

### 6.2 响应格式不一致

```java
// ❌ JwtAuthenticationFilter 返回硬编码 JSON（不使用ApiResponse）
response.getWriter().write(
    "{\"code\":401,\"message\":\"" + message + "\",\"data\":null}"
);
// 缺少 timestamp 字段，格式与 ApiResponse 不一致
```

**修复**: 统一使用 `ApiResponse.unauthorized(message).toJson()`。

### 6.3 SysConfigController 响应结构不统一

```java
// SysConfigController 返回格式:
{ "code": 0, "message": "ok", "data": { "data": [...], "total": N } }
//                                        ^^^^ 嵌套 data，与 ParetoController 一致

// 但 DataLakeController 返回格式:
{ "code": 0, "message": "ok", "data": { "sql": "...", "row_count": N, "data": [...] } }
```

分页/列表接口的 `data` 嵌套格式应统一。

---

## 七、安全配置审计

### 7.1 SecurityConfig.java 白名单

```
状态: ⚠️ 中等风险
```

**问题**:
1. **所有 Phase 3 端点 `.permitAll()`** — 意味着数据湖、数字孪生、遥测、Workbook 等端点**无需认证即可访问**
2. `/actuator/health` 暴露了内部健康端点
3. 无 CORS 配置 — 默认允许所有来源（取决于 Spring Boot 版本）

```
白名单端点（均无需认证）:
  /api/datalake/**    ← 数据导出 + OLAP查询
  /api/workbook/**    ← 代码执行沙盒
  /api/pareto/**      ← 优化引擎
  /api/twins/**       ← 数字孪生设备
  /api/telemetry/**   ← 遥测Trace数据
  /api/tenants/**     ← 多租户计费
```

### 7.2 安全评分配置表

| 端点 | 敏感度 | 当前权限 | 建议 | 
|------|:-----:|:-------:|------|
| `/api/datalake/query` | 🟡 中 | 公开 | 需认证 + 角色 |
| `/api/datalake/export` | 🔴 高 | 公开 | 需认证 + ADMIN |
| `/api/workbook/execute` | 🔴 高 | 公开 | 需认证 + 沙盒隔离 |
| `/api/twins/{id}/command` | 🔴 高 | 公开 | 需认证 + 操作员角色 |
| `/api/telemetry/traces` | 🟡 中 | 公开 | 需认证（至少） |
| `/api/telemetry/tokens/summary` | 🟡 中 | 公开 | 需认证 + 计费查看权限 |

---

## 八、性能审计

### 8.1 连接池配置

```yaml
# ✅ HikariCP 配置合理
hikari:
  minimum-idle: 5
  maximum-pool-size: 20
  connection-timeout: 30000   # 30秒
  max-lifetime: 1800000       # 30分钟
```

### 8.2 性能风险点

| 位置 | 风险 | 严重度 |
|------|------|:-----:|
| DuckDBQueryService | 每次查询新建JDBC连接，无连接池 | 🔴 |
| DataLakeExportService | 全量加载表到内存后写Parquet | 🔴 |
| TelemetryInterceptor | 异步写DB，但出错静默丢弃 | 🟡 |
| WorkbookController | 执行历史无限增长 | 🟡 |
| ParetoController | 优化结果无限缓存 | 🟡 |
| DataLakeController /health | 每次调 MinIO health 同步HTTP，阻塞主线程 | 🟢 |

### 8.3 修复优先级

```
P0 (立即修复):
  1. SQL注入  — DuckDBQueryService + DataLakeExportService
  2. 安全白名单 — 敏感端点改为需认证

P1 (本周内):
  3. 执行历史/优化结果内存泄漏 — Workbook/Pareto Controller
  4. 配额竞态条件 — QuotaFilter
  5. ApiResponse 统一 — JwtAuthenticationFilter

P2 (下个迭代):
  6. Swagger/OpenAPI 注解
  7. DuckDB 连接池
  8. TelemetryInterceptor 优雅关闭
  9. deviceList 线程安全
```

---

## 九、总结

Phase 3 后端代码在功能完整性上达到预期，7个服务均已实现核心能力。但在以下方面需要加强：

1. **安全性**: SQL 注入是最严重问题，`DuckDBQueryService` 和 `DataLakeExportService` 中多处直接拼接 SQL；敏感端点全部公开
2. **API 契约**: 无 Swagger 文档，路径不一致导致 3 个已知 API 问题（405/404/403）
3. **性能**: DuckDB 无连接池、大数据量导出 OOM 风险、内存缓存无清理机制
4. **一致性**: 两套白名单、硬编码 JSON vs ApiResponse 混用

**综合评级**: ⚠️ **需要 1 周整改**后可进入集成测试阶段。
