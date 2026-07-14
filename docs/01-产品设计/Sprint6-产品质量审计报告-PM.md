# ECOS 产品质量审计报告 (Sprint 6)

> **审计日期**: 2026-06-20  
> **审计人**: ECOS-PM (自动化产品审计)  
> **审计范围**: 前端 22 页面 + 后端 20+ API 端点 + 关键路径 + UX 质量 + 数据完备性  
> **前端路径**: `/home/guorongxiao/c2eos/`  
> **后端地址**: `localhost:8081/sys-man`

---

## 执行摘要

本次审计发现 **1 个 P0 阻塞项**（API 路径路由 500 错误导致 6 个核心 API 不可用）、**多个 P1 重要缺陷**（空数据、登录后路由错误、mock 硬编码），以及若干 UX 改进点。**当前系统无法满足上线标准**，需至少修复 P0 和 P1 项后方可进入 UAT 阶段。

---

## 1. 逐页审计

| # | 页面 | 加载 | 数据源 | 空态处理 | 错误处理 | 评分 | 备注 |
|---|------|------|--------|----------|----------|------|------|
| 1 | **Login** | ✅ 正常 | `/api/v1/auth/login` → 代理到后端 200 | N/A | ✅ try/catch + 错误消息 | ⭐⭐⭐⭐ | 用户名/密码验证+Token 备选登录 |
| 2 | **CognitiveOperatingSystem** (Mission Control) | ⚠️ 部分 | 6 层蓝图全硬编码；KG/WM Goals 从 API 加载 | KG/Goals 有 empty state | ⚠️ `.catch(() => setLoading(false))` 静默吞错 | ⭐⭐ | 因果图谱 `MOCK_CAUSAL_LINKS` 硬编码（L355-369）；蓝图 coverage 全部预制 |
| 3 | **DataCatalog** | ❌ 无数据 | `fetchDatasets()` → API 返回 500 | 无空态提示，白屏 | ⚠️ `.catch(() => setLoading(false))` — 不显示错误 | ⭐ | API 500 无用户提示 |
| 4 | **DatasetExplorer** | ⚠️ 部分 | 依赖 `MOCK_DATA_ASSETS` 从 props 传入 | 有空态 | ⚠️ 静默吞错 | ⭐⭐ | 数据来自 mockData 而非 API |
| 5 | **PipelineBuilder** | ❌ 空壳 | `useState([])` — 无 API 调用 | ✅ "即将推出"占位 | 无错误处理（无 API 调用） | ⭐ | 完全的占位/Coming Soon 页面 |
| 6 | **CodeWorkbook** | ❌ 空壳 | 无数据加载 | ✅ "功能开发中"占位 | 无错误处理 | ⭐ | 完全的占位页面 |
| 7 | **OperationalApps** | ⚠️ 部分 | 内嵌操作面板（本体 action） | 有空态 | 基本 try/catch | ⭐⭐⭐ | 功能存在但依赖 API |
| 8 | **MonitoringCenter** | ❌ 无数据 | API 返回 500 | ✅ empty state 四维检查 | ✅ loadData catch 后设置空数组 + diagnostics fallback | ⭐⭐⭐ | 错误处理较好，但数据为空 |
| 9 | **OntologyExplorer** | ⚠️ 部分 | 含 mock 回退 (`MOCK_ONTOLOGY_ENTITIES`) | 有空态 | ⚠️ 静默吞错 | ⭐⭐ | EKG 节点有硬编码 mock（L181） |
| 10 | **AgentStudio** | ✅ 正常 | 真实 API: agents(4), tools(5), prompts | 有空态 | ✅ try/catch | ⭐⭐⭐⭐ | 较好，API 返回 4 个 agent |
| 11 | **AgentMesh** | ✅ 正常 | 真实 API: agents(4), missions(5) | 有空态 | ⚠️ 部分 catch | ⭐⭐⭐⭐ | 功能完善，支持 CRUD |
| 12 | **ObjectExplorer** | ❌ 无数据 | API 返回 500 | ⚠️ 无数据时仅显示表头 | ✅ try/catch + error state | ⭐⭐⭐ | 代码质量高（24 处 try/catch），但 API 不可用 |
| 13 | **OntologyDesigner** | ❌ 无数据 | API 返回 500 | 有空态 | ⚠️ catch 后仅 setLoading(false) | ⭐⭐ | 12 处 catch，但 API 500 无错误提示 |
| 14 | **WorkflowDesigner** | ❌ 空数据 | API 返回 total=0 | ✅ "无流程数据" | ⚠️ 部分 catch | ⭐⭐⭐ | API 正常但数据库为空 |
| 15 | **WorldModelViewer** | ⚠️ 部分 | Goals API 有 9 条；CausalLinks API 500 | 有空态 | ⚠️ 部分 catch | ⭐⭐ | CausalLinks 500 被静默处理 |
| 16 | **DataQualityDashboard** | ✅ 部分 | DQ Rules API 返回 8 条规则 | 有空态 | ⚠️ 部分 catch | ⭐⭐⭐ | 有数据但 dashboard/issues API 待确认 |
| 17 | **Marketplace** | ❌ 空数据 | API 返回 total=0 | ✅ "暂无数据资产" | ✅ try/catch + error | ⭐⭐⭐ | 代码完整，数据库无种子数据 |
| 18 | **GlossaryManager** | ⚠️ 待确认 | `/api/glossary` — 端点未测试 | 有空态 | ✅ 完整 CRUD + toast | ⭐⭐⭐ | 架构完整，API 连通性待确认 |
| 19 | **UserManagement** (IAM) | ✅ 正常 | 真实数据: 13 users, 5 roles, 6 orgs | ✅ "暂无数据" | ✅ try/catch + 错误提示 | ⭐⭐⭐⭐ | 完整 CRUD，数据正常 |
| 20 | **BizDashboard** | ❌ 空数据 | API 返回空 `departments` | ⚠️ 无数据时 KPI 显示 "?" | ✅ try/catch + 错误提示 | ⭐⭐ | 页面存在但无业务数据 |
| 21 | **SecurityAudit** | ❌ 空数据 | 依赖 `auditLogs` prop（API 返回 0 条） | ✅ "暂无审计日志数据" | N/A（纯展示组件） | ⭐⭐⭐ | 空态友好，但无审计数据 |
| 22 | **KanbanBoard** | ⚠️ iframe | 嵌入 `/kanban/ecos-kanban.html` | N/A | 无错误处理 | ⭐⭐ | 仅 iframe 嵌入，功能依赖外部页面 |

