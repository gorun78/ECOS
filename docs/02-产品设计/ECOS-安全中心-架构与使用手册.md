# ECOS 安全中心 — 架构逻辑与使用手册

> 最后更新: 2026-06-29 | 来源: Sprint 17 安全中心精修

---

## 一、定位与入口

**安全中心** 是 ECOS 平台统一的「安全与审计管理控制台」，将 6 个安全子模块聚合为单一 Tab 页面。

| 项目 | 值 |
|------|-----|
| 前端路由 | `/ecos/security-center` |
| 侧边栏 | 系统管理 → 安全与审计 → **安全中心** |
| 页面入口 | `SecurityCenter.tsx` (86行 Tab 容器) |

---

## 二、6个子模块全景

```
┌─────────────────────────────────────────────────────────────────────┐
│                    ECOS 安全中心 (SecurityCenter)                     │
│  ┌─────────┬──────────┬──────────┬──────────┬──────────┬──────────┐ │
│  │安全审计  │密码审计  │安全配置  │ABAC策略  │OPA引擎   │数据脱敏  │ │
│  │ (Audit) │ (Crypto) │(Config)  │(Policy)  │(Engine)  │(Masking) │ │
│  └────┬─────┴────┬─────┴────┬─────┴────┬─────┴────┬─────┴────┬─────┘ │
│       │          │          │          │          │          │       │
└───────┼──────────┼──────────┼──────────┼──────────┼──────────┼───────┘
        │          │          │          │          │          │
   ┌────▼────┐ ┌───▼────┐ ┌───▼────┐ ┌───▼────┐ ┌───▼────┐ ┌───▼────┐
   │审计日志  │ │哈希链   │ │安全配置 │ │ABAC    │ │OPA     │ │脱敏    │
   │时间线    │ │验证    │ │面板    │ │策略CRUD│ │Rego编辑│ │演示    │
   └─────────┘ └────────┘ └────────┘ └────────┘ └────────┘ └────────┘
```

### 子模块速览

| # | Tab | 文件 | 行数 | 后端API | 功能 |
|:--:|------|------|:--:|------|------|
| 1 | 安全审计 | SecurityAudit.tsx | 259 | `/api/v1/audit/logs` | 审计日志时间线 + 参与者/操作类型洞察面板 |
| 2 | 密码审计 | CryptoAuditPanel.tsx | 458 | `/api/v1/audit/crypto/*` | 哈希链密码审计日志 + 链完整性验证 |
| 3 | 安全配置 | SecurityConfigPanel.tsx | 482 | `/api/v1/security/profile` | 准入等级/审计模式/沙箱/密码策略/会话管理 |
| 4 | ABAC策略 | AbacPolicyManager.tsx | 432 | `/api/v1/abac/policies` | 基于属性的访问控制CRUD |
| 5 | OPA引擎 | PolicyEngine.tsx | 463 | `/api/v1/policy-engine/*` | Rego策略编辑器 + OPA在线评估 |
| 6 | 数据脱敏 | DataMaskingDemo.tsx | 318 | `/api/v1/data-masking/*` | 脱敏对比示例 + 自定义文本脱敏 |

---

## 三、各模块详细逻辑

### 3.1 安全审计 (SecurityAudit)

**API**: `GET /api/v1/audit/logs?userId=&action=&resourceType=&page=`

**数据流**:
```
用户操作 → @RequirePermission AOP 拦截
         → AuditEvent 写入 audit_log 表
         → AuditController 分页查询
         → SecurityAudit.tsx 时间线渲染 + 洞察面板
```

**后端核心**: AuditController + IAuditLogService（`@Autowired(required=false)` 可选服务）

**前端逻辑**:
- 左侧（2/3宽）：审计日志时间线卡片，按时间倒序展示
- 右侧（1/3宽）：安全洞察面板，从日志数据中聚合 Top Actors / Top Actions / Recent Events
- 每条日志支持中英双语翻译（硬编码字典映射 action→中文描述）

**与外围关系**: 
- 所有需要审计的操作（用户CRUD、角色CRUD、权限变更、配置变更）都会自动生成审计事件
- 审计日志不可删除，只能查询

---

### 3.2 密码审计 (CryptoAuditPanel)

**API**: 
- `GET /api/v1/audit/crypto/logs` — 加密审计日志
- `POST /api/v1/audit/crypto/verify` — 哈希链完整性验证
- `POST /api/v1/audit/crypto/records` — 创建测试记录

**数据流**:
```
操作事件 → CryptographicAuditAspect AOP拦截
         → SHA-256 哈希链（previous_hash 链接）
         → crypto_audit_ledger 表
         → 链验证 (遍历所有区块，验证 hash=sha256(data+prev_hash))
```

**后端核心**: 
- `CryptoAuditController` — REST端点
- `CryptographicAuditAspect` — AOP切面自动拦截带 `@CryptographicAudit` 注解的方法
- `CryptoAuditLedger` — 哈希链数据结构

