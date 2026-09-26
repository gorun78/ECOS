# 知识工作台模块关系重构 — 6 页面平面化 产品需求文档

> 来源: PM 初稿 | 日期: 2026-09-24 | 责任人: PM Agent
> 版本: v1.0.0-draft（第二门禁待确认）
> 追溯: 第一门禁需求分析摘要（`docs/engine-kb/knowledge-workbench-refactor-prd-v0.1.md`）+ 复杂度评估 36/60 L2+ + 原型 `c:\Users\guoro\Desktop\ECOS-Knowledge-Workbench-Prototype-v1.0\index.html`
> 上游: `docs/engine-kb/knowledge-navigation-design.md` v1.1（批次 2 已落地 §7 #1-#3）+ 铁律 §0.5 三工作台 / §2.4 安全 / §4.1 主题 / §4.3 i18n / §3.1 只加不删 / §5.3 ≤5 Task
> 配套规范: `.trae/rules/文档编写规范.md` R1~R13 / `frontend-dev-rules.md` / `backend-dev-rules.md`

## 1. 概述

### 1.1 背景

知识工作台（kb-engine :18086 + 前端 KnowledgeView）自 PMO-A 知识导航能力增强交付以来，前端已累积 5 组 14 个 Tab，语义交叉严重：

- **抽取链路 4 个 Tab 分散**：`datasync` / `streaming` / `review` / `update` 都属 I→K 链路但分属 manage 组
- **治理 4 个 Tab 语义重叠**：`rules` / `eval` / `lifecycle` / `compliance` 都属治理但用户需 4 次切页
- **资产管理 `classification` 不够突出**：分类/标签/资产表混在 manage 组末尾
- **检索场景割裂**：`rag`（问答）vs `graph_explorer`（图谱）分属 retrieve 组但业务耦合
- **无企业知识（Wiki / md 文档）页面**：用户上传 md 文档需绕 `DocumentUploadTab`（未挂导航），无统一入口
- **引擎配置面向运维**：`engine_config` 不应出现在业务用户工作台侧栏

用户提供了界面原型 v1.0（93 行 HTML），明确 7 个平铺侧栏页面。本 PRD 按用户 4 条微调收敛为 **6 页面**（撤知识中心），保留原型的核心心智模型。

### 1.2 目标

**业务目标**（量化）：
- 前端 Tab 个数 14 → **6 页面**（侧栏平铺，无嵌套分组）
- 用户从侧栏到达任一功能 **≤ 1 次点击**（现部分功能需 2 次：选组 → 选 Tab）
- 6 页面 Tab 文件数从 18 个（有效 14 + 备用 6）收敛为 **6 个 Page 文件**（撤下 12 个路由保留）
- 后端 Controller endpoint 数 **不变**（119），仅做文档化分组

**用户目标**：
- 知识管理员：md 导入 → 抽预览 → 审核发布 → 治理指标，1 页 1 链
- 业务用户：浏览知识资产 + 图谱探索（categoryIds 后端下沉）
- 工程师：选 DW 层已发布映射 → 提交抽取 → 看图/向量双写状态

### 1.3 范围

**包含**（12 功能点，详见 §3）：
- KnowledgeView 骨架重构（sidebar 平铺 6 按钮，替代 5 组 14 Tab）
- 6 页面 Page 文件（Overview / AssetList / Extraction / Graph / Wiki / Govern）
- 撤下 8 个 Tab 导航但路由保留（R9 只加不删）
- 后端 Controller 文档化分组（常量类 + Javadoc，endpoint 不动）
- i18n key 子集标注（不清理 2010，仅标 `@deprecated`）
- 设计文档 v2.0（6 页面结构定稿）
- 回归编译 / 类型 / 6 页面 console err / 4 用例

**不包含**（8 件明确排除）：
1. **知识中心 / RAG 问答页面**（用户裁决 P3 后置）
2. **引擎配置页**（撤导航，归 `engine-knowledge` 监控页）
3. **本体模型 Tab**（归本体工作台）
4. **图谱构建 Tab**（归 runtime-task）
5. **术语表 Tab**（P3 后置）
6. **在线 Wiki 编辑器**（用户裁决 md 导入，非编辑器）
7. **后端表结构变更**（V155/156/157 已落，架构图不动）
8. **前端 dev server 端口变更**（沿用 3000 + gateway 8080）

### 1.4 成功标准

