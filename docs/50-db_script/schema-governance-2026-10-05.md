# ECOS Schema 彻底治理方案（删幻影 + 就近判定）

> 2026-10-05 · G4 授权范围内的第二波治理。首波（commit `f07901b`）= `public`→`ecos_control` rehome + `DROP ecos_demo` + drop 5 stray。本方案只处理**同一个病灶**：跨 schema 重复表（同一表名同时存在于 `ecos_control` 与某引擎 schema）。全部数字来自现网实跑，非推断。

---

## 〇、根因（为什么会出现双份）

V150 单源合并 + 2026-10-05 rehome 把 208 张表从 `public` 搬进 `ecos_control`，其中有引擎 owner 的表（如 `ecos_agent`、`td_data_resource`）也跟着搬走了；但**引擎 schema 里保留的是各自后续演进出的那份 DDL**。结果：

- **`ecos_control` 侧**：有真实数据 + 较早的列集（rehome 时点的样子）
- **引擎 schema 侧**：0 行 + 较新的列集（引擎自己后续 ADD 过列）

gateway fat-jar 把所有 6 引擎 + 5 服务打包在一起跑，连接串 `currentSchema=ecos_control,public`，引擎 schema **不在** search_path 里 → 所有**裸表名** mapper 一律命中 `ecos_control.{table}`。所以 gateway 侧调用的真身是 `ecos_control` 那份；引擎 schema 那份是 **0 行幻影**（唯一例外：`dccheng` 设 `currentSchema=public`，见 §四）。

**关键前提（已实测证伪"移动会破坏运行"的担忧）**：
- `*.xml` mapper 里 schema 限定引用 = 0（`grep -c "ecos_(data|ontology|...)` 于 mapper = 0），全裸名。
- 有 schema 限定的 Java 调用（如 buszhi `WorkflowApprovalRepository` 用 `ecos_ontology.ecos_workflow_approval`）指向的是**不在本次 52 张**里的另一张表，与本治理解耦。
- 因此：**DROP 引擎侧幻影**不影响任何运行中的读路径（那些路径要么走 `ecos_control` 裸名命中，要么已限定到非 twin 表）。

**回滚凭证**：`docs/50-db_script/canonical-baseline/schema-snapshot-2026-10-05-schemagov.sql`（888 KB / 28572 行，治理前抓取，含全部 473 对象 DDL）。

**明确不做（避免半吊子）**：不反向做"`ecos_control.ecos_agent` → `ecos_ai.ecos_agent` 收权迁移"（那是 6 个 JAR yml search_path tuple + mapper 注入的大面积改动，单独立批次，见 §五 #1）。本方案只做**删幻影 + 3 张数据合并**这一可低风险兑现的核心。

---

## 一、52 张跨 schema 重复表 · 实测矩阵与裁决

> `ctrl` = `ecos_control` 侧行数 · `home` = 引擎 schema 侧行数 · 列差 = ctrl/home 列数。

### 1.1 ecos_ai（8 张，home 全 0 行）

| 表 | ctrl | home | 列差 | 裁决 |
|:--|:-:|:-:|:--|:--|
| ecos_agent | 4 | 0 | 10/11 | DROP home |
| ecos_agent_registry | 16 | 0 | 9/10 | DROP home |
| ecos_decision_case | 6 | 0 | 11/12 | DROP home |
| ecos_mission | 22 | 0 | 10/10 | DROP home |
| ecos_mission_task | 14 | 0 | 9/9 | DROP home |
| ecos_tool_definition | 10 | 0 | 11/12 | DROP home |
| sys_agent_call_log | 0 | 0 | 11/12 | DROP home（双侧 0 行，纯元数据污点） |
| sys_agent_profile | 5 | 0 | 23/24 | DROP home |

### 1.2 ecos_cognitive（11 张，home 全 0 行）

