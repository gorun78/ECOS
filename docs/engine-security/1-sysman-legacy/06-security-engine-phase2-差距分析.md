# Phase 1-sysman: security-engine Phase 2 — 差距分析

> 基线: security-engine V1 (2026-08-05) | 目标: 安全引擎第二阶段能力补齐

---

## 〇、当前状态

### Controller（9个）

| Controller | 端点前缀 | 状态 |
|------|------|:--:|
| SecurityController | `/api/security` | ✅ mask/decrypt/evaluate-filter/audit-logs |
| AbacController | `/api/v1/abac` | ✅ CRUD策略 |
| PolicyEngineController | `/api/v1/policy-engine` | ✅ evaluate(OPA)/listPolicies/updatePolicy |
| AuditController | — | ✅ 审计日志查询 |
| CryptoAuditController | `/api/v1/audit/crypto` | ✅ 加密审计 |
| DataMaskingController | — | ⚠️ 需确认端点 |
| DataPermissionController | — | ✅ CRUD数据权限策略 |
| SecurityConfigController | — | ✅ 安全配置 |
| SecurityEngineStatusController | — | ✅ health |

### Service（4个）

| Service | 状态 |
|------|:--:|
| OpaPolicyService | ✅ 真实OPA集成(evaluate/list/update) |
| DataMaskingService | ✅ SHA256/PHONE/EMAIL/ID_CARD/AMOUNT |
| SecurityConfigService | ✅ |
| SecuritySandboxService | ✅ |

### API接口（5个）

| 接口 | 实现 |
|------|:--:|
| IDataSecurityPolicyService | ✅ impl存在 |
| DataSecurityPolicyDao | ✅ |
| DataSecurityPolicy | ✅ |
| RowLevelSecurityService | ❌ 只有接口，无实现 |
| ColumnLevelSecurityService | ❌ 只有接口，无实现 |

---

## 一、能力差距

### 1.1 ABAC策略评估端点 — 🟡 路径未对齐

**现状**：PolicyEngineController有完整的OPA集成（evaluate/list/update），但端点路径是 `/api/v1/policy-engine/evaluate`。

**差距**：PMO-05安全接入规则文档定义的端点路径是 `/api/v1/security/policy/evaluate`。其他引擎按文档接入时会调错路径。

**措施**：新增一个 `PolicyEvaluateController` 在 `/api/v1/security/policy` 下，转发到现有 `OpaPolicyService.evaluate()`。不改旧端点（兼容）。

### 1.2 RLS行级安全 — 🔴 缺失

**现状**：`RowLevelSecurityService` 接口定义了 `apply(String tableName, String userId)` 返回 `WHERE` 子句。无实现。

**差距**：
- 无RLS策略表（表级/角色级/用户级过滤规则）
- 无Controller端点
- data-engine查询前无法注入RLS条件

**措施**：
1. 实现 `RowLevelSecurityService` → 从策略表查询→拼 `WHERE tenant_id='xxx' AND dept_id='yyy'`
2. 新增 `POST /api/v1/security/rls/apply` → 入参{tableName, userId} → 返回{rlsCondition, params}
3. 前端：RLS策略CRUD（策略管理页面）

### 1.3 CLS列级安全 — 🔴 缺失

**现状**：`ColumnLevelSecurityService` 接口定义了 `getVisibleColumns(String tableName, String userId)` 返回列名集合。无实现。

**差距**：
- 无CLS策略表（角色级/用户级可见列配置）
- 无Controller端点
- data-engine查询结果返回前无法过滤敏感列

**措施**：
1. 实现 `ColumnLevelSecurityService` → 从策略表查询→返回可见列清单
2. 新增 `POST /api/v1/security/cls/columns` → 入参{tableName, userId} → 返回{visibleColumns: [...]}
3. 前端：CLS策略CRUD

### 1.4 审计日志写入 — 🟡 未暴露

**现状**：`SecurityController` 有 `GET /api/security/audit-logs`（读），但无写入端点。

**差距**：PMO-05文档定义的 `POST /api/v1/security/audit/log` 不存在。各引擎无法集中写审计。

**措施**：新增 `POST /api/v1/security/audit/log`，异步写入 `ecos_audit_log` 表，不阻塞调用方。

### 1.5 脱敏联动 — 🟡 未联动

**现状**：`/api/security/mask` 可对单条数据脱敏。

**差距**：产品方案要求"脱敏规则变更后通知data-engine刷新缓存"。脱敏规则变更是分散在 `sys_config` 和 `DataMaskingController`，无统一通知机制。

**措施**：脱敏规则变更时发 `PipelineEvent.DATA_MASKING_RULES_CHANGED`，data-engine订阅后刷新本地缓存。

### 1.6 会话管理 — 🟡 基本成型但缺细节

**现状**：JWT签发/验证/刷新已有，AuthController支持登录。

**差距**：产品方案要求支持token主动踢出、多地登录检测。`forceLogout` 按钮已经存在但后端未实现token黑名单/失效逻辑。

**措施**：新增 `token_blacklist` 表，强制下线时加入黑名单（TTL=token剩余有效期）。JwtAuthenticationFilter增加黑名单检查。

### 1.7 安全中心前端"事中Tab" — 🔴 占位

**现状**：安全中心 → 安全策略Tab显示"功能建设中"。

**差距**：需要实际的策略配置页：
1. ABAC策略管理（CRUD+OPA rego编辑）
2. RLS策略管理（按角色/用户的表级过滤规则）
3. CLS策略管理（按角色/用户的可见列配置）
4. 脱敏规则管理（按角色/用户的数据脱敏规则）

**措施**：新建 `security-center/tabs/DetectTab.tsx` 替换占位，含4个策略子Tab。

---

## 二、路径对齐

PMO-05安全接入规则文档 vs 当前端点路径：

| 接入规则文档 | 当前路径 | 状态 |
|------|------|:--:|
| `POST /api/v1/security/policy/evaluate` | `/api/v1/policy-engine/evaluate` | ⚠️ 路径不一致 |
| `POST /api/v1/security/audit/log` | 不存在 | ❌ 缺失 |
| `POST /api/v1/security/rls/apply` | 不存在 | ❌ 缺失 |
| `POST /api/v1/security/cls/columns` | 不存在 | ❌ 缺失 |

---

## 三、严重度汇总

| 严重度 | 数量 | 关键项 |
|:--:|:--:|------|
| 🔴 阻塞 | 3 | RLS实现、CLS实现、安全中心事中Tab替换 |
| 🟡 重要 | 4 | ABAC路径对齐、审计写入、脱敏联动、token踢出 |
| 🟢 文档 | 1 | 更新security-engine AGENTS.md |

---

## 四、可复用资产

- ✅ OpaPolicyService — 真实OPA集成（evaluate/list/update/rego文件管理）
- ✅ AbacController — ABAC策略CRUD完整
- ✅ DataMaskingService — 脱敏引擎（5种策略）
- ✅ SecurityController — mask/decrypt/evaluate-filter
- ✅ RowLevelSecurityService 接口已定义 — 只需补实现
- ✅ ColumnLevelSecurityService 接口已定义 — 只需补实现
- ✅ PMO-05安全接入规则文档 — 端点定义+请求格式已写好
- ✅ `ecos_docker/` — OPA容器已配置
