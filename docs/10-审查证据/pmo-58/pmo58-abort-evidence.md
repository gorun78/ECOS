# PMO-58 中止留痕（T1 PASS / T3 无损中止 — ARTIFACT_MISMATCH）

> 来源: 全栈开发工程师 / 日期: 2026-09-13 / 责任人: fullstack-implementer
> 对应指令: [PMO-58](../../04-本体/PMO-58-ecos-ontology-schema-ghost表物理清理.md)
> 分支: feat/ontology-workbench-wave-b | T3 执行前 HEAD: 77dc4fb
> **结论**: T1 硬卡口 PASS (8/8 = 0 行) → 进入 T3 → **ON_ERROR_STOP 无损中止
> (0 张表删除，运行库零变更)**。根因: PMO-58 预估"schema 下仅 8 表"与运行库
> 现状不一致 (**89 对象** + **4 条外键依赖**，含 1 条跨引擎 FK)，超出
> "8 表死锁"红线授权范围 → 上报 ARTIFACT_MISMATCH，中止等待 PMO 授权扩展。

## § T1 — 8 ghost 表逐行 count (硬卡口 PASS)

| ghost_table (ecos_ontology.*) | count |
|:--|--:|
| ecos_ontology_entity | 0 |
| ecos_ontology_property | 0 |
| ecos_ontology_relationship | 0 |
| ecos_ontology_action | 0 |
| ecos_ontology_rule | 0 |
| ecos_ontology_version | 0 |
| ecos_domain | 0 |
| ecos_business_glossary | 0 |

- 快照时间: 2026-09-13 13:07:52+00 (psql `-1` 单事务 REPEATABLE READ)
- **判停条件未触发** → T3 放行

## § T1 延伸核查 — schema 全对象清单 (T2 的 schema 决策依据)

`SELECT relname, relkind FROM pg_class WHERE relnamespace='ecos_ontology'::regnamespace;`
= **89 对象** (25 table + 59 index + 1 sequence)。其中：

| 分类 | 对象 | 处置 |
|:--|:--|:--|
| **8 张目标 ghost** | ecos_ontology_{entity,property,relationship,action,rule,version} + ecos_domain + ecos_business_glossary | PMO-58 授权 DROP |
| **17 张非 ghost 活表 (不在 PMO 范围)** | outbox_event (V48) / ecos_object_{attachment,data,links,relation,relationship,state_machine,timeline,version} (V9 建+V47 搬) / ecos_workflow{,_approval,_instance,_log,_task} (V9 建+V47 搬) / entity_definition, relationship_definition, metric_definition, action_definition, policy_definition, event_definition (V52 建) | **保留** — DROP SCHEMA 放弃 (非空 + 无 CASCADE 授权，PMO-58 前置决策 2 明确"若下还 有其它对象则只 DROP 表留 schema") |
| 1 sequence | ecos_business_glossary_id_seq | 随表 DROP 自动消解 |

⇒ **原计划末尾的 `DROP SCHEMA ecos_ontology` 已按 PMO-58 §前置决策 2 判定放弃**（schema 保留）。

### V 号裁决记录
- V123 (PMO-51 已占 commit)、V124 (认知模型)、V125 (PMO-52 并发批次未跟踪，working tree 存在)
- **裁决: V126 为空 → 原拟取 V126**；T3 中止后 **V126 文件已删除**，号位释放（不留未过门禁的迁移文件）

## § T3 — psql ON_ERROR_STOP 无损中止 (exit 3)

执行命令: `docker exec -e PGUSER=postgres ecos-postgres psql -U postgres -d sys_man -X -v ON_ERROR_STOP=1 -f /tmp/pmo58-drop.sql`

```
psql:/tmp/pmo58-drop.sql:46: ERROR:  cannot drop table ecos_ontology.ecos_ontology_entity because other objects depend on it
DETAIL:  constraint fk_onto_prop_ent on table ecos_ontology.ecos_ontology_property depends on table ecos_ontology.ecos_ontology_entity
HINT:  Use DROP ... CASCADE to drop the dependent objects too.
```

psql 单语句失败 → ON_ERROR_STOP 立即退出，**0 张表删除**。

### 中止后复查 (无损确认)

| 指标 | T1 基线 | T3 中止后 |
|:--|--:|--:|
| pg_class `ecos_ontology` 对象数 | 89 | **89** |
| 8 表存在性 | 8/8 | **8/8 (全存在，全 0 行)** |

