# ECOS 架构审计报告 (Sprint 6)

> **审计日期**: 2026-06-20  
> **审计角色**: ECOS-ARCH  
> **范围**: 后端 `databridge-v2/` + 前端 `c2eos/`  
> **模式**: 只读分析，未修改任何代码

---

## 1. 控制器覆盖度

### 1.1 按模块统计

| 模块 | 控制器数 | 端点数 | 状态 | 说明 |
|------|---------|--------|------|------|
| **databridge-sysman** | 16 | 93 | 🟢 正常 | sysman-impl(14) + sysman-boot(2)，含双路径控制器 |
| **databridge-dccheng** | 11 | 87 | 🟢 正常 | 本体/分类/词汇表/知识图谱全覆盖 |
| **databridge-buszhi** | 4 | 46 | 🟢 正常 | 工作流/审批/任务/DQ |
| **databridge-aimod** | 5 | 35 | 🟢 正常 | Agent呼叫/配置/Mesh/Profile/NLQ |
| **databridge-workspace** | 7 | 34 | 🟢 正常 | 对象/关系/状态机/时间线/QL/动作/查询历史 |
| **databridge-datanet** | 5 | 28 | 🟢 正常 | 目录/分类/数据源/元数据/健康检查 |
| **databridge-portal** | 2 | 11 | 🟡 注意 | FrontendBridge(7) + Menu(4)，FrontendBridge 膨胀到 850 行 |
| **databridge-worldmodel** | 1 | 10 | 🟢 正常 | 单一 WorldModelController |
| **databridge-market** | 1 | 3 | 🟡 偏少 | 仅有 MarketplaceController，功能可能不完整 |
| **ecos-kanban** | 0 | 0 | 🔴 空壳 | 模块在 root pom.xml 中被注释掉，无任何 Java 文件 |
| **databridge-gateway** | 0 | 0 | 🟢 预期 | Gateway 层，不需要 @RestController |
| **databridge-runtime** | 0 | 0 | 🟢 预期 | 基础设施层 |
| **databridge-common** | 0 | 0 | 🟢 预期 | 公共库 |

**总计**: 52 个控制器（含 1 个误报），约 347 个端点

### 1.2 误报排除

| 文件 | 标注 | 实际身份 |
|------|------|----------|
| `sysman-boot/.../GlobalExceptionHandler.java` | `@RestController` 命中 | 实际是 `@RestControllerAdvice`，非控制器 |
| `sysman-impl/.../AuditAspect.java` | `@RequestMapping` 命中 | 实际是切面中引用注解，非控制器 |

### 1.3 路径冲突分析

4 个控制器共享基础路径 `/api/v1/ecos`（无子路径前缀）：

| 控制器 | 子路径空间 | 冲突风险 |
|--------|-----------|----------|
| `OntologyRuleController` | `/entities/{id}/rules`, `/rules/*` | 与 Relationship/Action 共享 `/entities/{id}/*` 模式 |
| `OntologyRelationshipController` | `/entities/{id}/relationships`, `/relationships/*` | 同上 |
| `OntologyActionController` | `/entities/{id}/actions`, `/actions/*` | 同上 |
| `ObjectStateMachineController` | `/objects/{code}/{id}/transitions`, `/statemachine/*` | 路径空间独立，但同基路径 |

**风险**: 🟡 中等。当前各控制器子路径不重叠，但缺乏模块级路径前缀隔离，未来新增端点时容易产生冲突。建议添加子模块前缀如 `/api/v1/ecos/ontology/rules`。

### 1.4 双路径控制器

以下控制器同时暴露 v1 和旧版路径（兼容性设计）：

| 控制器 | 路径 |
|--------|------|
| `UserController` | `/api/v1/system/users`, `/api/system/users` |
| `RoleController` | `/api/v1/system/roles`, `/api/system/roles` |
| `OrganizationController` | `/api/v1/system/organizations`, `/api/system/organizations` |
| `AuthController` | `/api/v1/auth`, `/auth` |
| `NLQController` | `/api/v1/query`, `/api/query` |

🟢 合理的迁移兼容策略。

---

## 2. 模块依赖健康

### 2.1 sysman-boot 依赖完整性

