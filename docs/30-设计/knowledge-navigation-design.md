# 知识导航设计文档（PMO-B T4）

> 来源: PMO-B 任务规格 | 日期: 2026-09-24 | 责任人: FullStack 实现工程师
> 版本: v2.0（批次 3：6 页面结构定稿 + §7 未做项 #1-#3 已落 commit `12a20d6` + §9 铁律 §5.3 批次表/§4.6 800 行上限）
> 追溯: PMO-A 已交付 kb_nav_category / kb_nav_tag / kb_nav_article_rel 3 张 DDL（V155/156/157）
> 上游: .trae/rules/架构铁律.md §2.4（安全）/ §4.1（主题）/ §4.3（i18n）/ §5.3（单指令 ≤ 5 Task）/ §4.6（组件 ≤ 800 行）

## 批次 3 变更表（v2.0）

> 本表对应 PRD `knowledge-workbench-refactor-prd-v1.1-draft.md` §4.4.A 的 **Batch 1/2/3 收口**（3 个 commit hash 已落，本批不 merge release，待 Reviewer 第三门险）。

| # | 功能点 | 动作 | commit hash |
|:--:|:--|:--|:--|
| 1 | F10 后端 — Controller 分组 + `KbEngineModuleRegistry` + 2 强类型 VO endpoint（`GET /api/v1/knowledge/nav/modules` + `GET /api/v1/knowledge/assets/status`） | `kb-engine-impl` 新增 `KbEngineModuleRegistry.java` / 21 Controller 全打标 `@group` Javadoc / `NavModulesVO` + `ModuleInfoVO` + `AssetStatusVO` + `AssetStatusItemVO` 4 VO 落 `kb-engine-api` / `NavTaxonController.modulesList` + `KnowledgeArticleController.assetStatus` 2 强类型 endpoint / 单测 `NavModulesTest`(5 case) + `AssetStatusControllerTest`(3 case) 8/8 PASS | `73e68fd` |
| 2 | F1 + F3 前端骨架 — 6 平铺侧栏 Page + `KnowledgeView` 重构 + `AssetListPage`（8 列资产表 + 批量 `/assets/status` 状态列 + domain 切换 reset） | `src/pages/knowledge/typesAndConstants.ts` 新增 `ACTIVE_PAGES` 6 项 + `DEPRECATED_TAB_IDS` 8 项 + `ACTIVE_PAGE_ICONS` / `ACTIVE_PAGE_I18N_KEYS` 映射 / `src/pages/KnowledgeView.tsx` 重写（F1 侧栏 + F8 deep-link + warn banner）/ `pages/knowledge/pages/AssetListPage.tsx` 完整 / `services/knowledgeNavApi.ts` 加 `fetchAssetStatuses`（强类型 `AssetStatusItem`）/ 单测 `AssetListPage.test.tsx` 6 case | `693617d` |
| 3 | Batch 2 5 Page 实体 — `OverviewPage` / `ExtractionPage` / `GraphPage` / `WikiPage` / `GovernPage` + 4 子组件（`AuditFeedCard` / `QualityMetricsTable` / `RuleRepoTable` / `EvalRunPanel`） | 6 Page 文件（i18n `knowledge.overview.*` / `knowledge.extract.*` / `knowledge.graph.*` / `knowledge.wiki.*` / `knowledge.govern.*`）/ `GovernPage` 主文件 ~300 行 + 4 子组件各 ≤ 300 行（铁律 §4.6 ≤ 800 行）/ 0 硬编码色值 + 0 硬编码中文 | `69d3095` |
| 4 | F9 i18n `_depr` 元 key 标注（本批 Batch 3 新增） | `src/locales/knowledge/zh-CN.json` + `en.json` 各新增 265 个 `_depr.knowledge.{ragtab,graphexplorert,glossarytab,synctab,vector_index,graph_builder,engine_config,ontology_model,closedlooptab,indextab,lineagetab,cognitiveconfigtab,graphsynctab}.*: true` 元 key（对应 8 撤下 Tab 实际消费的 13 个前缀 === 265 key）/ zh-CN 补 7 个 en 既有但 zh 缺失的 `knowledge.datasync.*` 对偶 key（平行度 0 差集）/ 新建 `ecos_frontend/scripts/check-i18n-parity.mjs`（CI 可选门禁，本批不强制启用） | 本 commit |
| 5 | F11 设计文档 v1.1 → v2.0（本批） | §七 未做项 #1-#3 已 ✅ 2026-09-24 PMO-C（commit `12a20d6`）/ **新增 §八 6 页面结构定稿**（核心交付）/ §2.3 子树补 F6 说明（走 `POST /api/v1/knowledge/docs/ingest` + `Content-Type: text/markdown`，不新增 endpoint）/ §九 铁律引用补 §5.3 批次表 + §4.6 800 行上限 | 本 commit |
| 6 | F12 回归 & 质量门（5 用例 + build-approval） | 后端 `mvn install -Penterprise -pl kb-engine-api,kb-engine-impl -am -DskipTests` 成功 + `mvn test -Penterprise -pl kb-engine-impl -Dtest='NavModulesTest,AssetStatusControllerTest'` 8/8 PASS / 前端 `npx tsc --noEmit` 0 error + `npx vitest run` 全 PASS / 5 用例 C1-C5 全 PASS（静态审查 + 运行态）/ 铁律 grep 5 项 0 命中 / `docs/30-cross-cutting-docs/reviews/kb-workbench-refactor/BUILD-APPROVAL-2026-09-24.json`（7 门禁全真，`deliverable_allowed: true`） | 本 commit |

