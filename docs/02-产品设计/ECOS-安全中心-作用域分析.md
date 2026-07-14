# ECOS 安全中心 — 功能/策略作用域分析

> 分析日期: 2026-06-29 | 代码验证: 全量源码 + 实体模型 + 拦截器逻辑

---

## 一、总览矩阵

| 模块 | 全局 | 租户 | 机构 | 用户 | 角色 | 验证依据 |
|------|:--:|:--:|:--:|:--:|:--:|------|
| 🔍 安全审计 | ✅ | ✅ | ❌ | ✅ | ❌ | `AuditLog.tenantId` + `userId` 字段 |
| ⚙️ 安全配置 | ✅ | ❌ | ❌ | ✅ | ✅ | `SecurityProfile.userId/roleId/isDefault` |
| 📋 ABAC策略 | ✅ | 🔸 | 🔸 | 🔸 | 🔸 | 条件表达式内嵌，无结构化字段 |
| 🧠 OPA引擎 | ✅ | ❌ | ❌ | ❌ | ❌ | 全量 Rego 策略加载 |
| 🎭 数据脱敏 | ✅ | ❌ | ❌ | ❌ | ❌ | 演示性质，无隔离 |

> 图例: ✅ 硬编码支持 | 🔸 条件表达式间接支持 | ❌ 不支持

---

## 二、逐模块详析

### 2.1 安全审计 — 租户 + 用户

```
作用域: 租户隔离 + 用户追溯 + 全局可查
```

**实体字段** (AuditLog.java):
```
logId, eventType, timestamp, userId, tenantId,
resource, action, result, ipAddress, userAgent, requestId, duration, details
```

**隔离逻辑**:
- 每条审计日志写入时携带 `tenantId`（来自 `TenantContextFilter` 设置的当前租户上下文）
- 查询时通过 `AuditQueryCondition` 可过滤 `tenantId` + `userId`
- 管理员可跨租户查询（不传 tenantId）

**结论**: 审计日志天然租户级隔离，但查询权限决定是否可见其他租户日志。

---

### 2.2 安全配置 — 用户 → 角色 → 全局 三级级联

```
作用域: 用户/角色/全局。无租户维度，无机构维度。
```

**实体字段** (SecurityProfile.java):
```
id, name, userId, roleId, isDefault,
clearanceLevel (0-4), auditMode, sandboxMandatory,
passwordMinLength, passwordExpireDays, mfaEnabled,
sessionTimeoutMinutes, maxConcurrentSessions, ...
```

**级联查询逻辑** (ClearanceInterceptor.java):
```
1. SELECT clearance_level FROM td_user_security_profile  WHERE user_id = ?  LIMIT 1
                         ↓ 未命中
2. SELECT MAX(rsp.clearance_level) FROM td_role_security_profile rsp
   JOIN td_user_role ur ON rsp.role_id = ur.role_id
   WHERE ur.user_id = ?
                         ↓ 未命中
3. SELECT clearance_level FROM td_user_security_profile WHERE is_default = TRUE LIMIT 1
```

**关键发现**:
- ✅ 用户级配置 — 最精细，单用户定制
- ✅ 角色级配置 — 按角色批量管理，取用户所有角色中的**最高**等级
- ✅ 全局默认 — 兜底值
- ❌ 无租户维度 — 所有租户共享同一套安全配置表
- ❌ 无机构维度 — 不支持按组织部门差异化

**影响**:
- 多租户场景下，租户A的管理员修改全局默认值会影响租户B
- 无法按「部门/机构」设置不同的准入等级（如：研发部L1 vs 财务部L4）

---

### 2.3 ABAC策略 — 全局 + 条件内嵌维度

```
作用域: 全局表。维度通过条件表达式(condition)间接支持。
```

**实体字段** (AbacPolicy.java):
```
policyId, policyName,
subjectCondition, resourceCondition, actionCondition, environmentCondition,
effect (ALLOW/DENY), priority, createdTime
```

**⚠️ 当前无结构化 scope 字段**。但条件表达式可嵌入任意维度：

| 条件字段 | 可嵌入的维度示例 |
|------|------|
| `subjectCondition` | `"role:admin"`, `"tenant:tenantA"`, `"org:dept-finance"`, `"userId:u001"` |
| `resourceCondition` | `"dataset:customer_360"`, `"region:APAC"` |
| `actionCondition` | `"READ"`, `"DELETE"` |
| `environmentCondition` | `"time:09:00-18:00"`, `"ip:10.0.0.0/8"` |

