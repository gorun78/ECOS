# DataBridge V2 — 安全策略 / 审计与合规 / 系统配置 使用说明书

> **版本**：v2 迁移分析  
> **日期**：2026-06-04  
> **前提**：用户管理（IAM）5个模块（用户/角色/权限/组织/菜单）已全部实现并验证通过  

---

## 一、模块总览

三组共 **10 个子模块**，全部隶属于 `sys-man` 子系统：

| 分组 | 子模块 | 后端状态 | 必要性 |
|------|--------|:------:|:------:|
| **安全策略** | ABAC策略管理 | ✅ 完整 | 🔴 必须 |
| | 数据权限策略 | ✅ 完整 | 🔴 必须 |
| | 策略引擎 | ✅ 完整 | 🔴 必须 |
| | 数据安全策略 | ✅ 完整 | 🟡 建议 |
| **审计与合规** | 审计日志查询 | ✅ 完整 | 🔴 必须 |
| | 审计统计 | ⚠️ 需实现 | 🟡 建议 |
| | 合规策略管理 | ✅ 完整 | 🟢 可选 |
| **系统配置** | 配置管理 | ✅ 完整 | 🔴 必须 |
| | 系统参数 | ✅ 完整 | 🔴 必须 |
| | IP访问控制 | ✅ 骨架 | 🟡 建议 |

> **后端状态说明**：V2 已迁移全部 Service / DAO / Entity / SQL XML 映射文件。  
> 仅缺少 Controller（REST 层）和前端页面。

---

## 二、必要性评估标准

| 级别 | 含义 | 评判依据 |
|:--:|------|---------|
| 🔴 必须 | 系统运行刚需，缺则功能链断裂 | 被其他模块硬依赖 / 安全合规强制要求 / 运维必用 |
| 🟡 建议 | 完整产品体验，可增可分阶段 | 独立功能不阻塞其他模块 / 提升产品完整度 |
| 🟢 可选 | 锦上添花，后续按需迁移 | V1 为 Mock/存根 / 实际业务暂未使用 |

---

## 三、逐模块详细说明

### 3.1 安全策略

#### 3.1.1 ABAC策略管理 🔴 必须

**是什么**：基于属性的访问控制（Attribute-Based Access Control）策略管理。不同于 RBAC 的"角色→权限"，ABAC 通过 **主体属性 + 资源属性 + 环境属性 + 动作** 四个维度动态评估访问决策。

**数据库**：`td_abac_policy`
```
POLICY_ID   — 策略ID（PK）
POLICY_NAME — 策略名称
POLICY_RULE — 策略规则（JSON表达式）
RESOURCE_ID — 目标资源
ACTION      — 操作类型（READ/WRITE/DELETE/EXECUTE）
STATUS      — 状态（ACTIVE/INACTIVE）
```

**使用场景**：
- **用户管理模块**：当用户的 RBAC 角色权限不足以覆盖细粒度场景时，ABAC 提供补充 —— 例如"只允许在工作时间（环境属性）访问客户手机号（资源属性）、且用户级别≥P3（主体属性）"
- **数据权限策略**（3.1.2）的策略评估引擎就是 ABAC 的策略决策点
- **审计日志**（3.2.1）使用 ABAC 上下文记录 "谁在什么条件下访问了什么"

**V2 关键类**：
```
api:  AbacPolicy, AbacContext, IAbacPolicyService, IAbacPermissionChecker
impl: AbacPolicyServiceImpl, AbacPermissionCheckerImpl, InMemoryAbacPolicyCacheService
dao:  AbacPolicyDao → AbacPolicyDaoImpl
```

**必做**：创建 Controller + 前端 CRUD 页面（策略的增删改查、启用/停用）。

---

#### 3.1.2 数据权限策略 🔴 必须

**是什么**：针对数据层面的行级/列级/动态权限控制。在用户通过 RBAC+ABAC 拿到"能看这张表"的权限后，数据权限策略进一步限制"能看这张表的哪些行、哪些列"。

**数据库**：`td_data_permission_policy`
```
POLICY_ID     — 策略ID
POLICY_NAME   — 策略名称
POLICY_TYPE   — 策略类型（ROW_LEVEL / COLUMN_LEVEL / DYNAMIC）
RESOURCE_ID   — 目标资源（表/视图/API）
POLICY_RULE   — 策略规则（行过滤条件 / 列掩码规则）
```

**使用场景**：
- **用户管理模块**：管理员查看用户列表时，"HR 部门的经理只能看到自己部门的员工"——这就是行级安全策略
- **数据权限策略页面**上配置的规则，在用户查询 TD_USER 表时被 `RowLevelSecurityService` 拦截并改写 SQL（追加 WHERE 条件）
- **列级安全**：敏感字段（手机号、身份证）在列表中被 `DataMaskingService` 自动脱敏显示