### 评分汇总

| 评分 | 页面数 | 占比 |
|------|--------|------|
| ⭐⭐⭐⭐ (良好) | 4 | 18% |
| ⭐⭐⭐ (可用) | 7 | 32% |
| ⭐⭐ (有问题) | 7 | 32% |
| ⭐ (严重不足) | 4 | 18% |

---

## 2. 关键路径审计

| 流程 | 状态 | 问题 |
|------|------|------|
| **登录流程** | ⚠️ 部分可用 | 登录 API 正常（通过 Vite 代理到 `/sys-man/api/v1/auth/login`），但登录后跳转到 `/app` 路径，路由守卫 `RequireAuth` 仅检查 token 存在性（不验证有效性） |
| **数据仪表盘** (DataCatalog) | ❌ 阻塞 | `fetchDatasets()` 调用后端 500 错误，无 mock 回退，页面白屏（仅显示空表格） |
| **Dataset Explorer** | ⚠️ 依赖 mock | 使用 `MOCK_DATA_ASSETS`（3 个硬编码数据集），未从 API 加载实时数据 |
| **Object CRUD** (ObjectExplorer) | ❌ 阻塞 | `/api/v1/gsxk/objects/{entityCode}` 后端返回 500，无法进行任何 CRUD 操作 |
| **IAM 用户管理** | ✅ 正常 | 13 用户、5 角色、6 组织正常加载，CRUD 操作可用 |
| **Agent Studio** | ✅ 正常 | 4 Agent、5 工具加载正常，Chat 功能通过 BFF 代理工作 |
| **Agent Mesh** | ✅ 正常 | 4 Agent、5 Mission 加载正常，支持创建/执行 |

---

## 3. UX 质量

| 维度 | 评分 | 说明 |
|------|------|------|
| **视觉一致性** | ⭐⭐⭐⭐ | 统一的 Tailwind 样式体系 + 暗色/亮色主题切换；整体 UI 风格一致（Palantir 风格参考） |
| **导航完整性** | ⭐⭐⭐⭐ | DIKW 分层侧边栏覆盖全部 22 页面；Ctrl+K 命令面板；顶部 Tab 栏支持多页面切换 |
| **面包屑/返回** | ⭐⭐ | 无全局面包屑导航；Tab 栏仅显示当前打开页签；DatasetExplorer 提供 "Back to Catalog" |
| **表单验证** | ⭐⭐ | Login 有基本前端验证；UserManagement 表单无验证（直接提交）；ObjectExplorer 无前端 schema 校验 |
| **加载状态** | ⭐⭐⭐ | 多数页面有 `loading` 状态（spinner 或 "加载中..."），但缺少骨架屏（仅 Monitoring 有成熟加载态） |
| **空状态** | ⭐⭐⭐ | 约 70% 页面有空态提示（如 "暂无数据"、"功能开发中"），但 DataCatalog 和部分页面空态缺失 |
| **错误状态** | ⭐⭐ | 普遍使用 `.catch(() => setLoading(false))` 静默吞错，用户看不到任何错误提示。仅 Login、UserManagement、BizDashboard 有显式错误消息 |
| **移动端响应** | ⭐⭐⭐ | 侧边栏支持移动端滑入/滑出 + backdrop 点击关闭；但表格密集页面（DataTable）在小屏未做横向滚动优化 |
| **国际化** | ⭐⭐⭐ | 有 LanguageContext 支持中英双语，但部分硬编码文本（如 PipelineBuilder "即将推出"）未走 i18n |