| 指标 | 目标值 | 测量方式 |
|:--|:--:|:--|
| 侧栏按钮数 | 6 | 视觉审查 |
| 页面文件数 | 6 | `ls src/pages/knowledge/pages/` |
| 后端 endpoint 不变 | 119 | `grep -c "@.*Mapping" controller/*.java` 前后对比 |
| `mvn install -Penterprise -pl kb-engine-impl -am -DskipTests` | SUCCESS | CI |
| `npx tsc --noEmit` | 0 新增错误 | CI |
| 6 页面 console err | 0 | 手动 vite :3000 截图 |
| i18n key 无硬编码中文 | 0 | 正则扫 6 Page 文件 |
| 后端 Controller 分组注释 | 21 个全标 | grep 注释数 |
| 撤下 Tab 路由保留 | 12 个 | `grep -c "Route" main.tsx` |

## 2. 用户分析

### 2.1 目标用户

| 角色 | 描述 | 主要场景 |
|:--|:--|:--|
| 知识管理员 | 企业知识运营（主用户） | md 导入 / 审核 / 发布 / 看治理指标 |
| 业务用户 | 产品 / 开发 / 运营 | 浏览知识资产 + 探索图谱 |
| 数据/本体工程师 | 跨工作台 | 选 DW 层契约 → 提交抽取 |
| 系统管理员 | 运维 | 走 engine-knowledge 监控页（不在本工作台） |

### 2.2 用户故事地图

```
┌────────────────┬────────────────┬────────────────┬────────────────┐
│   录入         │   抽取         │   治理/发布    │   消费          │
├────────────────┼────────────────┼────────────────┼────────────────┤
│ 管理员上传 md   │ 系统抽预览      │ 审核通过       │ 业务用户检索    │
│ 管理员选 DW 契约│ 工程师选映射    │ 管理员看指标  │ 工程师查图    │
│ 管理员建资产  │ 系统图+向量双写  │ 管理员看规则  │ 工程师问答  │
└────────────────┴────────────────┴────────────────┴────────────────┘
      F6/F3/F2         F4/F5/F12          F7                  F5/F1
```

## 3. 功能需求

### 3.1 功能点清单

| ID | 功能名称 | 优先级 | 来源 |
|:--|:--|:--:|:--|
| F1 | KnowledgeView 侧栏导航重构 | P0 | 第一门 R-001 |
| F2 | OverviewPage — KPI + 流水线 + 待办 | P0 | 第一门 R-002 |
| F3 | AssetListPage — 资产表 + 图/向量状态 | P0 | 第一门 R-003 |
| F4 | ExtractionPage — DW 层源头 + 文档导入 | P0 | 第一门 R-004 |
| F5 | GraphPage — 图谱可视化 + categoryIds 下沉 | P0 | 第一门 R-005 |
| F6 | WikiPage — md 导入 + 预览 | P0 | 第一门 R-006 |
| F7 | GovernPage — 5 in 1 治理 | P0 | 第一门 R-007 |
| F8 | 撤下 Tab 路由保留 | P1 | 第一门 R-008 |
| F9 | i18n key 子集标注 | P1 | 第一门 R-009 |
| F10 | 后端 Controller 分组文档化 | P2 | 第一门 R-010 |
| F11 | 设计文档 v2.0 归档 | P2 | 第一门 R-011 |
| F12 | 回归 & 质量门 | P0 | 第一门 R-012 |

### 3.2 详细需求

#### F1 KnowledgeView 侧栏导航重构

**描述**：`KnowledgeView.tsx` 从 5 组 14 Tab（`typesAndConstants.ts` 的 `KNOWLEDGE_TAB_GROUPS`）重构为 6 平铺侧栏页面。

**用户故事**：作为业务用户，我打开知识工作台看到 6 个清晰按钮，1 次点击即达目标页面。

**功能详情**：
- 左侧 sidebar 232px（对齐原型 `.sidebar` 宽度），深色 `#111827`
- 侧栏项：
  ```
  ◆ ECOS Knowledge
  ─────────────────
  [Knowledge Workbench]
  · 知识总览        (ic: LayoutDashboard)
  · 知识资产        (ic: Database)
  · 知识抽取        (ic: ArrowDownUp)
  · 知识图谱        (ic: Network)
  · 企业知识        (ic: BookOpen)
  · 知识治理        (ic: ShieldCheck)
  ─────────────────
  [Global 入口]
  · 任务中心 ↗
  ```
