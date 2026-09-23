# 知识导航设计文档（PMO-B T4）

> 来源: PMO-B 任务规格 | 日期: 2026-09-24 | 责任人: FullStack 实现工程师
> 版本: v1.0（任务批次 1：在 PMO-A 之上做检索下沉 + 前端替换）
> 追溯: PMO-A 已交付 kb_nav_category / kb_nav_tag / kb_nav_article_rel 3 张 DDL（V155/156/157）
> 上游: .trae/rules/铁律 §2.4（安全）/ §4.1（主题）/ §4.3（i18n）

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

- POST /rag 的 tags 字段增下行 WHERE（仅占位）；
- 图谱 GET /graph 增加 categoryIds 参数（当前前端二次过滤，后续后端下沉）；
- 多 domain 切换（当前前端硬编码 default）；
- kb_nav_category 拆表（如 level 3 以上 3 级深度演进）；
- 批量 IN 优化 matchesNavFilter 查询次数。

---
> 本设计文档 v1.0 制定于 2026-09-24，由 PMO-B 任务批次 1 提交，后续批次（PMO-C 性能 / P3 接 Copilot）需同步更新。