**V2 关键类**：
```
api:  DataPermissionPolicy, IDataPermissionPolicyService, IDataPermissionEnforcer
      RowLevelSecurityService, ColumnLevelSecurityService, DataMaskingService
impl: DataPermissionPolicyServiceImpl, DataPermissionEnforcerImpl
      RowLevelSecurityServiceImpl, ColumnLevelSecurityServiceImpl, DataMaskingServiceImpl
dao:  DataPermissionPolicyDao
```

**必做**：创建 Controller + 前端 CRUD 页面（策略增删改查、策略类型筛选）。

---

#### 3.1.3 策略引擎 🔴 必须

**是什么**：XACML 风格策略评估引擎的核心管道 —— PAP（策略管理点）→ PDP（策略决策点）→ PEP（策略执行点）→ PIP（策略信息点）。这是 ABAC + 数据权限策略的**运行时执行器**。

**没有独立数据库表**，它消费 ABAC 策略和数据权限策略的数据，运行时产生决策。

**使用场景**：
- 每次用户访问受保护的资源时，PEP 拦截请求 → PDP 评估策略 → PIP 补充属性 → PAP 提供策略库 → 返回 PERMIT/DENY
- **ABAC策略管理**页面上新增/修改的策略，通过 PAP 提交到引擎的策略仓库
- **审计日志**记录每次 PDP 的决策结果（PERMIT/DENY/INDETERMINATE）

**V2 关键类**：
```
api:  PAP, PDP, PEP, PIP, AccessRequest, PolicyContext, PolicyDecision
      PolicyAdministrationPoint, PolicyDecisionPoint, PolicyEnforcementPoint, PolicyInformationPoint
impl: PAPImpl, PDPImpl, PEPImpl, PIPImpl
      PolicyAdministrationPointImpl, PolicyDecisionPointImpl, PolicyEnforcementPointImpl, PolicyInformationPointImpl
      ParallelPolicyEvaluator, InMemoryDecisionCacheService
```

**必做**：创建 Controller（至少暴露策略评估测试接口 + 决策缓存监控）+ 前端监控页面（策略命中率、缓存状态、近期决策日志）。

---

#### 3.1.4 数据安全策略 🟡 建议

**是什么**：数据分类分级、DLP（数据防泄漏）、完整性校验、加密策略的综合管理。

**数据库**：`td_data_security_policy`
```
policy_id      — 策略ID
policy_name    — 策略名称
policy_type    — 策略类型（CLASSIFICATION/DLP/INTEGRITY/ENCRYPTION）
policy_content — 策略内容（JSON）
scope/scope_id — 作用范围
priority       — 优先级
enabled        — 是否启用
```

**使用场景**：
- 数据治理模块（dc-cheng）的数据分类打标结果在此处生效
- 数据共享/导出的 DLP 扫描规则（如"包含身份证号的数据不允许导出"）

**V1 状态**：DataSecurityController 有完整的内存级 CRUD（ConcurrentHashMap），但未持久化到数据库。V2 已有 DAO 层可实现持久化。

**建议**：分阶段 —— 第一版提供只读列表页 + 启用/停用，第二版完整 CRUD。

---

### 3.2 审计与合规

#### 3.2.1 审计日志查询 🔴 必须

**是什么**：记录系统中所有敏感操作的完整审计轨迹 —— 谁、什么时候、做了什么、结果如何。

**数据库**：`td_audit_log`
```
LOG_ID          — 日志ID（PK）
USER_ID         — 操作用户
USERNAME        — 用户名
ACTION          — 操作类型（LOGIN/LOGOUT/CREATE/UPDATE/DELETE/EXPORT/...）
RESOURCE_TYPE   — 资源类型（USER/ROLE/PERMISSION/CONFIG/...）
RESOURCE_ID     — 资源ID
OPERATION_RESULT — 操作结果（SUCCESS/FAIL）
IP_ADDRESS      — 客户端IP
USER_AGENT      — 浏览器/客户端信息
REQUEST_DATA    — 请求参数快照
RESPONSE_DATA   — 响应数据快照
ERROR_MESSAGE   — 错误信息
CREATED_TIME    — 操作时间
```

**使用场景**：
- 用户管理模块每次增删改用户/角色/权限时自动写入审计日志
- **角色管理**页面的变更、**权限管理**页面的授权/收回操作都需要审计追踪
- 安全事件调查：通过 USER_ID + 时间范围 + ACTION 组合查询定位问题操作
- 合规审计：导出审计日志给第三方审计机构