- 顶栏：面包屑 `ECOS / {current}` + 右侧任务状态 / i18n 切换 / Avatar
- 当前页高亮：`box-shadow: inset 3px 0 var(--primary)` 原型同款
- `KnowledgeView.tsx` 拆为 `views/KnowledgeView.tsx`（布局骨架）+ `pages/{OverviewPage, AssetListPage, ExtractionPage, GraphPage, WikiPage, GovernPage}.tsx`
- 原 14 Tab 组件（`tabs/*.tsx`）**不删除**（R9 只加不删），但不再从 `KNOWLEDGE_TAB_GROUPS` 挂载

**业务流程**：
```
User 点侧栏 → useState activePage → renderPage(activePage) → Page 组件内拉 API
```

**验收标准**：
- [ ] 侧栏 6 按钮全 i18n（`knowledge.nav1.page_*` 8 个 key）
- [ ] 原 14 Tab `tabs/*.tsx` 文件保留，路由（deep-link `?tab=xxx`）仍可达
- [ ] 撤下 8 Tab 仍可通过 `#/knowledge_view?tab=rag` 等访问
- [ ] 主题 `useTheme().styles` 全用，0 硬编码色值（§4.1）
- [ ] 路由表 6 个 + 撤下 8 个 = 14 个 `Route`（`main.tsx` 不删）

**来源**：第一门 R-001

#### F2 OverviewPage — KPI + 流水线 + 待办

**描述**：复用现有 `OverviewDashboard.tsx` 内容 + 扩展 KPI 6 指标 + 流水线 5 步 + 待办 3 项。

**用户故事**：作为知识管理员，打开首屏看到 6 个 KPI + 流水线状态 + 3 个待处理事项，1 屏掌握状态。

**功能详情**：
- **KPI 6 指标**（对齐原型 `.kpis` grid 6 列）：
  | 指标 | 数据源 | API |
  |:--|:--|:--|
  | 知识资产数 | `articleMapper.count()` | `GET /knowledge/api/index-status` |
  | 图谱实体 | `nodeMapper.count()` | `GET /knowledge/api/index-status` |
  | 图谱关系 | `edgeMapper.count()` | `GET /knowledge/api/index-status` |
  | 向量索引 | `embeddingMapper.count()` | `GET /knowledge/api/index-status` |
  | 待审核 | `fetchExtractCandidates` 待审数 | `GET /knowledge/extract/candidates?status=pending` |
  | 抽取任务 | `fetchStructuredJobs` 运行中 | `GET /knowledge/structured/jobs?status=running` |
- **知识生产流水线**（5 步横条）：`本体映射 → 数据抽取 → 知识候选 → 审核发布 → KG / Vector`（I → K）
- **待处理事项**（3 行）：AI 抽取候选 X 条 / Wiki 待审核 Y 条 / 索引失败 Z 条，链接跳 F7
- **最近知识资产表**（5 列）：名称 / 类型 / 状态 / 来源 / 更新时间（复用 `knowledgeApi.fetchArticles`）

**验收标准**：
- [ ] 6 KPI 全调用真实 API，无假数据
- [ ] KPI 卡片数字字体 25px / 标签 12px + 灰色（原型 `.kpi`）
- [ ] 流水线 5 step + 4 arrow，蓝色 `#1d4ed8` chip
- [ ] 「查看任务」按钮跳统一任务中心（toast 提示"已打开统一任务中心"）

**来源**：第一门 R-002

#### F3 AssetListPage — 资产表 + 图/向量状态

**描述**：合并 `DatasyncTab` + `ClassificationTab` 能力，资产表新增「图谱实体 / 向量索引」状态列，统一展示 DW 层契约来源 + 向量/图异步状态。

**用户故事**：作为业务用户，我想一页看清所有知识资产（Wiki / 实体 / 文档 / 知识集），每一行是否已入图谱 + 向量。

