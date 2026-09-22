# PMO指令：Phase1-sysman-07 — security-engine Phase 2 能力演进

> **来源**: 差距分析 `06-security-engine-phase2-差距分析.md` | **工期**: 5天 | **范围**: 后端为主 + 前端事中Tab | **依赖**: PMO-01~06 完成

---

## §背景

security-engine Phase 1已完成认证/脱敏/审计查询/OPA集成。Phase 2补齐三大缺口：RLS行级安全、CLS列级安全、安全中心事中Tab替换。四项对齐：ABAC路径规范、审计写入、Token踢出、脱敏联动。

---

## §禁止清单

1. ❌ 不新增Maven模块（RLS/CLS实现放在 security-engine-impl 中）
2. ❌ 不删改已有端点（SecurityController的旧端点照旧留，只新增 `/api/v1/security/*` 端点）
3. ❌ 不新增Docker容器（OPA已在docker-compose中）
4. ❌ RLS/CLS策略表不设物理外键（PG性能考量）
5. ❌ 不改 GatewayApplication 的 excludeFilters（新增Controller在security-engine-impl内，自然会被扫描）

---

## §Task

### P0: 安全核心（3天）

#### T1: RLS行级安全实现

**现状**：`RowLevelSecurityService` 接口存在，零实现。

**后端**：

**1.1 新增DB表** (`ecos_rls_policy`):
```sql
CREATE TABLE IF NOT EXISTS ecos_rls_policy (
    id          VARCHAR(64) PRIMARY KEY,
    policy_name VARCHAR(128) NOT NULL,
    table_name  VARCHAR(128) NOT NULL,
    filter_expr VARCHAR(512) NOT NULL,  -- e.g. "tenant_id = :tenantId AND dept_id = :deptId"
    role_id     VARCHAR(64),             -- NULL=全局, 指定角色
    user_id     VARCHAR(64),             -- NULL=全局, 指定用户
    priority    INTEGER DEFAULT 0,
    enabled     BOOLEAN DEFAULT true,
    description TEXT,
    created_by  VARCHAR(64),
    created_at  TIMESTAMP DEFAULT NOW(),
    updated_at  TIMESTAMP DEFAULT NOW()
);
```

**1.2 实现 Service**：
- 文件：`security-engine-impl/.../service/RowLevelSecurityServiceImpl.java`
- 实现 `RowLevelSecurityService.apply(tableName, userId)`
- 逻辑：查 `ecos_rls_policy` → 按 priority 排序 → 合并 filter_expr → 返回 `{condition: "WHERE ...", params: {...}}`
- 合并规则：同表多策略用 `AND` 连接

**1.3 新增 Controller**：
- 文件：`security-engine-impl/.../controller/RlsController.java`
- 端点 `POST /api/v1/security/rls/apply`
- 请求格式：
```json
{
  "tableName": "ecos_finance_data",
  "userId": "admin"
}
```
- 响应格式：
```json
{
  "code": 0,
  "data": {
    "condition": "tenant_id = :tenantId AND dept_id = :deptId",
    "params": {"tenantId": "t_001", "deptId": "d_003"}
  }
}
```
- 同时提供 CRUD：`GET /api/v1/security/rls/policies`、`POST /api/v1/security/rls/policies`、`PUT /api/v1/security/rls/policies/{id}`、`DELETE /api/v1/security/rls/policies/{id}`

**验收**：
```bash
# 创建策略
curl -X POST http://localhost:8080/api/v1/security/rls/policies \
  -H "Authorization: Bearer $TOKEN" \
  -d '{"policyName":"租户隔离","tableName":"ecos_finance_data","filterExpr":"tenant_id = :tenantId","roleId":"analyst","enabled":true}'

# 应用RLS
curl -X POST http://localhost:8080/api/v1/security/rls/apply \
  -H "Authorization: Bearer $TOKEN" \
  -d '{"tableName":"ecos_finance_data","userId":"admin"}'
# 期望: 返回condition+params
```

