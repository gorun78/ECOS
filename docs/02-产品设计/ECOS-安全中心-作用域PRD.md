# PRD: ECOS 安全中心 — 作用域体系

> 版本: v1.0 | 日期: 2026-06-29 | 作者: ECOS-PMO
> 前置分析: [ECOS-安全中心-作用域分析.md](./ECOS-安全中心-作用域分析.md)

---

## 一、产品愿景

将 ECOS 安全中心从「全局扁平」升级为「**五维分层**」的作用域体系，使多租户企业级客户能够按组织架构精细管控安全策略。

```
当前:  全局(共享) → 用户/角色  (2.5维，缺租户和机构)
目标:  全局 → 租户 → 机构 → 角色 → 用户  (5维级联)
```

---

## 二、作用域模型

### 2.1 五层定义

| 层级 | 标识 | 语义 | 典型场景 |
|:--:|------|------|------|
| L0 | **GLOBAL** | 平台全局默认 | 超级管理员设置系统基线安全等级 |
| L1 | **TENANT** | 租户级 | 租户A要求所有用户至少L2准入 |
| L2 | **ORG** | 机构/部门级 | 财务部(org-001)准入L4，研发部(org-002)准入L1 |
| L3 | **ROLE** | 角色级 | 管理员角色准入L3，访客角色准入L1 |
| L4 | **USER** | 用户级 | CEO(user-001)临时提权至L5查看绝密数据 |

### 2.2 级联优先级（覆盖规则）

```
USER (L4) > ROLE (L3) > ORG (L2) > TENANT (L1) > GLOBAL (L0)
   │            │           │           │            │
   └── 最精细 ──┴───────────┴───────────┴── 最宽泛 ──┘
```

**解析规则**：查询时从 L4→L0 逐级查找，任一命中即返回，不再向下查找。

### 2.3 隔离规则

| 维度 | 隔离语义 |
|------|------|
| GLOBAL | 所有租户可见，仅超管可修改 |
| TENANT | 仅本租户内可见和生效 |
| ORG | 仅本机构及子机构可见 |
| ROLE | 仅拥有该角色的用户继承 |
| USER | 仅该用户本人 |

---

## 三、模块影响矩阵

### 3.1 安全配置 (SecurityProfile)

**当前**: `td_user_security_profile` (user_id/is_default) + `td_role_security_profile` (role_id)
**目标**: 两张表加 `tenant_id` / `org_id` / `scope_type` 字段

| 字段 | 类型 | 说明 |
|------|------|------|
| `scope_type` | VARCHAR(16) | GLOBAL / TENANT / ORG / ROLE / USER |
| `tenant_id` | VARCHAR(64) | 租户ID (scope_type=TENANT时填充) |
| `org_id` | VARCHAR(64) | 机构ID (scope_type=ORG时填充) |
| `role_id` | VARCHAR(64) | 已有，保留 |
| `user_id` | VARCHAR(64) | 已有，保留 |

**合并为单表**: `td_security_profile`（统一五维，替代两张分离表）

### 3.2 ABAC策略 (AbacPolicy)

**当前**: 全局，无结构化 scope 字段
**目标**: 加 `scope_type` / `scope_id` 字段

| 字段 | 类型 | 说明 |
|------|------|------|
| `scope_type` | VARCHAR(16) | GLOBAL / TENANT / ORG |
| `scope_id` | VARCHAR(64) | 对应ID |

**评估前预过滤**: `WHERE scope_type='GLOBAL' OR (scope_type='TENANT' AND scope_id=?)`

### 3.3 OPA引擎 (PolicyEngine)

**当前**: 纯全局
**目标**: 按租户分区 Rego Bundle（P2，暂不实施）

### 3.4 审计日志 (AuditLog)

**当前**: 已有 `tenant_id` 字段 ✅
**目标**: 无需改动

### 3.5 数据脱敏 (DataMasking)

**当前**: 演示模块
**目标**: 无需改动

---

## 四、数据库设计

### 4.1 新表: `td_security_profile`（合并用户+角色+五维）

```sql
CREATE TABLE td_security_profile (
    id              VARCHAR(64)   PRIMARY KEY,
    scope_type      VARCHAR(16)   NOT NULL,  -- GLOBAL/TENANT/ORG/ROLE/USER
    tenant_id       VARCHAR(64),             -- NULL=全局
    org_id          VARCHAR(64),             -- NULL=不区分
    role_id         VARCHAR(64),             -- NULL=不区分
    user_id         VARCHAR(64),             -- NULL=不区分
    clearance_level INT           DEFAULT 1,
    linked_workstation VARCHAR(256),
    audit_mode      VARCHAR(32)   DEFAULT 'basic',
    sandbox_mandatory BOOLEAN     DEFAULT FALSE,
    password_min_length INT       DEFAULT 8,
    password_expire_days INT      DEFAULT 90,
    mfa_enabled     BOOLEAN       DEFAULT FALSE,
    session_timeout_minutes INT   DEFAULT 30,
    max_concurrent_sessions INT   DEFAULT 3,
    description     VARCHAR(512),
    created_at      TIMESTAMP     DEFAULT NOW(),
    updated_at      TIMESTAMP     DEFAULT NOW()
);

-- 唯一约束: 同scope_type下同一维度只能有一条
CREATE UNIQUE INDEX idx_sp_scope_tenant ON td_security_profile(scope_type, tenant_id) WHERE scope_type='TENANT';
CREATE UNIQUE INDEX idx_sp_scope_org    ON td_security_profile(scope_type, org_id)    WHERE scope_type='ORG';
CREATE UNIQUE INDEX idx_sp_scope_role   ON td_security_profile(scope_type, role_id)   WHERE scope_type='ROLE';
CREATE UNIQUE INDEX idx_sp_scope_user   ON td_security_profile(scope_type, user_id)   WHERE scope_type='USER';
```