**功能详情**：
- **筛选工具栏**：关键词 input + 类型 select（全部 / Wiki / 实体 / 文档 / 知识集）+ 状态 select（全部 / 草稿 / 审核中 / 已发布）
- **资产表 8 列**（对齐原型 `.table`）：
  | 列 | 数据源 |
  |:--|:--|
  | 知识资产 | `article.title`，点击进详情 |
  | 类型 | `article.assetType` |
  | 状态 | `article.status`，badge 绿/橙/灰 |
  | 语义关联 | `article.relatedEntities[]` 逗号串 |
  | 来源 | `article.sourceType`（人工 / 结构化抽取 / AI / 文档导入）|
  | **图谱实体** | `article.graphStatus`（✓ 已入 / — 未入），调 `KnowledgeGraphService` 检查 |
  | **向量索引** | `article.vectorStatus`（✓ 已入 / ⏳ 同步中 / — 未入），调 `PgVectorSupport` |
  | 更新时间 | `article.updatedAt` |
- **Nav 过滤**：复用 `fetchNavProducts` 接口的 `categoryIds` / `tags` 参数（PMO-A 已落地）
- **domain 切换**：顶栏 domain Select（PMO-C 已落地 `fetchNavDomains`）
- **右侧面板**（可选，≥1280px）：选中资产 → 详情 + 图谱上下文 + 语义实体 chips

**业务流程**：
```
User 输入关键词/选类型/选状态/选 domain → GET /nav/products?... → 表格 8 列渲染
→ 点击行 → 右侧详情面板 + 图谱上下文（调 KnowledgeGraphService.getNeighbors）
```

**验收标准**：
- [ ] 8 列全表渲染，badge 颜色随状态
- [ ] 图/向量状态列独立查（不强耦合主资产表，允许 N+1 调一次 status 集合接口）
- [ ] 选 domain 切换时筛选器 reset
- [ ] 空状态提示 `knowledge.asset.empty`

**来源**：第一门 R-003

#### F4 ExtractionPage — DW 层源头 + 文档导入

**描述**：合并 `StreamingTab` + `VectorIndexTab` + `DocumentUploadTab` + `DatasyncTab`（DW 源）。**源头标注**：所有抽取的唯一源头 = DW 层（结构化映射契约 / 文档导入）。

**用户故事**：作为数据工程师，我想选已发布的 DW 层映射契约 → 提交抽取 → 看实体/关系/向量 3 种结果，非结构化走文档导入路径。

**功能详情**：
- **左栏（结构化 I→K）**：
  - 映射契约 select（`fetchOntologyMappings` 已发布列表）
  - 抽取目标 3 checkbox：☑ 图谱实体/属性 ☑ 图谱关系 ☑ 向量索引（文本化属性）
  - 「预览并提交」按钮 → POST `/knowledge/structured/extract/submit`
- **右栏（非结构化 Document→K）**：
  - 文档导入（`uploadDocumentChunked` 已落地）
  - 4 项能力 badge：文档导入 / OCR / 分块 / 实体关系抽取
  - 导入后走 `TRANSFORM_DOC_PARSE` → `ecos_dw.doc/doc_chunk` → 知识审
- **抽取预览**（bottom 3 KPI 卡片）：实体 X / 关系 Y / 候选知识 Z（原型 `.grid-3`）
- **标注**：`title` 属性下挂 `badge blue` 显示 `I → K` / `Document → K`（对齐原型）

**验收标准**：
- [ ] 左右栏 1.25fr / .75fr 网格（原 `.two`）
- [ ] 映射契约 select 仅显示 `status=published`
- [ ] 抽取目标 3 checkbox 独立勾选
- [ ] 「预览并提交」调真实 API（`POST /knowledge/structured/extract/submit`），toast 成功/失败
- [ ] 文档导入走 `uploadDocumentChunked` 分片

**来源**：第一门 R-004

#### F5 GraphPage — 图谱可视化 + categoryIds

**描述**：复用 `GraphExplorerTab.tsx` 主体逻辑，重命名 + 对齐原型页 4。

**用户故事**：作为业务用户，我想浏览企业知识图谱，按 domain + categoryIds 后端下沉过滤（本 goal 已落地 `12a20d6`），查实体 / 关系。

**功能详情**：
- 顶部搜索栏：input + 实体/关系 select + 搜索按钮
- 图谱画布（深色 `#0f172a`，`.graph` 高度 520px）：`GraphCanvas` 组件
- 左工具栏：全文搜索 / 路径查找 / domain select / category checkbox（后端下沉）/ 邻居度 / 图例
- 右详情面板：节点属性 / 关系 / 操作按钮（展开邻居 / 设路径源）
- 底栏：`实体 N · 关系 M · 当前视图 K 个节点`

