# 真实库数据基线快照（2026-11-17）

> 用途：`docs/50-db_script/00_init/` 初始化脚本的对账参照，**不参与初始化**。
> 采集命令：`docker exec ecos-postgres psql -U postgres -d sys_man -tAc "SELECT schemaname||'.'||relname||' | '||n_live_tup FROM pg_stat_user_tables WHERE n_live_tup>0 ORDER BY schemaname, n_live_tup DESC"`

## 结构总览

| 指标 | 数值 |
|:--|--:|
| 数据库 | `sys_man`（PostgreSQL 16.15 + pgvector + pg_trgm） |
| schema 数 | 11（8 目标 + `ecos_sysman` / `ecos_security` / `public`） |
| 基表总数 | 488（含 292 张分区子表） |
| 独立表数 | 384 |
| 8 目标 schema 表数 | 372（+ 235 张分区子表） |
| 分区父表 | 4（`ecos_control.td_audit_log` 实为普通表，分区父表只有 `ecos_ai.agent_cost` / `ecos_infra.outbox_event` 在 8 schema 内） |
| 外键数 | 44 |
| 视图 | 3 |
| 序列 | 60 |
| 触发器 | 0 |
| 扩展 | plpgsql / vector / pg_trgm |

## 非空表清单（53 张）

### ecos_control（34 张）

| 表 | 行数 | 分类 |
|:--|--:|:--|
| `ecos_audit_log` | 31913 | 运行时噪声 |
| `td_runtime_task_execution` | 14721 | 运行时噪声 |
| `td_runtime_task_status` | 295 | 运行时 |
| `td_runtime_task` | 289 | 运行时 |
| `ecos_business_scenario` | 245 | 演示/业务 |
| `td_audit_log` | 192 | 运行时 |
| `sys_config` | 178 | **系统配置** |
| `ecos_pareto_problem` | 27 | 业务 |
| `ecos_tenant_usage` | 24 | 系统 |
| `ecos_object_timeline` | 18 | 业务 |
| `ecos_rls_policy` | 28 | **系统安全**（`pg_stat` 显示 15 属统计滞后，实查 28） |
| `ecos_ontology_entity` | 15 | 演示/业务 |
| `sys_dict` | 14 | **系统字典** |
| `ecos_pipeline_node` | 12 | 业务 |
| `demo_customer` | 10 | 演示 |
| `sys_agent_message` | 9 | 运行时 |
| `ecos_ontology_relationship` | 7 | 演示/业务 |
| `ecos_scenario_binding_link` | 5 | 业务 |
| `ecos_scenario_blueprint` | 4 | 业务 |
| `ecos_ontology_property` | 2 | 演示/业务 |
| `users` | 2 | 系统 |
| `sys_agent_session` | 2 | 运行时 |
| `dict_table` | 2 | 系统字典 |
| `dict_column` | 2 | 系统字典 |
| `ecos_wm_goal` | 1 | 业务 |
| `ecos_scenario_sandbox_layout` | 1 | 业务 |
| `ecos_agent` | 1 | 业务 |
| `ecos_wm_scenario` | 1 | 业务 |

> 注：`td_user`(17) / `td_role`(14) / `td_permission`(79) / `td_role_permission`(135) / `td_organization`(12) / `td_user_role`(18) / `td_user_organization`(12) 未出现在 `pg_stat_user_tables` 非零列表（统计信息滞后），以下为 `count(*)` 实查值。

### ecos_ai（6 张）

| 表 | 行数 | 分类 |
|:--|--:|:--|
| `ecos_evolution_log` | 175 | 运行时 |
| `agent_approval` | 169 | 业务 |
| `ecos_wagent_candidate` | 134 | 业务 |
| `ecos_agent_registry` | 13 | 业务 |
| `ecos_aip_pipeline` | 9 | 业务 |
| `ecos_ai_guardrail_decision_ledger` | 4 | 业务 |

### ecos_knowledge（5 张）

| 表 | 行数 | 分类 |
|:--|--:|:--|
| `ecos_kb_extract_audit` | 45 | 业务 |
| `kb_doc` | 36 | 演示/业务 |
| `ecos_kg_sync_log` | 10 | 运行时 |
| `kb_scheduled_extract` | 1 | 业务 |
| `ecos_kb_ontology_snapshot` | 1 | 业务 |

### ecos_data（3 张）

| 表 | 行数 | 分类 |
|:--|--:|:--|
| `ecos_pipeline_definition` | 41 | 演示/业务 |
| `ecos_pipeline_execution` | 39 | 运行时 |
| `dq_rule` | 37 | 演示/业务 |

### ecos_ontology（7 张）

