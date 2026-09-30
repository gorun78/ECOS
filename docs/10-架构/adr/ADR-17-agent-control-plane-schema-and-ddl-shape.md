# ADR-17 — Agent 控制面落位与 DDL 形态（收编 ST07 五枚举 + 单源脚本与库对齐）

> 来源: 详细设计 10 册 §六（数据设计）与 §一 X-3~X-9（只读实测） | 日期: 2026-09-29 | 责任人: AI Agent（架构治理）
> 版本: v1.1
> 裁决: **Accepted**（2026-09-29 用户批量批准，凭证 = 需求检视报告 §十四 14.1 之 **R-33／R-34／R-35／R-36／R-41**，按各表「本设计推荐」列定版）
> 上游: 数据库访问规范 v1.2（ST07 / ST03-A / ST04 / ST05 / MC01~MC06 / DR01 / DR05~DR08 / §四附则1）、架构铁律 §3.1（DDL 单源）、ADR-3（单库 5+1）
> 状态: **Accepted** — 分册 10 的 `V227~V240` 现按批准口径落**迁移脚本文件**（只写文件，不对 `ecos-postgres` 实跑，见报告 §14.4）；本 ADR §2.1 的"单源脚本与库不一致"结论属**已实测确定的缺陷**，其订正本就直改

---

## 1. 背景（Context）

### 1.1 附件的落位主张与规则冲突

附件三册（第二册 §1.1、第三册 §1.2/附录 A）要求：新建"Agent 控制面库"，表前缀 `agt_*`（编排）、`aim_*`（模型与能力）、`cog_*`（认知模型）；DDL 使用 `text PRIMARY KEY` + 前缀 ULID、`jsonb` 参与唯一约束与索引、`timestamptz`、`text[]`、`PARTITION BY RANGE`、partial unique index、原生 `CREATE POLICY USING (tenant_id = current_setting('app.tenant_id'))`、`DEFAULT now()`。

ST07 规定引擎 schema **仅五枚举**（`ecos_data/ecos_ontology/ecos_knowledge/ecos_ai/ecos_cognitive`）+ 主控制 schema，且内容表已明写 `ecos_ai` = "Agent/Loop 定义"、`ecos_cognitive` = "认知模型"；MC01 要求 PK `VARCHAR(36)` 应用侧生成 UUID 且 DDL 无默认值；MC02 要求受控 JSON **只准 TEXT** 且不参与 WHERE/JOIN/索引；ST04/ST05 要求 CLS/RLS 一律经 security-engine REST。**结论：附件 DDL 不可原样采纳。**

### 1.2 本 ADR 的新发现：单源 DDL 与库内实际不一致（确定性缺陷）

以下四项均为只读实测（`grep` + `docker exec ecos-postgres psql -Atc` catalog 查询，无任何 DDL/DML）：

| # | 实测 | 证据 |
|:--|:--|:--|
| ① | 库内**不存在 `ecos_agent` schema**；实存 16 个：`ecos_ai / ecos_aiming / ecos_buszhi / ecos_cognitive / ecos_data / ecos_datanet / ecos_dccheng / ecos_demo / ecos_dq / ecos_dw / ecos_infra / ecos_knowledge / ecos_ontology / ecos_security / ecos_sysman / public` | `SELECT string_agg(nspname,',' ORDER BY nspname) FROM pg_namespace …` |
| ② | 单源目录仍**建该 schema 与 21 张表**：`V47__ecos_schema_isolation.sql:11` `CREATE SCHEMA IF NOT EXISTS ecos_agent;`；V48/V50/V52 内 `CREATE TABLE IF NOT EXISTS ecos_agent.*` 去重 **21 张**（agent_definition / agent_execution / agent_execution_step / agent_memory / agent_cost / agent_evaluation / agent_governance_policy / agent_approval / agent_registry / ecos_mission / ecos_mission_task / outbox_event / world_state / world_snapshot / scenario / simulation / simulation_result / forecast / optimization_job / strategy_recommendation / causal_edge）；V55/V56 另向 `ecos_agent.ecos_tool_definition`/`agent_registry` INSERT | `grep -rhoE "CREATE TABLE IF NOT EXISTS ecos_agent\.[a-z_]+" … \| sort -u \| wc -l` = 21 |
| ③ | 同名职责表**实存 `ecos_ai`**（该 schema 50 表，agent 族 14 张、**全 0 行**、PK 仍 `VARCHAR(64)` 违 MC01、`agent_cost*` 25~26 个按月分区子表）+ `public` 双镜像（`ecos_mission`/`ecos_mission_task` 等） | `pg_tables` / `information_schema.columns` |
| ④ | `ecos_agent → ecos_ai` 的迁表语句**只写在第二 DDL 源**：`ecos-sql/migration/12_to_8_schema.sql:127-145`（Phase 8，15 条 `ALTER TABLE ecos_agent.* SET SCHEMA ecos_ai`），而同文件 `:197` 的 `DROP SCHEMA IF EXISTS ecos_agent` **被注释掉** | 同文件行号 |