运行库零变更确认：DROP 语句在本库 **第一次尝试**即被拦，无半落盘状态。

## § 根因 — 外键依赖 (T1 延伸核查盲区)

`pg_constraint` 探查 (T1 只查 pg_class 对象清单，未查 pg_constraint)：

| FK 约束 | from 表 | to 表 | 处置 |
|:--|:--|:--|:--|
| fk_onto_prop_ent | ecos_ontology.ecos_ontology_property | ecos_ontology.ecos_ontology_entity | 附着在 8 ghost 表之间，DROP 表前需先 `DROP CONSTRAINT` (8 表 DROP 的固有前置) |
| fk_onto_ent_domain | ecos_ontology.ecos_ontology_entity | ecos_ontology.ecos_domain | 同上 |
| fk_onto_gloss_domain | ecos_ontology.ecos_business_glossary | ecos_ontology.ecos_domain | 同上 |
| **fk_cog_goal_domain** | **ecos_cognitive.ecos_wm_goal** (认知引擎活表，0 行) | ecos_ontology.ecos_domain | **跨引擎出界** — 需 `ALTER TABLE ecos_cognitive.ecos_wm_goal DROP CONSTRAINT fk_cog_goal_domain`，触碰认知引擎 schema，**超出 PMO-58 "8 表死锁"授权** |

### FK 来源溯源
- 全部 4 条 FK **不在 gateway/migration V*.sql** (grep `fk_onto|fk_cog` 全域 V 脚本 0 命中)
- 来源: `ecos-sql/postgresql/` 双轨初始化脚本（postgres/mysql/oracle 三套）：
  | FK | 源文件 |
  |:--|:--|
  | fk_onto_ent_domain / fk_onto_prop_ent / fk_onto_gloss_domain | `ecos-sql/postgresql/04_ecos_ontology.sql` L55/L87/L194 |
  | fk_cog_goal_domain | `ecos-sql/postgresql/07_ecos_cognitive.sql` L51 |
- 说明该 schema 曾被 init 脚本完整建库（v0.1 DDL 体系），与 gateway/migration V 系列**双轨并存** — 这也是 PMO-58 背景 "8 张表" 预估未覆盖的直接原因

## § 状态与未闭环项

| Task | 状态 |
|:--|:--|
| T1 8 表 count 终验 | PASS (8/8 = 0, 快照见上) |
| T2 V126 DROP 脚本 | 已起草 → **T3 未过门禁前已删除** (不留裸迁移文件)；所需前置变更见 § 待授权项 |
| T3 运行库执行 | **中止** (0 表删除，无损) |
| T4 回滚 DDL 留档 | **PASS** — [rollback-ddl-pmo58.sql](../../11-运维/rollback-ddl-pmo58.sql) (8 表 CREATE + canonical→alias 数据快照段 + schema 保留声明；**新增 § (d) 4 条 FK 重建段** — 需 PMO 确认) |
| T5 冒烟 + 留痕 | 未执行 (无 DROP 发生，无回归面)；本文件即 T5 留痕 |
| commits | 1 个 (T4 回滚 DDL + 本中止留痕)，**V126 不入库**，**并发批次 V125 未 add**，**0 行 Java 变更** |

## § 待 PMO 授权项 (解除 ARTIFACT_MISMATCH 三选项)

1. **授权扩展 (推荐)**: PMO-58 修订 V126 为 "先 DROP 4 约束 → DROP 8 表"，
   `ecos-sql/postgresql/04*.sql` 与 `07*.sql` 同步追加 DROP CONSTRAINT 段
   （全套一键 init 新库一致性）；`ecos_cognitive.ecos_wm_goal` 的
   `domain_id FK` 随 DROP 彻底移除，若后续需要请改 ON DELETE SET NULL 重建
2. **仅解认知侧 FK**: 保 ecosystem 内 dept 依赖链（抚养范围最小）
   — 新 V127 + `ecos-sql/` update
3. **维持现状**: ghost 表已 COMMENT 登记 (V122) + 本留痕；未来并入 V127 时一并清理

> 再次强调: 本次执行 **0 张表删除**, 运行库零变更; 只动 working tree 的
> `docs/` 留痕文件。pmo-58 实际 DROP 需 PMO 修订授权后, 重新走一次 T1 → T3 全流程。