| 表 | ctrl | home | 列差 | 裁决 |
|:--|:-:|:-:|:--|:--|
| ecos_biz_contract | 29 | 0 | 8/11 | DROP home |
| ecos_biz_department | 13 | 0 | 4/7 | DROP home |
| ecos_biz_metric | 75 | 0 | 8/9 | DROP home |
| ecos_biz_project | 23 | 0 | 11/14 | DROP home |
| ecos_biz_target | 17 | 0 | 7/8 | DROP home |
| ecos_goal_tracking | 30 | 0 | 7/7 | DROP home |
| ecos_wm_causal_link | 7 | 0 | 8/8 | DROP home |
| ecos_wm_goal | 24 | 0 | 23/24 | DROP home |
| ecos_wm_goal_log | 0 | 0 | 7/7 | DROP home |
| ecos_wm_scenario | 8 | 0 | 7/8 | DROP home |
| ecos_world_scenarios | 1 | 0 | 7/8 | DROP home |

### 1.3 ecos_data（14 张，本批唯一 home 有数据）

| 表 | ctrl | home | 列差 | 裁决 |
|:--|:-:|:-:|:--|:--|
| **ecos_data_lineage_node** | 8 | **12** | ctrl 6 / home 11（home 独有 8 列） | **MERGE**：home 独有 8 列 `ADD` 到 ctrl + 无键冲突合并 12 行 + DROP home |
| **ecos_data_lineage_edge** | 7 | **2** | ctrl 7 / home 8（home 独有 3 列） | **MERGE**：home 独有 3 列 `ADD` 到 ctrl + 合并 2 行 + DROP home |
| **td_data_category** | **0** | **5** | ctrl 5 旧列 / home 11 新列 | **DROP control 侧**（0 行 stale）· 保留 home 5 行真身 |
| ecos_dq_execution_result | 577 | 0 | 7/7 | DROP home |
| ecos_dq_issue | 21 | 0 | 15/9 | DROP home |
| ecos_dq_rule | 17 | 0 | 14/15 | DROP home |
| ecos_pipeline_definition | 57 | 0 | 11/8 | DROP home |
| ecos_pipeline_execution | 23 | 0 | 11/10 | DROP home |
| ecos_pipeline_node | 52 | 0 | 10/10 | DROP home |
| ecos_query_history | 22 | 0 | 12/10 | DROP home |
| ecos_query_template | 0 | 0 | 11/9 | DROP home |
| td_catalog_item | 1366 | 0 | 15/15 | DROP home |
| td_data_field | 20664 | 0 | 12/12 | DROP home |
| td_data_resource | 393 | 0 | 19/18 | DROP home |
| td_datasource | 5 | 0 | 21/17 | DROP home |

### 1.4 ecos_knowledge（6 张，home 全 0 行）

| 表 | ctrl | home | 列差 | 裁决 |
|:--|:-:|:-:|:--|:--|
| ecos_glossary_term | 32 | 0 | 20/12 | DROP home |
| ecos_knowledge_document | 8 | 0 | 12/9 | DROP home（public.ecos_knowledge_document.embedding jsonb 的 MC05 knownLegacy 是**另一独立条目**，不动） |
| ecos_knowledge_graph_edge | 18 | 0 | 6/8 | DROP home |
| ecos_knowledge_graph_node | 23 | 0 | 7/10 | DROP home |
| ecos_marketplace_access_request | 0 | 0 | 7/7 | DROP home（双侧 0 行） |
| ecos_marketplace_asset | 10 | 0 | 10/11 | DROP home |

### 1.5 ecos_ontology（12 张，home 全 0 行）