> 保留未做项（P3 后置）：
> - `kb_nav_category` level 4+ 拆表 / `matchesNavFilter` 批量 IN 优化（candidateIds 展开）。
> - 知识中心 / RAG 问答（P3，撤导航，路由保留）。
> - 在线 Wiki 编辑器（用户裁决 md 导入，非编辑器）。

### 批次 2（v1.1）变更表（历史溯源，commit `12a20d6` 已落）

> 批次 2（v1.1）已交付：§7 未做项 #1（`matchesTagFilter` 对称）/#2（`graph?categoryIds=` 后端下沉）/#3（多 domain 切换 + `GET /nav/domains`）。本表保留作历史溯源。

| # | 项 | 动作 | commit |
|:--:|:--|:--|:--|
| 1 | §7 未做项 #1：`POST /rag` 的 `tags` 字段增下行 WHERE | `KnowledgeRetrievalServiceImpl` 新增 `matchesTagFilter(articleId, tags)` 对称 `matchesNavFilter`；向量 + 关键词回退两路 OR 命中（navFilter/tagFilter 二选一） | `12a20d6` |
| 2 | §7 未做项 #2：`GET /graph` 增加 `categoryIds` 参数 | `KnowledgeGraphService` 加 `getGraph(domain, List<String> categoryIds)` overload；`KnowledgeGraphController` 加 `@RequestParam(required=false) List<String> categoryIds`；impl 走策略 B（PG `kb_nav_article_rel` 白名单 + 内存过滤 nodes/edges，`kg-root` 锚点保留） | `12a20d6` |
| 3 | §7 未做项 #3：多 domain 切换 | `INavService.listDomains()` + `NavTaxonController` `GET /api/v1/knowledge/nav/domains`（UNION category ∪ tag distinct domain）；前端 `knowledgeNavApi.ts` 加 `fetchNavDomains()`，`ClassificationTab` / `GraphExplorerTab` domain 下拉改用真实列表 | `12a20d6` |

## 一、定位边界

**知识导航**是知识引擎（kb-engine :18086）在「资产 × 目录 × 标签」三维的导航中枢，
消费方覆盖：