| 表 | 行数 | 分类 |
|:--|--:|:--|
| `ecos_ontology_proposal` | 11 | 业务 |
| `ecos_workflow_log` | 5 | 业务 |
| `action_definition` | 3 | 业务 |
| `ecos_workflow_approval` | 1 | 业务 |
| `ecos_workflow_instance` | 1 | 业务 |
| `ecos_workflow` | 1 | 业务 |
| `ecos_workflow_task` | 1 | 业务 |

### ecos_cognitive（2 张）

| 表 | 行数 | 分类 |
|:--|--:|:--|
| `ecos_cognitive_framework` | 12 | 业务 |
| `ecos_cognitive_outcome_evaluation` | 2 | 业务 |

### ecos_dw（1 张）

| 表 | 行数 | 分类 |
|:--|--:|:--|
| `e2e_target` | 34 | 演示 |

### public（4 张非空 / 共 59 张基表）

> **2026-11-17 阶段 1 订正**：原记「public 仅 1 张有数据、43 张唯一表全 0 行」**与实测不符**。
> `pg_class.reltuples` 全为 -1（从未 analyze），据其判断会漏掉 3 张活表。改用逐表 `count(*)` 实查。

| 表 | 行数 | 分类 |
|:--|--:|:--|
| `ecos_pipeline_function` | 120 | 业务（管道内置函数注册表，**待迁 ecos_data**） |
| `ecos_scenario_query_history` | 18 | 业务（**待迁 ecos_control**，V345） |
| `ecos_skill` | 3 | 业务（技能包，**待迁 ecos_ai**） |
| `ecos_cron_job` | 2 | 业务（定时任务，**待迁 ecos_ai**） |

#### public 59 张基表的三分类（逐表 `count(*)` + 代码引用审计）

| 类 | 张数 | 判据 | 处置 |
|:--|--:|:--|:--|
| A 被遮蔽死壳 | 23 | 他 schema 有同名表，且权威副本在 `ecos_control`（`currentSchema` 首位）⇒ public 侧永不命中 | 阶段 3 DROP（先解 FK） |
| B public 独有 | 36 | 全库无同名表 | 见下细分 |
| B1 独有且被引用 | 29 | 剥注释后含 SQL 关键字的引用行 > 0（其中 4 张带数据、25 张 0 行但**是唯一载体**） | **搬迁**（V345 一张 + V346 二十八张），禁 DROP |
| B2 独有仅注释提及 | 4 | `ecos_decision_source_ref` / `ecos_fc_action_ext` / `ecos_knowledge_document` / `ecos_ontology_domain` | 待裁（前两者代码读 `ecos_cognitive`/`ecos_knowledge` 同名表但**库中不存在** ⇒ 属缺表缺陷，非清退面） |
| B3 独有零引用 | 3 | `ecos_runtime_llm_usage` / `ecos_scenario_active_mind` / `ecos_scenario_mind_ref` | 阶段 3 DROP 候选 |

**关键教训**：「0 行」不等于「无用」。B1 的 25 张空表是管道/场景/本体/知识功能的唯一物理载体，
按旧判断直接 DROP 会打断功能；A 类才是真死壳。归属判据 = 引用方所属模块映射 ST07 schema，
29 张每张均有单一主导模块（次要跨域读 ≤1 行），无歧义。

## 空壳 schema（0 行，待 DROP）

| schema | 表数 | 状态 |
|:--|--:|:--|
| `ecos_sysman` | 8 | 全部 0 行，不在任何 `currentSchema` |
| `ecos_security` | 51 | 全部 0 行，不在任何 `currentSchema` |

## 数据分类汇总

| 分类 | 表数 | 总行数（约） | 初始化脚本处置 |
|:--|--:|--:|:--|
| 系统配置（`sys_config`/`sys_dict`/IAM） | 6 | ~300 | **导出到 `09_system_seed.sql`** |
| 系统安全（`ecos_rls_policy` 等） | 2 | ~20 | 导出 |
| 演示/业务 | ~25 | ~600 | 导出到 `04-demo-data/demo_seed.sql` |
| 运行时噪声（审计/任务/消息） | ~12 | ~47000 | **不导出** |

## 重复表实查

| 表 | ecos_control | 旧位置 | 实际命中（`currentSchema` 首位） |
|:--|--:|--:|:--|
| `sys_config` | 178 | `ecos_sysman` 0 | ecos_control |
| `sys_dict` | 64 | `ecos_sysman` 0 | ecos_control |
| `td_audit_log` | 192 | `ecos_security` 0 | ecos_control |
| `ecos_rls_policy` | 28 | `public` 0 | ecos_control（`SET search_path` 实测；行数 2026-11-17 `count(*)` 复核，原记 15 系 `pg_stat` 陈旧值） |
| `ecos_ontology_entity` | 18 | `public` 0 / `ecos_ontology` 0 | ecos_control（**与 `DdlCodeSchemaAlignmentGateTest` 白名单登记的"权威在 ecos_ontology"不符，待订正**；行数 2026-11-17 `count(*)` 复核，原记 15） |