---

#### T2: CLS列级安全实现

**现状**：`ColumnLevelSecurityService` 接口存在，零实现。

**后端**：

**2.1 新增DB表** (`ecos_cls_policy`):
```sql
CREATE TABLE IF NOT EXISTS ecos_cls_policy (
    id             VARCHAR(64) PRIMARY KEY,
    policy_name    VARCHAR(128) NOT NULL,
    table_name     VARCHAR(128) NOT NULL,
    visible_cols   TEXT NOT NULL,           -- JSON数组: ["col1","col2","col3"]
    blocked_cols   TEXT,                    -- JSON数组: ["salary","phone"]
    role_id        VARCHAR(64),
    user_id        VARCHAR(64),
    priority       INTEGER DEFAULT 0,
    enabled        BOOLEAN DEFAULT true,
    description    TEXT,
    created_by     VARCHAR(64),
    created_at     TIMESTAMP DEFAULT NOW(),
    updated_at     TIMESTAMP DEFAULT NOW()
);
```

**2.2 实现 Service**：
- 文件：`security-engine-impl/.../service/ColumnLevelSecurityServiceImpl.java`
- 实现 `ColumnLevelSecurityService.getVisibleColumns(tableName, userId)`
- 逻辑：查 `ecos_cls_policy` → 合并可见列 → 返回 `{visibleColumns: [...], blockedColumns: [...]}`
- 优先级：用户级策略 > 角色级策略 > 全局策略

**2.3 新增 Controller**：
- 文件：`security-engine-impl/.../controller/ClsController.java`
- 端点 `POST /api/v1/security/cls/columns`
- 请求格式：
```json
{
  "tableName": "ecos_finance_data",
  "userId": "analyst_001",
  "allColumns": ["id","company","revenue","cost","salary","profit"]
}
```
- 响应格式：
```json
{
  "code": 0,
  "data": {
    "visibleColumns": ["id","company","revenue","cost","profit"],
    "blockedColumns": ["salary"]
  }
}
```
- 同时提供 CRUD：`GET/POST/PUT/DELETE /api/v1/security/cls/policies`

**验收**：
```bash
# 创建CLS策略
curl -X POST http://localhost:8080/api/v1/security/cls/policies \
  -H "Authorization: Bearer $TOKEN" \
  -d '{"policyName":"屏蔽薪资","tableName":"ecos_finance_data","visibleCols":["id","company","revenue","cost","profit"],"blockedCols":["salary"],"roleId":"analyst","enabled":true}'

# 应用CLS
curl -X POST http://localhost:8080/api/v1/security/cls/columns \
  -H "Authorization: Bearer $TOKEN" \
  -d '{"tableName":"ecos_finance_data","userId":"test_analyst","allColumns":["id","company","revenue","cost","salary","profit"]}'
# 期望: visibleColumns不含salary
```

---

#### T3: ABAC路径对齐

**现状**：PolicyEngineController在 `/api/v1/policy-engine/evaluate`，接入规则文档要求 `/api/v1/security/policy/evaluate`。

**后端**：
- 文件：`security-engine-impl/.../controller/SecurityPolicyController.java`
- 端点 `POST /api/v1/security/policy/evaluate`
- **转发到已有 `OpaPolicyService.evaluate()`**，不重复实现
- 请求格式按PMO-05文档：
```json
{
  "subject": {"userId": "u_001", "role": "analyst", "clearanceLevel": 2},
  "resource": {"type": "ontology", "id": "obj_finance", "classification": "内部"},
  "action": "read",
  "context": {"ip": "192.168.1.1", "time": "2026-08-05T09:00:00"}
}
```
- 内部将 subject/action/resource 映射为 OPA input json 后调 `opaService.evaluate("abac", inputMap)`