### 4.2 旧表迁移

- `td_user_security_profile` → 数据迁移到 `td_security_profile` (scope_type='USER')
- `td_role_security_profile` → 数据迁移到 `td_security_profile` (scope_type='ROLE')
- `_global_default_` 行 → scope_type='GLOBAL'
- 旧表保留但不写入（向后兼容读）

### 4.3 ABAC策略表

```sql
ALTER TABLE td_abac_policy ADD COLUMN IF NOT EXISTS scope_type VARCHAR(16) DEFAULT 'GLOBAL';
ALTER TABLE td_abac_policy ADD COLUMN IF NOT EXISTS scope_id   VARCHAR(64);
```

---

## 五、API变更

### 5.1 安全配置 API

| 端点 | 变更 |
|------|------|
| `GET /api/v1/security/profile` | 新增 `?scopeType=&scopeId=` 参数；级联查询从新表 |
| `PUT /api/v1/security/profile` | 新增 `scopeType/scopeId` 参数 |
| `GET /api/v1/security/profiles` | 返回列表加 scope 信息 |
| `GET /api/v1/security/profile/user/{id}` | 兼容保留，内部查 scope_type='USER' |
| `GET /api/v1/security/profile/role/{id}` | 兼容保留，内部查 scope_type='ROLE' |

### 5.2 ABAC策略 API

| 端点 | 变更 |
|------|------|
| `GET /api/v1/abac/policies` | 新增 `?scopeType=&scopeId=` 过滤参数 |
| `POST /api/v1/abac/policies` | Body 新增 `scopeType/scopeId` |
| `PUT /api/v1/abac/policies/{id}` | Body 新增 `scopeType/scopeId` |

### 5.3 ClearanceInterceptor

- 级联查询路径从 3 级扩展为 5 级
- 每级查询追加 `tenant_id` 过滤（从 `TenantContextFilter` 获取当前租户）

---

## 六、前端设计

### 6.1 安全配置面板 (SecurityConfigPanel.tsx)

**新增 Scope 选择器**:
```
┌─ 作用域选择 ────────────────────────────────┐
│ [全局 ▼] [────────── 选择租户/机构 ──────]   │
│                                              │
│ 当前作用域: 全局默认                          │
│ 影响范围:   所有租户的所有用户                │
└──────────────────────────────────────────────┘
```

- Dropdown: 全局 / 租户 / 机构 / 角色 / 用户
- 选中非全局时，弹出第二级选择器（租户列表/机构树/角色列表/用户搜索）
- 加载时根据当前选中作用域加载对应配置

### 6.2 ABAC策略面板 (AbacPolicyManager.tsx)

**策略列表加 scope 列**:
- 表格新增「作用域」列，显示 GLOBAL / TENANT:xxx / ORG:yyy
- 新建/编辑弹窗新增 scope 选择器

---

## 七、实施计划

| Sprint | 内容 | 估时 |
|--------|------|:--:|
| **Sprint 19a** | DB迁移: V41__security_scope.sql + 数据迁移 | 1h |
| **Sprint 19b** | 后端: SecurityProfile 模型+Controller 改造 | 2h |
| **Sprint 19c** | 后端: ClearanceInterceptor 五层级联 | 1h |
| **Sprint 19d** | 后端: AbacPolicy 加 scope + AbacPepService 过滤 | 1h |
| **Sprint 19e** | 前端: SecurityConfigPanel scope选择器 | 2h |
| **Sprint 19f** | 前端: AbacPolicyManager scope列+弹窗 | 1h |
| **Sprint 19g** | 全链路验证: curl + 种子数据 | 1h |
| **合计** | | **9h** |

---

## 八、验收标准

- [ ] 超级管理员可创建「全局默认」安全配置，所有租户继承
- [ ] 租户管理员可创建「租户级」安全配置，仅本租户用户生效
- [ ] 机构级安全配置仅影响本机构及子机构用户
- [ ] 五层级联查询: USER > ROLE > ORG > TENANT > GLOBAL
- [ ] ClearanceInterceptor 级联查询追加 tenant_id 过滤
- [ ] ABAC策略可按 scope 过滤，PEP评估时先按作用域预过滤
- [ ] 前端面板可切换作用域查看/编辑对应配置
- [ ] 旧API兼容，不破坏现有功能
- [ ] 种子数据包含各层级示例配置