**验收标准**：
- [ ] 后端 `categoryIds` 透传 `fetchGraph(domain, categoryIds)`（已落地）
- [ ] 前端 checkbox 变更 → 不调前端二次过滤，walk 后端
- [ ] domain select 从 `fetchNavDomains()` 动态（已落地）
- [ ] 节点 detail 面板保留（现有逻辑不动）

**来源**：第一门 R-005

#### F6 WikiPage — md 文档导入 + 预览

**描述**：**新建**企业知识页面。**核心裁决**：md 文档导入（非在线编辑器），三栏布局（左 tree / 中 md 预览只读 / 右 metadata）。

**用户故事**：作为知识管理员，我想把项目复盘 / 交付规范的 md 文件批量导入，看 markdown 渲染结果 + 元数据（语义实体 chips / 相关知识 / 图谱关系）。

**功能详情**：
- **左栏（知识空间 tree，220px）**：`.tree` 一级节点「产品知识」「技术知识」「项目知识」「行业知识」「制度与规范」+ 二级具体 md 文件列表（`📄` 前缀）
- **中栏（md 预览，read-only）**：
  - 顶栏：状态 badge（已发布/草稿）+ 版本 v1.8 · 作者 · 日期
  - `<h2>` 标题 + 富文本 body（`react-markdown` 渲染，含 `@实体` chips 高亮）
  - **无编辑能力**（非 textarea / 无 toolbar 的 B/H1/链接 等）
- **右栏（Knowledge Context，280px）**：
  - 语义实体 chips（`@项目` / `@客户` / `@数据实施流程`）
  - 相关知识链接（3 个）
  - 图谱关系（项目 → 遵循 → 交付规范 等）
  - 「查看图谱上下文」按钮 → 跳 F5 GraphPage 带 entity 锚点
- **顶部**：「＋ 新建」按钮 → 实际是「上传 .md 文件」对话框（walk `uploadDocumentChunked` 路径，但走 md 专属），**非打开编辑器**

**md 导入流程**：
```
User 点「上传 .md」 → file input .md 扩展名校验 → POST /api/v1/knowledge/articles/import-md
→ 后端落 kb_document（layer=RAW, zone=UNSTRUCTURED, resource_type=FILE）→ 触发 TRANSFORM_DOC_PARSE
→ 落入 ecos_dw.doc/doc_chunk → 知识审（F7 GovernPage 审核 feed）
```

**验收标准**：
- [ ] 上传只允许 .md（白名单过滤）
- [ ] 中栏 `<Markdown>` 渲染，`@实体` chip 走 `knowledge.assets.entity_ref`
- [ ] 导入走 `POST /knowledge/articles/import-md`（新 endpoint，见 F3 后端）
- [ ] 右栏「查看图谱上下文」跳 `#/knowledge_view?page=graph&focusEntity={id}`
- [ ] 0 硬编码中文，全 `knowledge.wiki.*` i18n key（约 25 个新 key）

**来源**：第一门 R-006

#### F7 GovernPage — 5 in 1 治理

**描述**：合并 `ReviewTab` / `LifecycleTab` / `ComplianceRuleTab` / `KnowledgeRuleRepositoryTab` / `KnowledgeEvalTab` 为 1 页 3 区。

**用户故事**：作为知识管理员，开治理页看到审核队列 + 生命周期 + 质量指标 + 规则仓库 + 评测运行，1 页 5 维考核。

**功能详情**：
- **上区 — AI 候选审核**（原 review Tab，双 diff 卡片并列）：
  - 左卡：候选 feed（来源 / 抽取 / 置信度 / 关联实体）+ 确认发布 / 退回按钮
  - 右卡：生命周期 pipeline `草稿 → 抽取 → 审核 → 发布`（4 步）
- **中区 — 质量监控**（原 compliance Tab 部分）：
  - 3 指标表：来源完整率 / Ontology 关联率 / 索引成功率
  - 状态 badge（正常/告警/异常）
- **下区 — 治理策略 + 评测**（原 rules + eval Tab）：
  - 左：规则仓库表格（`fetchRules`）+ CRUD 4 按钮
  - 右：评测运行面板（`runEval` + 结果历史）

