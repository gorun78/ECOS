> 来源: PMO（用户指令，2026-09-22） | 日期: 2026-09-22 | 责任人: PM（Fullstack 待领取）
> 版本: v1.0（批准，可进入拆解）
> 追溯: 知识工作台 Replan（I→K 致知契约）+ 架构铁律 §0.5 三工作台边界 + 数据湖存储分层规范 §一
> PRD 编号: PRD-知识导航（number_to_be_assigned，落地时分配）

# PRD：知识导航（原「知识分类」重定位）

## 〇、背景与现状（实证结论）

> 依据 2026-09-22 代码库核查。

1. **知识工作台「分类体系」Tab 是 UI 空壳**：`ClassificationTab.tsx` 硬编码 DEMO 数据（`c1/c1-1`），树形 2 级，无后端数据；「执行分类」按钮调 `knowledgeApi.classifyAsset` → 走错门（打到 ai-engine 的 PII 敏感分级 `/catalog/assets/{id}/auto-classify`），**无业务分类持久化、无消费**。
2. **同一「分类」概念在工作台被混用两义**：
   - **数据分级分类**（安全维度：能不能给人看、脱不脱敏）→ 属**数据工作台 + 安全中心**（见 `20-services/datanet/data-asset-classification-prd.md`），**不在本 PRD 范围**。
   - **知识业务分类**（运营/导航维度：怎么建知识体系结构、怎么找知识）→ 本 PRD 重定位为「**知识导航**」。
3. **现状字段**：`knowledge_article` 仅有 `category VARCHAR(64)` 孤儿字段（无索引/无 WHERE 消费/无层级）；kb-engine 无 taxonomy Controller/Service/Mapper。

## 一、功能定位

> **知识导航 = 知识资产（K 层）的结构化组织 + 多维检索入口 + 生命周期与质量度量。**

偏离"挂在分类 Tab 上的一个目录树"，定位为一套**知识运营基建**，由 5 个正交能力组成（非单一功能）：

| 模块 | 职责 | 服务对象 |
|:--|:--|:--|
| **M1 知识目录（Directory）** | 实体节点 + 关系边（`node_type` + `relation_type`），可复用图谱本体作为目录骨架；给知识条目打"目录归属" | 知识浏览者（导航找知识） |
| **M2 多维检索（Filter）** | 按 `知识类型 × 来源(domain) × 敏感级 × 生命周期 × 质量分` 组合过滤，输出到 RAG/图谱/文章列表 | 知识消费者 |
| **M3 快捷入口（Shortcut/常用视图）** | 命名视图保存 + 一键跳转 + 个人常用知识（PIN） | 高频使用者 |
| **M4 知识健康度（Health）** | 每条/每类知识的 `validity`：新鲜度(最后更新)、覆盖率(被引用次数)、孤儿检测（无引用孤立知识）、过期标记 | 知识管理者 |
| **M5 待办推荐（Suggest）** | 基于「被引用但缺说明」「同主题多篇冲突」「新导入未归类」三类信号推送待整理清单 | 知识管理者 |

**与既有能力的边界**：
- **M1 目录 ≠ 知识图谱本体**：目录是**导航骨架**（人阅读视角的分组），图谱是**语义网络**（实体关系）。二者**可共享节点，但目录是可选视图**，不强制要求全量知识入图。
- **M1 目录 ≠ Glossary 术语表**：术语表（金 I）管"概念词"，目录管"知识资产的业务归类"，二者通过 `anchor_term_id` 可选挂接（概念术语可作为目录节点锚点）。
- **M2 过滤 ≠ 数据安全分级**：过滤是"哪些知识可被检索命中"（运营选择），安全是"哪些知识对人可见"（`RLS/CLS/脱敏` 由安全中心执行）。二者**独立字段、独立判定**，导航层**只输出可见集**，**不执行**脱敏。

## 二、目标（P0/P1/P2）

| 期 | 目标 |
|:--|:--|
| **P0（闭环）** | M1 目录 CRUD + 知识条目打"目录归属"；M2 按 5 维组合过滤进 RAG/图谱/文章列表；前端「知识导航」Tab 替换原空壳 |
| **P1（运营完整）** | M3 命名视图 + 个人常用（PIN）；M4 健康度报表（新鲜度/覆盖率/孤儿/过期）；批量移动/归类 |
| **P2（智能）** | M5 推荐待办；LLM 推荐目录归属（人工确认）；目录与 Glossary 概念锚点联动 |

