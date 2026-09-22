# Wave-7 Fix Plan (T-26 ~ T-30)

## Scope (实际 5xx 端点 7 → 0)

基于 2026-09-04 login-state probe 和 PG 真实 schema 确认的 5xx 真实根因

### 1. T-26R1/R2 -- Workflow Instance Transition
**Root cause**: `ecos_workflow_instance` 缺 `updated_at` 列。`WorkflowInstanceRepository` 三条 transition 都 update `updated_at = NOW()` → BadSqlGrammar。
**Fix**:
- Option A (schema): V109 migration 加 `ALTER TABLE ecos_workflow_instance ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP NOT NULL DEFAULT NOW();`
- **选择 A**（最小损伤，schema 只加不删铁律）
- 文件: `ecos_backend/gateway/src/main/resources/db/migration/V109__workflow_instance_updated_at.sql`

### 2. T-27R3 -- DQ issue create
**Root cause**: `ecos_dq_issue` NOT NULL on `rule_id, field_name, severity, description, current_value, expected_value, detected_at, status`。空 body 全 null → 500。
**Fix**:
- `DqController.createIssue`: 加 guard `if (body == null || body.isEmpty()) return ApiResponse.badRequest("请求体不能为空")`
- `DqService.createIssue`: 必填字段缺失 → 抛 ValidationException
- 文件: `DqController.java` 和 `DqService.java`

### 3. T-29R5 -- Agent-mesh POST agents
**Root cause**: `ecos_agent_registry` NOT NULL on `status, endpoint, metadata, created_at, updated_at`。MyBatis `#{id}, #{name}, #{role}, #{capability}::jsonb, #{status}, #{endpoint}, #{metadata}::jsonb` — 空 body 时 status/endpoint/metadata 都是 null → DataIntegrityViolationException。
**Fix**:
- `AgentMeshController.createAgent`:
  ```java
  if (agent == null) return badRequest("请求体不能为空");
  if (agent.getName() == null || agent.getName().isBlank()) return badRequest("name 不能为空");
  if (agent.getId() == null || agent.getId().isBlank()) agent.setId(UUID.randomUUID().toString().replace("-", ""));
  if (agent.getStatus() == null) agent.setStatus("ACTIVE");
  if (agent.getEndpoint() == null) agent.setEndpoint("");
  if (agent.getCapability() == null) agent.setCapability("{}");
  if (agent.getMetadata() == null) agent.setMetadata("{}");
  ```
- 文件: `AgentMeshController.java`

### 4. T-29R5b -- A7 UsageCollector BadSqlGrammar
**Root cause**: `ecos_spans` 真实列无 `tenant_id`，SQL `WHERE s.tenant_id IS NOT NULL GROUP BY s.tenant_id` → 每 60s BadSqlGrammar。
**Fix**:
- `UsageCollector.java` SQL 改为：
  ```sql
  INSERT INTO ecos_tenant_usage (tenant_id, usage_date, quota_type, used_count, updated_at)
  SELECT e.tenant_id, ?::date, 'API_CALLS', COUNT(*), NOW()
  FROM ecos_spans s
  LEFT JOIN (SELECT trace_id, STRING_AGG(attrs->>'tenant.id', ';') AS tenant_id FROM
       (SELECT trace_id, (SELECT jsonb_array_elements(attributes) ->> 'tenant.id') AS tenant_id
       FROM ecos_spans WHERE attributes IS NOT NULL) AS a) e ON s.trace_id = e.trace_id
  ```
- 简化：因 spans 无 tenant_id 列，直接 `INSERT ...( 'unknown', ... )` + 按 trace 计数。避免 JOIN 复杂度。
- 文件: `UsageCollector.java`

### 5. T-30R6b -- knowledge rules PUT (可选)
当前 probe 中 `PUT /api/v1/knowledge/rules/x` 已 200，不再需要修。

### 6. T-29R5c -- GlobalExceptionHandler 补 HttpMessageNotReadable
**Root cause**: 空 body / JSON 解析失败是 400 不应 5xx。
**Fix**:
- `GlobalExceptionHandler.java` 加:
  ```java
  @ExceptionHandler(HttpMessageNotReadableException.class)
  @ResponseStatus(HttpStatus.BAD_REQUEST)
  public ApiResponse<Void> handleHttpMessageNotReadable(HttpMessageNotReadableException ex) {
      log.warn("Body parse error: {}", ex.getMessage());
      return ApiResponse.badRequest("请求体 JSON 格式错误");
  }
  ```
- 文件: `GlobalExceptionHandler.java`

## 实现顺序
1. V109 migration (schema)
2. AgentMeshController (R5)
3. DqController + DqService (R3)
4. UsageCollector (R5b, 真正的 FR-4900-B11-T1 修复)
5. GlobalExceptionHandler (HttpMessageNotReadable → 400)

## 验收
- 重编译 + 重启 GW
- 跑 36-endpoint login-state probe
- 目标 0 个 5xx