**验收标准**：
- [ ] 3 区 grid 布局（上 1fr / 中 1fr / 下 1.25fr）
- [ ] 审核 feed 点击「确认发布」调 `POST /knowledge/lifecycle/transition`
- [ ] 规则仓库 CRUD 走 `ExpertRuleController`（已落地）
- [ ] 评测 `runEval` 返回异步任务 id，定时 poll
- [ ] 5 Tab 代码合入 1 文件 `GovernPage.tsx`（≤ 现有 5 Tab 总和 - 20%）

**来源**：第一门 R-007

#### F8 撤下 Tab 路由保留

**描述**：8 个 Tab 从侧栏撤下但路由保留（R9 只加不删）。

**撤下清单**（路由 `#/knowledge_view?tab=xxx` 仍可 deep-link）：
| Tab id | 文件 | 处置 |
|:--|:--|:--|
| `rag` | `RagTab.tsx` | P3 后置 |
| `engine_config` | `EngineConfigTab.tsx` | 归 engine-knowledge 监控页 |
| `ontology_model` | `OntologyModelTab.tsx` | 归本体工作台 |
| `graph_builder` | `GraphBuilderTab.tsx` | 归 runtime-task |
| `glossary` | `GlossaryTab.tsx` (0.5KB) | P3 后置 |
| `sync` | `SyncTab.tsx` | 与 datasync 重复 |
| `data_import` | `DataWorkbenchImportTab.tsx` | 归数据工作台 |
| `vector_index` / `doc_upload` / `extraction_streaming` | 备用 Tab | 能力已合入 F3/F4/F5 |

**验收标准**：
- [ ] `main.tsx` 保留全部 14 个 `Route`（6 + 8）
- [ ] 撤下 Tab 在侧栏不可见（`KNOWLEDGE_TAB_GROUPS` 里不挂）
- [ ] 直接 `#/knowledge_view?tab=rag` 可达，顶部显示 `⚠️ 已撤下，此链接保留供 P3 参考`

**来源**：第一门 R-008

#### F9 i18n key 子集标注

**描述**：`knowledge/zh-CN.json` ≈ 2010 key，**不清理**（R9 只加不删），但 6 Page 消费子集内所有 key 加 `@active` 注释，废弃 key 加 `@deprecated` JSON 注释（JSON 不支持注释 → 用 `//` 之外的 `"_depr": true` 字段标记，工具友好）

**验收标准**：
- [ ] 6 Page 文件 grep 命中 key 集合 → 全部在 i18n 文件内存在
- [ ] 0 硬编码中文（正则 `[\u4e00-\u9fa5]{2,}` 扫 6 文件）
- [ ] `_depr` 标记 JSON 字段格式合法（`npx tsc` + `jq .` 不报错）

**来源**：第一门 R-009

#### F10 后端 Controller 分组文档化

**描述**：`engine/kb-engine/kb-engine-impl/.../` 21 个 Controller 不改 endpoint，只：
1. 新增 `KbEngineModuleRegistry.java` 常量类（`enum Module { OVERVIEW, ASSETS, EXTRACT, GRAPH, WIKI, GOVERN, CENTER_P3 }`，每个 module 挂一个 `List<ControllerClass>`）
2. 每个 Controller 类 Javadoc 头部加 `@group {MODULE}` 注解（纯注释，无 runtime 反射，避免引入新依赖）
3. `GET /api/v1/knowledge/nav/modules` 新 endpoint（`KbEngineHealthController` 加），返回 `Map<String, List<String>>` 模块 → controller class 名（供前端调试）

**验收标准**：
- [ ] 21 Controller 全有 `@group` Javadoc 注释
- [ ] `KbEngineModuleRegistry` 7 个模块（含 CENTER_P3）
- [ ] `GET /nav/modules` 返回 JSON，无业务逻辑（只读常量）
- [ ] 不引入新 maven 依赖

**来源**：第一门 R-010

#### F11 设计文档 v2.0

**描述**：`docs/engine-kb/knowledge-navigation-design.md` v1.1 → v2.0。

**变更**：
- §一 定位：消费方从 4 个（分类 Tab / RAG 实验台 / 图谱探索 / 未来 Copilot）改写为「6 页面中的 5 个」（撤 RAG）
- §七 未做项：#1-#3 标 ✅ 2026-09-24 PMO-C（已落 commit `12a20d6`），#4/#5 P3 不动
- §八 **新增「6 页面结构定稿」**：侧栏 6 按钮 + 各页 Page 文件路径 + 数据源 API + 铁律引用

