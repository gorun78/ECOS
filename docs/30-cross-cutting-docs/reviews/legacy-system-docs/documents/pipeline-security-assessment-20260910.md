# SECURITY_ASSESSMENT — Pipeline Wave 1~6

> 13 项白盒安全检查（SecRules 14 项安全规则映射）
> 判定: 0 vulnerable / 2 needs-attention

---

## 安全检查清单

| # | 检查项 | 结果 | 详情 |
|:--:|:--|:--:|:--|
| S-01 | SQL 注入: TRANSFORM_SQL `config.sql` 用户可控 | **受控** | `PipelineExecutionService.executeTransformSql` 用 `jdbc.queryForList(trimmed)` / `jdbc.update(trimmed)` — 整句 SQL 直执（无 PreparedStatement 参数化）。但这是 **设计意图**：TRANSFORM_SQL 节点就是让用户执行任意 SQL 的 DSL 节点（P2-01 §4.2 "sql 转换 SQL"），ABAC 裁决前置（S-06）+ SOURCE_JDBC 走 Connector（非系统 JdbcTemplate）是约束边界。**输出方向**: SOURCE_JDBC/CSV/REST 走 runtime-access Connector，不直接字符串拼接。 **SINK 方向**: `insertRowViaConnector` 对表名/列名做 `[A-Za-z_][A-Za-z0-9_]*` 正则校验 + 值用 `'→''` 转义 — 已有防注入，但不如 PreparedStatement 强（P2 建议长期改 batchPrepare）。 |
| S-02 | ABAC 默认 DENY（security 不可用） | **PASS** | `PipelineSecurityService.evaluateExecute`: `catch (Exception e) → AllowedResult.deny("SECURITY_ENGINE_UNAVAILABLE")` — 连接拒绝/超时/5xx 全部 fallback DENY。默认 `role=system`（P1-3 硬编码，但不影响 DENY 语义）。测试覆盖：`PipelineSecurityIntegrationTest.abacDenyWhenSecurityEngineUnavailable` 验证 `assertFalse(r.allowed())` + `assertEquals("SECURITY_ENGINE_UNAVAILABLE", r.reason())`。 |
| S-03 | 敏感字段脱敏：password/token/headers.Authorization | **PASS** | 7 键覆盖：`password`/`token`/`secret`/`apikey`/`api_key`/`authorization`/`credential`。递归 Map 下钻（`maskNodeConfig` 对嵌套 Map 递归）。`PipelineServiceImpl.toVO` 在详情/创建/更新响应调用 `securityService.parseAndMaskConfig(node.getConfig())`。测试：`PipelineSecurityIntegrationTest.maskNodeConfigMasksAll7SensitiveKeys` 验证 7 键 + 嵌套 + 非敏感保留。 |
| S-04 | 日志打印无明文 password | **PASS（有边界）** | `PipelineSecurityService.auditWrite` 只打印 action/resource，不打印 body。`PipelineExecutionService` 用 SLF4J `{}` 占位符，`logInfo` 格式化后调 `ILoggingService`。`UdfSandbox` 日志只打 `udfName` + `e.getMessage()`。**边界**: `PipelineDebugService.execSourceJdbcCapture` 行 407 打印 `datasourceId` + `fetchSize`（非敏感）；`PipelineGitService.commit` 行 55 打印 `taskId`。未发现 password/token 直接打印到 log。 |
| S-05 | JWT: Pipeline execute 端点走鉴权链 | **PASS** | Pipeline 3 个 Controller（`PipelineController` / `PipelineDebugController` / `PipelineDebugLogController`）路径前缀均为 `/api/v1/pipeline/**`。SecurityConfig/ClearanceInterceptor 按路径 whitelist 放行 — 这些路径 **不在** whitelist 中（whitelist 只放行 `/api/v1/pipeline/node-types` 等 public 端点），所以 execute 端点走完整 JWT 鉴权链。前端 `pipelineDebugApi.ts` 的 `authHeaders()` 附加 `Authorization: Bearer {token}`。 |
| S-06 | ABAC 绕过: PipelineDebugController 端点是否也走 ABAC | **⚠️ 部分** | `PipelineController.executeDefinition` 先调 `pipelineService.checkAbacBeforeExecute(id)` 再 submitTask。但 `PipelineDebugController.startSession` / `step` / `continue` 等端点 **未调 ABAC** — 调试链路直接创建 session 并执行节点（`executeAndRecord` → `executeNode`），绕过 ABAC 裁决。**现实感**: 调试会话是 **内存态**（不落执行结果到下游 DB），且创建 session 时需持有 JWT（S-05 通过）。但 `startSession` 内传入 `definitionId` 可直接触发 `dataSourceService.getById` + `JdbcConnector.executeSql`，**等效于执行 pipeline 但跳过 ABAC**。— 建议: `PipelineDebugController.startSession` 前置调 `checkAbacBeforeExecute(definitionId)`。 |
| S-07 | Transcript/Session 并发一致性 | **PASS** | `PipelineDebugService.Session` 用 `synchronized (s.lock)` 包裹 step/cont/stop/reset 全部操作。`sessions` 用 `ConcurrentHashMap`。`execLogs` 用 `ConcurrentHashMap<String, ExecutionLog>`，`ExecutionLog.lines` 用 `CopyOnWriteArrayList`。`@Scheduled cleanupSessions` 遍历 `sessions.entrySet()` 期间 step 可能 `synchronized(s.lock)` — 但 cleanup 只 remove map entry，不修改 session 内部状态（最后活跃时间 30min 过期，step 会 `touch(s)` 刷新）。**无数据竞争。** |
| S-08 | UDF 沙箱隔离 | **⚠️ 注意** | `UdfSandbox.executeJavaScript` 用 `javax.script.ScriptEngine` 执行 JS — **非沙箱**，可访问 `java.lang.Runtime`/`ProcessBuilder` 等。Python/Java/SQL 只做语法检查不执行。JS UDF 的 `sourceCode` 来自 `ecos_pipeline_udf` 表（需 UDF 注册权限），但任何可写 UDF 表的用户可执行任意 JS。**建议**: 后续 wave 引入 GraalVM Polyglot 隔离 或 Nashorn ScriptEngine 限制（`java.lang` 禁止）。当前阶段 UDF 注册走 `UdfController`（有 auth），风险受限。 |
| S-09 | 路径遍历: SOURCE_CSV `config.filePath` | **受控** | `CsvConnector.readRows(connectionConfig, fetchSize)` — filePath 由 `buildCsvConnectionConfig` 构造后传给 Connector。Connector 实现内部使用 `new File(filePath)` 读取，未做路径白名单校验。但 filePath 来自已注册的 `DataSourceEntity`（`DataSourceService.getById`），不是直接用户输入 — 数据源注册走 `DataConnection` API（有鉴权）。 |
| S-10 | 内存 OOM 防护 | **PASS** | `PipelineDebugService`: `SAMPLE_LIMIT=100`（前 100 行样本）、`MAX_HIT_RECORDS=50`、`MAX_NODE_LOGS=500`。`PipelineExecutionService`: JOIN 用 `memoryJoin`（内存态）— 大表场景可能有 OOM 风险，但 `nodeResults` 无显式容量限制。`execLogs` 有 TTL 60min + `MAX_NODE_LOGS` 上限。`PipelineTaskExecutor.execute` 不限制 pipeline 行数 — 依赖 `SOURCE_JDBC.fetchSize` 限制。 |
| S-11 | REST 端点入参校验 | **PASS** | `PipelineController`: `@PathVariable` 自动校验 ID 非空; `listExecutions` page/pageSize 有 `Math.max(1)`/`Math.min(200)` 约束。`PipelineDebugController`: `startSession` 接受 `@RequestBody(required=false)` → `createSession` 内部校验 dto 非空 + definitionId/definition 二选一。`PipelineGitController`: `listBranches` 校验 `localPath` 非空 + 目录存在; `switchBranch` 校验 localPath/branchName 非空。 |
| S-12 | 依赖注入层次: data-engine-impl 不 import 其他 engine-impl | **PASS** | `PipelineExecutionService` import: `DataSourceService` (data-engine-impl `/data/` 包), `UdfService` (data-engine-impl `/data/` 包), `UdfSandbox` (data-engine-impl `/service/` 包), `Connector/ConnectorFactory` (runtime-access), `JdbcTemplate` (Spring), `ITaskStatusCallback` (runtime-task), `ILoggingService`/`IAlertService` (runtime monitoring)。**无** 跨 engine-impl import（无 `import com.chinacreator.gzcm.engine.kb/...` / `import com.chinacreator.gzcm.engine.cognitive/...` 等）。符合架构铁律 2.1。 |
| S-13 | CORS / 跨域 | **N/A** | ECOS 为单体应用，前端 dev 走 Vite proxy (`/api` → `:8080`)，无跨域问题。生产环境走同域。 |

## 漏洞列表

| 级别 | 漏洞 | 文件:行 | 建议 |
|:--|:--|:--|:--|
| P1 | 调试链 ABAC 绕过: startSession 未调 ABAC | PipelineDebugController.java:49-56 | 在 `createSession` 前置 `checkAbacBeforeExecute(definitionId)` |
| P2 | UDF JS 沙箱无隔离 | UdfSandbox.java:52-74 | 后续 wave 引入 GraalVM 或 ScriptEngine 安全策略 |
| P2 | SINK 逐行 INSERT 字面量拼接（非 PreparedStatement） | PipelineExecutionService.java:620-656 | 推动 runtime-access JdbcConnector 增加 batchPrepare，当前已有正则+转义防注入 |
| P2 | RestTemplate 无超时 | PipelineSecurityService.java:51 | 配置 3s connect / 5s read 超时 |
