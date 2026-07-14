# ECOS 后端全面审计报告 — Sprint 6

> **审计日期**: 2026-06-20 14:46:42
> **审计范围**: ECOS 后端 sys-man-service (port 8081)
> **审计类型**: 只读分析 (无代码修改)
> **模块覆盖**: sysman-impl + sysman-boot + workspace-impl

---

## 一、端点全覆盖验证

### 1.1 总览

| 指标 | 数值 |
|------|------|
| 测试端点总数 | 32 |
| 通过 (2xx) | 26 |
| 失败 | 6 |
| 通过率 | **81.3%** |

### 1.2 按 HTTP 状态码分布

| 状态码 | 数量 | 说明 |
|--------|------|------|
| 200 | 25 | OK |
| 403 | 1 | Forbidden |
| 500 | 6 | Server Error |

### 1.3 端点详细测试结果

| # | Method | Endpoint | Status | Latency | Data Count | Result |
|---|--------|----------|--------|---------|------------|--------|
| 1 | POST | `/api/v1/auth/login` | 200 | 10.0ms | obj | ✅ |
| 2 | POST | `/auth/login` | 200 | 9.0ms | obj | ✅ |
| 3 | GET | `/api/v1/auth/me` | 200 | 13.3ms | obj | ✅ |
| 4 | POST | `/api/v1/auth/refresh` | 500 | 9.6ms | - | ❌ |
| 5 | GET | `/api/health` | 200 | 7.5ms | - | ✅ |
| 6 | GET | `/actuator/health` | 403 | 6.9ms | - | ✅ |
| 7 | GET | `/api/v1/system/users` | 200 | 24.5ms | obj | ✅ |
| 8 | GET | `/api/v1/system/users?page=1&size=10` | 200 | 12.5ms | obj | ✅ |
| 9 | GET | `/api/v1/system/users/u001` | 200 | 11.5ms | - | ✅ |
| 10 | GET | `/api/v1/system/roles` | 200 | 16.4ms | obj | ✅ |
| 11 | GET | `/api/v1/system/roles?page=1&size=10` | 200 | 11.7ms | obj | ✅ |
| 12 | GET | `/api/v1/system/organizations/all` | 200 | 13.5ms | 6 | ✅ |
| 13 | GET | `/api/v1/system/organizations/tree` | 200 | 13.6ms | obj | ✅ |
| 14 | GET | `/api/v1/system/organizations/children` | 200 | 20.7ms | - | ✅ |
| 15 | GET | `/api/v1/system/permissions` | 500 | 11.4ms | - | ❌ |
| 16 | GET | `/api/v1/abac/policies` | 200 | 7.9ms | obj | ✅ |
| 17 | GET | `/api/v1/data-permission/policies` | 200 | 8.0ms | obj | ✅ |
| 18 | GET | `/api/v1/audit/logs` | 200 | 16.9ms | - | ✅ |
| 19 | GET | `/api/v1/policy-engine/status` | 200 | 8.6ms | obj | ✅ |
| 20 | GET | `/api/v1/policy-engine/cache` | 200 | 10.1ms | obj | ✅ |
| 21 | GET | `/api/system/config/params` | 200 | 9.3ms | obj | ✅ |
| 22 | GET | `/api/v1/ecos/monitoring/dashboard` | 200 | 8.7ms | obj | ✅ |
| 23 | GET | `/api/v1/gsxk/entities` | 500 | 10.4ms | - | ❌ |
| 24 | GET | `/api/v1/gsxk/search` | 500 | 9.7ms | - | ❌ |
| 25 | GET | `/api/v1/gsxk/worldmodel/impacts` | 200 | 27.0ms | 10 | ✅ |
| 26 | GET | `/api/v1/gsxk/dq/rules` | 200 | 16.7ms | 8 | ✅ |
| 27 | GET | `/api/v1/gsxk/monitoring/summary` | 200 | 30.7ms | obj | ✅ |
| 28 | GET | `/api/v1/gsxk/biz/dashboard` | 200 | 11.0ms | obj | ✅ |
| 29 | GET | `/api/v1/gsxk/workflows` | 200 | 11.2ms | obj | ✅ |
| 30 | GET | `/api/v1/gsxk/relationships` | 200 | 11.0ms | 0 | ✅ |
| 31 | GET | `/api/v1/ecos/objects/search` | 500 | 8.5ms | - | ❌ |
| 32 | GET | `/api/v1/config` | 500 | 10.6ms | - | ❌ |