## 三、依赖与支撑

### 3.1 前置硬依赖
| 依赖 | 说明 | 现状 |
|:--|:--|:--|
| `knowledge_article`/`kb_doc_chunk`/`graph_node` | 知识实体源 | ✅ 已有 |
| RAG/图谱检索主链路 | 注入 `filter` 参数 | ✅ 已有（`KNaviRagService`/`KNaviGraphService`），需加过滤入参 |
| security CLS/RLS | 导航层消费其判定（**只读**，不执行脱敏） | ✅ 已有 |
| 数据分级分类（指令 1） | 字段级敏感度作为 M2 "敏感级"过滤维度 | 依赖指令 1 P0 完成 |
| `kb_nav_*` 新表 | 目录/关系/视图/PIN | ❌ 需新建（V159~V160） |

### 3.2 支撑（下游消费）
- 知识资产列表/搜索/详情（`KNaviView`）
- RAG 检索（注入 `categoryIds` + `types` + `sensitivity` 过滤）
- 图谱浏览（按目录过滤/高亮子图）
- 知识治理仪表板（M4 健康度）

## 四、数据模型（2 份 DDL，只加不删，V159~V160）

### V159 `kb_nav_directory` + `kb_nav_directory_rel`
```
-- 知识目录节点（导航骨架，非图谱本体）
kb_nav_directory:
  id PK UUID | domain |  parent_id FK(self NULL 根)
  | path VARCHAR(512)  -- /root/child 冗余
  | level SMALLINT(1~3) | node_name | node_kind ENUM('folder','term_anchor','custom')
  | anchor_term_id VARCHAR(64) NULL  -- 可选挂接 Glossary 术语
  | 审计5字段 | version_no
  UNIQUE(domain, path)；idx: domain, parent_id

-- 知识条目 → 目录归属（多对多）
kb_nav_directory_rel:
  id PK UUID | domain |  directory_id FK
  | ref_type ENUM('article','chunk','graph_node')
  | ref_id VARCHAR(64)
  | 审计5字段 | version_no
  UNIQUE(domain, directory_id, ref_type, ref_id)；idx: ref_type, ref_id
```
> **ADR-K1 裁决**：目录独立成树，`node_kind='term_anchor'` 时 `anchor_term_id` 可选指向 Glossary 术语（概念锚点），不强制全量入图。

### V160 `kb_nav_view` + `kb_nav_pin`
```
-- 命名检索视图（M3，保存用户常用过滤组合）
kb_nav_view:
  id PK UUID | domain |  owner_user_id | view_name
  | filter_json JSONB  -- {nodeTypes[], domains[], levels[], lifecycle[], qualityMin}
  | is_default SMALLINT | 审计5字段 | version_no

-- 个人常用知识（PIN，M3）
kb_nav_pin:
  id PK UUID | domain |  user_id | ref_type | ref_id | 审计5字段 | version_no
  UNIQUE(domain, user_id, ref_type, ref_id)
```

> 注：M4 健康度字段（`last_cited_time`/`cite_count`/`orphan_flag`/`expire_date`）**复用** `knowledge_article`/`graph_node` 既有时间戳 + 运行时统计，**不改既有表、不新增列**（R9 兼容）。

## 五、接口契约（kb-engine 强类型 VO，禁 Map）

`KNaviController`（`/api/v1/knowledge/nav`）：
- **目录**：`GET /directory?domain=`（树）/ `POST|PUT|DELETE /directory`（≤3 级校验）
- **归属**：`POST /directory/{id}/rel`（挂接条目）/ `POST /directory/rel/batch`（批量移动，≤100/批）
- **多维检索**：`POST /query`（body `KNaviFilterQuery{nodeTypes,domains,levels,lifecycle,qualityMin,relation?}`）→ 命中的知识列表（已过滤 CLS/RLS 可见集）
- **视图/PIN**：`GET|POST|PUT|DELETE /view`（命名视图）/ `POST|DELETE /pin`（个人常用）
- **健康度**：`GET /health?domain=`（新鲜度/覆盖率/孤儿/过期分布）/ `GET /health/{refId}`
- **推荐**：`GET /suggest`（待整理清单，P2）