- 知识工作台前端「分类体系 Tab」（主消费方，本批次重写）
- 知识工作台前端「RAG 实验台」（按业务域过滤直送 /rag）
- 知识工作台前端「图谱探索」Tab（按业务域前后端二次过滤）
- 未来 Copilot / AIP（基于 kb_nav_article_rel 做语义召回 — P3 接入）

**与知识图谱 / 向量库的边界**：

- 导航只读知识资产（ecos_article / kb_document），不写；
- 导航不重复存储向量、不做 Embedding 检索；
- 落地入口只能是 kb_nav_category + kb_nav_tag + kb_nav_article_rel 三表（PMO-A 既有）。

## 二、DDL（PMO-A 已交付，PMO-B 无新增）

### 2.1 kb_nav_category — 3 级目录
- id：VARCHAR(36) PK DEFAULT gen_random_uuid()
- domain：VARCHAR(50) NOT NULL DEFAULT 'default'
- parent_id / name / path / level / sort_order / article_count
- 审计 5 字段 + version_no + is_deleted

**关键约束**：

- path 字段存 /a/b/c 完整路径，是子树过滤的唯一手段；
- 过滤模板：WHERE path LIKE '/${selectedPath}/%'；
- 前端 UI 在第 3 层隐藏「+ 子级」按钮（铁律性能 — 树深度硬上限 3）。

### 2.2 kb_nav_tag — 全域标签
- UNIQUE (domain, tag_name)；
- use_count 作为标签云加权（热门标签 Top 20 在前端算）。

### 2.3 kb_nav_article_rel — 资产 ↔ 目录 / 标签
- scope = 'category' | 'tag'；
- 对 category：ref_id = kb_nav_category.id；对 tag：ref_id = tag_name；
- UNIQUE (article_id, scope, ref_id, domain)。

> **批次 3 补充（F6）**：`_depr` 元 key + 6 Page `knowledge.wiki.*` 文案消费方是 WikiPage。其「md 导入」能力复用既有 `POST /api/v1/knowledge/docs/ingest`，通过 `Content-Type: text/markdown` 与普通 text 分支区分（同一 endpoint，**不新增**任何 ingest 子路径），无需在本节新增 DDL 行（`kb_nav_article_rel` 当初即以 scope='category' 表示文档↔目录关系，md 导入后落 `kb_document` 时按既有 `NavCategoryAssigner` 钩子入这张关联表）。

## 三、后端接口契约

### 3.1 分类体系（PMO-A 完整保留，本批次无新增）

- GET /api/v1/knowledge/nav/categories?domain=&parentId=&search=
- POST/PUT/DELETE /api/v1/knowledge/nav/categories[/{id}]
- POST /api/v1/knowledge/nav/categories/move { ids, targetParentId }
- GET/POST /api/v1/knowledge/nav/tags?domain=
- DELETE /api/v1/knowledge/nav/tags/{id}
- GET /api/v1/knowledge/nav/products?domain=&keyword=&categoryIds=&tags=&pageNum=&pageSize=
- PUT/POST/DELETE /api/v1/knowledge/nav/products/{articleId}/categories
- PUT/POST /api/v1/knowledge/nav/products/{articleId}/tags
- POST /api/v1/knowledge/nav/products/{articleId}/undo?scope=category|tag
- GET /api/v1/knowledge/nav/recommend/{articleId}（LLM 候选，仅建议）

### 3.2 检索侧打通（PMO-B T1 范围）

`POST /api/v1/knowledge/rag` — 强类型 `RagSearchRequest`（新增）：

    {
      "query": "...",
      "topK": 5,
      "threshold": 0.7,
      "categoryIds": ["cat-a", "cat-b"],   // null/[] = 全量（回归保证）
      "tags": ["speed"]                      // 占位，本期不下行 WHERE
    }

**兼容策略**：未带 categoryIds 的旧客户端 → 行为与历史版本一致（回归保证）。

**过滤语义**：