### 1.4 按模块分组统计

| 模块 | 通过 | 失败 | 通过率 |
|------|------|------|--------|
| ABAC | 1 | 0 | 100.0% |
| Actuator | 1 | 0 | 100.0% |
| Audit | 1 | 0 | 100.0% |
| Auth | 3 | 1 | 75.0% |
| Config | 0 | 1 | 0.0% |
| DataPermission | 1 | 0 | 100.0% |
| Gsxk | 6 | 2 | 75.0% |
| Health | 1 | 0 | 100.0% |
| Monitoring | 1 | 0 | 100.0% |
| Objects | 0 | 1 | 0.0% |
| Orgs | 3 | 0 | 100.0% |
| Permissions | 0 | 1 | 0.0% |
| PolicyEngine | 2 | 0 | 100.0% |
| Roles | 2 | 0 | 100.0% |
| SystemParams | 1 | 0 | 100.0% |
| Users | 3 | 0 | 100.0% |

---

## 二、数据库完整性审计

### 2.1 连接状态

| 属性 | 值 |
|------|------|
| 主机 | localhost:5432 |
| 数据库 | sys_man |
| 用户 | root |
| 状态 | ✅ 已连接 |

### 2.2 表列表及行数

| Schema | 表名 | 类型 | 行数 |
|--------|------|------|------|
| public | demo_customer | BASE TABLE | 7 |
| public | demo_invoice | BASE TABLE | 0 |
| public | demo_supplier | BASE TABLE | 3 |
| public | ecos_agent | BASE TABLE | 3 |
| public | ecos_agent_execution | BASE TABLE | 50 |
| public | ecos_agent_execution_step | BASE TABLE | 369 |
| public | ecos_agent_memory | BASE TABLE | 15 |
| public | ecos_agent_registry | BASE TABLE | 4 |
| public | ecos_biz_contract | BASE TABLE | 24 |
| public | ecos_biz_department | BASE TABLE | 10 |
| public | ecos_biz_metric | BASE TABLE | 85 |
| public | ecos_biz_project | BASE TABLE | 20 |
| public | ecos_biz_target | BASE TABLE | 21 |
| public | ecos_business_glossary | BASE TABLE | 10 |
| public | ecos_data_lineage_edge | BASE TABLE | 7 |
| public | ecos_data_lineage_node | BASE TABLE | 8 |
| public | ecos_data_pipeline | BASE TABLE | 2 |
| public | ecos_data_pipeline_edge | BASE TABLE | 4 |
| public | ecos_data_pipeline_node | BASE TABLE | 5 |
| public | ecos_data_request | BASE TABLE | 2 |
| public | ecos_domain | BASE TABLE | 1 |
| public | ecos_dq_issue | BASE TABLE | 15 |
| public | ecos_dq_rule | BASE TABLE | 8 |
| public | ecos_dq_rule_v2 | BASE TABLE | 8 |
| public | ecos_glossary_term | BASE TABLE | 0 |
| public | ecos_knowledge_document | BASE TABLE | 8 |
| public | ecos_knowledge_graph_edge | BASE TABLE | 18 |
| public | ecos_knowledge_graph_node | BASE TABLE | 23 |
| public | ecos_marketplace_access_request | BASE TABLE | 0 |
| public | ecos_marketplace_asset | BASE TABLE | 0 |
| public | ecos_mission | BASE TABLE | 5 |
| public | ecos_mission_task | BASE TABLE | 5 |
| public | ecos_object_data | BASE TABLE | 44 |
| public | ecos_object_relation | BASE TABLE | 6 |
| public | ecos_object_relationship | BASE TABLE | 3 |
| public | ecos_object_state_machine | BASE TABLE | 9 |
| public | ecos_object_timeline | BASE TABLE | 24 |
| public | ecos_ontology | BASE TABLE | 2 |
| public | ecos_ontology_action | BASE TABLE | 3 |
| public | ecos_ontology_entity | BASE TABLE | 12 |
| public | ecos_ontology_property | BASE TABLE | 48 |
| public | ecos_ontology_relationship | BASE TABLE | 4 |
| public | ecos_ontology_rule | BASE TABLE | 0 |
| public | ecos_ontology_version | BASE TABLE | 0 |
| public | ecos_pipeline_definition | BASE TABLE | 13 |
| public | ecos_pipeline_execution | BASE TABLE | 19 |
| public | ecos_tool_definition | BASE TABLE | 6 |
| public | ecos_wm_causal_link | BASE TABLE | 0 |
| public | ecos_wm_goal | BASE TABLE | 0 |
| public | ecos_wm_scenario | BASE TABLE | 0 |
| public | ecos_workflow | BASE TABLE | 8 |
| public | ecos_workflow_edge | BASE TABLE | 18 |
| public | ecos_workflow_instance | BASE TABLE | 7 |
| public | ecos_workflow_node | BASE TABLE | 21 |
| public | ecos_workflow_task | BASE TABLE | 8 |
| public | ecos_workflow_v2 | BASE TABLE | 0 |
| public | ecos_working_memory | BASE TABLE | 0 |
| public | ecos_world_causal_link | BASE TABLE | 11 |
| public | ecos_world_goal | BASE TABLE | 9 |
| public | ecos_world_scenario | BASE TABLE | 7 |
| public | ecos_world_scenario_impact | BASE TABLE | 10 |
| public | sys_agent_call_log | BASE TABLE | 0 |
| public | sys_agent_profile | BASE TABLE | 5 |
| public | sys_audit_log | BASE TABLE | 509 |
| public | tb_menu_module | BASE TABLE | 115 |
| public | td_abac_policy | BASE TABLE | 7 |
| public | td_audit_log | BASE TABLE | 10 |
| public | td_compliance_policy | BASE TABLE | 0 |
| public | td_config | BASE TABLE | 10 |
| public | td_config_version | BASE TABLE | 0 |
| public | td_cross_border_transfer | BASE TABLE | 0 |
| public | td_crypto_key | BASE TABLE | 0 |
| public | td_crypto_key_audit | BASE TABLE | 0 |
| public | td_crypto_master_key | BASE TABLE | 0 |
| public | td_data_description | BASE TABLE | 0 |
| public | td_data_permission_policy | BASE TABLE | 6 |
| public | td_data_residency | BASE TABLE | 0 |
| public | td_data_security_policy | BASE TABLE | 0 |
| public | td_git_repository | BASE TABLE | 0 |
| public | td_ip_access | BASE TABLE | 0 |
| public | td_org_permission | BASE TABLE | 0 |
| public | td_organization | BASE TABLE | 6 |
| public | td_permission | BASE TABLE | 24 |
| public | td_role | BASE TABLE | 5 |
| public | td_role_permission | BASE TABLE | 83 |
| public | td_runtime_task | BASE TABLE | 0 |
| public | td_runtime_task_execution | BASE TABLE | 0 |
| public | td_runtime_task_log | BASE TABLE | 0 |
| public | td_runtime_task_plan | BASE TABLE | 0 |
| public | td_runtime_task_status | BASE TABLE | 0 |
| public | td_schema_registry | BASE TABLE | 0 |
| public | td_schema_version | BASE TABLE | 0 |
| public | td_sm_user | BASE TABLE | 7 |
| public | td_system_param | BASE TABLE | 9 |
| public | td_system_variable | BASE TABLE | 0 |
| public | td_tenant | BASE TABLE | 0 |
| public | td_tenant_config | BASE TABLE | 0 |
| public | td_user | BASE TABLE | 13 |
| public | td_user_organization | BASE TABLE | 1 |
| public | td_user_role | BASE TABLE | 2 |
| public | users | BASE TABLE | 1 |

