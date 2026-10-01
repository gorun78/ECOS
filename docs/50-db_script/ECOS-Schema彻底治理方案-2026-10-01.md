# ECOS Schema 彻底治理方案（可删可改版）

> 版本: 2.0 | 2026-10-01
> 性质: **一次性彻底初始化迁移**（用户授权可删可改，不受 R9/knownLegacy 约束）
> 依据: 《数据库访问规范》v1.3 + 《数据湖存储分层规范》v2.0
> 前置: `docs/50-db_script/ECOS数据库现状盘点与交叉验证报告-2026-10-01.md`
> 迁移脚本: `docs/50-db_script/V200__schema_governance.sql`（本方案配套）

---

## 一、治理目标

### 1.1 目标态（8 schema）

| # | Schema | 性质 | 表数（治理后） | 说明 |
|:--|:--|:--|:--|:--|
| 1 | **ecos_control** | 主控制 schema | ~55 | sysman/security/runtime/workspace/workflow/配置/字典/运维 |
| 2 | **ecos_data** | 引擎 schema（土D） | ~33 | 管道 + DQ + 数据源 + 目录 |
| 3 | **ecos_ontology** | 引擎 schema（金I） | ~28 | 本体定义 + 工作流 + 口径 + 指标 |
| 4 | **ecos_knowledge** | 引擎 schema（水K） | ~25 | 知识文章 + 图谱 + 向量 + 导航 |
| 5 | **ecos_ai** | 引擎 schema（火W） | ~40 | Agent + 技能 + 任务 + 定时 + 仿真 + 场景 |
| 6 | **ecos_cognitive** | 引擎 schema（木C） | ~14 | 认知规则 + 世界模型 + 决策 |
| 7 | **ecos_infra** | 横切基础设施 | ~3 | 统一 outbox_event + saga + 追踪 |
| 8 | **ecos_dw** | 业务域 DW 层 | 3 | 演示数据（后续业务域表按需建） |

**消灭**：public（→ ecos_control）、ecos_identity、ecos_agent、ecos_dq + 7 空壳 schema = **消灭 11 个 schema**

### 1.2 治理动作汇总

| 动作 | 数量 | 说明 |
|:--|:--|:--|
| 新建 schema | 5 | ecos_ai / ecos_cognitive / ecos_control / ecos_infra / ecos_dw |
| 物理迁移表 | ~190 | ALTER TABLE SET SCHEMA（从 public/ecos_identity/ecos_agent/ecos_dq 迁入目标） |
| 删除重复空表 | ~40 | 两侧均空的重复表直接 DROP |
| 删除重复有数据表 | ~5 | 保留数据多的一侧 |
| 合并 outbox_event | 11→1 | 全部空表，直接 DROP 旧表 + 在 ecos_infra 建统一表 |
| 删除空壳 schema | 8 | ecos_catalog/pipeline/object/audit/mission/workflow/rule + config |
| 删除遗留 schema | 3 | ecos_identity / ecos_agent / ecos_dq（清空后 DROP） |
| 重命名 schema | 1 | public → ecos_control |
| 删除外键约束 | ~10 | 跨 schema FK 需先 DROP 再按需重建 |

---

## 二、数据量实测（迁移决策依据）

### 2.1 非空表清单（仅 ~50 张有数据）

| Schema | 表 | 行数 | 目标 Schema |
|:--|:--|:--|:--|
| public | sys_dict | 49 | ecos_control |
| public | sys_config | 35 | ecos_control |
| public | schema_changes | 191 | ecos_control |
| public | schema_snapshots | 191 | ecos_control |
| public | td_role_permission | 22 | ecos_control |
| public | td_permission | 20 | ecos_control |
| public | ecos_wm_goal | 16 | ecos_cognitive |
| public | ecos_tenant_usage | 13 | ecos_control |
| public | ecos_object_state_machine | 13 | ecos_control |
| public | ecos_pipeline_function | 120 | ecos_data |
| public | ecos_dq.dq_rule | 10 | → ecos_data（与 ecos_dq 合并） |
| public | ecos_object_links | 7 | ecos_control |
| public | ecos_ontology_property | 7 | ecos_ontology |
| public | ecos_agent_registry | 6 | ecos_ai |
| public | ecos_dq_rule | 5 | ecos_data |
| public | ecos_workflow | 5 | ecos_control |
| public | demo_customer | 5 | ecos_dw |
| public | ecos_dq_issue | 4 | ecos_data |
| public | ecos_agent | 4 | ecos_ai |
| public | ecos_tenant | 2 | ecos_control |
| public | ecos_tenant_quota | 2 | ecos_control |
| public | ecos_cron_job | 2 | ecos_ai |
| public | td_role | 5 | ecos_control |
| public | td_abac_policy | 4 | ecos_control |
| public | td_audit_log | 5 | ecos_control |
| public | td_catalog_item | 3 | ecos_data |
| public | td_data_resource | 3 | ecos_data |
| public | demo_invoice | 3 | ecos_dw |
| public | demo_supplier | 3 | ecos_dw |
| public | ecos_skill | 3 | ecos_ai |
| public | ecos_wm_causal_link | 3 | ecos_cognitive |
| public | td_role_security_profile | 3 | ecos_control |
| public | td_user_security_profile | 3 | ecos_control |
| public | ecos_ontology_entity | 3 | ecos_ontology |
| public | ecos_ontology_relationship | 2 | ecos_ontology |
| public | ecos_wm_scenario | 2 | ecos_cognitive |
| public | td_user | 1 | ecos_control |
| public | td_user_role | 1 | ecos_control |
| public | td_organization | 1 | ecos_control |
| public | td_datasource | 1 | ecos_data |
| public | users | 1 | ecos_control |
| ecos_identity | ecos_pipeline_function | 120 | **重复 DROP**（与 public 同数据） |
| ecos_identity | schema_changes | 173 | **重复 DROP**（public 有 191 更全） |
| ecos_identity | schema_snapshots | 173 | **重复 DROP**（public 有 191 更全） |
| ecos_identity | ecos_agent_registry | 9 | **保留迁移**（比 public 6 行多） |
| ecos_identity | ecos_scenario_binding | 8 | ecos_ai |
| ecos_identity | ecos_business_scenario | 3 | ecos_ai |
| ecos_identity | ecos_cron_job | 2 | **重复 DROP**（与 public 同数据） |
| ecos_identity | ecos_skill | 3 | **重复 DROP**（与 public 同数据） |
| ecos_identity | ecos_cognitive_belief | 2 | ecos_cognitive |
| ecos_identity | ecos_cognitive_evidence | 2 | ecos_cognitive |
| ecos_identity | ecos_cognitive_hypothesis | 1 | ecos_cognitive |
| ecos_identity | ecos_cognitive_model | 1 | ecos_cognitive |
| ecos_data | ecos_dq_legacy_migration_report | 5 | 保持不动 |
| ecos_dq | dq_rule | 10 | → ecos_data |
| ecos_agent | agent_registry | 4 | → ecos_ai |