**前端逻辑**:
- Tab1「审计日志」：搜索框 + 表格（ID/事件类型/操作/操作人/时间/验证状态）+ 分页
- Tab2「链验证」：按钮触发全链完整性校验，返回 totalBlocks / tamperedBlocks

**关键特性**: 区块链式不可篡改 — 任何一条记录被修改都会导致后续所有区块的 hash 校验失败。

---

### 3.3 安全配置 (SecurityConfigPanel)

**API**: 
- `GET /api/v1/security/profile` — 获取当前安全配置（级联优先级）
- `PUT /api/v1/security/profile` — 更新配置

**级联优先级**: `User级配置 > Role级配置 > 全局默认 (is_default=true)`

**后端核心**: SecurityConfigController，使用 JdbcTemplate 操作三张表：
- `td_user_security_profile` (user_id)
- `td_role_security_profile` (role_id)  
- 全局默认行 (user_id=`_global_default_`)

**配置项**:

| 配置 | 类型 | 说明 |
|------|------|------|
| clearanceLevel | 1-5滑块 | 安全准入等级(L1内部→L5绝密) |
| linkedWorkstation | 文本 | 绑定物理工作站(高危操作来源限制) |
| auditMode | 单选 | 审计力度(basic/detailed/comprehensive) |
| sandboxMandatory | 开关 | 强制沙盒预执行审查 |
| passwordMinLength | 数字 | 密码最小长度 |
| mfaEnabled | 开关 | 多因素认证 |
| passwordExpireDays | 数字 | 密码过期天数 |
| sessionTimeout | 数字 | 会话超时(分钟) |
| maxConcurrentSessions | 数字 | 最大并发会话数 |

**与外围关系**:
- `@MinimumClearance(level=N)` 注解 → ClearanceInterceptor 读取此配置决定是否放行
- 密码策略 → UserController 创建用户时校验
- 会话管理 → SessionService 超时检测

---

### 3.4 ABAC策略管理 (AbacPolicyManager)

**API**: 
- `GET /api/v1/abac/policies` — 列表+搜索+分页
- `POST/PUT/DELETE` — CRUD

**数据模型**:
```
AbacPolicy {
  policyId, policyName, resource, action, effect,
  subjectCondition, resourceCondition, actionCondition,
  environmentCondition, priority
}
```

**后端核心**: AbacController + IAbacPolicyService（`@Autowired(required=false)`）

**前端逻辑**: 搜索框 + 策略表格(name/resource/action/effect/priority) + 新建/编辑弹窗 + 删除确认弹窗

**与外围关系**:
- ABAC策略在 **OPA引擎** (PolicyEngine) 中被评估
- PermissionAspect 在拦截 `@RequirePermission` 注解时可能调用ABAC策略做动态决策
- 策略优先级决定评估顺序

---

### 3.5 OPA引擎 (PolicyEngine)

**API**:
- `GET /api/v1/policy-engine/status` — OPA连接状态
- `GET /api/v1/policy-engine/policies` — 策略名称列表
- `GET /api/v1/policy-engine/policies/{name}` — Rego源码
- `PUT /api/v1/policy-engine/policies/{name}` — 更新并热加载
- `POST /api/v1/policy-engine/evaluate` — 在线评估

**架构**:
```
ECOS Gateway ←→ OPA Server (:8181, Docker)
     │               │
     │  REST API     │  Rego Policy Engine
     ▼               ▼
 PolicyEngine.tsx   OPA Policy Bundle
```

**三栏布局**:
- 左栏（256px）：策略列表（点击选中）
- 中栏（自适应）：Rego 源码编辑器（monaco风格深色文本区）
- 右栏（320px）：JSON输入 + 评估按钮 + 结果展示(ALLOW/DENY)

**工作流**:
1. 选择策略 → 加载 Rego 源码
2. 编辑 Rego → 保存（热加载到 OPA）
3. 输入 JSON → 点击「执行评估」
4. OPA 返回 ALLOW/DENY + 原始响应

**与外围关系**:
- 评估结果直接影响 PermissionAspect 的鉴权决策
- 策略热加载后所有后续 API 请求立即生效
- Docker OPA 容器通过 `docker-compose-enterprise.yml` 管理

---

### 3.6 数据脱敏 (DataMaskingDemo)

**API**:
- `GET /api/v1/data-masking/demo` — 脱敏对比示例
- `POST /api/v1/data-masking/apply` — 应用脱敏规则

**前端逻辑**:
- 左栏：脱敏对比卡片（原始数据 vs 脱敏后数据）
- 右栏：输入文本 + 选择规则标签(phone/email/id_card/bank_card/name/address) + 应用按钮 + 结果展示

**支持规则**: 手机号 / 邮箱 / 身份证 / 银行卡 / 姓名 / 地址

**与外围关系**:
- 数据脱敏是安全中心最"独立"的模块 — 纯演示性质
- 未来可与 DataNet（数据目录）集成，对敏感字段自动应用脱敏
- 脱敏规则可被 Pipeline 执行引擎调用

---

## 四、安全体系全景：模块关系图