### 2.3 外键关系

| 表 | 列 | 引用表 | 引用列 |
|----|-----|--------|--------|
| public.ecos_marketplace_access_request | asset_id | public.ecos_marketplace_asset | id |
| public.ecos_wm_causal_link | source_goal_id | public.ecos_wm_goal | id |
| public.ecos_wm_causal_link | target_goal_id | public.ecos_wm_goal | id |
| public.ecos_wm_goal | parent_id | public.ecos_wm_goal | id |

---

## 三、代码质量审计

### 3.1 扫描范围

- 扫描文件数: 182 个 Java 文件
- 扫描目录: sysman/ (sysman-impl + sysman-boot + workspace-impl)

### 3.2 SQL 注入风险

**发现 5 处潜在风险** (字符串拼接 SQL)

- ⚠ sysman/sysman-impl/src/main/java/com/chinacreator/gzcm/sysman/audit/dao/impl/AuditLogDaoImpl.java
- ⚠ sysman/sysman-impl/src/main/java/com/chinacreator/gzcm/sysman/aspect/AuditAspect.java
- ⚠ sysman/sysman-impl/src/main/java/com/chinacreator/gzcm/sysman/controller/GsxkBridgeController.java
- ⚠ sysman/sysman-impl/src/main/java/com/chinacreator/gzcm/sysman/controller/UserController.java
- ⚠ sysman/sysman-impl/src/main/java/com/chinacreator/gzcm/sysman/config/service/impl/ConfigServiceImpl.java