| 依赖 | 状态 | 说明 |
|------|------|------|
| `sysman-impl` | ✅ 已包含 | 系统管理业务逻辑 |
| `runtime-security` | ✅ 已包含 | KMS/策略引擎/ABAC |
| `runtime-monitor` | ✅ 已包含 | 监控运行时 |
| `runtime-crypto` | ✅ 已包含 | 加密服务 |
| `runtime-task` | ✅ 已包含 | 任务调度 |
| `workspace-impl` | ✅ 已包含 | 对象运行时 |
| **`buszhi-impl`** | ❌ 缺失 | 工作流引擎无法加载 |
| **`dccheng-impl`** | ❌ 缺失 | 本体/分类/词汇表无法加载 |
| **`datanet-impl`** | ❌ 缺失 | 数据目录/元数据无法加载 |
| **`aimod-impl`** | ❌ 缺失 | AI模块无法加载 |
| **`portal-impl`** | ❌ 缺失 | FrontendBridge/Menu不可用 |
| **`market-impl`** | ❌ 缺失 | 市场模块不可用 |
| **`worldmodel-impl`** | ❌ 缺失 | 世界模型不可用 |

**严重性**: 🔴 高。sysman-boot 作为主启动模块，缺少 7 个领域模块的依赖，导致这些模块的 Controller 无法被 Spring 扫描加载。这是前端使用 `/api/v1/gsxk/` (GsxkBridgeController) 绕过领域模块的根本原因。

### 2.2 ecos-kanban 模块

- Root pom.xml 中 **已被注释掉**: `<!-- module>ecos-kanban</module -->`
- 子模块 (`kanban-api`, `kanban-impl`, `kanban-boot`) 仅有 pom.xml，**零 Java 文件**
- `kanban-boot` 引用了一个不存在的 MainClass: `com.chinacreator.gzcm.kanban.boot.KanbanApplication`
- 🔴 **完全不可构建**

### 2.3 循环依赖检查

未检测到模块间循环依赖。
- `runtime-core` 被多个 runtime 子模块依赖 (单向)
- `sysman-impl` 依赖 `sysman-api` (单向)
- `databridge-common-api` 被多数模块依赖 (单向)
- ✅ 依赖方向健康

### 2.4 GsxkBridge 绕过模式

`GsxkBridgeController` (401 行) 直接使用 `JdbcTemplate` 查询 `ecos_object_data` 等表，完全绕过 workspace/buszhi/dccheng 领域模块的 Service 层。这是应急方案，但：

- 🟡 违反分层架构原则
- 🟡 SQL 拼接存在注入风险（虽有 `replace("'", "''")` 但非参数化查询）
- 🟡 与领域模块功能重复

---

## 3. 前端架构

### 3.1 路由覆盖度

| 视图 ID | 页面组件 | App.tsx 注册 | 状态 |
|---------|---------|-------------|------|
| `mission_control` | `CognitiveOperatingSystem` | ✅ | 🟢 |
| `catalog` | `DataCatalog` | ✅ | 🟢 |
| `dataset_explorer` | `DatasetExplorer` | ✅ | 🟢 |
| `pipeline` | `PipelineBuilder` | ✅ | 🟢 |
| `workbook` | `CodeWorkbook` | ✅ | 🟢 |
| `ops_apps` | `OperationalApps` | ✅ | 🟢 |
| `monitor` | `MonitoringCenter` | ✅ | 🟢 |
| `ontology` | `OntologyExplorer` | ✅ | 🟢 |
| `agent_studio` | `AgentStudio` | ✅ | 🟢 |
| `agent_mesh` | `AgentMesh` | ✅ | 🟢 |
| `objects` | `ObjectExplorer` | ✅ | 🟢 |
| `ontology_designer` | `OntologyDesigner` | ✅ | 🟢 |
| `workflow_designer` | `WorkflowDesigner` | ✅ | 🟢 |
| `world_model` | `WorldModelViewer` | ✅ | 🟢 |
| `dq_dashboard` | `DataQualityDashboard` | ✅ | 🟢 |
| `glossary` | `GlossaryManager` | ✅ | 🟢 |
| `security` | `SecurityAudit` | ✅ | 🟢 |
| `marketplace` | `Marketplace` | ✅ | 🟢 |
| `iam` | `UserManagement` | ✅ | 🟢 |
| `biz_dashboard` | `BizDashboard` | ✅ | 🟢 |
| `kanban` | `KanbanBoard` | ✅ | 🟢 |
| `lineage` | → `DatasetExplorer` (fallback) | ✅ | 🟡 回退到DatasetExplorer |
| — | `Login` | `main.tsx` 路由 | 🟢 独立路由 |