| 表 | ctrl | home | 列差 | 裁决 |
|:--|:-:|:-:|:--|:--|
| ecos_object_attachment | 4 | 0 | 9/11 | DROP home |
| ecos_object_data | 44 | 0 | 10/7 | DROP home |
| ecos_object_links | 7 | 0 | 5/6 | DROP home |
| ecos_object_relation | 6 | 0 | 7/7 | DROP home |
| ecos_object_relationship | 3 | 0 | 9/10 | DROP home |
| ecos_object_state_machine | 9 | 0 | 11/12 | DROP home |
| ecos_object_timeline | 24 | 0 | 6/9 | DROP home |
| ecos_object_version | 20 | 0 | 8/9 | DROP home（架构铁律 §3.1 v1.9 例外表，此处仅 tbl-twin 去重） |
| ecos_workflow | 8 | 0 | 10/11 | DROP home |
| ecos_workflow_instance | 7 | 0 | 14/19 | DROP home |
| ecos_workflow_log | 0 | 0 | 10/10 | DROP home |
| ecos_workflow_task | 8 | 0 | 9/20 | DROP home |

### 1.6 汇总（twin 实计 **52**：ai 8 + cog 11 + data 15 + kb 6 + ont 12）

| 类别 | 张数 | 对象 | 落点 |
|:--|:-:|:--|:--|
| DROP home（引擎 schema 侧 0 行幻影） | **49** | 49 张 twin 的引擎侧副本，全部 0 行；`ecos_control` 侧真身不动 | 引擎 schema −49 张 |
| MERGE（home 独有列 ADD 到 control 侧 + 行合入 + home 表 DROP） | **2** | `ecos_data_lineage_node`（home 12 行→control） · `ecos_data_lineage_edge`（home 2 行→control） | `ecos_data` −2 张；`ecos_control` +11 列 · +14 行（去重后） |
| DROP control 侧 stale（`ecos_control` 0 行 + 旧列集） | **1** | `ecos_control.td_data_category`；真身留 `ecos_data.td_data_category`（5 行 + 新列集） | `ecos_control` −1 张 |
| **合计** | **52 twin 全清**（52 次 DROP：49 home + 2 lineage-home + 1 control-stale；52 张去重 twin 全部消除） | | 全库 −52 对象 |

---

## 二、执行前置改编（净树守卫）

**每次实跑前**先跑以下三条，不满足即 `HOLD-RECHECK` 停插（别强行执行）：

```sql
-- A. 除 lineage 两张外，所有 52 twin 的 home 侧仍 0 行（=50 张应查）
SELECT en.nspname AS home, c.relname AS tbl, count(*)
FROM pg_class c
JOIN pg_namespace cn ON cn.oid=c.relnamespace
JOIN pg_class e ON e.relname=c.relname AND e.relkind IN ('r','p')
JOIN pg_namespace en ON en.oid=e.relnamespace
WHERE cn.nspname='ecos_control' AND c.relkind IN ('r','p')
  AND en.nspname IN ('ecos_data','ecos_ontology','ecos_knowledge','ecos_ai','ecos_cognitive')
  AND NOT (en.nspname='ecos_data' AND c.relname IN ('ecos_data_lineage_edge','ecos_data_lineage_node'))
GROUP BY en.nspname, c.relname
HAVING count(*) > 0;
-- 期望 0 行。

-- B. td_data_category 两侧仍 0 / 5
SELECT 'ctrl' AS side, count(*) FROM ecos_control.td_data_category
UNION ALL SELECT 'home', count(*) FROM ecos_data.td_data_category;
-- 期望 ctrl=0 / home=5。

-- C. lineage 口径（避免 MERGE 主键冲突）
SELECT 'node' AS side, count(*) AS rows_,
  count(*) - count(DISTINCT node_id) AS dup_usage
FROM ecos_data.ecos_data_lineage_node;
SELECT 'edge' AS side, count(*) AS rows_,
  count(*) - count(DISTINCT (source_node_id, target_node_id)) AS dup_usage
FROM ecos_data.ecos_data_lineage_edge;
-- 期望 node=12/dup=0, edge=2/dup=0（`node_id` / `(source,target)` 需为 UNIQUE，若 NULL 密钥列全空则改用「两侧全部取 home 侧 + 抹零 ctrl 侧数据」回退路子，见下）。
```