**当前限制** (AbacPepService.matches):
```java
// MVP: 简单字符串包含匹配
return value.contains(condition) || condition.contains(value);
```

- 无 JSONPath/SpEL 解析
- 不支持 `==`、`!=`、`IN` 等结构化运算符
- 所有策略全量加载，不做租户/机构预过滤

**结论**: ABAC 策略设计上**可以**表达任何维度，但当前 MVP 没有结构化的作用域隔离机制。

---

### 2.4 OPA引擎 — 纯全局

```
作用域: 全局。Rego 策略无租户/机构/用户隔离。
```

**数据流**:
```
PolicyEngineController → OPA Server (:8181) → Rego Policy Bundle
```

- 所有策略全量存储在 OPA Bundle 中
- REST API 无 scope 参数
- 策略热加载后对所有租户/用户全局生效

**结论**: OPA 引擎是全局策略执行点，适合编写跨租户的通用规则。

---

### 2.5 数据脱敏 — 纯全局演示

```
作用域: 全局。演示性质，无隔离。
```

- `GET /api/v1/data-masking/demo` — 无认证要求
- 脱敏规则纯函数，不区分租户/用户

**结论**: 当前是独立演示模块，不涉及作用域。

---

## 三、差距分析：缺失的维度

```
                    当前支持        业务需要
                    ─────────      ─────────
全局 (Global)         ✅ 全部         ✅
租户 (Tenant)         🔸 仅审计       ✅ 多租户SaaS核心需求
机构 (Organization)   ❌ 无           ✅ 企业组织架构权限管控
用户 (User)           ✅ 配置+审计    ✅
角色 (Role)           ✅ 配置         ✅
```

### 3.1 核心缺口

| 缺口 | 影响 | 优先级 |
|------|------|:--:|
| 安全配置无租户维度 | 租户A修改全局默认 → 影响租户B | P0 |
| 安全配置无机构维度 | 无法按部门差异化管控 | P1 |
| ABAC 策略无结构化 scope | 需手动在 condition 中写 `tenant:xxx`，易遗漏 | P1 |
| ABAC 策略评估无租户预过滤 | 租户A的策略可能误匹配租户B的请求 | P1 |
| OPA 无租户隔离 | 所有租户共享同一套 Rego 规则 | P2 |

### 3.2 建议补齐路径

```
Phase 1 (P0): 安全配置表加 tenant_id
  td_user_security_profile   + tenant_id (可为NULL=全局)
  td_role_security_profile   + tenant_id
  ClearanceInterceptor       查询时追加 AND tenant_id = ?

Phase 2 (P1): ABAC 策略表加 scope
  td_abac_policy  + scope_type (GLOBAL/TENANT/ORG/USER) + scope_id
  AbacPepService  评估时先按 scope 过滤

Phase 3 (P2): OPA 策略分区
  OPA Bundle 按租户拆分 → /v1/policies/{tenantId}/...
```

---

## 四、当前数据流端到端（含作用域标注）

```
请求进入 → TenantContextFilter [设置 tenantId]
  │
  ├→ ClearanceInterceptor
  │    └─ 查 td_user_security_profile [⚠️ 无 tenant 过滤]
  │       → 查 td_role_security_profile   [⚠️ 无 tenant 过滤]
  │       → 查全局默认 (is_default=TRUE)   [⚠️ 跨租户共享]
  │
  ├→ RequirePermissionAspect
  │    └─ AbacPepService.evaluate(subject, resource, action)
  │         ├─ ABAC 策略匹配 [⚠️ 全局匹配，无 scope 过滤]
  │         └─ RBAC 回退 [✅ 角色权限已有 tenant 维度]
  │
  ├→ AuditAspect
  │    └─ AuditLog 写入 [✅ tenantId 已携带]
  │
  └→ 业务 Controller
```

**红线标 ⚠️ 的环节** 都缺少租户/机构隔离，是当前架构的核心风险点。

---

## 五、结论

| 问题 | 答案 |
|------|------|
| 安全配置的作用域？ | **用户 → 角色 → 全局**。无租户、无机构 |
| ABAC 策略的作用域？ | **全局**（条件表达式可间接表达维度，但无结构化隔离） |
| 审计日志的作用域？ | **租户 + 用户**（实体有 tenantId 字段） |
| OPA 引擎的作用域？ | **全局** |
| 数据脱敏的作用域？ | **全局**（演示模块） |
| 最紧急待补齐的维度？ | **租户** — 安全配置和 ABAC 策略 |