**结论**: 🟢 所有页面均已正确注册。`Login.tsx` 通过 react-router-dom 在 `main.tsx` 中独立路由，架构合理。

### 3.2 组件复用度

| 类型 | 数量 | 详情 |
|------|------|------|
| 共享组件 (`src/components/`) | 12 | Sidebar, Topbar, CommandPalette, GraphCanvas, DataTable, Modal, EmptyState, ErrorBoundary, LoadingSkeleton, RequireAuth, ThemeContext, LanguageContext |
| 页面组件 (`src/pages/`) | 22 | 全部为页面级组件 |

🟢 共享组件覆盖率良好。`DataTable`, `Modal`, `EmptyState`, `LoadingSkeleton` 等通用组件可被所有页面复用。

### 3.3 API 路径一致性

| 前端前缀 | 后端对应 | 一致性 |
|----------|---------|--------|
| `/api/v1/system/*` | sysman 双路径控制器 | 🟢 匹配 |
| `/api/v1/auth/login` | AuthController | 🟢 匹配 |
| **`/api/v1/gsxk/*`** | GsxkBridgeController | 🟡 桥接路径，非领域模块原生路径 |
| `/api` (API_BASE) | 通用基础路径 | 🟢 |

**关键发现**: 前端大量使用 `/api/v1/gsxk/` 前缀（objects, workflows, ontologies, entities, monitoring, dq, worldmodel 等），这些请求全部打到 `GsxkBridgeController`，而非原生的领域模块 Controller (如 `/api/v1/ecos/objects`, `/api/v1/ecos/workflows`)。这是因为 sysman-boot 未加载领域模块。

🔴 **风险**: 如果未来领域模块独立部署，前端需要大规模路径迁移。

### 3.4 状态管理

| 方式 | 使用位置 | 评价 |
|------|---------|------|
| `useState` + props | App.tsx 管理全部状态并向下传递 | 🟡 存在 prop drilling |
| React Context | ThemeContext, LanguageContext | 🟢 全局主题/语言合理 |
| `useState` (页面内) | 各页面组件内部 | 🟢 页面级状态合理 |

**问题**: App.tsx (386行) 集中管理了 `currentView`, `selectedAssetId`, `openTabs`, `auditLogs`, `sidebarWidth` 等状态，并通过 props 传递给 Sidebar/Topbar 和页面组件。随页面增多，App.tsx 会持续膨胀。

🟡 **建议**: 考虑引入轻量状态管理（如 Zustand 或 React Context + useReducer）来管理 tabs 和导航状态。

### 3.5 大文件警告 (>500 行 TSX/TS)