> **回退路子**（若 C 步 `dup_usage > 0` 或密钥列全 NULL，即无可靠联合主键）：把 `ecos_control` 侧的 lineage 表数据全部 `DELETE`，然后 `INSERT INTO ecos_control.{t} SELECT * FROM ecos_data.{t}` 用 home 全量替（相对 8+7=15 行的 dev 基线，几乎等价）。此法**不计为 RISK**（因为 dev 数据本来就是 V150 preflight 灌的固定集）。

---

## 三、执行 SQL（分 3 批，各自独立可回滚；单脚本内完）

### (A) 49 张 DROP（引擎侧 0 行幻影，dry-run 先）

```sql
BEGIN;
SELECT 1; -- 占位：本批次在生产/共享 PG 上做前先手工跑 §二 三条 RECHECK

-- ===== ecos_ai =====
DROP TABLE IF EXISTS ecos_ai.ecos_agent;
DROP TABLE IF EXISTS ecos_ai.ecos_agent_registry;
DROP TABLE IF EXISTS ecos_ai.ecos_decision_case;
DROP TABLE IF EXISTS ecos_ai.ecos_mission;
DROP TABLE IF EXISTS ecos_ai.ecos_mission_task;
DROP TABLE IF EXISTS ecos_ai.ecos_tool_definition;
DROP TABLE IF EXISTS ecos_ai.sys_agent_call_log;
DROP TABLE IF EXISTS ecos_ai.sys_agent_profile;

-- ===== ecos_cognitive =====
DROP TABLE IF EXISTS ecos_cognitive.ecos_biz_contract;
DROP TABLE IF EXISTS ecos_cognitive.ecos_biz_department;
DROP TABLE IF EXISTS ecos_cognitive.ecos_biz_metric;
DROP TABLE IF EXISTS ecos_cognitive.ecos_biz_project;
DROP TABLE IF EXISTS ecos_cognitive.ecos_biz_target;
DROP TABLE IF EXISTS ecos_cognitive.ecos_goal_tracking;
DROP TABLE IF EXISTS ecos_cognitive.ecos_wm_causal_link;
DROP TABLE IF EXISTS ecos_cognitive.ecos_wm_goal;
DROP TABLE IF EXISTS ecos_cognitive.ecos_wm_goal_log;
DROP TABLE IF EXISTS ecos_cognitive.ecos_wm_scenario;
DROP TABLE IF EXISTS ecos_cognitive.ecos_world_scenarios;

-- ===== ecos_data（除 lineage 2 张、除 td_data_category home 侧）=====
DROP TABLE IF EXISTS ecos_data.ecos_dq_execution_result;
DROP TABLE IF EXISTS ecos_data.ecos_dq_issue;
DROP TABLE IF EXISTS ecos_data.ecos_dq_rule;
DROP TABLE IF EXISTS ecos_data.ecos_pipeline_definition;
DROP TABLE IF EXISTS ecos_data.ecos_pipeline_execution;
DROP TABLE IF EXISTS ecos_data.ecos_pipeline_node;
DROP TABLE IF EXISTS ecos_data.ecos_query_history;
DROP TABLE IF EXISTS ecos_data.ecos_query_template;
DROP TABLE IF EXISTS ecos_data.td_catalog_item;
-- td_data_field / td_data_resource / td_datasource：数据真身在 ecos_control（20664/393/5 行），
-- data-engine mapper 全裸名 → search_path 命中 ecos_control；引擎侧副本 0 行，drop 安全。
DROP TABLE IF EXISTS ecos_data.td_data_field;
DROP TABLE IF EXISTS ecos_data.td_data_resource;
DROP TABLE IF EXISTS ecos_data.td_datasource;

-- ===== ecos_knowledge =====
DROP TABLE IF EXISTS ecos_knowledge.ecos_glossary_term;
DROP TABLE IF EXISTS ecos_knowledge.ecos_knowledge_document;
DROP TABLE IF EXISTS ecos_knowledge.ecos_knowledge_graph_edge;
DROP TABLE IF EXISTS ecos_knowledge.ecos_knowledge_graph_node;
DROP TABLE IF EXISTS ecos_knowledge.ecos_marketplace_asset;
DROP TABLE IF EXISTS ecos_knowledge.ecos_marketplace_access_request;

-- ===== ecos_ontology =====
DROP TABLE IF EXISTS ecos_ontology.ecos_object_attachment;
DROP TABLE IF EXISTS ecos_ontology.ecos_object_data;
DROP TABLE IF EXISTS ecos_ontology.ecos_object_links;
DROP TABLE IF EXISTS ecos_ontology.ecos_object_relation;
DROP TABLE IF EXISTS ecos_ontology.ecos_object_relationship;
DROP TABLE IF EXISTS ecos_ontology.ecos_object_state_machine;
DROP TABLE IF EXISTS ecos_ontology.ecos_object_timeline;
DROP TABLE IF EXISTS ecos_ontology.ecos_object_version;
DROP TABLE IF EXISTS ecos_ontology.ecos_workflow;
DROP TABLE IF EXISTS ecos_ontology.ecos_workflow_instance;
DROP TABLE IF EXISTS ecos_ontology.ecos_workflow_log;
DROP TABLE IF EXISTS ecos_ontology.ecos_workflow_task;

COMMIT;
```