### 2.2 重复表处置裁定（25 对）

| 表名 | public 行数 | ecos_identity 行数 | 处置 |
|:--|:--|:--|:--|
| dict_column | 0 | 0 | DROP ecos_identity 侧 |
| dict_table | 0 | 0 | DROP ecos_identity 侧 |
| ecos_agent_alert | 0 | 0 | DROP ecos_identity 侧 |
| ecos_agent_metrics | 0 | 0 | DROP ecos_identity 侧 |
| ecos_agent_registry | **6** | **9** | DROP public 侧；ecos_identity 侧迁移到 ecos_ai |
| ecos_cron_job | 2 | 2 | DROP ecos_identity 侧（同数据）；public 侧迁移到 ecos_ai |
| ecos_cron_job_execution | 0 | 0 | DROP ecos_identity 侧 |
| ecos_mission | 0 | 0 | DROP ecos_identity 侧 |
| ecos_mission_task | 0 | 0 | DROP ecos_identity 侧 |
| ecos_ontology_proposals | 0 | 0 | DROP ecos_identity 侧 |
| ecos_pipeline_function | **120** | **120** | DROP ecos_identity 侧（同数据）；public 侧迁移到 ecos_data |
| ecos_pipeline_run | 0 | 0 | DROP ecos_identity 侧 |
| ecos_pipeline_step | 0 | 0 | DROP ecos_identity 侧 |
| ecos_pipeline_step_run | 0 | 0 | DROP ecos_identity 侧 |
| ecos_pipeline_task | 0 | 0 | DROP ecos_identity 侧 |
| ecos_pipeline_udf | 0 | 0 | DROP ecos_identity 侧 |
| ecos_quality_evaluation | 0 | 0 | DROP ecos_identity 侧 |
| ecos_quality_rule | 0 | 0 | DROP ecos_identity 侧 |
| ecos_skill | 3 | 3 | DROP ecos_identity 侧（同数据）；public 侧迁移到 ecos_ai |
| ecos_spans | 0 | 0 | DROP ecos_identity 侧 |
| ecos_token_usage | 0 | 0 | DROP ecos_identity 侧 |
| ecos_workflow_log | 0 | 0 | DROP ecos_identity 侧 |
| schema_changes | **191** | **173** | DROP ecos_identity 侧（public 更全）；public 侧迁移到 ecos_control |
| schema_snapshots | **191** | **173** | DROP ecos_identity 侧（public 更全）；public 侧迁移到 ecos_control |
| sys_token_blacklist | 0 | 0 | DROP ecos_identity 侧 |

**汇总**：DROP ecos_identity 侧 25 张，其中 4 张需先保留 public 侧数据迁移到目标 schema。

---

## 三、迁移批次（可执行 SQL）

### Phase 0：预检 + 备份

```sql
-- ============================================================
-- Phase 0: 预检 + 备份
-- ============================================================

-- 0.1 确认当前连接
SELECT current_database(), current_user, current_schema;
-- 预期: sys_man / postgres / public

-- 0.2 快照当前表数
SELECT schemaname, count(*) FROM pg_tables
WHERE schemaname NOT IN ('pg_catalog', 'information_schema')
GROUP BY schemaname ORDER BY schemaname;

-- 0.3 全库备份（建议 pg_dump）
-- pg_dump -U postgres -Fc -f ecos_backup_pre_governance.dump sys_man
```

### Phase 1：创建目标 schema

```sql
-- ============================================================
-- Phase 1: 创建 5 个新 schema
-- ============================================================
CREATE SCHEMA IF NOT EXISTS ecos_ai;
CREATE SCHEMA IF NOT EXISTS ecos_cognitive;
CREATE SCHEMA IF NOT EXISTS ecos_control;
CREATE SCHEMA IF NOT EXISTS ecos_infra;
CREATE SCHEMA IF NOT EXISTS ecos_dw;

-- 授权
DO $$
DECLARE s text;
BEGIN
    FOREACH s IN ARRAY ARRAY['ecos_ai','ecos_cognitive','ecos_control','ecos_infra','ecos_dw']
    LOOP
        EXECUTE format('GRANT ALL ON SCHEMA %I TO postgres', s);
        EXECUTE format('GRANT USAGE ON SCHEMA %I TO CURRENT_USER', s);
    END LOOP;
END $$;
```

### Phase 2：删除跨 schema 外键约束

```sql
-- ============================================================
-- Phase 2: 删除跨 schema 外键（迁移后需重建）
-- ============================================================

-- ecos_identity → public 的 FK
ALTER TABLE ecos_identity.dict_column DROP CONSTRAINT IF EXISTS dict_column_table_id_fkey;
ALTER TABLE ecos_identity.ecos_mission_task DROP CONSTRAINT IF EXISTS ecos_mission_task_mission_id_fkey;

-- public → ecos_identity 的 FK
ALTER TABLE public.dict_column DROP CONSTRAINT IF EXISTS dict_column_table_id_fkey;
ALTER TABLE public.ecos_mission_task DROP CONSTRAINT IF EXISTS ecos_mission_task_mission_id_fkey;

-- ecos_agent 内部 FK（随表整体迁移到 ecos_ai，无需 DROP）
-- 注：ALTER TABLE SET SCHEMA 保留同 schema 内 FK
```

### Phase 3：删除重复空表（ecos_identity 侧）