| 文件 | 行数 | 风险 |
|------|------|------|
| `src/pages/CognitiveOperatingSystem.tsx` | 1081 | 🔴 超大，建议拆分 |
| `src/api.ts` | 887 | 🔴 应按模块拆分为 api/*.ts |
| `src/components/LanguageContext.tsx` | 811 | 🟡 含大量翻译数据，可外置 |
| `src/pages/ObjectExplorer.tsx` | 796 | 🟡 建议拆分 |
| `src/pages/WorkflowDesigner.tsx` | 681 | 🟡 建议拆分 |
| `src/pages/GlossaryManager.tsx` | 671 | 🟡 建议拆分 |
| `src/pages/OntologyDesigner.tsx` | 623 | 🟡 建议拆分 |
| `src/pages/DatasetExplorer.tsx` | 606 | 🟡 建议拆分 |

---

## 4. 代码坏味道

### 4.1 大文件 (>500 行 Java)

| 文件 | 行数 | 模块 | 说明 |
|------|------|------|------|
| `SystemDatabaseAccessImpl.java` | 1355 | runtime-core | 🔴 数据库访问实现过于庞大 |
| `BaseJdbcAdapter.java` | 897 | runtime-core | 🔴 JDBC适配器需拆分 |
| **`FrontendBridgeController.java`** | **850** | portal-impl | 🔴 桥接控制器膨胀，混杂多种职责 |
| `ScheduleBean.java` | 827 | runtime-core | 🔴 Bean类过大 |
| `LogArchiveServiceImpl.java` | 772 | runtime-core | 🟡 日志归档需拆分 |
| `WorkflowEngine.java` | 619 | buszhi-impl | 🟡 引擎核心可模块化 |
| `TaskManagementServiceImpl.java` | 604 | runtime-task | 🟡 任务管理可拆分 |
| `DatabaseTaskPersistenceServiceImpl.java` | 573 | runtime-task | 🟡 持久化可拆分 |
| `DataDescriptionDiscoveryImpl.java` | 564 | runtime-core | 🟡 数据发现可拆分 |
| `ObjectController.java` | 547 | workspace-impl | 🟡 控制器过大 |
| `ModelAccessServiceImpl.java` | 535 | runtime-core | 🟡 可拆分 |
| `DatabaseLogStorage.java` | 524 | runtime-core | 🟡 日志存储可拆分 |
| `ObjectRuntimeService.java` | 510 | buszhi-impl | 🟡 可拆分 |

**共 13 个文件超 500 行**，主要集中在 `runtime-core` 模块（7个）。

### 4.2 TODO/FIXME 标记

| 统计 | 数量 |
|------|------|
| TODO 标记总数 | **117** |
| 集中在 runtime-core | ~80% |
| 主要集中在 | `DataSourceServiceImpl`, `TransManageCallerImpl`, `MetadataService`, `TransactionManager`, `PermissionsDataUtil` 等 |

🔴 **高**: 大量 runtime-core 中的 TODO 标注了「实现实际的 X 逻辑」，表明该模块大量功能为占位/桩代码（来自老工程迁移未完成）。

### 4.3 硬编码凭证/URL

🟢 未发现硬编码的密码或敏感 URL。`password` 关键字出现在 setter 方法和实体类中，属于正常的数据模型。

### 4.4 console.log 残留

🟢 前端代码中未发现 `console.log` 残留，代码质量良好。

### 4.5 ecos-kanban 空壳模块

| 文件 | 状态 |
|------|------|
| `ecos-kanban/pom.xml` | ✅ 仅父 POM |
| `kanban-api/pom.xml` | ❌ 无任何 Java 文件 |
| `kanban-impl/pom.xml` | ❌ 无任何 Java 文件 |
| `kanban-boot/pom.xml` | ❌ 引用不存在的 MainClass |
| `gen_part1.sh` | ❓ 可能是代码生成脚本 |

🔴 **严重**: 模块结构完整但零实现，在 root pom.xml 中被注释排除。如需启用需从零开发。

---

## 5. 风险总评

| 风险等级 | 数量 | 关键项 |
|----------|------|--------|
| 🔴 严重 | 4 | sysman-boot 缺少域模块依赖、ecos-kanban 空壳、GsxkBridge 绕过分层、117 个 TODO |
| 🟡 中等 | 5 | `/api/v1/ecos` 路径冲突风险、前端 api.ts 过大(887行)、prop drilling、FrontendBridgeController 膨胀(850行)、13个超500行文件 |
| 🟢 正常 | 多数 | 无循环依赖、无硬编码凭证、无 console.log、组件复用良好、路由全覆盖 |

### 优先修复建议

1. **P0**: 将缺失的域模块依赖添加到 `sysman-boot/pom.xml`，使所有域 Controller 可被加载
2. **P0**: 前端将 `/api/v1/gsxk/` 路径迁移到域模块原生路径，淘汰 GsxkBridge
3. **P1**: 拆分 `FrontendBridgeController` (850行) 和 `CognitiveOperatingSystem.tsx` (1081行)
4. **P1**: 将 `api.ts` (887行) 按域模块拆分为 `api/users.ts`, `api/ontology.ts` 等
5. **P2**: 为 `/api/v1/ecos` 共享基路径添加子模块前缀
6. **P2**: 处理 117 个 TODO 标记，特别是 runtime-core 中的桩代码
7. **P3**: 决定 ecos-kanban 是开发还是移除以清理代码库

---

*报告由 ECOS-ARCH 自动审计生成 | 2026-06-20*