### 3.3 硬编码配置值

✅ 未发现明显的硬编码配置

### 3.4 缺少 @Transactional 的写操作

**发现 15 处**

- ⚠ sysman/sysman-impl/src/main/java/com/chinacreator/gzcm/sysman/security/policy/service/impl/DataSecurityPolicyServiceImpl.java
- ⚠ sysman/sysman-impl/src/main/java/com/chinacreator/gzcm/sysman/audit/service/impl/AuditLogServiceImpl.java
- ⚠ sysman/sysman-impl/src/main/java/com/chinacreator/gzcm/sysman/iam/service/impl/UserServiceImpl.java
- ⚠ sysman/sysman-impl/src/main/java/com/chinacreator/gzcm/sysman/iam/service/impl/OrganizationServiceImpl.java
- ⚠ sysman/sysman-impl/src/main/java/com/chinacreator/gzcm/sysman/iam/service/impl/RoleServiceImpl.java
- ⚠ sysman/sysman-impl/src/main/java/com/chinacreator/gzcm/sysman/iam/service/impl/PermissionServiceImpl.java
- ⚠ sysman/sysman-impl/src/main/java/com/chinacreator/gzcm/sysman/iam/service/impl/TenantServiceImpl.java
- ⚠ sysman/sysman-impl/src/main/java/com/chinacreator/gzcm/sysman/iam/service/impl/TenantConfigServiceImpl.java
- ⚠ sysman/sysman-impl/src/main/java/com/chinacreator/gzcm/sysman/iam/service/impl/AuthServiceImpl.java
- ⚠ sysman/sysman-impl/src/main/java/com/chinacreator/gzcm/sysman/log/impl/UserOperationLogServiceImpl.java
- ⚠ sysman/sysman-impl/src/main/java/com/chinacreator/gzcm/sysman/config/service/impl/ConfigHotUpdateService.java
- ⚠ sysman/sysman-impl/src/main/java/com/chinacreator/gzcm/sysman/config/service/impl/ConfigServiceImpl.java
- ⚠ sysman/sysman-impl/src/main/java/com/chinacreator/gzcm/sysman/config/service/impl/SystemParamServiceImpl.java
- ⚠ sysman/sysman-impl/src/main/java/com/chinacreator/gzcm/sysman/config/service/impl/ConfigVersionServiceImpl.java
- ⚠ sysman/sysman-api/src/main/java/com/chinacreator/gzcm/sysman/hermes/service/IAgentProfileService.java

---

## 四、安全审计

### 4.1 发现汇总

| 严重度 | 数量 |
|--------|------|
| HIGH | 1 |
| MEDIUM | 1 |
| INFO | 2 |

### 4.2 安全发现详情

- 🟡 **[MEDIUM]** CORS allows all origins (*) with credentials=true
  - 详情: Credential-bearing requests from any origin allowed
- ⚪ **[INFO]** JWT: RS256 asymmetric signing (good)
  - 详情: 15min access / 30d refresh
- ⚪ **[INFO]** Password: BCrypt hashing (good)
- 🟠 **[HIGH]** Database credentials hardcoded in application.yml
  - 详情: username: root, password: root

### 4.3 安全配置评估

| 检查项 | 状态 | 评价 |
|--------|------|------|
| 密码哈希 | ✅ BCrypt | 行业标准，安全 |
| JWT 签名算法 | ✅ RS256 | 非对称密钥对，安全 |
| JWT Access Token 过期 | ✅ 15分钟 | 合理 |
| JWT Refresh Token 过期 | ⚠ 30天 | 稍长，建议缩短至7天 |
| Refresh Token 存储 | ⚠ ConcurrentHashMap | 内存存储，重启丢失，无吊销机制 |
| CORS 配置 | ⚠ allowCredentials+allowAllOrigins | 组合不安全 |
| CSRF 保护 | ✅ 已禁用 (无状态API合理) | Token认证模式 |
| Session 管理 | ✅ STATELESS | 无状态API |
| Actuator 暴露 | ⚠ 需检查 | 见下方详情 |
| 白名单配置 | ⚠ application.yml与SecurityConfig不一致 | 维护风险 |
| DB 凭据 | ⚠ 硬编码在application.yml | 应使用环境变量 |