```sql
-- ============================================================
-- Phase 3: 删除 ecos_identity 侧重复空表（21 张零数据表）
-- ============================================================
DROP TABLE IF EXISTS ecos_identity.dict_column;
DROP TABLE IF EXISTS ecos_identity.dict_table;
DROP TABLE IF EXISTS ecos_identity.ecos_agent_alert;
DROP TABLE IF EXISTS ecos_identity.ecos_agent_metrics;
DROP TABLE IF EXISTS ecos_identity.ecos_cron_job_execution;
DROP TABLE IF EXISTS ecos_identity.ecos_mission;
DROP TABLE IF EXISTS ecos_identity.ecos_mission_task;
DROP TABLE IF EXISTS ecos_identity.ecos_ontology_proposals;
DROP TABLE IF EXISTS ecos_identity.ecos_pipeline_run;
DROP TABLE IF EXISTS ecos_identity.ecos_pipeline_step;
DROP TABLE IF EXISTS ecos_identity.ecos_pipeline_step_run;
DROP TABLE IF EXISTS ecos_identity.ecos_pipeline_task;
DROP TABLE IF EXISTS ecos_identity.ecos_pipeline_udf;
DROP TABLE IF EXISTS ecos_identity.ecos_quality_evaluation;
DROP TABLE IF EXISTS ecos_identity.ecos_quality_rule;
DROP TABLE IF EXISTS ecos_identity.ecos_spans;
DROP TABLE IF EXISTS ecos_identity.ecos_token_usage;
DROP TABLE IF EXISTS ecos_identity.ecos_workflow_log;
DROP TABLE IF EXISTS ecos_identity.sys_token_blacklist;

-- 有数据但 public 侧更全/相同的重复表（DROP ecos_identity 侧）
DROP TABLE IF EXISTS ecos_identity.ecos_pipeline_function;  -- 120=120 同数据
DROP TABLE IF EXISTS ecos_identity.ecos_cron_job;           -- 2=2 同数据
DROP TABLE IF EXISTS ecos_identity.ecos_skill;              -- 3=3 同数据
DROP TABLE IF EXISTS ecos_identity.schema_changes;          -- 173 < public 191
DROP TABLE IF EXISTS ecos_identity.schema_snapshots;        -- 173 < public 191

-- public 侧 ecos_agent_registry（6 行）DROP，保留 ecos_identity 侧（9 行更多）
DROP TABLE IF EXISTS public.ecos_agent_registry;
```

### Phase 4：合并 outbox_event → ecos_infra

```sql
-- ============================================================
-- Phase 4: outbox_event 合并到 ecos_infra（全部 0 行）
-- ============================================================

-- 4.1 在 ecos_infra 建统一 outbox_event
CREATE TABLE IF NOT EXISTS ecos_infra.outbox_event (
    id              VARCHAR(36) PRIMARY KEY,
    source_schema   VARCHAR(100) NOT NULL,
    aggregate_type  VARCHAR(100) NOT NULL,
    aggregate_id    VARCHAR(36) NOT NULL,
    event_type      VARCHAR(100) NOT NULL,
    payload_json    TEXT,
    is_published    SMALLINT NOT NULL DEFAULT 0,
    create_time     TIMESTAMP NOT NULL DEFAULT NOW(),
    update_time     TIMESTAMP NOT NULL DEFAULT NOW(),
    create_by       VARCHAR(100),
    update_by       VARCHAR(100),
    version_no      VARCHAR(20) NOT NULL,
    is_deleted      SMALLINT NOT NULL DEFAULT 0,
    domain          VARCHAR(50) NOT NULL DEFAULT 'default'
);
CREATE INDEX IF NOT EXISTS idx_outbox_unpublished ON ecos_infra.outbox_event(is_published) WHERE is_published = 0;

-- 4.2 DROP 11 个旧 outbox_event（全部 0 行）
DROP TABLE IF EXISTS ecos_agent.outbox_event;
DROP TABLE IF EXISTS ecos_identity.outbox_event;
DROP TABLE IF EXISTS ecos_knowledge.outbox_event;
DROP TABLE IF EXISTS ecos_ontology.outbox_event;
DROP TABLE IF EXISTS ecos_catalog.outbox_event;
DROP TABLE IF EXISTS ecos_pipeline.outbox_event;
DROP TABLE IF EXISTS ecos_object.outbox_event;
DROP TABLE IF EXISTS ecos_audit.outbox_event;
DROP TABLE IF EXISTS ecos_mission.outbox_event;
DROP TABLE IF EXISTS ecos_workflow.outbox_event;
DROP TABLE IF EXISTS ecos_rule.outbox_event;
```

### Phase 5：迁移表到 ecos_ai（火W）