- categoryIds != null && len > 0 时，ragQuery 先在向量侧取 probeK = effectiveTopK × 5；
- 行级用 matchesNavFilter(articleId, subtreePaths) 校验，校验失败的 chunk 跳过；
- 最终截到 effectiveTopK；
- 关键词回退路径同样带 EXISTS (kb_nav_article_rel r JOIN kb_nav_category c ON r.ref_id = c.id WHERE r.article_id = a.id AND r.scope = 'category' AND c.path LIKE ?)。

**变更文件**：

- api 层：engine/kb-engine/kb-engine-api/.../model/dto/RagSearchRequest.java（新增）
- impl：engine/kb-engine/kb-engine-impl/.../service/KnowledgeRetrievalServiceImpl.java（新增 resolveCategorySubtree / matchesNavFilter 两个 helper）
- controller：engine/kb-engine/kb-engine-impl/.../controller/KnowledgeApiController.java（rag / query 改强类型）

## 四、前端交互（PMO-B T2 / T3）

### 4.1 分类体系 Tab（重写 — ClassificationTab.tsx）

布局三面板（grid-cols-3）：

- 左：3 级目录树（递归 TreeRow，depth ≤ 3），单点操作（新建一级 / 新建子级 / 重命名 / 删除）
- 中：标签云 + 热门标签 Top 20（按 use_count 排序）
- 右：资产列表（关键词 + 目录 + 标签过滤）+ LLM 推荐候选浮层

**契约变化**：

- 移除 DEMO_CLASSIFICATIONS / setTimeout(500)；
- 所有文案走 t('knowledge.nav.*')（zh-CN/en 各约 45 key）；
- 主题全部 useTheme().styles（含 sidebarBg / sidebarHoverBg / sidebarText / cardBorder）；
- 末尾保留 kb:stats:refresh CustomEvent（OverviewDashboard 监听）。

### 4.2 RAG 实验台（PMO-B T3）

- 在输入框下增加「按业务域过滤」多 checkbox 列表（最多显示前 20 条）；
- checkbox 勾选 → runQuery 调用时传 categoryIds 到 runRAGQuery；
- 默认空（全量）= 现行为，不强制选；
- 不引入额外面板复杂度（仅一个折叠列表）。

### 4.3 图谱探索（PMO-B T3）

- 在 Domain Filter 下增加「Category Filter」checkbox 列表（最多前 15 条）；
- 勾选 → loadGraph 触发前端二次过滤（后端暂不支持该字段，按 properties.categoryId / navCategoryId 匹配）；
- 后续批次由后端 GET /api/v1/knowledge/graph?categoryIds= 下沉后切换为真过滤。

### 4.4 新增 API 服务

`src/services/knowledgeNavApi.ts`（新建）封装全部 PMO-A nav 端点 + 强类型 VO/DTO。
更新 `pages/knowledge/services/knowledgeApi.ts`：

- RagRequest（typesAndConstants.ts）追加 categoryIds / tags 字段；
- 新增 fetchNavProductsByCategory helper（预留 Asset 列表 Tab 跨 Tab 复用）。

### 4.5 i18n（铁律 §4.3 — 中文硬编码 0 hits）

新增 key（zh-CN / en 各一份）：

- knowledge.nav.{title, hint, domain_all, domain_default, domain_filter_label, refresh}
- knowledge.nav.tree_{title, empty, new_top, new_child, rename, delete, name_placeholder, confirm, cancel, new_node}
- knowledge.nav.tag_{title (tags_title), empty, new, new_placeholder, created, deleted, op_failed, name_empty, selected, count, delete_hint}
- knowledge.nav.products_{title, empty}, product_{search_placeholder, updated, recommend, undo, page}
- knowledge.nav.cnt_{created, updated, deleted, name_empty, op_failed}
- knowledge.nav.recommend_{panel, tags, tags_empty, category_suggested, cancel, apply, failed, applied, apply_failed, reason}
- knowledge.nav.{undo_success, undo_failed, nav_filter, nav_filter_pair}