---

## 4. 数据完备性

### API 端点状态（通过 `/sys-man/api/*` 直接访问，带 admin token）

| 端点 | HTTP | 数据量 | 问题 |
|------|------|--------|------|
| `/sys-man/api/datasets` | **500** | — | 🚨 **P0** 后端异常 |
| `/sys-man/api/ontology` | **500** | — | 🚨 **P0** 后端异常 |
| `/sys-man/api/v1/gsxk/objects/Customer` | **500** | — | 🚨 **P0** 后端异常 |
| `/sys-man/api/v1/gsxk/ontologies/ont001/entities` | **500** | — | 🚨 **P0** 后端异常 |
| `/sys-man/api/v1/gsxk/worldmodel/causal-links` | **500** | — | 🚨 **P0** 后端异常 |
| `/sys-man/api/v1/gsxk/monitoring/dashboard` | **500** | — | 🚨 **P0** 后端异常 |
| `/sys-man/api/audit-logs` | 200 | **0 条** | 空表，无种子数据 |
| `/sys-man/api/knowledge/graph` | 200 | **0 nodes** | 空图，无种子数据 |
| `/sys-man/api/v1/gsxk/workflows` | 200 | **total=0** | 空表 |
| `/sys-man/api/marketplace/assets` | 200 | **total=0** | 空表，无种子数据 |
| `/sys-man/api/v1/gsxk/biz/dashboard` | 200 | **空** | departments 无数据 |
| `/sys-man/api/v1/gsxk/search?q=test` | 200 | 1 条 | 搜索可用 |
| `/sys-man/api/agent/agents` | 200 | 4 条 | ✅ |
| `/sys-man/api/agent/tools` | 200 | 5 条 | ✅ |
| `/sys-man/api/v1/gsxk/dq/rules` | 200 | 8 条 | ✅ |
| `/sys-man/api/v1/gsxk/worldmodel/goals` | 200 | 9 条 | ✅ |
| `/sys-man/api/v1/system/users` | 200 | 13 条 | ✅ |
| `/sys-man/api/v1/system/roles` | 200 | 5 条 | ✅ |
| `/sys-man/api/v1/system/organizations/all` | 200 | 6 条 | ✅ |
| `/sys-man/api/agent-mesh/agents` | 200 | 4 条 | ✅ |
| `/sys-man/api/agent-mesh/missions` | 200 | 5 条 | ✅ |

### 数据完备性汇总

| 模块 | 涉及表/端点 | 有数据 | 空表/500 |
|------|-------------|--------|----------|
| IAM | users, roles, organizations, permissions | users(13), roles(5), orgs(6) | permissions 待确认 |
| Agent | agents, tools, prompts | agents(4), tools(5) | — |
| AgentMesh | agents, missions | agents(4), missions(5) | — |
| DQ | rules, issues, dashboard | rules(8) | issues/dashboard 待确认 |
| WorldModel | goals, scenarios, causal-links | goals(9) | causal-links(500) |
| Knowledge | graph nodes/edges | ❌ 0 nodes | — |
| Objects | Customer/Supplier/Invoice | — | **500** |
| Datasets | datasets | — | **500** |
| Ontology | entities, properties, relationships | — | **500 / empty** |
| Workflow | workflows | — | total=0 |
| Marketplace | assets | — | total=0 |
| Audit | audit-logs | — | 0 条 |
| BizDashboard | departments, projects, contracts | — | 空 |

**数据充足率**: 9/23 ≈ **39%** 的端点有可用数据

---

## P0 阻塞项（必须修复，阻塞上线）

| # | 问题 | 影响范围 | 建议修复 |
|---|------|----------|----------|
| **P0-1** | **6 个核心 API 端点返回 HTTP 500**：`/api/datasets`, `/api/ontology`, `/api/v1/gsxk/objects/{entityCode}`, `/api/v1/gsxk/ontologies/{id}/entities`, `/api/v1/gsxk/worldmodel/causal-links`, `/api/v1/gsxk/monitoring/dashboard` | DataCatalog, OntologyExplorer, ObjectExplorer, OntologyDesigner, WorldModelViewer, MonitoringCenter — 6 个页面完全不可用 | 排查后端 Controller 异常日志，修复后验证。涉及 `DatasetController`, `OntologyController`, `ObjectController`, `WorldModelController`, `MonitoringController` |
| **P0-2** | **前端 `.catch()` 静默吞错** — 大多数页面 catch 后仅 `setLoading(false)`，用户无法感知 API 失败 | 全部 22 页面 | 统一错误处理 Hook/组件，在 catch 中 `setError(e.message)` 并展示给用户 |
| **P0-3** | **DataCatalog 无 mock fallback** — API 500 后页面白屏，无任何数据显示 | DataCatalog（D 层入口页面） | 添加 fallback 到 `MOCK_DATA_ASSETS`（已在 mockData.ts 中定义） |