```sql
-- ============================================================
-- Phase 5: 迁移表到 ecos_ai
-- ============================================================

-- 5.1 从 public 迁入
ALTER TABLE public.ecos_agent SET SCHEMA ecos_ai;
ALTER TABLE public.ecos_agent_alert SET SCHEMA ecos_ai;
ALTER TABLE public.ecos_agent_metrics SET SCHEMA ecos_ai;
ALTER TABLE public.ecos_agent_registry SET SCHEMA ecos_ai;  -- 注意：此表来自 ecos_identity（9 行）
ALTER TABLE public.ecos_cron_job SET SCHEMA ecos_ai;
ALTER TABLE public.ecos_cron_job_execution SET SCHEMA ecos_ai;
ALTER TABLE public.ecos_mission SET SCHEMA ecos_ai;
ALTER TABLE public.ecos_mission_task SET SCHEMA ecos_ai;
ALTER TABLE public.ecos_skill SET SCHEMA ecos_ai;
ALTER TABLE public.ecos_token_usage SET SCHEMA ecos_ai;
ALTER TABLE public.sys_agent_message SET SCHEMA ecos_ai;
ALTER TABLE public.sys_agent_session SET SCHEMA ecos_ai;

-- 5.2 从 ecos_identity 迁入（非重复表）
ALTER TABLE ecos_identity.ecos_business_scenario SET SCHEMA ecos_ai;
ALTER TABLE ecos_identity.ecos_scenario_binding SET SCHEMA ecos_ai;
ALTER TABLE ecos_identity.ecos_scenario_run SET SCHEMA ecos_ai;
ALTER TABLE ecos_identity.sys_agent_message SET SCHEMA ecos_ai;
ALTER TABLE ecos_identity.sys_agent_session SET SCHEMA ecos_ai;

-- 5.3 从 ecos_agent 整体迁入（19 表）
ALTER TABLE ecos_agent.agent_approval SET SCHEMA ecos_ai;
ALTER TABLE ecos_agent.agent_cost SET SCHEMA ecos_ai;
ALTER TABLE ecos_agent.agent_definition SET SCHEMA ecos_ai;
ALTER TABLE ecos_agent.agent_evaluation SET SCHEMA ecos_ai;
ALTER TABLE ecos_agent.agent_execution SET SCHEMA ecos_ai;
ALTER TABLE ecos_agent.agent_execution_step SET SCHEMA ecos_ai;
ALTER TABLE ecos_agent.agent_governance_policy SET SCHEMA ecos_ai;
ALTER TABLE ecos_agent.agent_memory SET SCHEMA ecos_ai;
ALTER TABLE ecos_agent.agent_registry SET SCHEMA ecos_ai;
ALTER TABLE ecos_agent.causal_edge SET SCHEMA ecos_ai;
ALTER TABLE ecos_agent.forecast SET SCHEMA ecos_ai;
ALTER TABLE ecos_agent.optimization_job SET SCHEMA ecos_ai;
ALTER TABLE ecos_agent.scenario SET SCHEMA ecos_ai;
ALTER TABLE ecos_agent.simulation SET SCHEMA ecos_ai;
ALTER TABLE ecos_agent.simulation_result SET SCHEMA ecos_ai;
ALTER TABLE ecos_agent.strategy_recommendation SET SCHEMA ecos_ai;
ALTER TABLE ecos_agent.world_snapshot SET SCHEMA ecos_ai;
ALTER TABLE ecos_agent.world_state SET SCHEMA ecos_ai;
-- ecos_agent.outbox_event 已在 Phase 4 DROP

-- 5.4 重建 ecos_agent 内部 FK（现在在 ecos_ai 内）
-- 注：ALTER TABLE SET SCHEMA 保留同 schema 内 FK，无需手动重建
-- 验证：
-- SELECT conname FROM pg_constraint WHERE conrelid = 'ecos_ai.agent_execution'::regclass AND contype = 'f';
```

### Phase 6：迁移表到 ecos_cognitive（木C）

```sql
-- ============================================================
-- Phase 6: 迁移表到 ecos_cognitive
-- ============================================================

-- 6.1 从 public 迁入
ALTER TABLE public.ecos_cognitive_rule SET SCHEMA ecos_cognitive;
ALTER TABLE public.ecos_decision_source_ref SET SCHEMA ecos_cognitive;
ALTER TABLE public.ecos_goal_tracking SET SCHEMA ecos_cognitive;
ALTER TABLE public.ecos_wm_causal_link SET SCHEMA ecos_cognitive;
ALTER TABLE public.ecos_wm_goal SET SCHEMA ecos_cognitive;
ALTER TABLE public.ecos_wm_goal_log SET SCHEMA ecos_cognitive;
ALTER TABLE public.ecos_wm_scenario SET SCHEMA ecos_cognitive;
ALTER TABLE public.ecos_world_scenarios SET SCHEMA ecos_cognitive;

-- 6.2 从 ecos_identity 迁入
ALTER TABLE ecos_identity.ecos_cognitive_belief SET SCHEMA ecos_cognitive;
ALTER TABLE ecos_identity.ecos_cognitive_evidence SET SCHEMA ecos_cognitive;
ALTER TABLE ecos_identity.ecos_cognitive_hypothesis SET SCHEMA ecos_cognitive;
ALTER TABLE ecos_identity.ecos_cognitive_model SET SCHEMA ecos_cognitive;
ALTER TABLE ecos_identity.ecos_cognitive_run_invalidation SET SCHEMA ecos_cognitive;
ALTER TABLE ecos_identity.ecos_decision SET SCHEMA ecos_cognitive;
ALTER TABLE ecos_identity.ecos_decision_approval SET SCHEMA ecos_cognitive;
ALTER TABLE ecos_identity.ecos_decision_causal_link SET SCHEMA ecos_cognitive;
ALTER TABLE ecos_identity.ecos_decision_exception SET SCHEMA ecos_cognitive;
ALTER TABLE ecos_identity.ecos_decision_policy SET SCHEMA ecos_cognitive;
ALTER TABLE ecos_identity.ecos_decision_precedent SET SCHEMA ecos_cognitive;
ALTER TABLE ecos_identity.ecos_provenance_entry SET SCHEMA ecos_cognitive;
```

### Phase 7：迁移表到 ecos_data（土D）