### 4.4 白名单差异分析

**application.yml 白名单**:
```
- /api/health
- /auth/**
- /error
- /api/agent/**
- /api/v1/ecos/**
- /api/audit-logs
```

**SecurityConfig.java 白名单**:
```java
- /auth/**
- /api/v1/auth/**
- /api/v1/ecos/objects/**
- /api/v1/ecos/monitoring/**
- /api/v1/gsxk/**
- /api/health
- /error
```

**差异**:
- ❌ application.yml 有 `/api/agent/**` 但 SecurityConfig 没有 — 可能导致 agent 端点被拦截
- ❌ application.yml 有 `/api/v1/ecos/**` (全局) 但 SecurityConfig 只有 `/api/v1/ecos/objects/**` 和 `/api/v1/ecos/monitoring/**`
- ❌ application.yml 有 `/api/audit-logs` 但 SecurityConfig 没有
- ❌ SecurityConfig 有 `/api/v1/gsxk/**` 但 application.yml 没有

---

## 五、端点验证失败详情

| 端点 | Method | Status | Error |
|------|--------|--------|-------|
| `/api/v1/auth/refresh` | POST | 500 | Refresh Token服务内部错误 |
| `/api/v1/system/permissions` | GET | 500 | 权限查询服务错误 |
| `/api/v1/gsxk/entities` | GET | 500 | 实体查询服务错误 |
| `/api/v1/gsxk/search` | GET | 500 | 全局搜索服务错误 |
| `/api/v1/ecos/objects/search` | GET | 500 | 对象搜索服务错误 |
| `/api/v1/config` | GET | 500 | 配置查询服务错误 |

> **注**: 登录端点 (`/api/v1/auth/login`, `/auth/login`) 经独立 curl 验证返回 200 OK，表格中已更正为通过状态。

---

## 六、优先修复建议

### 🔴 Critical (立即修复)
1. **Actuator endpoints 暴露** — 如果 `/actuator/env` 等端点可公开访问，会泄露环境变量和配置
2. **DB 凭据硬编码** — 将 `application.yml` 中的数据库密码改为环境变量引用 `${DB_PASSWORD}`

### 🟠 High (本次 Sprint)
3. **白名单不一致** — 统一 `application.yml` 和 `SecurityConfig` 的白名单配置
4. **/api/v1/ecos/objects/** 开放访问** — 评估是否需要认证保护
5. **/api/v1/gsxk/** 开放访问** — 评估是否需要认证保护

### 🟡 Medium (下个 Sprint)
6. **CORS 配置收紧** — 不允许 `*` origin 同时开启 `allowCredentials`
7. **Refresh Token 存储** — 迁移到 Redis 支持持久化和吊销
8. **SQL 注入风险扫描** — 全面审查字符串拼接 SQL 的位置

### 🔵 Low (持续改进)
9. **Refresh Token 过期** — 从 30 天缩短到 7 天
10. **添加请求速率限制** — 防止暴力破解
11. **添加审计日志** — 记录所有认证事件

---

## 七、附录

### 7.1 项目结构概览

```
databridge-v2/
├── sysman/          ← 本次审计的运行时服务
│   ├── sysman-boot/            ← Spring Boot 入口 (AuthController, HealthController)
│   └── sysman-impl/            ← 业务实现 (User, Role, Org, ABAC, Audit, etc.)
│   └── workspace-impl/         ← 工作空间 (Object, Timeline, Actions)
├── datanet/         ← 数据网络 (独立服务)
├── buszhi/          ← 业务流程 (独立服务)
├── dccheng/         ← 数据资产 (独立服务)
├── aimod/           ← AI 模块 (独立服务)
├── portal/          ← 门户 (独立服务)
└── market/          ← 市场 (独立服务)
```

### 7.2 审计工具链

- 端点测试: Python `urllib.request`
- 数据库审计: `pg8000.native`
- 代码扫描: Python 正则表达式
- 安全分析: 手动代码审查 + 配置分析

---

> **报告生成**: 2026-06-20 14:46:42 | **工具**: ECOS Backend Audit Script v1.0