```
                        ┌──────────────────────┐
                        │    ECOS 业务系统      │
                        │  (Agent/Pipeline/     │
                        │   DataNet/BizDash)    │
                        └──────────┬───────────┘
                                   │ 所有API请求经过
                                   ▼
┌──────────────────────────────────────────────────────────────────┐
│                        安全拦截链 (Filter Chain)                    │
│                                                                    │
│  JwtAuthFilter → TenantContextFilter → ClearanceInterceptor       │
│       │                                       │                   │
│       ▼                                       ▼                   │
│  @RequirePermission AOP ←── ABAC策略评估 ←── OPA引擎              │
│       │                    (AbacPolicy)      (PolicyEngine)       │
│       ▼                                                           │
│  ┌─────────────────────────────────────────┐                      │
│  │           安全配置 (SecurityConfig)       │                      │
│  │  准入等级 │ 审计模式 │ 沙箱 │ 密码策略    │◄── SecurityConfigPanel│
│  └─────────────────────────────────────────┘                      │
└──────────────────────────────────────────────────────────────────┘
                                   │
                    ┌──────────────┼──────────────┐
                    ▼              ▼              ▼
           ┌───────────┐  ┌───────────┐  ┌──────────────┐
           │ 审计日志   │  │ 密码审计   │  │  数据脱敏     │
           │ AuditLog  │  │ CryptoChain│  │ DataMasking  │
           │ (不可删除) │  │ (不可篡改) │  │ (规则引擎)    │
           └───────────┘  └───────────┘  └──────────────┘
```

---

## 五、如何使用（典型场景）

### 场景1：管理员配置安全策略

1. 打开 **安全中心 → 安全配置**
2. 调整准入等级滑块（L1-L5）
3. 选择审计模式（基础/详细/全面）
4. 设置密码策略（最小长度/过期天数/MFA）
5. 配置会话管理（超时/并发数）
6. 点击「保存并重连核准通道」

### 场景2：创建ABAC访问控制策略

1. 打开 **安全中心 → ABAC策略**
2. 新建策略：填写名称/资源/操作/效果/条件表达式/优先级
3. 示例: `read-customer-apac` — 资源 `dataset:customer_360`, 条件 `region == 'APAC'`

### 场景3：测试OPA策略

1. 打开 **安全中心 → OPA引擎**
2. 左侧选择策略（如 `ecos.rbac`）
3. 中间编辑器修改 Rego 代码
4. 右侧输入测试 JSON：`{"user":"alice","action":"read","resource":"documents/report-001"}`
5. 点击「执行评估」→ 查看 ALLOW/DENY 结果

### 场景4：审计溯源

1. 打开 **安全中心 → 安全审计**
2. 查看时间线上所有操作事件
3. 右侧洞察面板显示 Top Actors / Action Types

### 场景5：验证密码审计链

1. 打开 **安全中心 → 密码审计**
2. Tab2「链验证」→ 点击「开始验证」
3. 查看 totalBlocks / tamperedBlocks

### 场景6：数据脱敏测试

1. 打开 **安全中心 → 数据脱敏**
2. 输入含敏感信息的文本
3. 勾选规则(phone/email/id_card)
4. 点击「应用脱敏」→ 查看原始 vs 脱敏对比

---

## 六、后端端点汇总

| 方法 | 端点 | 模块 | 认证 |
|:--:|------|------|:--:|
| GET | `/api/v1/audit/logs` | 安全审计 | JWT |
| GET | `/api/v1/audit/crypto/logs` | 密码审计 | JWT |
| POST | `/api/v1/audit/crypto/verify` | 密码审计 | JWT |
| POST | `/api/v1/audit/crypto/records` | 密码审计 | JWT |
| GET | `/api/v1/security/profile` | 安全配置 | JWT |
| PUT | `/api/v1/security/profile` | 安全配置 | @RequirePermission |
| GET | `/api/v1/abac/policies` | ABAC策略 | JWT |
| POST | `/api/v1/abac/policies` | ABAC策略 | @RequirePermission |
| PUT | `/api/v1/abac/policies/{id}` | ABAC策略 | @RequirePermission |
| DELETE | `/api/v1/abac/policies/{id}` | ABAC策略 | @RequirePermission |
| GET | `/api/v1/policy-engine/status` | OPA引擎 | 免认证 |
| GET | `/api/v1/policy-engine/policies` | OPA引擎 | 免认证 |
| PUT | `/api/v1/policy-engine/policies/{name}` | OPA引擎 | JWT |
| POST | `/api/v1/policy-engine/evaluate` | OPA引擎 | JWT |
| GET | `/api/v1/data-masking/demo` | 数据脱敏 | 免认证 |
| POST | `/api/v1/data-masking/apply` | 数据脱敏 | JWT |

---

## 七、数据库表

| 表名 | 用途 | 模块 |
|------|------|------|
| `audit_log` | 审计事件日志 | 安全审计 |
| `crypto_audit_ledger` | 密码审计哈希链 | 密码审计 |
| `td_user_security_profile` | 用户级安全配置 | 安全配置 |
| `td_role_security_profile` | 角色级安全配置 | 安全配置 |
| `abac_policy` | ABAC策略定义 | ABAC策略 |