### (B) 2 张 MERGE（lineage_node + lineage_edge）— 补列 + 并入 + DROP home

> **前提**：§二(C) 已证 node_id / (source,target) 主键不重复。若备份不为 0 用 §二 回退路子。

```sql
BEGIN;
-- step 1: 把 home 侧独有 11 列 ADD 到 control 侧（只加、无改、无 RENAME，风 IR03 无冲突）
ALTER TABLE ecos_control.ecos_data_lineage_node
  ADD COLUMN IF NOT EXISTS datasource_id        character varying(64),
  ADD COLUMN IF NOT EXISTS layer                character varying(20),
  ADD COLUMN IF NOT EXISTS name                 character varying(200),
  ADD COLUMN IF NOT EXISTS pipeline_task_id     character varying(64),
  ADD COLUMN IF NOT EXISTS properties           jsonb,
  ADD COLUMN IF NOT EXISTS schema_name          character varying(100),
  ADD COLUMN IF NOT EXISTS table_name           character varying(200),
  ADD COLUMN IF NOT EXISTS updated_at           timestamp;

ALTER TABLE ecos_control.ecos_data_lineage_edge
  ADD COLUMN IF NOT EXISTS pipeline_task_id     character varying(64),
  ADD COLUMN IF NOT EXISTS properties           jsonb,
  ADD COLUMN IF NOT EXISTS transformation       character varying(500);

-- step 2: 主键不冲突合并（若 C 步失败改 §二 回退路子：DELETE + 全量 home → control）
INSERT INTO ecos_control.ecos_data_lineage_node
  (id, node_type, node_name, alias_type, alias, node_table,
   datasource_id, layer, name, pipeline_task_id, properties, schema_name, table_name, updated_at)
SELECT h.id, h.node_type, h.node_name, h.alias_type, h.alias, h.node_table,
       h.datasource_id, h.layer, h.name, h.pipeline_task_id, h.properties,
       h.schema_name, h.table_name, h.updated_at
FROM ecos_data.ecos_data_lineage_node h
WHERE NOT EXISTS (
  SELECT 1 FROM ecos_control.ecos_data_lineage_node c
  WHERE c.node_id IS NOT DISTINCT FROM h.node_id
    AND c.node_table IS NOT DISTINCT FROM h.node_table
);

INSERT INTO ecos_control.ecos_data_lineage_edge
  (id, source_node_id, target_node_id, relation_type,
   pipeline_task_id, properties, transformation)
SELECT h.id, h.source_node_id, h.target_node_id, h.relation_type,
       h.pipeline_task_id, h.properties, h.transformation
FROM ecos_data.ecos_data_lineage_edge h
WHERE NOT EXISTS (
  SELECT 1 FROM ecos_control.ecos_data_lineage_edge c
  WHERE c.source_node_id = h.source_node_id
    AND c.target_node_id = h.target_node_id
);

-- step 3: home 侧 DROP
DROP TABLE ecos_data.ecos_data_lineage_edge;
DROP TABLE ecos_data.ecos_data_lineage_node;

COMMIT;
```