**V2 关键类**：
```
api:  AuditLog, AuditEvent, IAuditLogService, AuditQueryCondition, AuditStatistics
impl: AuditLogServiceImpl
dao:  AuditLogDao → AuditLogDaoImpl
```

**必做**：创建 Controller（多条件查询 + 分页）+ 前端查询页面（高级筛选 + 详情弹窗）。

---

#### 3.2.2 审计统计 🟡 建议

**是什么**：对审计日志做聚合分析 —— 按用户、操作类型、资源类型、时间段统计操作频率和趋势。

**无独立表**，聚合查询 `td_audit_log`。

**使用场景**：
- 日报/周报："本周用户操作 Top10"、"失败操作趋势图"
- 异常检测：某用户短时间内大量失败操作 → 提示暴力破解风险

**V1 状态**：AuditController 有 `/logs/statistics` 端点但标注 `TODO: 实现审计统计`。

**建议**：基于已有 IAuditLogService 接口实现统计查询，前端 Dashboard 嵌入统计图表。

---

#### 3.2.3 合规策略管理 🟢 可选

**是什么**：数据合规扫描策略（GDPR/个人信息保护法/等保）以及跨境数据传输管控。

**数据库**：`td_compliance_policy`
```
POLICY_ID    — 策略ID
POLICY_NAME  — 策略名称
POLICY_TYPE  — 策略类型（DATA_RETENTION/CROSS_BORDER/PII/CONSENT）
POLICY_RULE  — 策略规则（JSON）
```

**使用场景**：
- 数据治理模块扫描数据资产时检查是否命中合规策略
- 数据共享/跨境传输前做合规校验

**V1 状态**：ICompliancePolicyService 接口已定义但实现是存根。

**建议**：暂不迁移前端页面，保留后端接口供未来扩展。数据治理模块（dc-cheng）迁移时再联动启用。

---

### 3.3 系统配置

#### 3.3.1 配置管理 🔴 必须

**是什么**：应用运行时配置的集中管理 —— 支持版本管理、环境隔离、热更新、Git 同步。

**数据库**：`td_config` (+ `td_config_version` 版本表)
```
CONFIG_ID     — 配置ID
CONFIG_KEY    — 配置键（唯一）
CONFIG_VALUE  — 配置值
CONFIG_TYPE   — 配置类型（STRING/NUMBER/BOOLEAN/JSON/LIST）
ENVIRONMENT   — 环境标识（DEV/TEST/PROD）
DESCRIPTION   — 配置说明
```

**使用场景**：
- **系统参数**（3.3.2）的底层存储就是 `td_config` 表，参数管理页面的增删改查实际操作的就是配置项
- 各业务模块读取配置：`IConfigService.get("feature.newDashboard.enabled")` → `true`
- **配置版本管理**：每次修改生成一条版本记录，支持回滚
- **热更新**：`ConfigHotUpdateService` 监听配置变更事件，自动刷新各模块缓存
- **Git 同步**：`ConfigGitSyncService` 将配置推送到 Git 仓库做备份和审计

**V2 关键类**：
```
api:  Config, ConfigVersion, IConfigService, IConfigManagementService
      IConfigVersionService, IConfigValidator, IConfigGitSyncService
      GitService, GitRepositoryService, GitHistoryService
impl: ConfigServiceImpl, ConfigManagementServiceImpl, ConfigVersionServiceImpl
      ConfigValidatorImpl, ConfigHotUpdateService, ConfigGitSyncServiceImpl
```

**必做**：创建 Controller + 前端管理页面（配置增删改查、环境切换、版本对比）。

---

#### 3.3.2 系统参数 🔴 必须

**是什么**：系统级参数（功能开关、阈值、限制值）的统一管理入口。这是 **配置管理的业务视图**——对管理员来说操作的是"系统参数"，底层存到 `td_config` 表。

**无独立表**，复用 `td_config` 表（通过 CONFIG_TYPE 区分）。

**使用场景**：
- 功能开关：`feature.enableAdvancedSearch` → 控制前端是否显示高级搜索
- 安全限制：`security.maxLoginAttempts` → 用户管理模块在登录失败计数时读取
- 数据限制：`data.maxExportRows` → 导出功能的分页大小上限
- 会话配置：`session.timeoutMinutes` → Token 过期时间

**V1 状态**：SystemConfigController 有 `/params` 端点但返回硬编码 Mock 数据。V2 已有 SystemParam 实体 + ISystemParamService + DAO。

**必做**：创建 Controller（参数 CRUD + 搜索）+ 前端页面。这是 **被其他模块依赖最多的配置入口**。

---

#### 3.3.3 IP访问控制 🟡 建议

**是什么**：IP 白名单/黑名单管理，控制哪些 IP 地址可以访问系统。