**推论**：现网库是"由 `ecos-sql` 初始化路径 + Phase 8 迁表"得到，**单源目录从未与库对齐**。后果是：按单源重放即在 ST07 五枚举之外造出第 12 个 schema，并与 `ecos_ai` 现版本构成第三套镜像；而代码侧写 `ecos_agent.` 前缀的唯一引用方 `services/agent-service` 又不在 7 个部署 JAR 之列（R-41）。这同时**推翻了既往三份文档中"存量 `ecos_agent` schema 11 表"的记载**（需求检视报告 §13.7 R-34/R-41、落地检视报告 WC-03/WC-09 已同步更正）。

## 2. 决策（Decision）

### 2.1 单源可信性修复（M0，不等裁决）

1. 单源目录内**禁止出现 ST07 五枚举之外的 `CREATE SCHEMA`**：`V47:11` 的 `ecos_agent` 条目与 V48/V50/V52/V55/V56 的 `ecos_agent.` 限定，**订正为 `ecos_ai.`**（只改脚本文字与 schema 限定 + 头注说明，**不 DROP、不改已入库对象、不新增迁移**）。
2. 第二 DDL 源 `ecos-sql/migration/12_to_8_schema.sql` 定性为**待作废**（其"迁表事实"须以单源新脚本重述），撤销顺序严格依赖 **C142/W160**（先完成 `{schema}.` 限定改造，后撤 V47 的 `ALTER DATABASE … SET search_path`），**否则现网立即崩**。此项与 **R-22** 合并裁决。
3. `_win_tasks/db-migration-lint.ps1` 新增三项判定（ARCH_SPEC §12.2 已登记）：脚本内 `CREATE SCHEMA` 必须落五枚举；脚本内 `{schema}.` 限定必须能在 `pg_namespace` 命中；第二源不得含 `SET SCHEMA`。

### 2.2 落位（随 R-33）

| 附件对象 | ECOS 落位 | 表名 |
|:--|:--|:--|
| `agt_*`（Goal/Mission/Question/Run/Step/Candidate/Evidence/Claim/Tool Contract） | **`ecos_ai`** | `ecos_wagent_*`（V227~V240 草案，分册 10 §六） |
| `aim_*`（模型与能力注册 / Router 配置） | **`ecos_ai`**（与 06 册 F06-18 合并立项，随 R-47） | `ecos_wagent_skill` / 06 册载体 |
| `cog_*`（认知模型定义/版本/评估） | **`ecos_cognitive`**（随 R-42 的规则更正）；**计算产物落业务域** `ecos_dw`/APPLICATION 经 data-engine 写通道（ADR-14） | 05 册载体 |
| 审计事件 | **不建表**，走 Kafka `ecos.audit`（ST06） | — |

**不新建 schema、不新建"Agent 控制面库"、不建审计表。**

### 2.3 DDL 形态（随 R-35）