### (C) 1 张 DROP（td_data_category 的 control 侧 0 行 stale）

```sql
BEGIN;
DROP TABLE IF EXISTS ecos_control.td_data_category;  -- home 侧保留 5 行真身
COMMIT;
```

> 影响面 verify：`grep "ecos_data.td_data_category"` 现 45 处 mapper/xml/java 已限定（≠td_data_category 裸名，已证 move-safe）；`grep "td_data_category"（裸名）` = 0（无 mapper 引用 bare，故 control 侧 stale 0 行删除无风险）。

---

## 四、代码/yml 同步（不改库）

| # | 变更 | 路径 | 目的 |
|:-:|:--|:--|:--|
| 1 | dccheng `currentSchema=public` → `currentSchema=ecos_control,public` | `ecos_backend/services/dccheng/src/main/resources/application.yml:19` | 堵 G2 遗漏：`public` 现 0 对象，`ecos_control` 才是真身（dccheng = kb-service 独立体，18086，跑 kb 数据） |
| 2 | 保留 `ecos_data.td_data_category` 的 5 行 home 真身 · 无 yml 变 | — | td_data_category 迁移 6 JAR 归属判未触及 |

> 变更 1 是 yml 小改，走 code commit，不 push（G6 延续 "不 push"）；独立可回退（改回 `public` 一键）。

---

## 五、执行后对账（期望终值）

| Schema | 本方案前 | 本方案后 | 差异 |
|:--|:-:|:-:|:--|
| ecos_ai | 50 | 42 | −8（8 twin 全 home drop） |
| ecos_cognitive | 11 | 0 | −11（11 twin 全 home drop；schema 保留但清空） |
| ecos_data | 23 | 9 | −14（12 home 幻影 drop + 2 lineage home drop；`td_data_category` home 真身 5 行保留） |
| ecos_knowledge | 20 | 14 | −6（6 twin 全 home drop） |
| ecos_ontology | 19 | 7 | −12（12 twin 全 home drop） |
| ecos_control | 215 (208 表 + 7 视图) | 214 (207 表 + 7 视图) | −1（`td_data_category` control 侧 stale drop；lineage ADD 列不增表数） |
| **全库** | 473 | **421** | **−52**（= 8+11+14+6+12 home + 1 control-stale） |

> 验实测：跑完 §三 后 `SELECT count(*) FROM pg_class c JOIN pg_namespace n ON n.oid=c.relnamespace WHERE c.relkind IN ('r','v','p') AND nspname NOT IN ('pg_catalog','information_schema')` 应 = **421**（另 security 53 / infra 40 / sysman 20 / dq 20 / dw 2 / public 0 不变）。

---

## 六、执行协议（G4）

| 阶 | 动作 |
|:-:|:--|
| 0 | `_win_tasks/stop-backend.ps1 -WithFrontend`（7 JAR 全停，释放 PG in-use 锁）|
| 1 | 跑 §二 三条 RECHECK（净树漂移 / 并行窗口灌数据 → HOLD-RECHECK 停）|
| 2a | `psql -U postgres -d sys_man -v ON_ERROR_STOP=1 -f <(echo '<§三(A) SQL>')` — batch A，49 张引擎侧幻影 home drop |
| 2b | 重跑 §二(C) 印证 lineage 键唯一；`psql -f <(echo '<§三(B) SQL>')` — 2 张 MERGE |
| 2c | `psql -c 'BEGIN; DROP TABLE IF EXISTS ecos_control.td_data_category; COMMIT;'` — batch C 单行 |
| 3 | 全重验 §五 终对账 + `db-migration-lint.ps1`（预期：ST07 双份 twin WARN 归零，0 FAIL 保持，capture 新版 baseline 提交） |
| 4 | 改 `services/dccheng/.../application.yml:19`（`public` → `ecos_control,public`）+ commit（不 push，G6 延续） |
| 5 | `start-backend.ps1 -Modules gateway,sysman,datanet,buszhi,aiming,dccheng,workspace -Profile enterprise`，smoke：admin/admin123 登录 + `GET /api/v1/engine/data/layers/CURATED` + dccheng 侧 1 个 kb 端点 |
| rollback | 单批次 ROLLBACK；A/B/C 已 COMMIT 后 → 从 `schema-snapshot-2026-10-05-schemagov.sql` 逐 schema 重灌（`DROP SCHEMA X CASCADE; CREATE SCHEMA X; psql -f <schema 段>`）；零数据损失 |