```sql
-- ============================================================
-- Phase 7: 迁移表到 ecos_data
-- ============================================================

-- 7.1 从 public 迁入
ALTER TABLE public.ecos_dq_execution_result SET SCHEMA ecos_data;
ALTER TABLE public.ecos_dq_issue SET SCHEMA ecos_data;
ALTER TABLE public.ecos_dq_rule SET SCHEMA ecos_data;
ALTER TABLE public.ecos_pipeline_edge SET SCHEMA ecos_data;
ALTER TABLE public.ecos_pipeline_function SET SCHEMA ecos_data;
ALTER TABLE public.ecos_pipeline_node SET SCHEMA ecos_data;
ALTER TABLE public.ecos_pipeline_run SET SCHEMA ecos_data;
ALTER TABLE public.ecos_pipeline_step SET SCHEMA ecos_data;
ALTER TABLE public.ecos_pipeline_step_run SET SCHEMA ecos_data;
ALTER TABLE public.ecos_pipeline_task SET SCHEMA ecos_data;
ALTER TABLE public.ecos_pipeline_udf SET SCHEMA ecos_data;
ALTER TABLE public.ecos_quality_evaluation SET SCHEMA ecos_data;
ALTER TABLE public.ecos_quality_rule SET SCHEMA ecos_data;
ALTER TABLE public.ecos_task SET SCHEMA ecos_data;
ALTER TABLE public.td_catalog_item SET SCHEMA ecos_data;
ALTER TABLE public.td_data_field SET SCHEMA ecos_data;
ALTER TABLE public.td_data_resource SET SCHEMA ecos_data;
ALTER TABLE public.td_datasource SET SCHEMA ecos_data;

-- 7.2 从 ecos_dq 整体迁入（11 表）
ALTER TABLE ecos_dq.dq_alert_record SET SCHEMA ecos_data;
ALTER TABLE ecos_dq.dq_knowledge_entry SET SCHEMA ecos_data;
ALTER TABLE ecos_dq.dq_report SET SCHEMA ecos_data;
ALTER TABLE ecos_dq.dq_rule SET SCHEMA ecos_data;
ALTER TABLE ecos_dq.dq_rule_check SET SCHEMA ecos_data;
ALTER TABLE ecos_dq.dq_rule_version SET SCHEMA ecos_data;
ALTER TABLE ecos_dq.dq_schedule SET SCHEMA ecos_data;
ALTER TABLE ecos_dq.dq_score_asset SET SCHEMA ecos_data;
ALTER TABLE ecos_dq.dq_score_snapshot SET SCHEMA ecos_data;
ALTER TABLE ecos_dq.dq_throttle SET SCHEMA ecos_data;
ALTER TABLE ecos_dq.dq_work_order SET SCHEMA ecos_data;

-- 7.3 从 ecos_identity 迁入
ALTER TABLE ecos_identity.ecos_pipeline_function SET SCHEMA ecos_data;  -- 注意：此表已在 Phase 3 DROP
-- 实际应从 public 迁入（已在 7.1 处理）
ALTER TABLE ecos_identity.kb_lineage_event SET SCHEMA ecos_data;
ALTER TABLE ecos_identity.td_metadata_collect_log SET SCHEMA ecos_data;
```

### Phase 8：迁移表到 ecos_ontology（金I）

```sql
-- ============================================================
-- Phase 8: 迁移表到 ecos_ontology
-- ============================================================

-- 8.1 从 public 迁入
ALTER TABLE public.ecos_glossary_term SET SCHEMA ecos_ontology;
ALTER TABLE public.ecos_marketplace_access_request SET SCHEMA ecos_ontology;
ALTER TABLE public.ecos_marketplace_asset SET SCHEMA ecos_ontology;
ALTER TABLE public.ecos_ontology_data SET SCHEMA ecos_ontology;
ALTER TABLE public.ecos_ontology_domain SET SCHEMA ecos_ontology;
ALTER TABLE public.ecos_ontology_entity SET SCHEMA ecos_ontology;
ALTER TABLE public.ecos_ontology_property SET SCHEMA ecos_ontology;
ALTER TABLE public.ecos_ontology_proposals SET SCHEMA ecos_ontology;
ALTER TABLE public.ecos_ontology_relationship SET SCHEMA ecos_ontology;
ALTER TABLE public.ecos_ontology_rule SET SCHEMA ecos_ontology;
ALTER TABLE public.ecos_ontology_version SET SCHEMA ecos_ontology;
ALTER TABLE public.ecos_ontology_workbench_object SET SCHEMA ecos_ontology;

-- 8.2 从 ecos_identity 迁入
ALTER TABLE ecos_identity.ecos_knowledge_graph_node SET SCHEMA ecos_ontology;
ALTER TABLE ecos_identity.kb_ontology_snapshot SET SCHEMA ecos_ontology;
```

### Phase 9：迁移表到 ecos_knowledge（水K）

```sql
-- ============================================================
-- Phase 9: 迁移表到 ecos_knowledge
-- ============================================================

-- 从 public 迁入
ALTER TABLE public.extraction_drafts SET SCHEMA ecos_knowledge;

-- 从 ecos_identity 迁入
ALTER TABLE ecos_identity.kb_cognitive_pipeline SET SCHEMA ecos_knowledge;
```

### Phase 10：迁移表到 ecos_dw（业务域）

```sql
-- ============================================================
-- Phase 10: 迁移演示数据到 ecos_dw（业务域）
-- ============================================================
ALTER TABLE public.demo_customer SET SCHEMA ecos_dw;
ALTER TABLE public.demo_invoice SET SCHEMA ecos_dw;
ALTER TABLE public.demo_supplier SET SCHEMA ecos_dw;
```

### Phase 11：迁移表到 ecos_infra（横切基础设施）

```sql
-- ============================================================
-- Phase 11: 迁移横切表到 ecos_infra
-- ============================================================

-- 从 public 迁入
ALTER TABLE public.ecos_spans SET SCHEMA ecos_infra;
ALTER TABLE public.ecos_function_audit_log SET SCHEMA ecos_infra;

-- 从 ecos_identity 迁入
ALTER TABLE ecos_identity.schema_changes SET SCHEMA ecos_infra;  -- 已在 Phase 3 DROP，此表从 public 迁
-- 注：ecos_identity.schema_changes 已 DROP；public.schema_changes 在此迁入
ALTER TABLE ecos_identity.td_metadata_collect_log SET SCHEMA ecos_infra;  -- 已在 Phase 7 迁入 ecos_data

-- saga_instance 从 ecos_config 迁入
ALTER TABLE ecos_config.saga_instance SET SCHEMA ecos_infra;
```

### Phase 12：剩余表迁入 ecos_control（主控制 schema）