## 五、检索侧打通（T1 核心）

### 5.1 兼容承诺
- 3 参 ragQuery(String, int, double) 保留，内部委托 5 参方法，行为与历史版本一致；
- 5 参 ragQuery(...categoryIds, tags)：null / empty → 直接走历史路径，无额外 WHERE；
- 非空 → 向量侧 probeK *= 5，行级 matchesNavFilter 过滤，最后截到 effectiveTopK；
- 关键词回退同样子树过滤（避免无结果 + 召回越界）。

### 5.2 性能
- 单次 RAG 调用，matchesNavFilter 对每个候选 chunk 跑一次小查询（article_id 单行 PK）；对 topK=5 × 5x probe=25 行可接受；
- 批量优化（candidateIds 展开 IN）留作 P3 性能专项。

### 5.3 安全
- 写操作（树 / 标签 / 关联变更）不在本批次；
- 读操作不涉及敏感列，无 ST03/04/05 触发；
- 审计走 sysMAN（后端全局 @PreAuthorize 已覆盖 gateway → kb-engine 链）。

## 六、回归测试清单

- 回归 1：POST /rag 不带 categoryIds → 与历史版本返回一致（全量）；
- 回归 2：POST /rag 带 categoryIds（含 1 个真实类目）→ 返回全在该类目子树下；
- 回归 3：POST /rag 带空数组 categoryIds=[] → 仍走全量（兼容 null/empty 等价）；
- 回归 4：前端「分类体系 Tab 不勾任何目录」时，RAG / Graph 调用不带 categoryIds。

## 七、未做项（P3 / 后续批次）

- ~~`POST /rag` 的 `tags` 字段增下行 WHERE（仅占位）~~ ✅ 2026-09-24 PMO-C（`matchesTagFilter` 对称 `matchesNavFilter`，向量 + 关键词回退 OR 命中，见批次 2 变更表 #1）；
- ~~图谱 `GET /graph` 增加 `categoryIds` 参数（当前前端二次过滤，后续后端下沉）~~ ✅ 2026-09-24 PMO-C（`getGraph(domain, categoryIds)` overload + PG `kb_nav_article_rel` 白名单 + 内存过滤，见批次 2 变更表 #2）；
- ~~多 domain 切换（当前前端硬编码 default）~~ ✅ 2026-09-24 PMO-C（`INavService.listDomains()` + `GET /nav/domains` + 前端 domain 下拉，见批次 2 变更表 #3）；
- `kb_nav_category` 拆表（如 level 3 以上 3 级深度演进）——P3；
- 批量 IN 优化 `matchesNavFilter` 查询次数（candidateIds 展开）——P3 性能专项。

## 八、6 页面结构定稿（v2.0 核心交付）

> 6 平铺侧栏 Page 取代原 v1.x 9 Tab 体系（8 撤下 Tab deep-link 由 `KnowledgeView` warn banner 兜底）。文件路径均相对 `ecos_frontend/src/`，单文件 ≤ 800 行（铁律 §4.6），i18n 0 中文硬编码（铁律 §4.3）。