**数据库**：`td_ip_access`
```
ACCESS_ID    — ID
IP_ADDRESS   — IP地址（支持 CIDR）
ACCESS_TYPE  — 类型（WHITELIST/BLACKLIST）
DESCRIPTION  — 描述
STATUS       — 状态（0=禁用, 1=启用）
```

**使用场景**：
- 管理后台限制仅办公网段可访问
- API 接口限制第三方服务 IP

**V1 状态**：SystemConfigController 有完整 CRUD 端点但全部标注 `功能暂未实现`。V2 已有 IPAccess 实体 + IIPAccessService。

**建议**：创建基础 Controller + 简单列表页（IP 增删改查 + 启用/停用）。

---

## 四、与已实现模块的关联关系

```
                    ┌──────────────────────┐
                    │   用户管理 (已实现)    │
                    │ 用户/角色/权限/组织/菜单│
                    └──────┬───────────────┘
                           │
          ┌────────────────┼─────────────────┐
          │                │                  │
          ▼                ▼                  ▼
┌─────────────────┐ ┌──────────────┐ ┌────────────────┐
│  ABAC策略管理    │ │数据权限策略   │ │  审计日志查询    │
│ (补充权限模型)   │ │(行/列级细控)  │ │ (操作全记录)    │
└────────┬────────┘ └──────┬───────┘ └───────┬────────┘
         │                 │                  │
         └────────┬────────┘                  │
                  │                           │
                  ▼                           │
         ┌────────────────┐                   │
         │   策略引擎       │◄─────────────────┘(记录决策)
         │ (统一决策执行)   │
         └────────────────┘

┌─────────────────────────────────────────────┐
│               系统配置                        │
│  ┌──────────┐  ┌──────────┐  ┌───────────┐  │
│  │ 配置管理  │  │ 系统参数  │  │ IP访问控制  │  │
│  │(底层存储) │  │(管理入口) │  │(网络层安全) │  │
│  └──────────┘  └──────────┘  └───────────┘  │
│          ↑ 被各模块运行时读取 ↑                │
└─────────────────────────────────────────────┘
```

### 典型数据流

1. **用户登录** → 审计日志写入 (`LOGIN` 事件) → `td_audit_log`
2. **管理员分配角色** → 审计日志 (`UPDATE` USER_ROLE) + 权限缓存刷新
3. **用户访问数据表** → PEP 拦截 → PDP 评估 ABAC 策略 + 数据权限策略 → PERMIT/DENY → 审计日志记录决策
4. **功能开关变更** → 系统参数页修改 → `td_config` 更新 → `ConfigHotUpdateService` 推送变更事件 → 各模块刷新本地配置缓存

---

## 五、迁移实施计划

| 阶段 | 模块 | 工作内容 | 预估 |
|:--:|------|---------|:--:|
| **1** | ABAC策略管理 | Controller + 前端 CRUD 页 | 即时 |
| **1** | 数据权限策略 | Controller + 前端 CRUD 页 | 即时 |
| **1** | 策略引擎 | Controller（监控视图） + 前端仪表板 | 即时 |
| **2** | 审计日志查询 | Controller + 前端查询/筛选页 | 即时 |
| **2** | 配置管理 | Controller + 前端管理页 | 即时 |
| **2** | 系统参数 | Controller + 前端管理页 | 即时 |
| **3** | 数据安全策略 | Controller（只读） + 前端列表页 | 后续 |
| **3** | 审计统计 | Controller + Dashboard 图表 | 后续 |
| **3** | IP访问控制 | Controller + 前端简单页 | 后续 |
| — | 合规策略管理 | 暂不迁移，保留后端 | 后续 |

---

## 六、未实现 Controller 占位管理

以下 Controller 使用轻量实现（与 IAM 模块 Controller 同模式），API 前缀统一为 `/sys-man/api/`：

| 功能 | API 端点 | Controller 类 |
|------|---------|--------------|
| ABAC策略 CRUD | `/api/v1/abac/policies` | `AbacController` |
| 数据权限 CRUD | `/api/v1/data-permission/policies` | `DataPermissionController` |
| 策略引擎监控 | `/api/v1/policy-engine/*` | `PolicyEngineController` |
| 数据安全策略 | `/api/v1/data-security/policies` | `DataSecurityController` |
| 审计日志查询 | `/api/v1/audit/logs` | `AuditController` |
| 合规策略 | `/api/v1/compliance/policies` | `ComplianceController` |
| 配置管理 | `/api/system/config/items` | `ConfigController` |
| 系统参数 | `/api/system/config/params` | `SystemParamController` |
| IP访问控制 | `/api/system/config/ip-access` | `IPAccessController` |

所有 Controller 返回统一 `ApiResponse` 格式：`{ code: 0, data: ..., message: "success" }`