**验收标准**：
- [ ] 头部 3 行元信息 + 批次 3 变更表（F1~F12 映射 commit hash）
- [ ] §八含 6 个 Page 的「数据源 → API 端点 → i18n key 前缀」三元组
- [ ] R1/R2/R3 红线全满足（文件名小写+日期 / 头部 3 行 / 版本 v2.0）

**来源**：第一门 R-011

#### F12 回归 & 质量门

**描述**：编译 + 类型 + 6 页面 console err + 4 用例（对齐 design.md §六）。

**4 用例**：
1. POST /rag 不带 categoryIds → 与历史一致（全量）
2. POST /rag 带 categoryIds → 返回全在子树下
3. POST /rag 带空数组 `[]` → 仍走全量（null/empty 等价）
4. 前端 6 页面 Tab 未勾任何目录 → 调 API 不带 categoryIds（静态审查）

**验收标准**：
- [ ] `mvn install -Penterprise -pl engine/kb-engine/kb-engine-impl -am -DskipTests` SUCCESS
- [ ] `npx tsc --noEmit`（ecos_frontend）0 新增错误
- [ ] 6 页面 vite :3000 启动后各页 console err = 0
- [ ] 4 用例回归 PASS（静态审查 + 编译级 + 手动 vite 截图 3 张）
- [ ] Reviewer 铁律 grep（禁 5 种违规：禁 `select *` / 禁 Map 入参 / 禁硬编码中文 / 禁 `var` / 禁行内样式）

**来源**：第一门 R-012

## 4. 非功能需求

### 4.1 性能

| 指标 | 要求 |
|:--|:--:|
| 页面切换 | < 200ms（SPA 已懒加载）|
| 6 页面首屏 | < 2s（含 API 调用）|
| 后端 `/nav/modules` | < 50ms（纯常量表）|
| 资产表「图/向量状态」列 | 单次 API 批量查（N+1 拆为 1 + 1，不循环）|

### 4.2 安全

- 所有接口需 Bearer token（gateway :8080 鉴权已覆盖）
- md 导入走 `KafkaTemplate` 发 `ecos.audit`（§2.5-5）
- 图谱 categoryIds 过滤参数化防 SQL 注入（`12a20d6` 已参数化 IR05）
- ABAC：写操作（审核 / 删除）走 `checkAbac`（12 处已落地，本 PRD 无新增）
- 敏感列脱敏（ST03/04/05 已落地，本 PRD 无新增）

### 4.3 兼容性

- 浏览器：Chrome / Firefox / Safari 最近 2 版本
- 主题：浅色 + 深色（`useTheme().styles` 双套）
- i18n：zh-CN / en 双语（新增 key 各 1 份）
- 浏览器 1280px / 1920px / iPad Pro 11 三档响应式

### 4.4 铁律约束

| 铁律 | 本 PRD 落实 |
|:--|:--|
| §0.5 三工作台 | 本 PRD 仅改 kb-engine，不动 data / ontology |
| §1.6 即时/定时任务 | F4 抽取走 runtime-task（定时）+ submitTask+executeTask（即时） |
| §2.4 安全 | 写操作 ABAC + Kafka audit + I/O 脱敏已覆盖 |
| §2.5-2 LLM 走 gateway | 无新增 LLM 调用（F4 抽取走既有 pipeline）|
| §3.1 PG 只加不删 | 0 表结构变更，仅 V{n} 加 `kb_nav_*.md_import_type` 一列（如有需要）|
| §4.1 主题 | 0 硬编码色值 |
| §4.2 lucide-only | 全部 icon 走 lucide-react |
| §4.3 i18n | 0 硬编码中文 |
| §5.2/§5.3 ≤5 Task | 单 Sub-Agent 每指令 ≤ 5 Task（本 PRD 12 Task 拆 3 批）|

## 5. 验收标准（总表）

| 功能 | 核心验收 |
|:--|:--|
| F1 侧栏 | 6 按钮 + 撤 8 路由保留 + 主题 0 硬编码 |
| F2 Overview | 6 KPI 真实 API + 流水线 5 步 + 3 待办 |
| F3 Asset | 8 列资产表 + 图/向量状态 |
| F4 Extract | DW 源头标注 + 结构化/非结构化分栏 + 3 KPI 预览 |
| F5 Graph | categoryIds 后端下沉 + domain 动态 |
| F6 Wiki | md 导入 + 预览 + 右栏 graph 锚点 |
| F7 Govern | 5 in 1 合 1 页 + 审核 feed 真实调 |
| F8 撤下 | 12 路由全保留 + deep-link 可达 |
| F9 i18n | 0 硬编码 + `_depr` 标注合法 |
| F10 后端 | 21 Controller `@group` 注释 + `/nav/modules` |
| F11 文档 | v2.0 头部 3 行 + 批次 3 变更表 + §八 6 页面 |
| F12 回归 | mvn + tsc + 6 页 console + 4 用例 |