| Page 名 | 文件路径 | 数据源 API | i18n 前缀 |
|:--|:--|:--|:--|
| 知识总览 | `pages/knowledge/pages/OverviewPage.tsx` | `GET /api/v1/engine/kb/index-status` · `GET /api/v1/knowledge/extract/candidates` · `GET /api/v1/knowledge/extract/jobs` · `GET /api/v1/knowledge/nav/products`（资产表 5 行） | `knowledge.overview.*` |
| 知识资产 | `pages/knowledge/pages/AssetListPage.tsx` | `GET /api/v1/knowledge/nav/products` · `GET /api/v1/knowledge/assets/status`（批量 status 列，>100 reject）· `GET /api/v1/knowledge/nav/domains` | `knowledge.asset.*` |
| 知识抽取 | `pages/knowledge/pages/ExtractionPage.tsx` | `GET /api/v1/knowledge/structured/mappings` · `POST /api/v1/knowledge/structured/extract/submit` · `GET /api/v1/knowledge/docs/ingest` | `knowledge.extract.*` |
| 知识图谱 | `pages/knowledge/pages/GraphPage.tsx` | `GET /api/v1/knowledge/graph?domain=&categoryIds=` · `GET /api/v1/knowledge/nav/domains` | `knowledge.graph.*` |
| 企业知识 | `pages/knowledge/pages/WikiPage.tsx` | `POST /api/v1/knowledge/docs/ingest`（`Content-Type: text/markdown` 分支）· `GET /api/v1/knowledge/nav/categories?domain=&parentId=` | `knowledge.wiki.*` |
| 知识治理 | `pages/knowledge/pages/GovernPage.tsx` + `pages/knowledge/components/govern/{AuditFeedCard,QualityMetricsTable,RuleRepoTable,EvalRunPanel}` | `GET /api/v1/knowledge/extract/candidates` · `GET /api/v1/knowledge/rules` · `POST /api/v1/knowledge/eval/run` | `knowledge.govern.*` |

**说明**：

- `GovernPage` 主文件 ~300 行，4 子组件各 ≤ 300 行 — 符合 §4.6 800 行上限。
- 6 Page 文件路径内的 13 个撤下 Tab 旧 i18n 前缀已在 `zh-CN.json` / `en.json` 中加 `_depr.{key}: true` 元 key（合计 265 个），由 `scripts/check-i18n-parity.mjs` 在 CI 可选门禁中校验平行度。
- `GraphPage.tsx` line 中 `categoryIds` 调用策略见 PRD §F12 C4（checkbox 不勾时传 undefined，不传字段）；后端接 `categoryIds` 入参行为见 C1-C3（同 PRD §F12）。

## 九、铁律引用

> 本批次（PMO-C v2.1-alpha Batch 1/2/3）全程遵循 `.trae/rules/架构铁律.md`（v1.4）以下条目：
>
> - **§5.3 单指令 ≤ 5 Task** — 本批次 4 Task（T1 F9 i18n / T2 F11 文档 / T3 F12 回归 / T4 终审），合于 ≤5；Batch 1/2 各批亦 ≤5 Task，均在 `PRD §4.4.A` 批次表对应 §5.3 边界内。
> - **§4.6 文件 ≤ 800 行** — 6 Page 文件均 ≤ 800 行：`KnowledgeView.tsx` 398 / `GraphPage.tsx` 687 / `OverviewPage.tsx` 514 / `AssetListPage.tsx` ~250 / `ExtractionPage.tsx` ~280 / `WikiPage.tsx` ~300 / `GovernPage.tsx` ~300 + 4 子组件各 ≤ 300；`LanguageContext.tsx` 81 行。
> - **§3.1 只加不删** — 撤下 8 Tab 用 `_depr` 元 key 标记而非物理删除，旧 key 值保留原文。
> - **§4.3 i18n 0 硬编码中文** — 6 Page 0 中文硬编码（详见 F12 铁律 grep 5 项报告）；`check-i18n-parity.mjs` 平行度门禁 zh/en 各 1268 有效 key，0 差集。
> - **§5.3 批次表**（`PRD §4.4.A` 配套）— Batch 1 (commit `693617d`)：F1+F10+F3；Batch 2 (commit `69d3095`)：F2+F4+F5+F6+F7；Batch 3 (本 commit)：F8+F9+F11+F12。

---
> 本设计文档 v1.0 制定于 2026-09-24（PMO-B 批次 1），v1.1 于 2026-09-24 由 PMO-C 批次 2 落地 §7 未做项 #1~#3（commit `12a20d6`），v2.0 于 2026-09-25 由 PMO-C 批次 3 收口（新增 §八 6 页面结构定稿 + §九 铁律引用 + §2.3 F6 补充 + 头部批次 3 变更表）。后续 PMO / P3 批次需同步更新。