```sql
-- ============================================================
-- Phase 12: 剩余 public 表迁入 ecos_control
-- ============================================================

-- sysman 域
ALTER TABLE public.sys_config SET SCHEMA ecos_control;
ALTER TABLE public.sys_dict SET SCHEMA ecos_control;
ALTER TABLE public.td_config SET SCHEMA ecos_control;
ALTER TABLE public.td_org_permission SET SCHEMA ecos_control;
ALTER TABLE public.td_organization SET SCHEMA ecos_control;
ALTER TABLE public.td_permission SET SCHEMA ecos_control;
ALTER TABLE public.td_role SET SCHEMA ecos_control;
ALTER TABLE public.td_role_permission SET SCHEMA ecos_control;
ALTER TABLE public.td_role_security_profile SET SCHEMA ecos_control;
ALTER TABLE public.td_user SET SCHEMA ecos_control;
ALTER TABLE public.td_user_organization SET SCHEMA ecos_control;
ALTER TABLE public.td_user_role SET SCHEMA ecos_control;
ALTER TABLE public.td_user_security_profile SET SCHEMA ecos_control;
ALTER TABLE public.ecos_tenant SET SCHEMA ecos_control;
ALTER TABLE public.ecos_tenant_quota SET SCHEMA ecos_control;
ALTER TABLE public.ecos_tenant_usage SET SCHEMA ecos_control;
ALTER TABLE public.users SET SCHEMA ecos_control;
ALTER TABLE public.dict_column SET SCHEMA ecos_control;
ALTER TABLE public.dict_table SET SCHEMA ecos_control;

-- security 域
ALTER TABLE public.ecos_cls_policy SET SCHEMA ecos_control;
ALTER TABLE public.ecos_rls_policy SET SCHEMA ecos_control;
ALTER TABLE public.ecos_security_asset SET SCHEMA ecos_control;
ALTER TABLE public.ecos_security_crypto_key SET SCHEMA ecos_control;
ALTER TABLE public.sys_token_blacklist SET SCHEMA ecos_control;
ALTER TABLE public.td_abac_policy SET SCHEMA ecos_control;
ALTER TABLE public.td_audit_log SET SCHEMA ecos_control;

-- runtime 域
ALTER TABLE public.ecos_object_attachment SET SCHEMA ecos_control;
ALTER TABLE public.ecos_object_links SET SCHEMA ecos_control;
ALTER TABLE public.ecos_object_relationship SET SCHEMA ecos_control;
ALTER TABLE public.ecos_object_state_machine SET SCHEMA ecos_control;
ALTER TABLE public.ecos_object_timeline SET SCHEMA ecos_control;
ALTER TABLE public.ecos_object_version SET SCHEMA ecos_control;

-- workspace 域
ALTER TABLE public.ecos_scenario_active_mind SET SCHEMA ecos_control;
ALTER TABLE public.ecos_scenario_asset_binding SET SCHEMA ecos_control;
ALTER TABLE public.ecos_scenario_binding_edge SET SCHEMA ecos_control;
ALTER TABLE public.ecos_scenario_canvas_layout SET SCHEMA ecos_control;
ALTER TABLE public.ecos_scenario_decision_record SET SCHEMA ecos_control;
ALTER TABLE public.ecos_scenario_definition SET SCHEMA ecos_control;
ALTER TABLE public.ecos_scenario_execution SET SCHEMA ecos_control;
ALTER TABLE public.ecos_scenario_mind_ref SET SCHEMA ecos_control;
ALTER TABLE public.ecos_scenario_mind_variant SET SCHEMA ecos_control;
ALTER TABLE public.ecos_scenario_query_history SET SCHEMA ecos_control;
ALTER TABLE public.ecos_fc_action_ext SET SCHEMA ecos_control;

-- workflow 域
ALTER TABLE public.ecos_workflow SET SCHEMA ecos_control;
ALTER TABLE public.ecos_workflow_approval SET SCHEMA ecos_control;
ALTER TABLE public.ecos_workflow_instance SET SCHEMA ecos_control;
ALTER TABLE public.ecos_workflow_log SET SCHEMA ecos_control;
ALTER TABLE public.ecos_workflow_task SET SCHEMA ecos_control;

-- 运维/元数据
ALTER TABLE public.schema_changes SET SCHEMA ecos_control;
ALTER TABLE public.schema_snapshots SET SCHEMA ecos_control;

-- 剩余 ecos_identity 表迁入 ecos_control
ALTER TABLE ecos_identity.ecos_agent_alert SET SCHEMA ecos_control;      -- 已在 Phase 3 DROP
-- 注：以下表在 Phase 3 已 DROP 的不再迁移
-- 剩余非 DROP 的 ecos_identity 表：
-- （大部分已在 Phase 5~11 迁走，此处处理 sysman/security/runtime 相关）
```

### Phase 13：清理空 schema

```sql
-- ============================================================
-- Phase 13: 清理空 schema
-- ============================================================

-- 13.1 验证 schema 已空
DO $$
DECLARE s text; cnt int;
BEGIN
    FOREACH s IN ARRAY ARRAY['ecos_identity','ecos_agent','ecos_dq',
        'ecos_catalog','ecos_pipeline','ecos_config','ecos_object',
        'ecos_audit','ecos_mission','ecos_workflow','ecos_rule']
    LOOP
        EXECUTE format('SELECT count(*) FROM pg_tables WHERE schemaname = %L', s) INTO cnt;
        RAISE NOTICE '% has % tables', s, cnt;
    END LOOP;
END $$;

-- 13.2 DROP 空 schema
DROP SCHEMA IF EXISTS ecos_identity CASCADE;
DROP SCHEMA IF EXISTS ecos_agent CASCADE;
DROP SCHEMA IF EXISTS ecos_dq CASCADE;
DROP SCHEMA IF EXISTS ecos_catalog CASCADE;
DROP SCHEMA IF EXISTS ecos_pipeline CASCADE;
DROP SCHEMA IF EXISTS ecos_config CASCADE;
DROP SCHEMA IF EXISTS ecos_object CASCADE;
DROP SCHEMA IF EXISTS ecos_audit CASCADE;
DROP SCHEMA IF EXISTS ecos_mission CASCADE;
DROP SCHEMA IF EXISTS ecos_workflow CASCADE;
DROP SCHEMA IF EXISTS ecos_rule CASCADE;
```

### Phase 14：重命名 public → ecos_control

```sql
-- ============================================================
-- Phase 14: 重命名 public → ecos_control
-- ============================================================

-- 14.1 确认 public 已空（所有表已迁走）
SELECT count(*) FROM pg_tables WHERE schemaname = 'public';
-- 预期: 0

-- 14.2 重命名
ALTER SCHEMA public RENAME TO ecos_control_old;
-- 或直接 DROP（因已空）:
DROP SCHEMA IF EXISTS public CASCADE;
-- 注：PG 默认会在新连接时重建空 public（由 createSchema=true 参数控制）
-- 建议在 JDBC URL 加 &createSchema=false 阻止自动重建

-- 14.3 如果选择 RENAME 而非 DROP:
-- ALTER SCHEMA ecos_control_old RENAME TO public;  -- 回退用
```

