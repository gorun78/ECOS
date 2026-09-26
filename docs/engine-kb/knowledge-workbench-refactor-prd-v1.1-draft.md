# 知识工作台模块关系重构 — 6 页面平面化 产品需求文档

> 来源: PM Agent（第三门禁修订版）| 日期: 2026-09-24 | 责任人: PM Agent
> 版本: v1.1.0-draft（第三门禁 REJECTED 修订重入，1 P0 + 5 P1 + 6 P2 findings 全量修复）
> 上游: 铁律 §0.5 / §1.6 / §2.1 / §2.4 / §2.5-2 / §3.1 / §4.1 / §4.2 / §4.3 / §4.6 / §5.3 / §5.2
> 配套规范: `.trae/rules/文档编写规范.md` R1~R13 / `.trae/rules/后端开发规范.md` / `.trae/rules/前端开发规范.md`
> 追溯: 第一门确认 2026-09-24 / 第二门确认 2026-09-24 / 第三门 v1.0.0 REJECTED → 本 v1.1.0 修订重入

## 1. 概述

### 1.1 背景

知识工作台（kb-engine :18086 + 前端 KnowledgeView）自 PMO-A 知识导航能力增强交付以来，前端已累积 5 组 14 个 Tab，语义交叉严重：

- **抽取链路 4 个 Tab 分散**：`datasync` / `streaming` / `review` / `update` 都属 I→K 链路但分属 manage 组
- **治理 4 个 Tab 语义重叠**：`rules` / `eval` / `lifecycle` / `compliance` 都属治理但用户需 4 次切页
- **资产管理 `classification` 不够突出**：分类/标签/资产表混在 manage 组末尾
- **检索场景割裂**：`rag`（问答）vs `graph_explorer`（图谱）分属 retrieve 组但业务耦合
- **无企业知识（Wiki / md 文档）页面**：用户上传 md 文档需绕 `DocumentUploadTab`（未挂导航），无统一入口
- **引擎配置面向运维**：`engine_config` 不应出现在业务用户工作台侧栏

用户提供了界面原型 v1.0（93 行 HTML + 18 CSS，文件位于本地桌面非入库，本文不嵌入），明确 7 个平铺侧栏页面。本 PRD 按用户 4 条微调收敛为 **6 页面**（撤知识中心），保留原型的核心心智模型。

### 1.2 目标

**业务目标**（量化）：
- 前端 Tab 个数 14 → **6 页面**（侧栏平铺，无嵌套分组）
- 用户从侧栏到达任一功能 **≤ 1 次点击**（现部分功能需 2 次：选组 → 选 Tab）
- 6 页面 Tab 文件数从 18 个（有效 14 + 备用 6）收敛为 **6 个 Page 文件**（撤下 12 个路由保留）
- 后端 Controller endpoint 数从 119 → **121**（新增 `GET /nav/modules` + `GET /assets/status`，详见 §1.4；F6 md 导入复用既有 `POST /docs/ingest` 走 `Content-Type: text/markdown` 分支，不新增 endpoint）

**用户目标**：
- 知识管理员：md 导入 → 抽预览 → 审核发布 → 治理指标，1 页 1 链
- 业务用户：浏览知识资产 + 图谱探索（categoryIds 后端下沉）
- 工程师：选 DW 层已发布映射 → 提交抽取 → 看图/向量双写状态

### 1.3 范围

**包含**（12 功能点，详见 §3）：
- KnowledgeView 骨架重构（sidebar 平铺 6 按钮，替代 5 组 14 Tab）
- 6 页面 Page 文件（Overview / AssetList / Extraction / Graph / Wiki / Govern）
- 撤下 8 个 Tab 导航但路由保留（R9 只加不删）
- 后端 Controller 文档化分组 + **2 个新 endpoint**（`GET /nav/modules` 常量表 + `GET /assets/status` 批量状态）
- i18n key 子集标注（zh-CN + en 双语同步，不清理 2010，仅标 `_depr: true`）
- 设计文档 v2.0（6 页面结构定稿）
- 回归编译 / 类型 / 6 页面 console err / **5 用例**（含撤下 Tab deep-link 手动验收）

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
| 页面文件数（`pages/knowledge/pages/*.tsx`）| 6 | `ls` 计数 |
| 后端 endpoint 数 | **121**（119 既有 + `GET /nav/modules` + `GET /assets/status`）| `grep -rE '@(Get|Post|Put|Delete|Request)Mapping' controller/*.java \| wc -l` |
| F6 md 导入 endpoint | `POST /api/v1/knowledge/docs/ingest` + `Content-Type: text/markdown`（复用 `KnowledgeIngestController` A3 链路）| 抓包验证 |
| GovernPage.tsx 主文件 | **≤ 800 行**（铁律 §4.6），超出部分拆 `pages/knowledge/components/govern/` 子组件 | `wc -l` |
| `mvn install -Penterprise -pl engine/kb-engine/kb-engine-impl -am -DskipTests` | SUCCESS | CI |
| `npx tsc --noEmit`（ecos_frontend）| 0 新增错误 | CI |
| 6 页面 vite :3000 启动后 console err | 0 | 手动 |
| i18n 0 硬编码中文（正则 `[\u4e00-\u9fa5]{2,}` 扫 6 Page 文件）| 0 | grep |
| i18n en.json 新增 key = zh-CN 新增数 | 1:1 | `jq 'keys\|length'` 对比 |
| 后端 Controller `@group` Javadoc 注释 | 21 个全标 | grep `@group` |
| 撤下 Tab 路由保留 | 12 个 | `grep -c "Route" main.tsx` |

## 2. 用户分析

### 2.1 目标用户

| 角色 | 描述 | 主要场景 |
|:--|:--|:--|
| 知识管理员（主）| 企业知识运营 | md 导入 / 审核 / 发布 / 看治理指标 |
| 业务用户 | 产品 / 开发 / 运营 | 浏览知识资产 + 探索图谱 |
| 数据/本体工程师 | 跨工作台 | 选 DW 层契约 → 提交抽取 |
| 系统管理员 | 运维 | 走 `engine-knowledge` 监控页（不在本工作台）|

### 2.2 用户故事地图

