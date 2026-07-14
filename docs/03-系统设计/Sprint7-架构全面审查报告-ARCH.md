# ECOS 架构全面审查报告 — Sprint 7

> **审查日期**: 2026-06-20  
> **审查范围**: ECOS 全栈 (后端 12 模块 + 前端 22 页面)  
> **审查人**: Hermes Agent (自动架构审计)  
> **前置基线**: Sprint 6 后端审计 (81.3% API 通过率)

---

## 目录

1. [总体评估](#一总体评估)
2. [模块加载状态](#二模块加载状态)
3. [GsxkBridgeController 兜底方案分析](#三gsxkbridgecontroller-兜底方案分析)
4. [安全隐患深度审查](#四安全隐患深度审查)
5. [数据完整性审计](#五数据完整性审计)
6. [前端架构审查](#六前端架构审查)
7. [API 契约一致性](#七api-契约一致性)
8. [可扩展性评估](#八可扩展性评估)
9. [架构演进路线图](#九架构演进路线图)

---

## 一、总体评估

### 1.1 架构成熟度评分

| 维度 | 得分 | 评级 | 说明 |
|------|------|------|------|
| 模块完整度 | 7.5/10 | 🟡 B | 12 模块中 kanban 被注释，1 个大兜底 Controller |
| 安全性 | 5.5/10 | 🟠 C | DB 凭据硬编码、CORS 配置不安全、白名单不一致 |
| 数据一致性 | 6.0/10 | 🟠 C | ecos_wm_* vs ecos_world_* 表重复，字段语义冲突 |
| 前端架构 | 7.0/10 | 🟡 B | state-based 路由可用但不可书签化，组件分层良好 |
| API 契约 | 6.5/10 | 🟡 B | ApiResponse 统一但路径前缀分散，fetch 层有 3 种模式 |
| 可扩展性 | 7.5/10 | 🟡 B | Maven 多模块清晰，但新模块集成需跨多文件修改 |
| 测试覆盖 | 3.0/10 | 🔴 D | 仅 3 个测试文件，无集成测试，无 API 契约测试 |
| **综合** | **6.1/10** | **🟡 B-** | **可演示，不可投产** |

### 1.2 当前架构拓扑

```
┌──────────────────────────────────────────────────────────┐
│                    前端 (c2eos)                           │
│  React 19 + Vite + Tailwind                              │
│  22 Pages | state-based routing | api.ts (3 fetch modes) │
└────────────────────┬─────────────────────────────────────┘
                     │ HTTP :5173 → Vite proxy → :8081
┌────────────────────▼─────────────────────────────────────┐
│              sysman-boot (Spring Boot 3.2, :8081)        │
│  ┌──────────────────────────────────────────────────┐   │
│  │  GsxkBridgeController (JdbcTemplate 兜底)        │   │
│  │  20+ endpoints: objects, ontology, worldmodel,   │   │
│  │  dq, workflows, search, monitoring, biz, actions │   │
│  └──────────────────────────────────────────────────┘   │
│  ┌──────────────┐ ┌──────────────┐ ┌──────────────┐    │
│  │ sysman-impl  │ │workspace-impl│ │runtime-*     │    │
│  │ IAM/Auth/    │ │ Object/Timeln│ │ security/    │    │
│  │ Audit/ABAC   │ │ /StateMachn  │ │ crypto/monit │    │
│  └──────────────┘ └──────────────┘ └──────────────┘    │
│  ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌──────────┐   │
│  │ buszhi   │ │ dccheng  │ │ datanet  │ │ aimod    │   │
│  │ -impl    │ │ -impl    │ │ -impl    │ │ -impl    │   │
│  └──────────┘ └──────────┘ └──────────┘ └──────────┘   │
│  ┌──────────┐ ┌──────────┐ ┌──────────────┐            │
│  │ portal   │ │ market   │ │ worldmodel   │            │
│  │ -impl    │ │ -impl    │ │ -impl        │            │
│  └──────────┘ └──────────┘ └──────────────┘            │
│  ┌──────────────────────────────────────────┐          │
│  │ databridge-common-api (ApiResponse, 异常)│          │
│  └──────────────────────────────────────────┘          │
└────────────────────┬─────────────────────────────────────┘
                     │ JDBC
┌────────────────────▼─────────────────────────────────────┐
│            PostgreSQL (sys_man, ~90 张表)                  │
│  ecos_* (业务表) + td_* (系统表) + sys_* (日志)           │
└──────────────────────────────────────────────────────────┘
```

---

## 二、模块加载状态

### 2.1 pom.xml 模块声明 (根 POM)

```xml
<modules>
    <module>databridge-common</module>      <!-- ✅ 公共模块 -->
    <module>databridge-runtime</module>      <!-- ✅ 运行时子模块 -->
    <module>databridge-sysman</module>       <!-- ✅ 主启动模块 -->
    <module>databridge-datanet</module>      <!-- ✅ 数据网络 -->
    <module>databridge-buszhi</module>       <!-- ✅ 业务流程 -->
    <module>databridge-dccheng</module>      <!-- ✅ 数据资产 -->
    <module>databridge-aimod</module>        <!-- ✅ AI 模块 -->
    <module>databridge-gateway</module>      <!-- ⚠ 降级为仅路由层 -->
    <module>databridge-portal</module>       <!-- ✅ 门户 -->
    <module>databridge-market</module>       <!-- ✅ 市场 -->
    <module>databridge-worldmodel</module>   <!-- ✅ 世界模型 -->
    <module>databridge-workspace</module>    <!-- ✅ 工作空间 -->
    <!-- module>ecos-kanban</module -->      <!-- ❌ 已注释，未加载 -->
</modules>
```

### 2.2 sysman-boot/pom.xml 依赖加载分析

sysman-boot 作为唯一的 Spring Boot 启动入口，通过 maven 依赖拉取所有模块:

| 依赖模块 | 状态 | 备注 |
|----------|------|------|
| sysman-impl | ✅ 已加载 | 核心业务 (IAM/Auth/Audit/ABAC) |
| runtime-security | ✅ 已加载 | KMS/策略引擎/ABAC |
| runtime-monitor | ✅ 已加载 | 运行监控 |
| runtime-crypto | ✅ 已加载 | 加密服务 |
| runtime-task | ✅ 已加载 | 任务调度 |
| workspace-impl | ✅ 已加载 | Object/Timeline/Workflow |
| buszhi-impl | ✅ 已加载 | 业务流程 (Sprint 6 修复加入) |
| dccheng-impl | ✅ 已加载 | 数据资产/本体 (Sprint 6 修复加入) |
| datanet-impl | ✅ 已加载 | 数据网络 (Sprint 6 修复加入) |
| aimod-impl | ✅ 已加载 | AI 模块 (Sprint 6 修复加入) |
| portal-impl | ✅ 已加载 | 门户 (Sprint 6 修复加入) |
| market-impl | ✅ 已加载 | 市场 (Sprint 6 修复加入) |
| worldmodel-impl | ✅ 已加载 | 世界模型 (Sprint 6 修复加入) |
| ecos-kanban | ❌ 未加载 | 根 POM 中已注释 |

**结论**: Sprint 6 修复后所有必要域模块依赖已就位，**当前无缺失依赖**。kanban 模块尚未完成开发，暂时注释。

### 2.3 模块内部结构完整性

| 模块 | api 层 | impl 层 | boot 层 | Controller | Service | Repository |
|------|--------|---------|---------|------------|---------|------------|
| sysman | ✅ (sysman-api) | ✅ | ✅ | 14 个 | 20+ 个 | 15+ 个 |
| datanet | ✅ (datanet-api) | ✅ | ✅ (datanet-boot) | 5 个 | ✅ | ✅ |
| buszhi | ❌ 无独立 api | ✅ | ❌ | 4 个 | ✅ | ✅ |
| dccheng | ✅ (dccheng-api) | ✅ | ❌ | 11 个 | ✅ | ✅ |
| aimod | ❌ 无独立 api | ✅ | ❌ | 5 个 | ✅ | ✅ |
| portal | ❌ 无独立 api | ✅ | ❌ | 2 个 | ✅ | ✅ |
| market | ❌ 无独立 api | ✅ | ❌ | 1 个 | ✅ | ✅ |
| worldmodel | ❌ 无独立 api | ✅ | ❌ | 1 个 | ✅ | ✅ |
| workspace | ❌ 无独立 api | ✅ | ❌ | 7 个 | ✅ | ✅ |

**问题**: buszhi、aimod、portal、market、worldmodel、workspace 缺少独立的 `-api` 模块。跨模块依赖时只能直接依赖 `-impl`，违反了接口隔离原则。

---

## 三、GsxkBridgeController 兜底方案分析

### 3.1 现状

`GsxkBridgeController` 位于 `sysman-impl` 中，直接使用 `JdbcTemplate` 绕过所有域模块的 Controller/Service/Repository 层，承载 20+ 个前端必需的 API 端点:

```java
@RestController
@RequestMapping("/api/v1/gsxk")
public class GsxkBridgeController {
    // objects, ontology/entities, ontology/properties
    // worldmodel/goals, worldmodel/scenarios, worldmodel/impacts
    // worldmodel/causal-links, dq/rules, monitoring/*
    // biz/dashboard, search, actions, workflows, relationships
    // entities/{id}/properties CRUD, entities/{id}/relationships CRUD
    // datasets
}
```

### 3.2 问题清单

#### 🔴 Critical: SQL 注入风险

多处字符串拼接 SQL，虽然做了简单的 `'` → `''` 替换，但**不是参数化查询**:

```java
// ⚠ GsxkBridgeController.java:34 — SQL 拼接
String where = (entityCode != null && !entityCode.isEmpty())
    ? " WHERE entity_code = '" + entityCode.replace("'", "''") + "'" : "";
```

受影响的端点: `/objects`, `/objects/{entityCode}`, `/ontology/properties`, `/search`

#### 🟠 High: 绕过域模块 Controller

以下域模块已有独立 Controller 实现，但被 GsxkBridgeController 绕过:

| 域模块 | 已有 Controller | 被 GsxkBridge 替代的端点 |
|--------|----------------|------------------------|
| workspace-impl | ObjectController, ObjectTimelineController 等 | `/api/v1/gsxk/objects/**` |
| worldmodel-impl | WorldModelController (`/api/v1/ecos/world-model/**`) | `/api/v1/gsxk/worldmodel/**` |
| dccheng-impl | OntologyController, GlossaryController 等 | `/api/v1/gsxk/ontology/**` |
| buszhi-impl | WorkflowController, DqController | `/api/v1/gsxk/workflows`, `/api/v1/gsxk/dq/**` |

#### 🟡 Medium: 表名硬编码

GsxkBridgeController 直接硬编码查询的表名和列名，与域模块的 Entity/RowMapper 定义完全脱节，形成两套并行的数据访问路径。

#### 🟡 Medium: 无事务管理

所有 GsxkBridgeController 写操作 (POST/PUT/DELETE) 均无 `@Transactional` 注解。

### 3.3 迁移建议

```
当前:  GsxkBridgeController (sysman-impl) → JdbcTemplate → DB
目标:  前端 → Proxy → 域模块 Controller → Service → Repository → DB
```

迁移优先级:
1. **P0 (Sprint 7)**: 将 `/api/v1/gsxk/objects/**` 迁移到 workspace-impl/ObjectController
2. **P1 (Sprint 8)**: 将 `/api/v1/gsxk/worldmodel/**` 迁移到 worldmodel-impl/WorldModelController
3. **P1 (Sprint 8)**: 将 `/api/v1/gsxk/ontology/**` 迁移到 dccheng-impl/OntologyController
4. **P2 (Sprint 9)**: 将 `/api/v1/gsxk/workflows`, `/api/v1/gsxk/dq/**` 迁移到 buszhi-impl
5. **P2 (Sprint 9)**: 迁移 search、biz/dashboard 等杂项端点
6. **P3 (Sprint 10)**: 删除 GsxkBridgeController

---

## 四、安全隐患深度审查

### 4.1 发现汇总

| ID | 严重度 | 问题 | 影响 |
|----|--------|------|------|
| SEC-01 | 🔴 HIGH | DB 凭据硬编码在 application.yml | 代码泄露 = 数据库泄露 |
| SEC-02 | 🔴 HIGH | CORS `*` + `allowCredentials=true` | CSRF/跨域攻击 |
| SEC-03 | 🟠 MEDIUM | 白名单 SecurityConfig 与 application.yml 不一致 | 认证绕过或误拦截 |
| SEC-04 | 🟠 MEDIUM | Refresh Token ConcurrentHashMap 内存存储 | 重启丢失、无法吊销 |
| SEC-05 | 🟠 MEDIUM | Actuator 端点暴露 | 配置/环境变量泄露 |
| SEC-06 | 🟡 MEDIUM | Refresh Token 30 天过期 | 长期有效令牌风险 |
| SEC-07 | 🟡 MEDIUM | 5 处 SQL 字符串拼接 | SQL 注入 |
| SEC-08 | 🟡 LOW | 15 处写操作缺 @Transactional | 数据不一致风险 |
| SEC-09 | 🟢 INFO | JWT RS256 (良好) | — |
| SEC-10 | 🟢 INFO | BCrypt 密码哈希 (良好) | — |

### 4.2 白名单不一致详细分析

| 路径 | application.yml | SecurityConfig.java | 实际行为 |
|------|:---:|:---:|------|
| `/api/health` | ✅ | ✅ | 一致 ✅ |
| `/auth/**` | ✅ | ✅ | 一致 ✅ |
| `/error` | ✅ | ✅ | 一致 ✅ |
| `/api/agent/**` | ✅ | ❌ | **所有 agent 请求被拦截** 🔴 |
| `/api/v1/ecos/**` | ✅ (全局) | 仅 `/objects/**` + `/monitoring/**` | **部分子路径被拦截** 🟠 |
| `/api/audit-logs` | ✅ | ❌ | **audit-logs 被拦截** 🟠 |
| `/api/v1/gsxk/**` | ❌ | ✅ | **gsxk 端点无需认证** 🟠 |
| `/api/v1/auth/**` | ❌ | ✅ | **auth 端点无需认证** 🟡 |

**根因**: 系统同时存在两套白名单机制，Spring Security 的 `SecurityFilterChain` 在运行时生效，application.yml 中的 `auth.whitelist.paths` 仅在自定义过滤器中使用。

### 4.3 CORS 配置

```java
// 当前配置 (不安全)
registry.addMapping("/**")
    .allowedOrigins("*")         // ⚠ 任意来源
    .allowedMethods("*")
    .allowedHeaders("*")
    .allowCredentials(true);     // ⚠ 与 * 不兼容
```

当 `allowCredentials=true` 时，浏览器规范不允许 `allowedOrigins="*"`，某些浏览器会静默失败，而另一些会拒绝请求。正确做法是显式列出允许的域名。

### 4.4 修复优先级

| 优先级 | ID | 修复方案 | 工时 |
|--------|----|----------|------|
| 🔴 P0 | SEC-01 | `application.yml` → `${DB_PASSWORD:root}` 环境变量 | 0.5h |
| 🔴 P0 | SEC-03 | 统一白名单到 SecurityConfig，删除 app.yml 冗余配置 | 1h |
| 🟠 P1 | SEC-02 | 限制 CORS origins 为前端域名列表 | 0.5h |
| 🟠 P1 | SEC-04 | Refresh Token 迁移到 Redis/DB 持久化 | 4h |
| 🟠 P1 | SEC-06 | Refresh Token 过期从 30d → 7d | 0.5h |
| 🟡 P2 | SEC-05 | Actuator 限制为 localhost 或需认证 | 0.5h |
| 🟡 P2 | SEC-07 | SQL 字符串拼接 → PreparedStatement 参数化 | 2h |
| 🔵 P3 | SEC-08 | 写操作添加 @Transactional | 1h |

---

## 五、数据完整性审计

### 5.1 表重复问题: ecos_wm_* vs ecos_world_*

这是本次审计发现的最严重数据架构问题。

**两套表结构对比**:

| 概念 | ecos_wm_* (WorldModelRepository 使用) | ecos_world_* (GsxkBridgeController 使用) |
|------|--------------------------------------|------------------------------------------|
| 目标 | `ecos_wm_goal` (id, name, description, parent_id, progress, status, created_at, updated_at) | `ecos_world_goal` (id, code, name, description, target_value, current_value, unit, status, priority, category, deadline) |
| 因果链 | `ecos_wm_causal_link` (id, source_goal_id, target_goal_id, relationship_type, description, created_at) | `ecos_world_causal_link` (id, source_goal_id, target_goal_id, relationship_type, description, created_at) + `ecos_world_scenario_impact` |
| 场景 | `ecos_wm_scenario` (id, name, description, config_json, status, created_at, updated_at) | `ecos_world_scenario` (id, code, name, description, status, probability, impact_score) |

**数据分布**:

| 表 | 行数 | 使用者 |
|----|------|--------|
| ecos_wm_goal | **0** | WorldModelRepository (域模块) |
| ecos_wm_causal_link | **0** | WorldModelRepository (域模块) |
| ecos_wm_scenario | **0** | WorldModelRepository (域模块) |
| ecos_world_goal | **9** | GsxkBridgeController (兜底) |
| ecos_world_scenario | **7** | GsxkBridgeController (兜底) |
| ecos_world_scenario_impact | **10** | GsxkBridgeController (兜底) |
| ecos_world_causal_link | **11** | GsxkBridgeController (兜底) |

**关键发现**:
- ecos_wm_* 表 (域模块正式路径) **全部为空**
- ecos_world_* 表 (兜底路径) **有真实数据**
- 两套表**字段结构不同**: ecos_world_goal 有 `code`, `target_value`, `current_value`, `unit`, `priority`, `category`, `deadline` 等业务字段，而 ecos_wm_goal 只有 `progress`
- GsxkBridgeController 查询 goals/scenarios 时读 ecos_world_* 表，但查询 causal-links 时又读 ecos_wm_causal_link 表

### 5.2 其他表问题

| 问题 | 表 | 说明 |
|------|-----|------|
| ecos_dq_rule 重复 | `ecos_dq_rule` (8行) + `ecos_dq_rule_v2` (8行) | 两套 DQ 规则表，GsxkBridgeController 使用 v2 |
| ecos_workflow 重复 | `ecos_workflow` (8行) + `ecos_workflow_v2` (0行) | 两套流程表，GsxkBridgeController 使用 v2 (为空) |
| ecos_object_relation 重复 | `ecos_object_relation` (6行) + `ecos_object_relationship` (3行) | 命名不一致 |
| 空表过多 | ~30 张表行数为 0 | 加密密钥、跨境传输、Schema 注册等模块尚未启用 |

### 5.3 修复建议

1. **统一 World Model 表**: 将 ecos_world_* 的业务字段合并到 ecos_wm_*，迁移数据后删除 ecos_world_* 表
2. **统一 DQ 表**: 确定 ecos_dq_rule 和 ecos_dq_rule_v2 的关系，合并或废弃旧表
3. **统一 Workflow 表**: 同上
4. **统一 Object Relationship 表**: 合并 ecos_object_relation 和 ecos_object_relationship

---

## 六、前端架构审查

### 6.1 技术栈

| 技术 | 版本 | 评价 |
|------|------|------|
| React | 19.x | ✅ 最新稳定版 |
| TypeScript | — | ✅ 全量 TS |
| Vite | — | ✅ 快速开发服务器 |
| Tailwind CSS | — | ✅ 实用优先 |
| React Router DOM | 6.x | ⚠ 仅用于 login/auth 路由 |
| Lucide React | — | ✅ 图标库 |

### 6.2 路由架构分析

当前使用 **state-based routing** (基于 `currentView` 状态变量) 而非 React Router:

```tsx
// App.tsx — 路由通过 switch 语句实现
const [currentView, setCurrentView] = useState<string>("mission_control");

const renderViewContent = () => {
    switch (currentView) {
        case "mission_control": return <CognitiveOperatingSystem />;
        case "catalog": return <DataCatalog ... />;
        // ... 20+ cases
    }
};
```

**优点**:
- 实现简单，无需路由配置
- 支持 Palantir-style 多 Tab 管理 (openTabs state)
- 页面切换不丢失上下文

**缺点**:
- ❌ URL 不变，无法书签化/分享特定页面
- ❌ 浏览器前进/后退按钮无效
- ❌ 无法深层链接到特定资源
- ❌ SEO 不友好 (对内部系统影响较小)
- ❌ 所有页面始终挂载在同一个 React 树中

### 6.3 组件架构

组件分层清晰:

```
src/
├── components/
│   ├── Sidebar.tsx          ← 导航侧边栏
│   ├── Topbar.tsx           ← 顶部标签栏
│   ├── RequireAuth.tsx      ← 认证守卫
│   ├── CommandPalette.tsx   ← Ctrl+K 命令面板
│   ├── ThemeContext.tsx      ← 主题 (亮/暗)
│   ├── LanguageContext.tsx   ← 国际化 (zh/en)
│   └── common/
│       ├── ErrorBoundary.tsx    ← ✅ 错误边界
│       ├── LoadingSkeleton.tsx  ← ✅ 骨架屏 (3 种变体)
│       ├── EmptyState.tsx       ← ✅ 空状态
│       ├── Modal.tsx            ← ✅ 模态框
│       ├── DataTable.tsx        ← ✅ 数据表格
│       └── GraphCanvas.tsx      ← ✅ 图可视化
├── pages/                   ← 22 个页面组件
├── hooks/                   ← useMobileSidebar
├── services/                ← glossary.ts
├── api.ts                   ← API 层 (887 行)
├── types.ts                 ← 类型定义
└── mockData.ts              ← Mock 数据
```

**正面评价**:
- ✅ ErrorBoundary 实现完整 (类组件，支持重试/刷新/错误详情)
- ✅ LoadingSkeleton 支持 3 种布局变体 (table/card/list)
- ✅ 主题和国际化通过 Context 管理
- ✅ 命令面板提供类 IDE 体验
- ✅ RequireAuth 守卫 (检查 localStorage token)

### 6.4 待改进项

| ID | 问题 | 建议 | 优先级 |
|----|------|------|--------|
| FE-01 | state-based 路由无 URL | 迁移到 React Router 嵌套路由 + useParams | 🟠 P1 |
| FE-02 | ErrorBoundary 标记 `@ts-nocheck` | 升级 @types/react 或改用函数组件 + useErrorBoundary hook | 🟡 P2 |
| FE-03 | 无懒加载 (code splitting) | 对 22 个页面使用 React.lazy() + Suspense | 🟡 P2 |
| FE-04 | mockData.ts 仍被导入 | 后端 API 全通后移除 mock fallback | 🟡 P2 |
| FE-05 | Login 登录调用 `/api/v1/auth/login` | 统一使用 api.ts 封装，避免裸 fetch | 🟡 P2 |
| FE-06 | 无 API 请求去重/缓存 | 添加 React Query 或 SWR | 🔵 P3 |

### 6.5 路由迁移方案

```tsx
// 目标架构：React Router 嵌套路由
<Routes>
  <Route path="/login" element={<Login />} />
  <Route element={<RequireAuth><AppLayout /></RequireAuth>}>
    <Route index element={<CognitiveOperatingSystem />} />
    <Route path="catalog" element={<DataCatalog />} />
    <Route path="pipeline" element={<PipelineBuilder />} />
    <Route path="objects" element={<ObjectExplorer />} />
    <Route path="objects/:entityCode" element={<ObjectExplorer />} />
    <Route path="objects/:entityCode/:objectId" element={<ObjectDetail />} />
    {/* ... 其他路由 */}
  </Route>
</Routes>
```

Tab 管理功能可与 URL 路由并存: sidebar 导航更新 URL，openTabs 维护打开标签列表。

---

## 七、API 契约一致性

### 7.1 路径前缀分布

当前 API 路径前缀碎片化，存在 5 种以上不同前缀:

| 前缀 | 来源 | 示例 |
|------|------|------|
| `/api/v1/gsxk/**` | GsxkBridgeController (兜底) | /objects, /worldmodel/goals, /dq/rules |
| `/api/v1/ecos/**` | worldmodel + 部分 sysman | /world-model/goals, /objects/search |
| `/api/v1/system/**` | sysman IAM Controllers | /users, /roles, /organizations |
| `/api/v1/auth/**` | AuthController | /login, /refresh, /me |
| `/api/**` | 杂项 Controller | /health, /datasets, /agent/chat |
| `/auth/**` | 兼容旧路径 | /login |

### 7.2 响应格式分析

后端 `ApiResponse<T>` 统一封装:

```json
{
    "code": 0,
    "message": "ok",
    "data": { ... },
    "timestamp": 1718870400000
}
```

前端 `api.ts` 存在 **3 种不同的 fetch 封装**:

| 函数 | 响应处理 | 使用场景 |
|------|----------|----------|
| `apiFetch<T>()` | 期望 `{ success, data }` 格式 | datasets, ontology, agents, audit-logs |
| `apiFetchData<T>()` | 兼容 `{ code, data }` 和 `{ success, data }` | objects, workflows, marketplace, monitoring |
| `doFetch()` | 直接返回 JSON | dq, worldmodel, knowledge-search |

**兼容性问题**:
- `apiFetch` 期望 `success` 字段，但后端 `ApiResponse` 使用 `code` 字段 — 可能导致响应解析失败
- `apiFetchData` 同时处理两种格式，但 `success === false` 检查对 `code !== 0` 的情况不适用
- WorldModel 端点使用 `doFetch` 绕过统一响应封装

### 7.3 修复建议

1. **统一路径前缀**: 所有 API 统一为 `/api/v1/{domain}/{resource}`
2. **统一响应格式**: 前端统一使用 `apiFetchData` 模式，删除 `apiFetch` 和裸 `doFetch`
3. **后端路径规范化**:

```
/api/v1/auth/**           → 认证
/api/v1/iam/**            → 用户/角色/组织/权限
/api/v1/object/**         → 对象运行时 (替代 gsxk/objects)
/api/v1/ontology/**       → 本体 (替代 gsxk/ontology)
/api/v1/world-model/**    → 世界模型 (替代 gsxk/worldmodel)
/api/v1/data-quality/**   → 数据质量 (替代 gsxk/dq)
/api/v1/workflow/**       → 工作流 (替代 gsxk/workflows)
/api/v1/marketplace/**    → 市场
/api/v1/monitoring/**     → 监控 (替代 gsxk/monitoring)
/api/v1/search/**         → 全局搜索 (替代 gsxk/search)
```

---

## 八、可扩展性评估

### 8.1 新增业务模块的整合路径

当前添加新模块需要以下步骤:

1. 创建模块目录结构 (`{module}/{module}-impl/src/main/java/...`)
2. 在根 `pom.xml` `<modules>` 中添加 `<module>databridge-{module}</module>`
3. 在 `sysman-boot/pom.xml` 中添加依赖 `<artifactId>{module}-impl</artifactId>`
4. 在模块内实现 Controller/Service/Repository
5. ComponentScan 自动发现 (依赖 `@SpringBootApplication` 的同包/子包扫描)

**问题**: 步骤 2-3 需要修改根 POM 和 sysman-boot POM，容易遗漏。且 `@SpringBootApplication` 扫描范围仅限于 `com.chinacreator.gzcm.sysman` 包，域模块包名 (如 `com.chinacreator.gzcm.worldmodel`) 需要显式配置 `@ComponentScan`。

### 8.2 域模块集成成熟度

| 模块 | 独立 Controller | 集成到 SysMan | 前端对接 | 状态 |
|------|:---:|:---:|:---:|------|
| workspace | ✅ ObjectController 等 7 个 | ⚠ 被 GsxkBridge 绕过 | ✅ | 已实现但未激活 |
| worldmodel | ✅ WorldModelController | ⚠ 路径不同 (`/ecos/world-model` vs `/gsxk/worldmodel`) | ⚠ 使用 /gsxk 路径 | 已实现但路径不匹配 |
| dccheng | ✅ OntologyController 等 11 个 | ⚠ 被 GsxkBridge 绕过 | ⚠ | 已实现但未激活 |
| buszhi | ✅ WorkflowController 等 4 个 | ⚠ 被 GsxkBridge 绕过 | ⚠ | 已实现但未激活 |
| aimod | ✅ AgentMeshController 等 5 个 | ⚠ 被 GsxkBridge 绕过? | ⚠ | 已实现但未激活 |
| datanet | ✅ CatalogController 等 5 个 | ⚠ 被 GsxkBridge 绕过? | ⚠ | 已实现但未激活 |
| portal | ✅ FrontendBridgeController | ⚠ | ⚠ | 状态不明 |
| market | ✅ MarketplaceController | ⚠ | ⚠ | 状态不明 |
| kanban | ❌ 模块已注释 | — | — | 未开始 |

### 8.3 架构瓶颈

| 瓶颈 | 说明 | 影响 |
|------|------|------|
| 单点启动 | 所有模块打入一个 sysman-boot fat JAR | JAR 体积膨胀，启动慢 |
| 无 API Gateway | gateway 模块已降级，无动态路由 | 无法按模块独立部署/扩缩 |
| 共享数据库 | 所有模块共用一个 sys_man 库和 schema | 耦合度高，难以拆分 |
| 无消息队列 | 模块间通信靠同步 HTTP/直接 import | 紧密耦合，无异步解耦 |

---

## 九、架构演进路线图

### 9.1 三阶段演进计划

```
┌─────────────────────────────────────────────────────────────┐
│                    Sprint 7-9: 稳定化                        │
│  "让现有架构可生产"                                          │
│  ┌─────────────────────────────────────────────────────┐   │
│  │ 🔴 安全加固 (P0/P1)                                  │   │
│  │ 🟠 API 路径统一 + GsxkBridge 逐步拆除                │   │
│  │ 🟡 数据表统一 (ecos_wm_* vs ecos_world_*)            │   │
│  │ 🟡 前端路由迁移 (React Router 嵌套路由)               │   │
│  └─────────────────────────────────────────────────────┘   │
├─────────────────────────────────────────────────────────────┤
│                   Sprint 10-12: 优化                        │
│  "让架构更健壮"                                             │
│  ┌─────────────────────────────────────────────────────┐   │
│  │ 🟡 前端 React.lazy + Suspense 代码分割               │   │
│  │ 🟡 Refresh Token Redis 持久化                         │   │
│  │ 🟡 SQL 注入全面修复                                   │   │
│  │ 🔵 API 版本化 (v1 → v2 过渡)                         │   │
│  │ 🔵 集成测试 + API 契约测试                            │   │
│  └─────────────────────────────────────────────────────┘   │
├─────────────────────────────────────────────────────────────┤
│                  Sprint 13+: 演进                           │
│  "让架构可扩展"                                             │
│  ┌─────────────────────────────────────────────────────┐   │
│  │ 🔵 API Gateway 动态路由 (Spring Cloud Gateway)       │   │
│  │ 🔵 模块独立部署 (多 JAR + 服务发现)                   │   │
│  │ 🔵 消息队列解耦 (RabbitMQ/Kafka)                     │   │
│  │ 🔵 数据库按域拆分 (Database per Service)             │   │
│  │ 🔵 Kanban 模块开发                                   │   │
│  └─────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────┘
```

### 9.2 Sprint 7 详细任务

| 任务 ID | 任务 | 工时 | 优先级 |
|---------|------|------|--------|
| S7-01 | 统一 SecurityConfig 白名单，删除 app.yml 冗余 | 1h | 🔴 P0 |
| S7-02 | DB 凭据环境变量化 `${DB_PASSWORD}` | 0.5h | 🔴 P0 |
| S7-03 | CORS origins 限制为前端域名 | 0.5h | 🟠 P1 |
| S7-04 | Refresh Token 过期 30d → 7d | 0.5h | 🟠 P1 |
| S7-05 | GsxkBridge.objects → workspace ObjectController 迁移 | 4h | 🟠 P1 |
| S7-06 | 统一 API 路径前缀规划 + `/api/v1/gsxk` → 新路径映射 | 2h | 🟠 P1 |
| S7-07 | 前端 api.ts 统一为 apiFetchData 模式 | 2h | 🟡 P2 |
| S7-08 | ecos_wm_* 与 ecos_world_* 表合并方案设计 | 2h | 🟡 P2 |
| S7-09 | 前端添加 React.lazy 为页面级代码分割准备 | 1h | 🟡 P2 |
| S7-10 | Actuator 端点安全加固 | 0.5h | 🟡 P2 |

### 9.3 关键指标目标

| 指标 | 当前 (Sprint 6) | Sprint 7 目标 | Sprint 10 目标 |
|------|:---:|:---:|:---:|
| API 通过率 | 81.3% | 90%+ | 95%+ |
| 安全评分 | 5.5/10 | 7.5/10 | 8.5/10 |
| GsxkBridge 端点占比 | 62% (20/32) | 40% | 0% |
| 前端路由可书签 | 0% | 0% | 100% |
| SQL 注入风险点 | 5 | 3 | 0 |
| 测试覆盖率 | <5% | <5% | 30%+ |

---

## 附录

### A. 模块 Controller 全量清单 (50 个)

| 模块 | Controller | 端点前缀 |
|------|-----------|----------|
| sysman-boot | AuthController | /api/v1/auth, /auth |
| sysman-boot | HealthController | /api/health |
| sysman-impl | GsxkBridgeController | /api/v1/gsxk/** |
| sysman-impl | UserController | /api/v1/system/users |
| sysman-impl | RoleController | /api/v1/system/roles |
| sysman-impl | OrganizationController | /api/v1/system/organizations |
| sysman-impl | PermissionController | /api/system/permissions |
| sysman-impl | AbacController | /api/v1/abac |
| sysman-impl | DataPermissionController | /api/v1/data-permission |
| sysman-impl | AuditController | /api/v1/audit |
| sysman-impl | PolicyEngineController | /api/v1/policy-engine |
| sysman-impl | ConfigController | /api/system/config |
| sysman-impl | SystemParamController | /api/system/config/params |
| sysman-impl | MonitoringController | /api/v1/ecos/monitoring |
| workspace-impl | ObjectController | /api/v1/ecos/objects |
| workspace-impl | ObjectTimelineController | (同) |
| workspace-impl | ObjectRelationshipController | (同) |
| workspace-impl | ObjectStateMachineController | (同) |
| workspace-impl | ObjectActionController | (同) |
| workspace-impl | ObjectQLController | (同) |
| workspace-impl | QueryHistoryController | (同) |
| worldmodel-impl | WorldModelController | /api/v1/ecos/world-model |
| dccheng-impl | OntologyController 等 11 个 | /api/v1/ecos/ontology |
| buszhi-impl | WorkflowController 等 4 个 | /api/v1/buszhi |
| aimod-impl | AgentMeshController 等 5 个 | /api/agent-mesh |
| datanet-impl | CatalogController 等 5 个 | /api/v1/datanet |
| portal-impl | FrontendBridgeController | /api/portal |
| market-impl | MarketplaceController | /api/v1/marketplace |

### B. 前端页面全量清单 (22 个)

| 页面 | viewId | 文件 |
|------|--------|------|
| Mission Control | mission_control | CognitiveOperatingSystem.tsx |
| Data Catalog | catalog | DataCatalog.tsx |
| Dataset Explorer | dataset_explorer | DatasetExplorer.tsx |
| Pipeline Builder | pipeline | PipelineBuilder.tsx |
| Code Workbook | workbook | CodeWorkbook.tsx |
| Operational Apps | ops_apps | OperationalApps.tsx |
| Monitoring Center | monitor | MonitoringCenter.tsx |
| Ontology Explorer | ontology | OntologyExplorer.tsx |
| Agent Studio | agent_studio | AgentStudio.tsx |
| Agent Mesh | agent_mesh | AgentMesh.tsx |
| Object Explorer | objects | ObjectExplorer.tsx |
| Ontology Designer | ontology_designer | OntologyDesigner.tsx |
| Workflow Designer | workflow_designer | WorkflowDesigner.tsx |
| World Model Viewer | world_model | WorldModelViewer.tsx |
| Data Quality | dq_dashboard | DataQualityDashboard.tsx |
| Glossary | glossary | GlossaryManager.tsx |
| Security Audit | security | SecurityAudit.tsx |
| Marketplace | marketplace | Marketplace.tsx |
| User Management | iam | UserManagement.tsx |
| Biz Dashboard | biz_dashboard | BizDashboard.tsx |
| Kanban Board | kanban | KanbanBoard.tsx |
| Login | — | Login.tsx |

---

> **报告生成**: 2026-06-20 | **工具**: Hermes Agent 架构审查 v1.0  
> **下一步**: 基于本报告制定 Sprint 7 Backlog，优先修复 SEC-01~SEC-04 安全问题和 S7-05 GsxkBridge 迁移。