**注意**：Phase 12 已将 public 所有表迁到新建的 `ecos_control` schema。因此 `public` 此时已空，直接 DROP 即可。不需要 RENAME。

### Phase 15：重建跨 schema 外键

```sql
-- ============================================================
-- Phase 15: 重建必要的外键约束
-- ============================================================

-- dict_column → dict_table（现在都在 ecos_control）
ALTER TABLE ecos_control.dict_column
    ADD CONSTRAINT dict_column_table_id_fkey
    FOREIGN KEY (table_id) REFERENCES ecos_control.dict_table(id);

-- ecos_mission_task → ecos_mission（现在都在 ecos_ai）
-- 注意：需确认两表都有 id 列
-- ALTER TABLE ecos_ai.ecos_mission_task
--     ADD CONSTRAINT ecos_mission_task_mission_id_fkey
--     FOREIGN KEY (mission_id) REFERENCES ecos_ai.ecos_mission(id);

-- td_org_permission → td_organization（都在 ecos_control）
ALTER TABLE ecos_control.td_org_permission
    ADD CONSTRAINT td_org_permission_org_id_fkey
    FOREIGN KEY (org_id) REFERENCES ecos_control.td_organization(id);

-- td_role_permission → td_role / td_permission（都在 ecos_control）
ALTER TABLE ecos_control.td_role_permission
    ADD CONSTRAINT td_role_permission_role_id_fkey
    FOREIGN KEY (role_id) REFERENCES ecos_control.td_role(id);
ALTER TABLE ecos_control.td_role_permission
    ADD CONSTRAINT td_role_permission_permission_id_fkey
    FOREIGN KEY (permission_id) REFERENCES ecos_control.td_permission(id);

-- td_user_organization → td_user / td_organization（都在 ecos_control）
ALTER TABLE ecos_control.td_user_organization
    ADD CONSTRAINT td_user_organization_user_id_fkey
    FOREIGN KEY (user_id) REFERENCES ecos_control.td_user(id);
ALTER TABLE ecos_control.td_user_organization
    ADD CONSTRAINT td_user_organization_org_id_fkey
    FOREIGN KEY (org_id) REFERENCES ecos_control.td_organization(id);

-- td_user_role → td_user / td_role（都在 ecos_control）
ALTER TABLE ecos_control.td_user_role
    ADD CONSTRAINT td_user_role_user_id_fkey
    FOREIGN KEY (user_id) REFERENCES ecos_control.td_user(id);
ALTER TABLE ecos_control.td_user_role
    ADD CONSTRAINT td_user_role_role_id_fkey
    FOREIGN KEY (role_id) REFERENCES ecos_control.td_role(id);

-- 认知域内部 FK（都在 ecos_cognitive）
ALTER TABLE ecos_cognitive.ecos_goal_tracking
    ADD CONSTRAINT ecos_goal_tracking_goal_id_fkey
    FOREIGN KEY (goal_id) REFERENCES ecos_cognitive.ecos_wm_goal(id);
ALTER TABLE ecos_cognitive.ecos_wm_causal_link
    ADD CONSTRAINT ecos_wm_causal_link_source_goal_id_fkey
    FOREIGN KEY (source_goal_id) REFERENCES ecos_cognitive.ecos_wm_goal(id);
ALTER TABLE ecos_cognitive.ecos_wm_causal_link
    ADD CONSTRAINT ecos_wm_causal_link_target_goal_id_fkey
    FOREIGN KEY (target_goal_id) REFERENCES ecos_cognitive.ecos_wm_goal(id);
ALTER TABLE ecos_cognitive.ecos_wm_goal
    ADD CONSTRAINT ecos_wm_goal_parent_id_fkey
    FOREIGN KEY (parent_id) REFERENCES ecos_cognitive.ecos_wm_goal(id);
ALTER TABLE ecos_cognitive.ecos_wm_goal_log
    ADD CONSTRAINT ecos_wm_goal_log_goal_id_fkey
    FOREIGN KEY (goal_id) REFERENCES ecos_cognitive.ecos_wm_goal(id);

-- 本体域内部 FK
ALTER TABLE ecos_ontology.ecos_marketplace_access_request
    ADD CONSTRAINT ecos_marketplace_access_request_asset_id_fkey
    FOREIGN KEY (asset_id) REFERENCES ecos_ontology.ecos_marketplace_asset(id);

-- DQ 内部 FK（都在 ecos_data）
ALTER TABLE ecos_data.ecos_dq_issue
    ADD CONSTRAINT fk_dq_issue_rule
    FOREIGN KEY (rule_id) REFERENCES ecos_data.ecos_dq_rule(id);
```

### Phase 16：验证

```sql
-- ============================================================
-- Phase 16: 最终验证
-- ============================================================

-- 16.1 Schema 清单（预期 8 个）
SELECT schema_name FROM information_schema.schemata
WHERE schema_name NOT IN ('pg_catalog', 'information_schema', 'pg_toast')
ORDER BY schema_name;
-- 预期: ecos_ai / ecos_cognitive / ecos_control / ecos_data / ecos_dw / ecos_infra / ecos_knowledge / ecos_ontology

-- 16.2 各 schema 表数
SELECT schemaname, count(*) FROM pg_tables
WHERE schemaname NOT IN ('pg_catalog', 'information_schema')
GROUP BY schemaname ORDER BY schemaname;

-- 16.3 总表数（预期 ~200，因删了重复表）
SELECT count(*) FROM pg_tables
WHERE schemaname NOT IN ('pg_catalog', 'information_schema');

-- 16.4 确认无空壳 schema
SELECT s.schema_name, count(t.tablename) AS tables
FROM information_schema.schemata s
LEFT JOIN pg_tables t ON s.schema_name = t.schemaname
WHERE s.schema_name NOT IN ('pg_catalog', 'information_schema', 'pg_toast')
GROUP BY s.schema_name
HAVING count(t.tablename) = 0;
-- 预期: 0 rows

-- 16.5 确认无跨域 FK 异常
SELECT tc.constraint_name, tc.table_schema, tc.table_name,
       ccu.table_schema AS ref_schema, ccu.table_name AS ref_table
FROM information_schema.table_constraints tc
JOIN information_schema.constraint_column_usage ccu
    ON tc.constraint_name = ccu.constraint_name
WHERE tc.constraint_type = 'FOREIGN KEY'
    AND tc.table_schema NOT IN ('pg_catalog', 'information_schema')
ORDER BY tc.table_schema;
```