**验收**：
```bash
curl -X POST http://localhost:8080/api/v1/security/policy/evaluate \
  -H "Authorization: Bearer $TOKEN" \
  -d '{"subject":{"userId":"admin","role":"admin"},"resource":{"type":"data","id":"tbl_finance"},"action":"read","context":{"ip":"127.0.0.1"}}'
# 期望: {"allow": true/false, ...}
```

---

### P1: 安全增强（1天）

#### T4: 审计日志写入端点

**现状**：`GET /api/security/audit-logs` 只读。

**后端**：

**4.1 确认审计表结构**（如 `ecos_audit_log` 已存在则复用，否则建表）:
```sql
CREATE TABLE IF NOT EXISTS ecos_audit_log (
    id           VARCHAR(64) PRIMARY KEY,
    user_id      VARCHAR(64),
    username     VARCHAR(128),
    action       VARCHAR(64) NOT NULL,
    resource     VARCHAR(256),
    result       VARCHAR(32),      -- ALLOW/DENY
    detail       TEXT,             -- JSON: 请求摘要/耗时/IP/UA
    ip_address   VARCHAR(45),
    created_at   TIMESTAMP DEFAULT NOW()
);
```

**4.2 新增端点**：
- 文件：`security-engine-impl/.../controller/SecurityAuditController.java`（或扩展现有 AuditController）
- 端点 `POST /api/v1/security/audit/log`
- 异步写入（`@Async`），不阻塞调用方
- 请求：
```json
{
  "userId": "admin",
  "action": "QUERY",
  "resource": "ecos_finance_data",
  "result": "ALLOW",
  "detail": "{\"rows\":150,\"elapsedMs\":230}",
  "ipAddress": "192.168.1.1"
}
```

**验收**：
```bash
curl -X POST http://localhost:8080/api/v1/security/audit/log \
  -H "Authorization: Bearer $TOKEN" \
  -d '{"userId":"test","action":"RLS_CHECK","resource":"ecos_finance_data","result":"ALLOW","detail":"{}"}'
# 期望: 200 OK
# 验证: GET /api/security/audit-logs 能查到新记录
```

---

#### T5: Token强制踢出

**现状**：前端有"强制下线"按钮，但token未失效。

**后端**：

**5.1 新增DB表** (`ecos_token_blacklist`):
```sql
CREATE TABLE IF NOT EXISTS ecos_token_blacklist (
    id          VARCHAR(64) PRIMARY KEY,
    jti         VARCHAR(128) NOT NULL UNIQUE,  -- JWT ID
    user_id     VARCHAR(64) NOT NULL,
    expires_at  TIMESTAMP NOT NULL,
    created_at  TIMESTAMP DEFAULT NOW()
);
-- 定时清理过期记录
```

**5.2 修改 JwtAuthenticationFilter**：
- 在token验证后增加黑名单检查：`SELECT 1 FROM ecos_token_blacklist WHERE jti = :jti`
- 命中则返回 401 `{"code":401,"message":"会话已被管理员终止"}`

**5.3 修改 强制下线逻辑**：
- UserController 的 `/force-logout` 端点：将用户所有未过期token的jti写入 `ecos_token_blacklist`

**验收**：
```bash
# 1. 用户A登录，获取token_A
# 2. admin调用强制下线
curl -X POST http://localhost:8080/api/v1/system/users/xxx/force-logout \
  -H "Authorization: Bearer $ADMIN_TOKEN"
# 3. 用户A用token_A请求 → 期望 401
```

---

#### T6: 脱敏联动通知（轻量）

**现状**：脱敏规则变更无通知。

**后端**：
- 文件：`security-engine-impl/.../service/DataMaskingService.java`（扩展）
- 在 `updateMaskingRule()` 方法末尾发事件：
```java
pipelineEventPublisher.publish(new PipelineEvent("DATA_MASKING_RULES_CHANGED", Map.of("timestamp", Instant.now())));
```
- 使用 `common-api` 中已定义的 `PipelineEvent`，data-engine后续订阅即可（订阅端留Phase 3）