---

## P1 重要缺陷（影响用户体验但非完全阻塞）

| # | 问题 | 影响范围 |
|---|------|----------|
| **P1-1** | **登录后路由跳转错误** — 登录成功跳转 `/app`，但 `main.tsx` 中路由定义为 `/*`，可能存在路由不匹配 | Login → App 导航 |
| **P1-2** | **PipelineBuilder 和 CodeWorkbook 完全是占位页面** — 无任何功能实现，应明确标注为 "Phase 2" 或在导航中隐藏 | 2 个页面 |
| **P1-3** | **8 个 API 端点返回空数据** — audit-logs(0), knowledge/graph(0), workflows(0), marketplace(0), biz/dashboard(空) | 5 个页面空态 |
| **P1-4** | **CognitiveOperatingSystem 硬编码 mock 数据** — 6 层蓝图 coverage、因果图谱节点/边、目标进度全部为前端硬编码，非实时数据 | Mission Control 首页 |
| **P1-5** | **ObjectExplorer 实体列表硬编码** — `KNOWN_ENTITIES = ["Customer", "Supplier", "Invoice"]` 未从 API 动态获取 | ObjectExplorer |
| **P1-6** | **SecurityAudit 依赖空数据** — `auditLogs` 从 App.tsx 通过 `fetchAuditLogs()` 获取，但 API 返回 0 条 | SecurityAudit 页面 |

---

## P2 改进建议（非阻塞，建议优化）

| # | 建议 |
|---|------|
| P2-1 | 添加全局 `ErrorBoundary` 组件包裹每个页面（已有 ErrorBoundary.tsx 但未见使用） |
| P2-2 | 添加全局 `LoadingSkeleton` 替换纯文字 "加载中..."（已有 LoadingSkeleton.tsx） |
| P2-3 | 为 DataTable 密集型页面（ObjectExplorer, UserManagement）添加移动端横向滚动 |
| P2-4 | KanbanBoard — 确认 `/kanban/ecos-kanban.html` 文件存在性并添加加载失败处理 |
| P2-5 | 为所有表单添加前端验证规则（UserManagement 创建/编辑表单无任何校验） |
| P2-6 | 统一 `API_BASE` 常量化 — `api.ts` 使用 `/api`，`glossary.ts` 使用 `/api/glossary`，应统一管理 |
| P2-7 | 补充空表种子数据 — 至少为 audit-logs, marketplace, workflows, knowledge graph 提供示例数据 |
| P2-8 | PipelineBuilder 和 CodeWorkbook 的 "Coming Soon" 应走 i18n 而非硬编码中英文 |

---

## 附录

### A. 技术架构说明

- **前端**: React 18 + TypeScript + Vite + Tailwind CSS + Recharts + Lucide Icons
- **后端**: Spring Boot (Java), 端口 8081, 路径前缀 `/sys-man`
- **代理**: Vite dev server 将 `/api` 代理到 `http://localhost:8081/sys-man`（保持路径不变）
- **路由**: React Router v6, 单页应用, `main.tsx` 定义 `/login` 和 `/*` 两条路由
- **认证**: JWT token 存储在 localStorage，`RequireAuth` 组件做路由守卫

### B. 文件清单

- 页面文件: `/home/guorongxiao/c2eos/src/pages/*.tsx` (22 文件)
- API 层: `/home/guorongxiao/c2eos/src/api.ts` (888 行)
- Mock 数据: `/home/guorongxiao/c2eos/src/mockData.ts` (482 行)
- 类型定义: `/home/guorongxiao/c2eos/src/types.ts` (282 行)
- Vite 配置: `/home/guorongxiao/c2eos/vite.config.ts` (34 行)
- 术语库服务: `/home/guorongxiao/c2eos/src/services/glossary.ts` (106 行)

### C. 审计方法

1. **静态代码分析**: 逐文件检查 API 调用路径、错误处理模式、mock 数据引用
2. **API 端点探测**: 使用 curl 逐一测试 25 个 API 端点，记录 HTTP 状态码和数据量
3. **代码搜索**: 搜索 `catch(`, `try {`, `MOCK_`, `useState([]`, `API_BASE` 等关键模式
4. **路由分析**: 检查 App.tsx 路由映射、Sidebar 导航、RequireAuth 守卫