---

## 四、代码侧配套改造

### 4.1 JDBC URL currentSchema 更新

```yaml
# 各 service application.yml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/sys_man?currentSchema=ecos_control  # 原 public
    # 各引擎 service 追加自己的 schema:
    # data-engine: currentSchema=ecos_data
    # ontology-engine: currentSchema=ecos_ontology
    # kb-engine: currentSchema=ecos_knowledge
    # ai-engine: currentSchema=ecos_ai
    # cognitive-engine: currentSchema=ecos_cognitive
```

### 4.2 MyBatis Mapper 限定名替换

全局替换（sed/脚本）：

| 原限定名 | 新限定名 |
|:--|:--|
| `public.sys_config` | `ecos_control.sys_config` |
| `public.sys_dict` | `ecos_control.sys_dict` |
| `public.td_*` | `ecos_control.td_*` |
| `public.ecos_agent` | `ecos_ai.ecos_agent` |
| `public.ecos_skill` | `ecos_ai.ecos_skill` |
| `public.ecos_mission*` | `ecos_ai.ecos_mission*` |
| `public.ecos_cron_job*` | `ecos_ai.ecos_cron_job*` |
| `public.ecos_cognitive_*` | `ecos_cognitive.ecos_cognitive_*` |
| `public.ecos_wm_*` | `ecos_cognitive.ecos_wm_*` |
| `public.ecos_dq_*` | `ecos_data.ecos_dq_*` |
| `public.ecos_pipeline_*` | `ecos_data.ecos_pipeline_*` |
| `public.td_data_*` | `ecos_data.td_data_*` |
| `public.td_datasource` | `ecos_data.td_datasource` |
| `public.td_catalog_item` | `ecos_data.td_catalog_item` |
| `public.ecos_ontology_*` | `ecos_ontology.ecos_ontology_*` |
| `public.ecos_workflow*` | `ecos_control.ecos_workflow*` |
| `public.ecos_scenario_*` | `ecos_control.ecos_scenario_*` |
| `public.ecos_object_*` | `ecos_control.ecos_object_*` |
| `public.ecos_security_*` | `ecos_control.ecos_security_*` |
| `public.ecos_rls_policy` | `ecos_control.ecos_rls_policy` |
| `public.ecos_cls_policy` | `ecos_control.ecos_cls_policy` |
| `public.demo_*` | `ecos_dw.demo_*` |
| `public.schema_*` | `ecos_control.schema_*` |
| `public.dict_*` | `ecos_control.dict_*` |
| `public.users` | `ecos_control.users` |
| `public.extraction_drafts` | `ecos_knowledge.extraction_drafts` |
| `public.ecos_glossary_term` | `ecos_ontology.ecos_glossary_term` |
| `public.ecos_marketplace_*` | `ecos_ontology.ecos_marketplace_*` |
| `public.ecos_token_usage` | `ecos_ai.ecos_token_usage` |
| `public.ecos_spans` | `ecos_infra.ecos_spans` |
| `public.ecos_function_audit_log` | `ecos_infra.ecos_function_audit_log` |
| `public.ecos_goal_tracking` | `ecos_cognitive.ecos_goal_tracking` |
| `public.ecos_world_scenarios` | `ecos_cognitive.ecos_world_scenarios` |
| `public.ecos_decision_*` | `ecos_cognitive.ecos_decision_*` |
| `public.ecos_task` | `ecos_data.ecos_task` |
| `public.ecos_quality_*` | `ecos_data.ecos_quality_*` |
| `public.ecos_fc_action_ext` | `ecos_control.ecos_fc_action_ext` |

### 4.3 批量替换脚本（参考）

```bash
# 在 ecos_backend/ 目录下执行
# 先 dry-run 看影响范围
grep -rl 'public\.\(sys_\|td_\|ecos_agent\|ecos_skill\|ecos_mission\|ecos_cron\|ecos_cognitive\|ecos_wm_\|ecos_dq\|ecos_pipeline\|ecos_ontology\|ecos_workflow\|ecos_scenario\|ecos_object\|ecos_security\|ecos_rls\|ecos_cls\|demo_\|schema_\|dict_\|users\|extraction_\|ecos_glossary\|ecos_marketplace\|ecos_token\|ecos_spans\|ecos_function_audit\|ecos_goal\|ecos_world\|ecos_decision\|ecos_task\|ecos_quality\|ecos_fc\)' \
    --include='*.xml' --include='*.java' --include='*.yml' \
    ecos_backend/ | wc -l
```

---

## 五、治理前后对比

| 维度 | 治理前 | 治理后 |
|:--|:--|:--|
| Schema 数 | 15（含 7 空壳） | **8**（全部活跃） |
| public 表数 | 104 | **0**（public 已 DROP） |
| knownLegacy schema | 9 个 | **0 个** |
| 重复表 | 25 对 | **0 对** |
| outbox_event 碎片 | 11 个 schema | **1 个**（ecos_infra） |
| ST07 合规 | 3/6 引擎 schema 存在 | **6/6 全部存在** |
| 总表数 | 238 | **~200**（删了 ~40 重复空表） |

---

## 六、风险与回退

| 风险 | 缓解 |
|:--|:--|
| Mapper 限定名漏改 | 迁移后跑 `grep -r 'public\.' --include='*.xml'` 全量扫描 |
| 外键遗漏 | Phase 15 重建后跑验证查询 |
| 序列未跟随 | ALTER TABLE SET SCHEMA 自动带序列（PG 10+ 隐式序列） |
| 视图断裂 | 4 个视图需重建（ecos_knowledge 2 个 + public 2 个） |
| 需要回退 | Phase 0 的 pg_dump 备份可还原 |