**RAG/图谱检索注入**：`KNaviRagService.runRagQuery` / `KNaviGraphService.query` 入参加**可选** `KNaviFilterQuery`（默认空 = 当前行为不变）；映射到 SQL `WHERE node_type IN (...) AND domain IN (...) ...`，走既有 CLS/RLS 过滤链（不绕过）。

## 六、安全与前台规则（承接铁律）
- 导航层**只读消费** CLS/RLS 判定，**不执行脱敏**（脱敏归安全中心 / 数据分级分类 PRD）
- 敏感级过滤维度在产品上称「敏感级」为 business 词汇，底层值为 `level_code`（L1~L4），**来自指令 1 的字段级敏感度**
- 多租户：所有 nav 表带 `domain` + 审计 5 字段（DR06/08）+ `version_no`
- 写操作（目录/视图/PIN 增删改）→ Kafka `ecos.audit`（ST06）
- RAG/图谱检索按 **memory 脱敏规则**返回脱敏内容（身份证/手机号/邮箱），脱敏不在导航层做，在检索返回 pipeline 末尾统一

## 七、范围与验收

### P0（本期一次完成，可验证闭环）
- [ ] V159/V160 DDL + rollback + lint 全 PASS
- [ ] kb-engine `KNaviController` + `KNaviService` + `KNaviDirectoryMapper`
- [ ] RAG/图谱检索注入 `KNaviFilterQuery`（默认行为不变）
- [ ] 前端 `KNaviView` Tab 替换原空壳 `ClassificationTab`：左目录树 + 中多维过滤 + 右结果列表（RAG/图谱/文章）
- [ ] **闭环回归**：建 3 级目录 → 挂接 20 条知识 → `POST /nav/query` 按 `nodeTypes+domain` 过滤仅返回子集 → 无过滤参数时行为与现状一致
- [ ] 移除原「执行分类」假按钮（不再指向 ai-engine mock auto-classify）
- [ ] `deliverable_allowed=true` + reviewer PASS + PM 验收报告

### 红线自检（违反即打回）
- IR02 只加不删 / 零 DROP ALTER 既有表（nav 表全新建）
- ST04/05 导航只读消费 CLS/RLS（不绕过、不自建）
- ST06 写操作 Kafka audit
- 前端：i18n `knowledge.nav.*` 补齐（zh-CN + en），不硬编中；不引入新依赖；不重写 assets，补响应式（断点类）

## 八、技术架构增量
- kb-engine 新增 Nav 模块（Controller + Service + Mapper + V159/V160）
- RAG/图谱检索加可选过滤参数
- workbench 布局重构（Nav 入驻 replace Classification）

## 九、风险与回滚
| 风险 | 等级 |
缓解 |
|:--|:--||:--|
| 目录树 ≤3 级前端校验易漏 | M | 后端 `KNaviDirectoryService` 二次校验 `level`，超限 400 |
| 多维过滤 SQL 拼接注入 | H | 全部 `#{}` 占位，禁 `${}`，禁字符串拼 WHERE（IR05） |
| RAG 加可选入参影响现有返回 | M | 默认空参数 = 现状行为；单测 + 回归覆盖"不带参数"路径 |
| 目录与图谱节点重叠语义混淆 | M | 文档 + UI 文案显式区分"目录（导航）/ 图谱（语义）"，ADR-K1 留痕 |

## 十、分期与里程碑
- M1（P0）：目录 CRUD + 多维过滤 → **闭环回归通过**
- M2（P1）：命名视图 + PIN + 健康度
- M3（P2）：推荐待办 + LLM 推荐 + 术语锚点联动

## 关联文档
- 数据分级分类 PRD（前置依赖）：[../datanet/data-asset-classification-prd.md](../datanet/data-asset-classification-prd.md)
- 数据湖存储分层规范：`.trae/rules/数据湖存储分层规范.md`
- 知识工作台 Replan：[../21-runtime/legacy-plans/knowledge-workbench-replan-v1.md](../21-runtime/legacy-plans/knowledge-workbench-replan-v1.md)
- 数据库访问规范：`.trae/rules/数据库访问规范.md`