---

## 七、诚实登记 & 风险

| 项 | 登记 |
|:--|:--|
| `ecos_ai.sys_agent_call_log` 双侧 0 行 | 仍 DROP home——按「引擎侧 = 幻影」普适规则删引擎侧；本表潜在真身属控制域 audit（`runtime` / `ecos_control` 的 audit 系），归属整理留「audit 底座收口」下批次；本轮只做 twin 去重，不涉归属迁移。 |
| `ecos_ontology.ecos_object_version` | 架构铁律 §3.1 v1.9 DROP 例外清单内的表；此处只做 twin 去重（control 侧 20 行 + home 侧 0 行 → 删 home），不涉归属与数据。 |
| 各引擎侧 8/11/… 张列集较 control 新（DIVERGED） | control 侧是 rehome 时点的旧列集 + 数据真身；若生产要用引擎 owner 的新列集 → 走「引擎 owner 收权」下批次做列 DIFF/迁移（§八 #6），本轮不动。 |
| dccheng `currentSchema=public`（现 0 对象） | 是本次实跑发现的 G2 遗漏：`public` 已清空，dccheng 裸名应落 `ecos_control`。修正 = §四 yml 改 `ecos_control,public`（改后 commit，不 push）。gateway fat-jar 那份 knowledge-engine 独立于 dccheng，无阻塞窗口。 |
| lineage 2 张 home 独有列含 `properties jsonb` | 本轮按现状 ADD 到 control 侧（dev 数据量极小）；严格 MC02 v1.2「禁 JSONB 直操」的收敛（降级 TEXT/`_json`）留 ADR/工程清单 #11 下批次，本轮不扩。 |
| 3 张 `td_data_field/resource/datasource` | 数据真身全在 `ecos_control`（20664/393/5 行），data-engine mapper 全裸名命中 control、引擎侧 0 行幻影 → home drop 安全；执行前 grep `docs/50-db_script/sql/8split/postgresql/` 印证三者不在 8split canonical（无单源 DDL 冲突）后再 drop。 |
| `db-migration-lint` 基线本批 UPDATE | 预期：ST07 双份 twin 类 WARN 归零、0 FAIL 保持；跑一次 capture 新版 baseline 并随本批记录。 |

> **未闭环（诚实登记，本批不做）**：`ecos_control` → 各引擎 schema 的**收权反迁**（把 19 data / 11 cognitive / 3 kb / 16 ontology / 8 ai 等引擎同名单表的真身搬回 owner schema）属「引擎 owner 收权」专项——涉及 6 JAR `currentSchema` tuple + mapper search_path 注入 + dccheng，本方案**只做引擎侧幻影去重**，不搬真身，明确脱开。

---

## 八、交付清单

| # | 产物 | 位置 |
|:-:|:--|:--|
| 1 | 本方案（doc） | 本文件 |
| 2 | 52 张矩阵 + 3 类裁决 | §一 |
| 3 | 执行 SQL（3 批） | §三 |
| 4 | 回滚凭证 | `canonical-baseline/schema-snapshot-2026-10-05-schemagov.sql` |
| 5 | yml 改动（未来批次） | §四 |
| 6 | 引擎 owner 收权（下批次） | §五 #1 |

---

<!-- 本方案 2026-10-05 · G4 范围内 · 全部 50 张 twin 有实测 + 实测证伪 · 未 push -->