1. PK `VARCHAR(36)`，应用侧 UUID，DDL 无默认值（MC01）；禁 `text`+ULID 前缀、禁 `nextval`、禁 `gen_random_uuid()`。
2. 快照类 JSON 一律 **`*_json` TEXT**（【2026-09-30 DR04 校订】原稿作 `*_text`，违 DR04「JSON 语义列必加 `_json` 后缀」；落地 25 列已按 `*_json` 收口，见 `docs/40-实现/DDL迁移脚本落地登记-2026-09-30.md` §五）且**不参与 WHERE/JOIN/索引**（MC02）；需检索的字段显式成列。纯文本/引用指针列仍可 `*_text`；向量降级列名以 MC05 正文点名的 `embedding_text` 为准（DR04 登记例外）。
3. 通用列齐备：DR06 五审计列 + DR07 `version_no VARCHAR(20) NOT NULL` + DR08 `domain VARCHAR(50) NOT NULL DEFAULT 'default'`，并**并带** `tenant_id`/`org_id`（新表可加，跨租户断言仍归 REQ-PLT-24/25，随 R-36）。
4. 金额/比率/概率列 `NUMERIC(18,2)`，**逐列写入 ST03-A 豁免登记表**（控制域列永不可豁免；文档中"明文即可"而不引用登记表条目者按违 ST03 处理）。
5. 禁 `PARTITION BY`、禁 partial index（MC03 多库兼容）；容量与转冷诉求改 runtime-event 冷存 + MinIO 归档。
6. 禁 `CREATE POLICY`：CLS/RLS 一律经 security-engine REST（ST04/ST05），附件 §12.3 的 RLS 代码块整体作废。

### 2.4 存量处置（随 R-34/R-41，默认不迁）

`ecos_ai` 内 agent 族 0 行表：**只定性、停写、不 DROP**（除非用户按 R-12 的"零行 ∧ 零引用"双条件判允许）；W Agent 控制面以 `_v2` 合规新表承接（只加不删）；`services/agent-service` 归属随 R-41（推荐"定性遗留停写并归档"）。

## 3. 后果（Consequences）

- **正面**：Agent 控制面从"新建问题"转为"归位 + 合规化问题"，复用 `ecos_ai` 现成 0 行语义位；lint 具备可判性，能自动拦住"脚本建库里没有的 schema"这类确定性缺陷；三方言（PG/MySQL/Oracle）兼容性不再被 PG 专有形态绑死。
- **代价**：`{schema}.` 限定改造（C142/W160）成为硬前置，撤 `search_path` 必须排在其后；表名 `ecos_wagent_*` 与附件 `agt_*` 需在各处文档做别名说明。
- **风险**：若 R-33 反向裁"新建 `ecos_agent` schema"，则本 ADR §2.2/§2.3 的落位表整体作废，且 ST07 需重开修订。
- **验收口径**：`mvn -Dtest=WAgentDdlShapeTest#noJsonbNoPartialIndexNoCreatePolicy` + `pw wagent-tenant-isolation.spec.ts`（P-3/P-4 未闭环前记"未执行"）+ `db-migration-lint.ps1` 三项新检查 0 FAIL。

## 4. 关联

| 关联 | 位置 |
|:--|:--|
| 需求 | PRD-10 REQ-WAG-04（落位与合规）；PRD-01 REQ-PLT-24/25（多租户前提） |
| 设计 | 详细设计-10 §六（V227~V240 表清单 + 读写边界 + seed 字典组 `wagent.*`） |
| 偏差 | ARCH_SPEC **C228/C229/C230/C231/C243/C251**（其中 C230 为本 ADR §1.2 的直接产物） |
| 裁决 | **R-33 / R-34 / R-35 / R-36 / R-41**，与 **R-1/R-1b/R-12/R-13/R-19/R-22** 同批（"存量载体归位"一族） |
| 其他 ADR | ADR-3（单库 5+1）、ADR-12（`public`→`ecos_control` 路径）、ADR-14（产物落业务域）、ADR-16（编排域落位） |

## 5. 变更日志

| 版本 | 日期 | 内容 |
|:--|:--|:--|
| v1.0 | 2026-09-29 | 首版（Proposed）。含新发现的确定性缺陷 C230（单源 DDL 写 `ecos_agent.*` 而库内无该 schema，迁表只在第二源，`:197` DROP 被注释），并据此更正既往"存量 `ecos_agent` schema 11 表"记载。 |
| v1.1 | 2026-09-29 | 随需求检视报告 §十四 批量批准：裁决 **Proposed → Accepted**（R-33／R-34／R-35／R-36／R-41 按推荐项定版）。正文决策未变；`V227~V240` 由"一律不落"改为"**落迁移脚本文件、不实跑库**"（§14.4 执行边界）。 |