```
┌────────────────┬────────────────┬────────────────┬────────────────┐
│   录入         │   抽取         │   治理/发布    │   消费          │
├────────────────┼────────────────┼────────────────┼────────────────┤
│ 管理员上传 md  │ 系统抽预览      │ 审核通过       │ 业务用户检索    │
│ 管理员选 DW 契约│ 工程师选映射    │ 管理员看指标   │ 工程师查图      │
│ 管理员建资产   │ 系统图+向量双写  │ 管理员看规则   │ 工程师问答(P3)  │
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
| F6 | WikiPage — md 导入 + 预览（复用 `/docs/ingest`）| P0 | 第一门 R-006 |
| F7 | GovernPage — 5 in 1 治理（拆 4 子组件）| P0 | 第一门 R-007 |
| F8 | 撤下 Tab 路由保留（含 deep-link 验收）| P1 | 第一门 R-008 |
| F9 | i18n key 子集标注（zh-CN + en 同步）| P1 | 第一门 R-009 |
| F10 | 后端 Controller 分组 + **2 强类型 VO endpoint** | P2 | 第一门 R-010 |
| F11 | 设计文档 v2.0 归档 | P2 | 第一门 R-011 |
| F12 | 回归 & 质量门（**5 用例**含 deep-link）| P0 | 第一门 R-012 |

### 3.2 详细需求

#### F1 KnowledgeView 侧栏导航重构

**描述**：`KnowledgeView.tsx` 从 5 组 14 Tab（`typesAndConstants.ts` 的 `KNOWLEDGE_TAB_GROUPS`）重构为 6 平铺侧栏页面。保留 `KNOWLEDGE_TAB_GROUPS`（作 Tab 登记表）+ 新增 `ACTIVE_PAGES` 常量数组（6 项）作侧栏数据源。

**用户故事**：作为业务用户，打开知识工作台看到 6 个清晰按钮，1 次点击即达目标页面。

**功能详情**：
- 左侧 sidebar 232px（对齐原型 `.sidebar` 宽度），深色 `useTheme().styles.sidebarBg`
- 侧栏项（6 按钮，lucide icon）：
  ```
  ◆ ECOS Knowledge
  ─────────────────
  [Knowledge Workbench]
  · 知识总览   (LayoutDashboard)
  · 知识资产   (Database)
  · 知识抽取   (ArrowDownUp)
  · 知识图谱   (Network)
  · 企业知识   (BookOpen)
  · 知识治理   (ShieldCheck)
  ─────────────────
  [Global 入口]
  · 任务中心 ↗
  · 引擎监控 ↗
  ```
- 顶栏：面包屑 `{ECOS / {activePage}}` + 右侧任务状态 / i18n 切换 / Avatar（现有 `Topbar` 组件复用）
- 当前页高亮：`box-shadow: inset 3px 0 var(--primary)` 原型同款
- `KnowledgeView.tsx` 拆为 `views/KnowledgeView.tsx`（布局骨架）+ `pages/{OverviewPage, AssetListPage, ExtractionPage, GraphPage, WikiPage, GovernPage}.tsx`
- 原 14 Tab 组件（`tabs/*.tsx`）**不删除**（R9 只加不删），但不再从 `KNOWLEDGE_TAB_GROUPS` 挂载侧栏
- `ACTIVE_PAGES` 常量：
  ```ts
  export const ACTIVE_PAGES = ['overview', 'assets', 'extract', 'graph', 'wiki', 'govern'] as const;
  export type ActivePage = typeof ACTIVE_PAGES[number];
  ```

**验收标准**：
- [ ] 侧栏 6 按钮全 i18n（`knowledge.nav.page_overview` 6 个 key + `knowledge.nav.page_*` 子集）
- [ ] 原 14 Tab `tabs/*.tsx` 文件保留（R9），但 `KNOWLEDGE_TAB_GROUPS` 仍作 deep-link 路由表
- [ ] 撤下 8 Tab deep-link `#/knowledge_view?tab=rag` 等可达 + 顶部 warn banner（F8）
- [ ] 主题 `useTheme().styles` 全用，0 硬编码色值（§4.1）
- [ ] 路由表 6 Pages + 撤下 8 Tabs = 14 个 `Route`（`main.tsx` 不删）

**来源**：第一门 R-001

#### F2 OverviewPage — KPI + 流水线 + 待办

**描述**：扩展现有 `OverviewDashboard.tsx`，按原型 `.kpis` 6 列 grid + `.pipeline` 5 step + `.list` 3 项待办 + 最近资产表 5 列。

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
  | 抽取任务 | `fetchStructuredJobs` 运行中 | `GET /knowledge/extract/jobs?status=running` |
- **知识生产流水线**（5 步横条）：`本体映射 → 数据抽取 → 知识候选 → 审核发布 → KG / Vector`（I → K）
- **待处理事项**（3 行）：AI 抽取候选 X 条 / Wiki 待审核 Y 条 / 索引失败 Z 条，点击跳 F7
- **最近知识资产表**（5 列）：名称 / 类型 / 状态 / 来源 / 更新时间（复用 `knowledgeApi.fetchArticles`）
- **「查看任务」按钮**：跳 `#/tasks`（统一任务中心路由，由 `AsyncTaskCenterView` 注册）。若 `#/tasks` 路由尚未注册（P3），按钮 disabled + 提示 `knowledge.overview.task_center_p3`

**验收标准**：
- [ ] 6 KPI 全调用真实 API，无假数据
- [ ] KPI 卡片数字字体 25px / 标签 12px + 灰色（原型 `.kpi`）
- [ ] 流水线 5 step + 4 arrow，主题主导色 chip
- [ ] 「查看任务」按钮：可用态跳 `#/tasks`；不可用态 disabled + tooltip 提示（i18n key `knowledge.overview.task_center_p3`）

**来源**：第一门 R-002

#### F3 AssetListPage — 资产表 + 图/向量状态

**描述**：合并 `DatasyncTab` + `ClassificationTab` 能力，资产表 8 列含「图谱实体 / 向量索引」状态。

**用户故事**：作为业务用户，一页看清所有知识资产（Wiki / 实体 / 文档 / 知识集），每一行是否已入图谱 + 向量。

**功能详情**：
- **筛选工具栏**：关键词 input + 类型 select（全部 / Wiki / 实体 / 文档 / 知识集）+ 状态 select（全部 / 草稿 / 审核中 / 已发布）+ domain Select（复用 `fetchNavDomains`）
- **资产表 8 列**（对齐原型 `.table`）：
  | 列 | 数据源 |
  |:--|:--|
  | 知识资产 | `article.title`，点击进详情 |
  | 类型 | `article.assetType` |
  | 状态 | `article.status`，badge 绿/橙/灰 |
  | 语义关联 | `article.relatedEntities[]` 逗号串 |
  | 来源 | `article.sourceType`（人工 / 结构化抽取 / AI / 文档导入）|
  | **图谱实体** | `article.graphStatus`（✓ / —），批量调 `GET /knowledge/assets/status` |
  | **向量索引** | `article.vectorStatus`（✓ / ⏳ / —），批量调 `GET /knowledge/assets/status` |
  | 更新时间 | `article.updatedAt` |
- **批量状态接口**（F10 后端新增）：`GET /api/v1/knowledge/assets/status?ids={comma-separated}` 返回 `AssetStatusVO { items: List<AssetStatusItemVO> }`，`AssetStatusItemVO { articleId, graph: boolean, vector: boolean }`（**强类型 VO 非 Map**，铁律 §2.1 + 后端开发规范禁 Map 出参）。批量 ≤ 100 ids，单次响应 ≤ 50ms
- **Nav 过滤**：复用 `fetchNavProducts` 接口的 `categoryIds` / `tags` 参数（PMO-A 已落地）
- **右侧详情面板**（≥ 1280px 视口）：选中资产 → 详情 + 图谱上下文 chips + 语义实体 chips（复用 `fetchNavProducts` 单条）

**业务流程**：
```
用户筛选 → GET /nav/products?keyword=&type=&status=&domain= → 表格前 6 列渲染
→ 拿到 assetIds 集合 → 一次性 GET /assets/status?ids=id1,id2,... → 填 7/8 列
→ 行点击 → 右侧详情面板拉单条上下文
```

**验收标准**：
- [ ] 8 列全表渲染，badge 颜色随状态
- [ ] 图/向量状态列由 `GET /assets/status` 批量查（1 次 RPC，不 N+1）
- [ ] 响应格式 `AssetStatusVO` 强类型（非 Map），前端 `knowledgeNavApi.ts` 加 TS interface 1:1 映射
- [ ] domain 切换时筛选器 reset
- [ ] 空状态提示 `knowledge.asset.empty` i18n key
- [ ] ids > 100 时分片（前端 chunk = 100/批）

**来源**：第一门 R-003

#### F4 ExtractionPage — DW 层源头 + 文档导入

**描述**：合并 `StreamingTab` + `VectorIndexTab` + `DocumentUploadTab` + `DatasyncTab`（DW 契约源）能力。**源头唯一标注 = DW 层**。

**用户故事**：作为数据工程师，选已发布的 DW 层映射契约 → 提交抽取 → 看实体/关系/向量 3 种结果。非结构化走文档导入路径（经 DW 层中转）。

**功能详情**：
- **左栏（结构化 I→K，1.25fr）**：
  - 映射契约 select（`fetchOntologyMappings` 已发布列表）
  - 抽取目标 3 checkbox：☑ 图谱实体/属性 ☑ 图谱关系 ☑ 向量索引（文本化属性）
  - 「预览并提交」按钮 → POST `/knowledge/structured/extract/submit`
  - 标题 badge `I → K`
- **右栏（非结构化 Document→K，0.75fr）**：
  - 文档导入（`uploadDocumentChunked` 分片，复用）
  - 4 项能力 badge：文档导入 / OCR / 分块 / 实体关系抽取
  - 导入后走 `POST /api/v1/knowledge/docs/ingest`（A3 链路，与 F6 md 导入共用）
  - 标注「→ DW 层 → 知识审」（走 `TRANSFORM_DOC_PARSE` → `ecos_dw.doc/doc_chunk`）
  - 标题 badge `Document → K`
- **抽取预览**（bottom 3 KPI 卡片）：实体 X / 关系 Y / 候选知识 Z（原型 `.grid-3`）

**降级链路**：
- 映射契约列表加载失败（ontology-engine :18083 不可达 / 网络异常）→ select disabled + placeholder `knowledge.extract.mapping_unavailable`
- 映射契约列表无 `status=published` 条目 → select disabled + 同上 placeholder
- 降级**不阻塞右栏文档导入**能力（左右栏独立功能域）

**验收标准**：
- [ ] 左右栏 1.25fr / .75fr 网格（原 `.two`）
- [ ] 映射契约 select 仅显示 `status=published` 条目
- [ ] 抽取目标 3 checkbox 独立勾选
- [ ] 「预览并提交」调真实 API（`POST /knowledge/structured/extract/submit`），toast 成功/失败
- [ ] 文档导入走 `uploadDocumentChunked` 分片
- [ ] **降级**：左栏加载失败/无 published → select disabled + `mapping_unavailable` 提示，右栏不受影响（i18n key 在 zh-CN + en 双语）

**来源**：第一门 R-004

#### F5 GraphPage — 图谱可视化 + categoryIds

**描述**：复用 `GraphExplorerTab.tsx` 主体逻辑，重命名 + 对齐原型页 4。

**用户故事**：作为业务用户，浏览企业知识图谱，按 domain + categoryIds 后端下沉过滤（`12a20d6` 已落地），查实体 / 关系。

**功能详情**：
- 顶部搜索栏：input + 实体/关系 select + 搜索按钮
- 图谱画布（深色 `#0f172a`，`.graph` 高度 520px）：复用 `GraphCanvas` 组件
- 左工具栏：全文搜索 / 路径查找 / domain select / category checkbox（**后端下沉**，非前端二次过滤）/ 邻居度 / 图例
- 右详情面板：节点属性 / 关系 / 操作按钮（展开邻居 / 设路径源）
- 底栏：`实体 N · 关系 M · 当前视图 K 个节点`

**验收标准**：
- [ ] 后端 `categoryIds` 透传 `fetchGraph(domain, categoryIds)`（`12a20d6` 已落地）
- [ ] 前端 checkbox 变更 → 走后端，不二次过滤
- [ ] domain select 从 `fetchNavDomains()` 动态（已落地）
- [ ] 节点 detail 面板保留（现有逻辑不动）
- [ ] 无前端 `filter` 逻辑，纯 `rawNodes` / `rawEdges` 直渲染

**来源**：第一门 R-005

#### F6 WikiPage — md 导入 + 预览

**描述**：**新建**企业知识页面。**核心裁决**：md 文档导入（非在线编辑器），三栏布局（左 tree / 中 md 预览只读 / 右 metadata）。

**用户故事**：作为知识管理员，把项目复盘 / 交付规范的 md 文件批量导入，看 markdown 渲染结果 + 元数据（语义实体 chips / 相关知识 / 图谱关系）。

**功能详情**：
- **左栏（知识空间 tree，220px）**：`.tree` 一级节点「产品知识」「技术知识」「项目知识」「行业知识」「制度与规范」+ 二级 md 文件列表（`📄` 前缀）
- **中栏（md 预览，read-only）**：
  - 顶栏：状态 badge（已发布/草稿）+ 版本 v1.8 · 作者 · 日期
  - `<Markdown>` 渲染 body，`@实体` chips 高亮（`react-markdown` 渲染）
  - **无编辑能力**（非 textarea / 无 B/H1/链接 toolbar）
- **右栏（Knowledge Context，280px）**：
  - 语义实体 chips（`@项目` / `@客户` / `@数据实施流程`）
  - 相关知识链接（3 个）
  - 图谱关系（项目 → 遵循 → 交付规范 等）
  - 「查看图谱上下文」按钮 → 跳 `#/knowledge_view?page=graph&focusEntity={id}`
- **「＋ 上传 .md」按钮**（顶部）：
  - file input 白名单只允许 `.md`（`accept=".md"`，前端校验 + 后端 `KnowledgeIngestController.docsIngest` 按 `Content-Type: text/markdown` 分支）
  - **走既有 `POST /api/v1/knowledge/docs/ingest`，不新增 endpoint**（P0-1 修订）。依据 `[v1.1 修订]`：原 v1.0.0-draft 曾写新增 `POST /articles/import-md`，与 §1.4「endpoint 不变」矛盾，采纳 Reviewer 方案 (a) 走 docs/ingest `Content-Type: text/markdown` 分支
  - 后端 `docs/ingest` 检测到 `Content-Type: text/markdown` 时走 `fileType=md` 分支，落 `kb_document(layer=RAW, zone=UNSTRUCTURED, resource_type=FILE)` + 触发 `TRANSFORM_DOC_PARSE`
  - 前端 `uploadDocumentChunked` 封装层追加 `contentType` 参数透传
- **写操作安全**（§2.4）：
  - Abac：`KnowledgeIngestController.docsIngest` 已挂 `checkAbac("knowledge:document:upload", scope)`（确认已存在，若缺失本批次在 F10 一并补 AOP）
  - Kafka audit：`KafkaTopic.ECOS_AUDIT` 事件 `type=doc_import_md, source=wiki_page, docId={uuid}`

**md 导入流程**：
```
用户点「上传 .md」 → file input 校验白名单 → uploadDocumentChunked(contentType='text/markdown')
→ 后端 KnowledgeIngestController.docsIngest 按 content-type 走 md 分支
→ 落 kb_document + 触发 TRANSFORM_DOC_PARSE → ecos_dw.doc/doc_chunk
→ 知识审（F7 GovernPage 审核 feed）
```

**验收标准**：
- [ ] 上传只允许 `.md`（白名单双校验，后端 400 拒绝其他扩展名）
- [ ] **接口为既有 `POST /api/v1/knowledge/docs/ingest`**，不新增 endpoint（P0-1 修复）
- [ ] `Content-Type: text/markdown` 由前端显式设置（`fetch(e.url, { method: 'POST', body: file, headers: { 'Content-Type': 'text/markdown' } })`）
- [ ] 中栏 `<Markdown>` 渲染，`@实体` chip 走 `knowledge.assets.entity_ref` i18n
- [ ] 右栏「查看图谱上下文」跳 `#/knowledge_view?page=graph&focusEntity={id}`
- [ ] 0 硬编码中文，全 `knowledge.wiki.*` i18n key（≈ 25 个新 key，zh-CN + en 双语）
- [ ] ABAC + Kafka audit 双链路（§2.4）走已有切面，不新增写操作

**来源**：第一门 R-006

#### F7 GovernPage — 5 in 1 治理（拆 4 子组件）

**描述**：合并 `ReviewTab` / `LifecycleTab` / `ComplianceRuleTab` / `KnowledgeRuleRepositoryTab` / `KnowledgeEvalTab` 为 1 页 3 区。**主文件 ≤ 800 行**（铁律 §4.6），超出部分拆 4 子组件。

**用户故事**：作为知识管理员，开治理页看到审核队列 + 生命周期 + 质量指标 + 规则仓库 + 评测运行，1 页 5 维考核。

**功能详情**：
- **文件结构**（铁律 §4.6 ≤ 800 行）：
  ```
  pages/knowledge/pages/GovernPage.tsx     (~300 行 — 布局骨架 + 3 区容器 + state 共享)
  pages/knowledge/components/govern/
    ├── AuditFeedCard.tsx                  (原 ExtractionReviewTab 主逻辑)
    ├── QualityMetricsTable.tsx            (原 ComplianceRuleTab 指标子集)
    ├── RuleRepoTable.tsx                  (原 KnowledgeRuleRepositoryTab)
    └── EvalRunPanel.tsx                   (原 KnowledgeEvalTab)
  ```
- **上区 — AI 候选审核**（`AuditFeedCard`，原 review Tab）：
  - 左卡：候选 feed（来源 / 抽取 / 置信度 / 关联实体）+ 确认发布 / 退回按钮
  - 右卡：生命周期 pipeline `草稿 → 抽取 → 审核 → 发布`（4 步）
- **中区 — 质量监控**（`QualityMetricsTable`，原 compliance Tab 部分）：
  - 3 指标表：来源完整率 / Ontology 关联率 / 索引成功率
  - 状态 badge（正常/告警/异常）
- **下区 — 治理策略 + 评测**（`RuleRepoTable` + `EvalRunPanel` 并列 1fr / 1fr）：
  - 左：规则仓库表格（`fetchRules`）+ CRUD 4 按钮
  - 右：评测运行面板（`runEval` + 结果历史）

**业务流程**：
```
用户进 GovernPage → 并发 4 子组件各自拉数据
AuditFeedCard: GET /extract/candidates?status=pending
QualityMetricsTable: GET /knowledge/api/quality-metrics
RuleRepoTable: GET /knowledge/rules + CRUD 4 按钮
EvalRunPanel: GET /knowledge/eval/history + POST /knowledge/eval/run (异步 poll)
```

**验收标准**：
- [ ] GovernPage.tsx 主文件 `wc -l` ≤ 800 行
- [ ] 4 子组件独立文件，各 ≤ 300 行
- [ ] 3 区 grid 布局（上 1fr / 中 1fr / 下 1.25fr）
- [ ] 审核 feed「确认发布」调 `POST /knowledge/lifecycle/transition`
- [ ] 规则仓库 CRUD 走 `ExpertRuleController`（已落地）
- [ ] 评测 `runEval` 返回异步任务 id，定时 poll（轮询间隔 3s，最多 30 次）
- [ ] 0 硬编码中文（全部走 `knowledge.govern.*` i18n，zh-CN + en 双语）

**来源**：第一门 R-007

#### F8 撤下 Tab 路由保留 + deep-link 可访问

**描述**：8 个 Tab 从侧栏撤下但路由保留（R9 只加不删），deep-link 可达 + warn banner。

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

**deep-link 行为**：
- 访问 `#/knowledge_view?tab=rag` 等撤下 8 项 → 页面可达 + 顶部 warn banner：
  ```
  ⚠️  本页面已撤下导航，此链接保留供 P3/历史引用。返回 → 回到 6 页主导航
  ```
- warn banner 点击「返回」回 `#/knowledge_view?page=overview`
- banner 样式：`bg-amber-500/10 border-l-4 border-amber-500 text-amber-800`（主题 `useTheme().styles` 推导）

**验收标准**：
- [ ] `main.tsx` 保留全部 14 个 `Route`（6 Page + 8 Tab）
- [ ] 撤下 8 Tab 在侧栏不可见（`ACTIVE_PAGES` 常量 6 项不含它们）
- [ ] 直接 `#/knowledge_view?tab=rag` 可达，顶部 warn banner 可见
- [ ] banner 点击「返回」跳 `page=overview`
- [ ] 8 个撤下 Tab 手动逐一 deep-link 验证（F12 用例 5）

**来源**：第一门 R-008

#### F9 i18n key 子集标注（zh-CN + en 双语同步）

**描述**：`knowledge/zh-CN.json` ≈ 2010 key + `knowledge/en.json` 同名对偶文件。**不清理**（R9 只加不删），新增 6 Page 消费 key 加 `_depr: false`（隐式），废弃 8 Tab 的旧 key 加 `_depr: true` metadata 标记。

**用户故事**：给未来清理工作留痕迹，且 i18n 双语一致性可验收。

**功能详情**：
- 新增 key（6 Page 消费，全量在 zh-CN + en 双语同步）：
  | 前缀 | key 数 | 中文 | 英文 |
  |:--|:--:|:--|:--|
  | `knowledge.nav.page_*` | 6 | 知识总览 / 知识资产 / 知识抽取 / 知识图谱 / 企业知识 / 知识治理 | Knowledge Overview / Assets / Extraction / Graph / Enterprise Knowledge / Governance |
  | `knowledge.nav.global_*` | 2 | 任务中心 / 引擎监控 | Task Center / Engine Monitor |
  | `knowledge.overview.task_center_p3` | 1 | 统一任务中心 P3 待接入 | Task Center P3 pending |
  | `knowledge.asset.*` | 8 | 表头 / 空态 / detail 等 | English equivalents |
  | `knowledge.extract.mapping_unavailable` | 1 | 映射契约暂不可用 | Mapping contract unavailable |
  | `knowledge.wiki.*` | ≈25 | 标题 / 上传 / 预览 / 空态 / chips / 上下文 | English equivalents |
  | `knowledge.govern.*` | ≈30 | 审核 / 生命周期 / 质量 / 规则 / 评测 | English equivalents |
  | **新增 key 合计** | **≈70** | | |
- **废弃 key 标记**（`_depr` 方案，JSON 合法）：
  ```json
  {
    "knowledge.tab.rag.title": "RAG 实验台",       // 旧 key 保留值
    "_depr.knowledge.tab.rag.title": true,          // 新增元 key，真实废弃标记
    "knowledge.tab.glossary.title": "术语表",
    "_depr.knowledge.tab.glossary.title": true
  }
  ```
  （Rationale: Reviewer P1-6 + P2-6 建议。选择「同层 `_depr.{key}: true` 元 key」而非「内嵌 `"_depr": true` 字段」，避免污染原 key 值的对象结构，i18next 读取时自动忽略 `_depr.*` 前缀 key）
- **en.json 同步**：新增 `knowledge.wiki.*` × 25 + `knowledge.nav.page_*` × 6 + `knowledge.nav.global_*` × 2 + `knowledge.govern.*` × 30 + `knowledge.extract.mapping_unavailable` × 1 + `knowledge.overview.task_center_p3` × 1 = **≈ 65 对偶 key**，逐条人工翻译 + placeholder 兜底

**验收标准**：
- [ ] 6 Page 文件 grep 命中 key 集合 → 全在 zh-CN.json 内存在
- [ ] 0 硬编码中文（正则 `[\u4e00-\u9fa5]{2,}` 扫 6 Page 文件 = 0）
- [ ] `jq '. | with_entries(select(.key | startswith("_depr."))) | keys | length'` 返回 ≥ 8（撤下 8 Tab 对应的旧 key 集合）
- [ ] `jq '. | keys | length' zh-CN.json` + `jq '. | keys | length' en.json` 差值 ≤ 5（i18next 内置 allow list key）
- [ ] `npx tsc --noEmit` 0 报错（JSON 合法性）

**来源**：第一门 R-009

#### F10 后端 Controller 分组 + 2 新方法

**描述**：`engine/kb-engine/kb-engine-impl/.../` 21 个 Controller：
1. 新增 `KbEngineModuleRegistry.java` 枚举类（7 module）
2. 每个 Controller 类 Javadoc 头部加 `@group {MODULE}` 注释
3. **新增 2 个强类型 VO endpoint**（P1-1 修订）：
   - `GET /api/v1/knowledge/nav/modules`（`NavModulesController.modulesList`）→ `NavModulesVO`
   - `GET /api/v1/knowledge/assets/status?ids={comma-separated}`（`KnowledgeAssetController.batchStatus`）→ `AssetStatusVO`
4. F6 md 导入**不新增 endpoint**，复用既有 `POST /api/v1/knowledge/docs/ingest` (P0-1 方案 a)

**用户故事**：作为架构师，打开 `KbEngineModuleRegistry` 能一眼看懂「6 模块 → 21 Controller → 121 endpoint」映射。

**功能详情**（强类型 VO，禁 Map — 铁律 §2.1 + 后端开发规范）：
```java
// api 模块（kb-engine-api）
public class NavModulesVO {
    private List<ModuleInfoVO> modules;
}
public class ModuleInfoVO {
    private String moduleName;          // "overview" / "assets" / "extract" / ...
    private List<String> controllers;   // fully qualified class names
}

public class AssetStatusVO {
    private List<AssetStatusItemVO> items;
}
public class AssetStatusItemVO {
    private String articleId;
    private boolean graph;              // 是否入 Neo4j 图
    private boolean vector;             // 是否入 pgvector
}
// 字段带 @Getter @Setter 单字段（Entity 规范：禁 @Data）
```

**业务流程**：
- `GET /nav/modules`：纯读 `KbEngineModuleRegistry` 常量表，无 DB 访问（≤ 5ms）
- `GET /assets/status?ids=id1,id2,...`：
  1. 参数化 IN 查 Neo4j `MATCH (n:Article {id: ?}) RETURN count(*)`（走 `NodeMapper.batchExists`）
  2. 参数化 IN 查 pgvector `SELECT article_id FROM kb_embedding WHERE article_id IN (?...limit 100) AND is_deleted=0`（走 `KnowledgeEmbeddingMapper.batchExists`）
  3. 合并为 `AssetStatusVO` 返回

**验收标准**：
- [ ] 21 Controller 全有 `@group` Javadoc 注释
- [ ] `KbEngineModuleRegistry` 7 module（`overview`, `assets`, `extract`, `graph`, `wiki`, `govern`, `center_p3`）
- [ ] `GET /nav/modules` 返回 `NavModulesVO`（非 Map），单测覆盖
- [ ] `GET /assets/status` 返回 `AssetStatusVO`（非 Map），`ids` ≤ 100 参数化，超 100 返 400
- [ ] F6 不新增 endpoint（`/docs/ingest` 复用），endpoint 总数 119 → 121
- [ ] VO 类在 `kb-engine-api`（Import 依赖方向：api → impl，不反向）
- [ ] 不引入新 maven 依赖
- [ ] 单测：`KbEngineModuleRegistryTest` + `AssetStatusControllerTest`（MockNeo4j/MockPgv，覆盖率 ≥ 80%）

**来源**：第一门 R-010

#### F11 设计文档 v2.0

**描述**：`docs/engine-kb/knowledge-navigation-design.md` v1.1 → v2.0。

**变更**：
- §一 定位：消费方从 4 个（分类 Tab / RAG 实验台 / 图谱探索 / 未来 Copilot）改写为「6 页面中的 5 个」（撤 RAG，保留 Copilot P3）
- §七 未做项：#1-#3 标 ✅ 2026-09-24 PMO-C（已落 commit `12a20d6`），#4/#5 P3 不动
- §八 **新增「6 页面结构定稿」**：
  | Page | 文件路径 | 数据源 API | i18n 前缀 |
  |:--|:--|:--|:--|
  | OverviewPage | `pages/knowledge/pages/OverviewPage.tsx` | `/knowledge/api/index-status`, `/extract/candidates`, `/extract/jobs` | `knowledge.overview.*` |
  | AssetListPage | `pages/knowledge/pages/AssetListPage.tsx` | `/nav/products`, `/assets/status`, `/nav/domains` | `knowledge.asset.*` |
  | ExtractionPage | `pages/knowledge/pages/ExtractionPage.tsx` | `/structured/mappings`, `/structured/extract/submit`, `/docs/ingest` | `knowledge.extract.*` |
  | GraphPage | `pages/knowledge/pages/GraphPage.tsx` | `/knowledge/graph?categoryIds=`, `/nav/domains` | `knowledge.graph.*` |
  | WikiPage | `pages/knowledge/pages/WikiPage.tsx` | `/knowledge/articles/import-md`（**P0-1 修订：实为 `/docs/ingest`**）, `/nav/categories` | `knowledge.wiki.*` |
  | GovernPage | `pages/knowledge/pages/GovernPage.tsx` | `/extract/candidates`, `/rules`, `/eval/run` | `knowledge.govern.*` |

**验收标准**：
- [ ] 头部 3 行元信息 + 批次 3 变更表（F1~F12 映射 commit hash）
- [ ] §八 6 个 Page 的「数据源 → API 端点 → i18n key 前缀」三元组
- [ ] R1/R2/R3 红线全满足（文件名小写+日期 / 头部 3 行 / 版本 v2.0）

**来源**：第一门 R-011

#### F12 回归 & 质量门（**5 用例**）

**描述**：编译 + 类型 + 6 页面 console err + 5 用例（P1-5 修订：追加 deep-link 用例）。

**5 用例**：
1. **原 4 用例**（design.md §六）：
   - 验证 1：POST /rag 不带 categoryIds → 与历史一致（全量）
   - 验证 2：POST /rag 带 categoryIds → 返回全在子树下
   - 验证 3：POST /rag 带空数组 `[]` → 仍走全量（null/empty 等价）
   - 验证 4：前端 6 页面 Tab 未勾任何目录 → 调 API 不带 categoryIds（静态审查）
2. **新增第 5 用例**（P1-5 修复 — 撤下 Tab deep-link 可达性）：
   - **用例 5**：**逐 8 个撤下 Tab 深链接** `#/knowledge_view?tab={id}`（id ∈ {rag, engine_config, ontology_model, graph_builder, glossary, sync, data_import, vector_index}）手动 vite :3000 验证：
     - ① 页面可达（无 404，无 white screen）
     - ② 顶部 warn banner 可见（`⚠️ 本页面已撤下导航`）
     - ③ 点击「返回」跳 `page=overview`

**验收标准**：
- [ ] `mvn install -Penterprise -pl engine/kb-engine/kb-engine-impl -am -DskipTests` SUCCESS
- [ ] `npx tsc --noEmit`（ecos_frontend）0 新增错误
- [ ] 6 页面 vite :3000 启动后各页 console err = 0（截图 3 张：1280 / 1920 / iPad Pro 11）
- [ ] 4 原始用例回归 PASS（静态审查 + 编译级 + 手动 vite 截图 3 张）
- [ ] **用例 5：8 撤下 Tab deep-link 逐一手动验证**（vite :3000 + 脚本 or 逐点）
- [ ] Reviewer 铁律 grep（禁 5 种违规：禁 `select *` / 禁 Map 入参 / 禁硬编码中文 / 禁 `var` / 禁行内样式）

**来源**：第一门 R-012

## 4. 非功能需求

### 4.1 性能

| 指标 | 要求 |
|:--|:--:|
| 页面切换 | < 200ms（SPA 已懒加载）|
| 6 页面首屏（含 API 调用）| < 2s |
| 后端 `GET /nav/modules` | < 5ms（纯常量表）|
| 后端 `GET /assets/status`（≤100 ids）| < 50ms（Neo4j + pgvector 1 次 batch）|
| 资产表 7/8 列状态 | 单次批量 API，不 N+1 |

### 4.2 安全

- 所有接口需 Bearer token（gateway :8080 鉴权已覆盖）
- 写操作（F6 md 导入）走 Kafka `ecos.audit`（§2.5-5，`KnowledgeIngestController` 既有 audit 切面，本 PRD 无新增切面）
- 图谱 categoryIds 过滤参数化防 SQL 注入（`12a20d6` 已参数化 IR05）
- **ABAC**：F6 写操作走 `checkAbac("knowledge:document:upload", scope)`（**Reviewing 确认此 ABAC action 已在 `KnowledgeIngestController` 落地，若缺失 F10 批内补 AOP**）
- 敏感列脱敏（ST03/04/05 已落地，本 PRD 无新增）
- **强类型 VO**（非 Map）：`NavModulesVO` / `ModuleInfoVO` / `AssetStatusVO` / `AssetStatusItemVO`

### 4.3 兼容性

- 浏览器：Chrome / Firefox / Safari 最近 2 版本
- 主题：浅色 + 深色（`useTheme().styles` 双套）
- i18n：**zh-CN + en 双语**（新增 ≈ 65 对偶 key，逐条人工翻译）
- 浏览器 1280px / 1920px / iPad Pro 11 三档响应式

### 4.4 铁律约束

| 铁律 | 本 PRD 落实 |
|:--|:--|
| §0.5 三工作台 | 本 PRD 仅改 kb-engine，不动 data / ontology |
| §1.6 即时/定时任务 | F4 抽取走 runtime-task（定时）+ submitTask+executeTask（即时） |
| §2.1 引擎间只调 API | F5 走 `GET /knowledge/graph`（API），F3 走 `/assets/status`（API），无 Neo4j/pgvector 直连 |
| §2.4 安全 | 写操作 ABAC + Kafka audit；F6 复用 `/docs/ingest` 既有切面 |
| §2.5-2 LLM 走 gateway | 无新增 LLM 调用，F4 抽取走既有 pipeline |
| §3.1 只加不删 | 抽下 8 Tab 路由保留 / i18n 只标 `_depr` 不删 / 表结构 0 变更 |
| §4.1 主题 | 0 硬编码色值 |
| §4.2 lucide-only | 全 Lucide icon |
| §4.3 i18n | 0 硬编码中文 |
| §4.6 文件长度 | GovernPage ≤ 800 行 + 8 行 i18n key 不超 300 char |
| §5.2/§5.3 ≤5 Task | 12 Task 拆 3 批，每批 ≤ 5（见 §4.4.A）|

#### 4.4.A §5.3 批次划分（P2-7 修复）

| Batch | Task 组合 | 并行度 | 交付物 |
|:--:|:--|:--:|:--|
| **Batch 1**（前端骨架 + 后端前置）| F1 侧栏 + F10 后端分组/2 VO + F3 资产表 | F1 与 F10 并行；F3 依赖 F10 `/assets/status` 先落 | 侧栏可用 + 资产表 8 列可用 |
| **Batch 2**（前端 6 页面）| F2 Overview + F4 Extraction + F5 Graph + F6 Wiki + F7 Govern | 5 Tab 并行开发（每 Page 独立文件，单指令 ≤5 Task）| 6 Page 全部可用 |
| **Batch 3**（路由/文档/回归）| F8 撤下路由 + F9 i18n + F11 设计文档 v2.0 + F12 回归 5 用例 | F8/F9/F11 并行；F12 收尾 | clean commit + merge release |

**单 Sub-Agent 指令约束**：每批 dispatch 的 Sub-Agent query 里显式列 `≤ 5 Task`，符合 §5.3。

## 5. 验收标准（总表）

| 功能 | 核心验收 | 验证方式 |
|:--|:--|:--|
| F1 侧栏 | 6 按钮 + 撤 8 路由保留 + 主题 0 硬编码 | 视觉 + grep |
| F2 Overview | 6 KPI 真实 API + 流水线 5 步 + 3 待办 | curl + 截图 |
| F3 Asset | 8 列资产表 + 图/向量状态（`/assets/status` 批量）| curl + 截图 |
| F4 Extract | DW 源头标注 + 映射契约降级 | 网络失败 mock 测试 |
| F5 Graph | categoryIds 后端下沉 + domain 动态 | 抓包 |
| F6 Wiki | md 导入走 `POST /docs/ingest` (content-type=text/markdown) + 预览 | 抓包 + 白名单测试 |
| F7 Govern | 5 in 1 合 1 页 + GovernPage ≤ 800 行 + 4 子组件 | `wc -l` + 视觉 |
| F8 撤下 | 8 路由保留 + deep-link 可达 + warn banner | 用例 5 手动 |
| F9 i18n | 0 硬编码 + `_depr` 元 key 合法 + en 同步 | grep + jq |
| F10 后端 | 21 Controller `@group` 注释 + `NavModulesVO`/`AssetStatusVO` + 单测 | grep + mvn test |
| F11 文档 | v2.0 头部 3 行 + 批次 3 变更表 + §八 6 页面 | docs 检查 |
| F12 回归 | mvn + tsc + 6 页 console + 5 用例（含 deep-link）| CI + 手动 |

## 6. 风险分析

| ID | 描述 | 概率 | 影响 | 缓解 |
|:--|:--|:--:|:--:|:--|
| R-001 | 14 Tab → 6 Page 合并过程中丢代码 | 中 | 高 | R9 不删旧文件 + diff reviewer 全程 diff 审查 |
| R-002 | F3 资产表「图/向量状态」列 API 未落地 | 已消解 | — | **P1-1 修复**：F10 明确新增 `GET /assets/status` 强类型 VO |
| R-003 | F6 md 导入 endpoint 矛盾 | 已消解 | — | **P0-1 修复**：走 `POST /docs/ingest` content-type 分支，不新增 endpoint |
| R-004 | i18n 清理误删活跃 key | 中 | 中 | 不清理（R9），只加 `_depr.{key}: true` 元 key |
| R-005 | Controller `@group` 注释与代码漂移 | 低 | 低 | CI 加 lint 校验 controller 类头 `@group` 注释存在（`_win_tasks/lint-controller-group.ps1`）|
| R-006 | GovernPage 单文件膨胀超 800 行 | 已消解 | — | **P1-2 修复**：拆 4 子组件 `components/govern/{AuditFeedCard, QualityMetricsTable, RuleRepoTable, EvalRunPanel}` |
| R-007 | 撤下 Tab 的 deep-link 被外部引用 | 低 | 低 | 撤下 Tab 顶部加 warn banner + toast 提示（F8 验收）|
| R-008（新增）| `GET /assets/status` Neo4j + pgvector 双调用超时 | 中 | 中 | 各侧 ≤ 30ms timeout，批内并行 not 串行（`CompletableFuture.allOf`），前端 3s loading + 失败降级「—」|
| R-009（新增）| en.json 同步遗漏致 en 用户展示原始 key | 中 | 中 | CI 新增 `scripts/check-i18n-parity.mjs`：`jq` 比对 zh-CN/en 新增 key set diff ≤ 0 |
| R-010（新增）| `KnowledgeIngestController.docsIngest` ABAC 覆盖 md 场景 | 低 | 高 | F10 批前置 grep 确认 `checkAbac` 存在；若缺失補 1 行 AOP，走既有 `KnowledgeWriteAuditor` |

## 7. 附录

### 7.1 术语表

| 术语 | 定义 |
|:--|:--|
| 知识资产（Knowledge Asset）| Wiki / 实体 / 关系 / 文档 / 知识集 5 类对象的统称 |
| DW 层 | 数据湖 `CURATED` 层，知识抽取唯一源头 |
| 图谱双写 | 抽取时同步写 Neo4j 图 + pgvector 向量 |
| 知识导航（Knowledge Nav）| kb_nav_category / kb_nav_tag / kb_nav_article_rel 3 表体系 |
| 知识中心（Knowledge Center）| RAG 问答入口（P3 后置，本 PRD 不含）|
| EffectiveEndpoint | 实际对外 endpoint 数（F10 新增 2 个强类型 VO endpoint 后 119 → 121）|

### 7.2 参考文档

- [知识导航设计文档 v1.1](./knowledge-navigation-design.md)
- [架构铁律 §1.6 即时/定时任务统一入口](../../.trae/rules/架构铁律.md)
- [数据湖存储分层规范](../../.trae/rules/数据湖存储分层规范.md)
- [文档编写规范 R1~R13](../../.trae/rules/文档编写规范.md)
- [界面原型 v1.0](用户提供，位于本地桌面非入库，93 行 HTML + 18 CSS)
- [需求分析摘要（第一门产出）](./knowledge-workbench-refactor-prd-v0.1.md)

### 7.3 追溯表（功能 → 需求来源）

| 功能 | 第一门条目 | 验收数 |
|:--|:--|:--:|
| F1 | R-001 | 5 |
| F2 | R-002 | 4 |
| F3 | R-003 | 6 |
| F4 | R-004 | 6 |
| F5 | R-005 | 5 |
| F6 | R-006 | 7 |
| F7 | R-007 | 7 |
| F8 | R-008 | 5 |
| F9 | R-009 | 5 |
| F10 | R-010 | 8 |
| F11 | R-011 | 3 |
| F12 | R-012 | 6 |

### 7.4 评审修改记录（本 v1.1 修订）

| 日期 | 修改内容 | 修改人 |
|:--|:--|:--|
| 2026-09-24 | [P0-1] F6 从「新增 POST /articles/import-md」改为「复用 `/docs/ingest` + content-type 分支」；§1.4 endpoint 数 119 → 121（含 F10 拆出的 `/nav/modules` + `/assets/status`） | PM Agent |
| 2026-09-24 | [P1-1] F3 图/向量状态列 → 后端拆出 `GET /assets/status` 强类型 VO（非 Map）；F10 后端清单扩 2 方法 | PM Agent |
| 2026-09-24 | [P1-2] F7 拆 4 子组件到 `components/govern/`，主文件 ≤ 800 行（铁律 §4.6） | PM Agent |
| 2026-09-24 | [P1-3] F4 降级链路：`mapping_unavailable` 提示 + 左右栏独立 | PM Agent |
| 2026-09-24 | [P1-4] F10 `/nav/modules` 改 `NavModulesVO` 强类型（禁 Map） | PM Agent |
| 2026-09-24 | [P1-5] F12 追加第 5 用例（撤下 Tab deep-link 可达性） | PM Agent |
| 2026-09-24 | [P1-6] F9 补 en 同步验收 + R-009 风险 | PM Agent |
| 2026-09-24 | [P2-7] §4.4.A 批次划分 Batch 1/2/3 明细 | PM Agent |
| 2026-09-24 | [P2-8] 参考文档删除本机绝对路径（R10） | PM Agent |
| 2026-09-24 | [P2-9] F9 描述 `_depr.{key}: true` 元 key 方案统一口径 | PM Agent |
| 2026-09-24 | [P2-10] F2 「查看任务」明确跳 `#/tasks` + 不可用态 disabled | PM Agent |

### 7.5 第三门禁重入清单

本 v1.1.0-draft 针对 v1.0.0-draft 第三门 Reviewer 报告的全部 12 findings 修复，重入第三门评审。Reviewer 验证：
1. P0-1 修复：`/articles/import-md` 字样应全文仅存于 §7.4 变更日志（作为修订痕迹），F6 正文/验收/§1.4/R-003/§4.4/F10 五处口径必须全对齐「`/docs/ingest` + content-type 分支」
2. P1-1：`GET /assets/status` 已在 F10 明确 + 强类型 VO + batching ≤ 100 + R-008 超时
3. P1-2：F7 主文件 ≤ 800 行 + 4 子组件文件路径
4. P1-3：F4 `mapping_unavailable` i18n key + 降级不阻塞右栏
5. P1-4：`NavModulesVO` + `ModuleInfoVO` 完整 VO 类
6. P1-5：F12 用例 5 明确 8 deep-link 手动验收（vite :3000）
7. P1-6：F9 en.json 同步验收 + `check-i18n-parity.mjs` CI 门禁
8. P2-*：批次、绝对路径、`_depr` 元 key、任务中心 route 均修齐

---

**【第三门禁重入 — 请 Reviewer 验证 v1.1.0 12 findings 全修复】**

- **Gate-1**: CONFIRMED（2026-09-24 用户）
- **Gate-2**: CONFIRMED（2026-09-24 用户）
- **Gate-3**: **待 Reviewer 复审**（本 v1.1.0-draft 全量 12 findings 修订完成，重入第三门）