## 6. 风险分析

| ID | 描述 | 概率 | 影响 | 缓解 |
|:--|:--|:--:|:--:|:--|
| R-001 | 14 Tab → 6 Page 合并过程中丢代码 | 中 | 高 | R9 不删，合并后旧文件保留，代码 diff 走 reviewer |
| R-002 | 资产表「图/向量状态」列 API 未落地 | 中 | 低 | 后端 F10 补 1 个批量状态 endpoint（≤ 50ms）|
| R-003 | md 导入新 endpoint 未设计 | 低 | 中 | 复用 `uploadDocumentChunked` 路径，加 `content-type: markdown` 分支 |
| R-004 | i18n 清理误删活跃 key | 中 | 中 | 不清理，只加 `@deprecated` JSON 字段（R9 只加不删）|
| R-005 | Controller `@group` 注释与代码漂移 | 低 | 低 | CI 加 lint 校验 controller 类头 `@group` 注释存在 |
| R-006 | 6 Page 文件膨胀（> 1000 行）| 中 | 中 | 每 Page 内拆组件（`components/` 子目录），本地 5 上限 |
| R-007 | 撤下 Tab 的 deep-link 被外部引用 | 低 | 低 | 撤下 Tab 顶部加 warn banner + toast 提示 |

## 7. 附录

### 7.1 术语表

| 术语 | 定义 |
|:--|:--|
| 知识资产（Knowledge Asset）| Wiki / 实体 / 关系 / 文档 / 知识集 5 类对象的统称 |
| DW 层 | 数据湖 `CURATED` 层，知识抽取唯一源头 |
| 图谱双写 | 抽取时同步写 Neo4j 图 + pgvector 向量 |
| 知识导航（Knowledge Nav）| kb_nav_category / kb_nav_tag / kb_nav_article_rel 3 表体系 |
| 知识中心（Knowledge Center）| RAG 问答入口（P3 后置，本 PRD 不含）|

### 7.2 参考文档

- [知识导航设计文档 v1.1](./knowledge-navigation-design.md)
- [架构铁律 §1.6 即时/定时任务统一入口](../../.trae/rules/架构铁律.md)
- [数据湖存储分层规范](../../.trae/rules/数据湖存储分层规范.md)
- [文档编写规范 R1~R13](../../.trae/rules/文档编写规范.md)
- [界面原型 v1.0](c:\Users\guoro\Desktop\ECOS-Knowledge-Workbench-Prototype-v1.0\index.html)
- [需求分析摘要（第一门产出）](./knowledge-workbench-refactor-prd-v0.1.md)

### 7.3 追溯表（功能 → 需求来源）

| 功能 | 第一门条目 | 验收数 |
|:--|:--|:--:|
| F1 | R-001 | 5 |
| F2 | R-002 | 4 |
| F3 | R-003 | 4 |
| F4 | R-004 | 5 |
| F5 | R-005 | 4 |
| F6 | R-006 | 5 |
| F7 | R-007 | 5 |
| F8 | R-008 | 3 |
| F9 | R-009 | 3 |
| F10 | R-010 | 4 |
| F11 | R-011 | 3 |
| F12 | R-012 | 5 |

---

**【第二门禁 — PRD 初稿确认】**

请逐条确认：

1. **背景**（14 Tab → 6 Page，理由充分）准确？
2. **12 功能点**详述（每功能 5 小节：描述 / 用户故事 / 功能详情 / 业务流程 / 验收标准）完整？
3. **验收标准可测试**（每项 checkbox 客观可验）？
4. **非功能需求**（性能 / 安全 / 兼容 / 铁律）符合预期？
5. **7 风险 + 缓解**完整？
6. **1.4 成功标准**量化指标合理？

**回复**：
- 「**第二门确认**」→ 进入第三门 PRD 评审
- 「**需要修正 XX**」→ 基于反馈修订
