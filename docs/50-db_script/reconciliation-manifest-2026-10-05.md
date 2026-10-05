# 现网 × 仓内脚本 对账台账（2026-10-05，Python 交叉实测）

> 责任人: AI Agent | 日期: 2026-10-05 | 方法: [live-objects-classified.tsv](./canonical-baseline/captured-live-2026-10-05/live-objects-classified.tsv)(686 现网对象) × [script_create_tables/views](./evidence-2026-10-05/)(仓内 260 表 + 13 视图声明) 按裸名交叉比对
> 用途: 落地 [数据层规范化治理方案](./数据层规范化治理方案-2026-10-05.md) §四 的归位判定；**本台账是对账事实源，非规划估算**
> 口径: 裸名去 schema（避 `schema.table` 限定差异假阳性）；正则近似 CREATE 解析（`IF NOT EXISTS` 容错），极端边界（动态 SQL/模板/子查询 `if`）可能漏计，已剔除 2 个解析噪声项

---

## 〇、对账总账

| 维度 | 数量 | 说明 |
|:--|:--:|:--|
| 仓内脚本声明表 | **260** | gateway 197 + 其它目录合并（去重降噪后 258 有效 + 2 噪声 `if`/`代`） |
| 现网表（kind r/p） | **596** | `pg_class` 实测，含分区 |
| **两者都有（声明·现网）** | **180** | DDL 依据充分，`git mv` 到 canonical 对应族即可 |
| **① 脚本声明·现网缺失** | **80** | 见 §一（多为分册09/10 未实跑，非缺陷） |
| **② 现网存在·脚本无 CREATE** | **409** | 见 §二（**核心发现**：主控制 public 表真源缺失） |

---

## 一、① 脚本声明 · 现网缺失（80 张）→ canonical 重放时依序落地

对战事实：这 80 张**不是缺陷**，是 **dev PG 落后于脚本**（分册09/10 交付按项目纪律「离线交付·不实跑库」，实跑属授权闸）。分簇归属 canonical 目录：

| 簇 | 表数 | canonical 落点 | 分册来源 |
|:--|:--:|:--|:--|
| `ecos_wagent_*`（goal/mission/question/readiness×3/run×3/tool_contract/skill/candidate×2/evidence/claim×2） | ~18 | `20-engine-ai/` | 分册10 W-Agent（V227~V240） |
| `ecos_kb_*`（access_request/assumption×2/document/extract×3/graph×2/lineage_event/market_asset/ontology_snapshot/profile×2/term/vector_fallback）+ `kb_nav_*`×3 | ~19 | `20-engine-kb/` | 分册04 知识域 |
| `ecos_fc_*`（run/result_detail/backtest/action_ext）+ `ecos_application_forecast_run` + `ecos_forecast_input_snapshot` + `ecos_caliber(_version)` + `ecos_metric_definition` | ~10 | `30-business-dw/` + `20-engine-ontology/`(caliber/metric) | 分册09 确定性预测 |
| `ecos_runtime_*`（alert×2/audit_retry/event_dlq/llm_usage） | 5 | `10-control-main/`（横切 runtime） | 分册09/10 runtime |
| `ecos_biz_*_fact`（cost/resource/stage/project_attribution） | 4 | `30-business-dw/`（业务事实五表） | 分册02 M0 |
| `ecos_ai_*`（compression_batch/guardrail_decision_ledger/tool_permission_binding/tool_registry） | 4 | `20-engine-ai/` | 智能域 |
| `ecos_cognitive_*`（mind/pipeline/scenario_mind） | 3 | `20-engine-cognitive/`木 C | 认知域 |
| `ecos_security_*`（asset/crypto_key/policy） | 3 | `10-control-main/`（security 护） | 横切 |
| `ecos_ontology_*`（domain/proposal/workbench_object）+ `ecos_interface_ref` | 4 | `20-engine-ontology/` | 本体域 |
| `sys_rule_version` / `sys_extraction_source` / `agent_registry` / `dq_score_asset` / `ecos_dq_*` | 6 | `10-control-main/` + `40-legacy-frozen/ecos_dq` | main-control + DQ |
| 其它（`ecos_kg_sync_log`/`ecos_evolution_log`/`*_migration_report`/`*_bak_v171`） | 6 | 按 owner engine | 跨分册 |

> **V214~V244 段**（fc/caliber/wagent/runtime）**未灌 dev**，canonical 重放时自动落地 → **同时回填当前 dev 缺口**（对齐 memory：分册09/10 实跑授权闸）。

---

## 二、② 现网存在 · 脚本无 CREATE（409 张）→ **核心发现**

| 簇 | 数量 | canonical 落点 | 判定 |
|:--|:--:|:--|:--|
| **`other`（各 schema `ecos_*` 平台表，尤以 `public` 主控制为主）** | **294** | **`10-control-main/`（pg_dump 反归并，本批核心工时段）** | **最关键**：这些是**当前系统真在跑的主控制承载表**，但 `CREATE TABLE` 不在 gateway V* 单源（源自 `12_to_8` 大改 + `01~08` 快照 + legacy 直接建表）——**即「gateway 单源不覆盖现网」的量化证据** |
| `act_*`（Flowable 工作流引擎表） | 39 | `40-legacy-frozen/`（若保留工作流）或随 demo drop | 引擎自带态，非项目脚本资产 |
| `outbox_event*`（8 outbox 分表 + 变体） | 37 | `10-control-main/` 或各引擎 outbox | 横切事件 outbox，按需用（脚本声明但各 schema 未建） |
| `agent_cost*`（2025/2026 月度分区） | 25 | `20-engine-ai/` | 运行时按月建分区，非 static DDL（**canonical 不逐月重建**，落分区策略说明） |
| `dw_*`（业务域演示加工表） | 12 | `30-business-dw/` 或 `99-reference-snapshots/` | 部分业务域，按具体表定性 |
| `common_*` + `demo_*`（残余，非完整 demo 前缀） | 2 | `40-legacy-frozen/` / `99-reference-snapshots/` | 演示/公共残留 |

> **294 张 `other` = 归集的价值本体**：此前 gateway 单源「漏 ~405 对象」的定性，本次精确定量为 **409 现网孤儿（其中 294 主控制平台表）**，须在 P3 用 [full-schema-only.sql](./canonical-baseline/captured-live-2026-10-05/full-schema-only.sql) 反归并进 `canonical/10-control-main/`，否则新单源仍不覆盖现网。

---

## 三、落位结论（供 P3 归集直接消费）

1. **180 两者都有**：DDL 依据在 gateway `V*`，直接 `git mv` 到 canonical 对应族。
2. **80 声明·现网缺失**：随 `git mv` 入 canonical；重放 `IF NOT EXISTS` 幂等落地（回填 dev）。
3. **294 现网·无脚本（主控制）**：**pg_dump 反归并** → `canonical/10-control-main/`（本批唯一大工时）。
4. **39 act_ / 37 outbox / 25 agent_cost分区 / 12 dw_ / 2 common/demo**：按上表簇分流到 `40-legacy-frozen/`、`10-control-main/`、`20-engine-ai/`、`30-business-dw/`。
5. **owner 归属**：每表 canonical 落点与 §2.4 归属表一一对应；`bare`/`sys_`/`kb_` 等非 owner 前缀表同时见 [数据访问违规台账](./data-access-violations-2026-10-05.md) ST09 项。

---
<!-- 现网×脚本对账台账 / 2026-10-05 / Python 交叉实测 180/80/409 / 供 P3 归集消费 -->