**验收**：编译通过，事件发布不抛异常即通过。data-engine订阅验证留Phase 3。

---

### P2: 前端事中Tab（1天）

#### T7: 安全策略Tab替换占位

**现状**：安全中心→安全策略Tab显示"功能建设中"。

**前端**：
- 文件：`pages/security-center/tabs/DetectTab.tsx`（替换现有占位组件）
- 4个子Tab：

| 子Tab | 内容 | 调用的API |
|------|------|------|
| ABAC策略 | CRUD表格+OPA rego编辑器 | `/api/v1/security/policy/*`, `/api/v1/policy-engine/*` |
| RLS策略 | 表级过滤规则CRUD | `/api/v1/security/rls/policies` |
| CLS策略 | 可见列配置CRUD | `/api/v1/security/cls/policies` |
| 脱敏规则 | 脱敏策略CRUD | `/api/security/mask` (复用) |

**ABAC策略子Tab**：
- 表格列：策略名/资源类型/操作/角色/状态/操作
- 新建/编辑Modal：subject配置/资源选择/action选择/OPA rego编辑区
- 测试按钮：弹出测试窗口→输入测试参数→调evaluate→显示结果

**RLS/CLS子Tab**：
- 表格列：策略名/表名/角色/状态/操作
- 新建Modal
- 策略列表页支持按表名筛选

**脱敏规则子Tab**：
- 现有脱敏能力可视化：5种策略(SHA256/PHONE/EMAIL/ID_CARD/AMOUNT)的规则配置
- 每行：策略类型/启用状态/参数/操作

**验收**：
- 安全中心→安全策略Tab不再显示"建设中"
- 4个子Tab均可切换
- ABAC策略可CRUD+测试评估
- RLS/CLS策略可CRUD

---

## §执行顺序

```
Day 1-2 (P0):  T1 RLS → T2 CLS → T3 ABAC路径对齐
               (RLS+CLS可并行，T3独立)
Day 3   (P1):  T4 审计写入 → T5 Token踢出 → T6 脱敏联动
               (T4/T5/T6相互独立，可并行)
Day 4-5 (P2):  T7 前端事中Tab
```

---

## §验收总清单

```bash
# ─── 后端 ───
# T1: RLS
curl -X POST $BASE/api/v1/security/rls/policies -H "$AUTH" -d '{"policyName":"test","tableName":"t1","filterExpr":"1=1","enabled":true}'
curl -X POST $BASE/api/v1/security/rls/apply -H "$AUTH" -d '{"tableName":"t1","userId":"admin"}'

# T2: CLS
curl -X POST $BASE/api/v1/security/cls/policies -H "$AUTH" -d '{"policyName":"test","tableName":"t1","visibleCols":["a","b"],"blockedCols":["c"],"enabled":true}'
curl -X POST $BASE/api/v1/security/cls/columns -H "$AUTH" -d '{"tableName":"t1","userId":"admin","allColumns":["a","b","c"]}'

# T3: ABAC路径
curl -X POST $BASE/api/v1/security/policy/evaluate -H "$AUTH" -d '{"subject":{"userId":"admin","role":"admin"},"resource":{"type":"data","id":"t1"},"action":"read"}'

# T4: 审计写入
curl -X POST $BASE/api/v1/security/audit/log -H "$AUTH" -d '{"userId":"test","action":"TEST","resource":"test","result":"ALLOW"}'
curl -s $BASE/api/security/audit-logs -H "$AUTH" | python3 -c "import sys,json; d=json.load(sys.stdin); assert len(d['data'])>0"

# T5: Token踢出
# (需要两步操作：登录→踢出→验证401)

# ─── 前端 ───
# T7: 安全策略Tab
# 浏览器 http://localhost:3000/#/security-center → 安全策略Tab → 4个子Tab可切换
# ABAC策略: CRUD+测试评估
# RLS/CLS策略: CRUD表格
# 脱敏规则: 策略配置
```
