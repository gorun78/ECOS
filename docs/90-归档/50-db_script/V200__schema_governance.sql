-- ============================================================
-- ECOS Schema 彻底治理迁移脚本 V200
-- ============================================================
-- 版本: V200 | 2026-10-01
-- 性质: 一次性彻底初始化（用户授权可删可改）
-- 目标: 15 schema / 238 table → 8 schema / ~200 table
-- 前置: pg_dump 全库备份
-- 依据: docs/50-db_script/ECOS-Schema彻底治理方案-2026-10-01.md
-- ============================================================

-- ============================================================
-- Phase 0: 预检 + 快照
-- ============================================================
DO $$
BEGIN
    RAISE NOTICE '=== Phase 0: 预检 ===';
    RAISE NOTICE 'Database: %, User: %', current_database(), current_user;
END $$;

-- 快照当前表数（迁移前基线）
SELECT schemaname, count(*) AS table_count FROM pg_tables
WHERE schemaname NOT IN ('pg_catalog', 'information_schema')
GROUP BY schemaname ORDER BY schemaname;

-- ============================================================
-- Phase 1: 创建目标 schema
-- ============================================================
DO $$ BEGIN RAISE NOTICE '=== Phase 1: 创建目标 schema ==='; END $$;

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
    END LOOP;
END $$;

-- ============================================================
-- Phase 2: 删除所有外键约束（迁移后按需重建）
-- ============================================================
DO $$ BEGIN RAISE NOTICE '=== Phase 2: 删除外键约束 ==='; END $$;

-- ecos_identity 侧 FK
ALTER TABLE ecos_identity.dict_column DROP CONSTRAINT IF EXISTS dict_column_table_id_fkey;
ALTER TABLE ecos_identity.ecos_mission_task DROP CONSTRAINT IF EXISTS ecos_mission_task_mission_id_fkey;
ALTER TABLE ecos_identity.sys_agent_message DROP CONSTRAINT IF EXISTS sys_agent_message_session_id_fkey;
ALTER TABLE ecos_identity.sys_rule_version DROP CONSTRAINT IF EXISTS fk_rule_version_rule;

-- public 侧 FK
ALTER TABLE public.dict_column DROP CONSTRAINT IF EXISTS dict_column_table_id_fkey;
ALTER TABLE public.ecos_dq_issue DROP CONSTRAINT IF EXISTS fk_dq_issue_rule;
ALTER TABLE public.ecos_goal_tracking DROP CONSTRAINT IF EXISTS ecos_goal_tracking_goal_id_fkey;
ALTER TABLE public.ecos_marketplace_access_request DROP CONSTRAINT IF EXISTS ecos_marketplace_access_request_asset_id_fkey;
ALTER TABLE public.ecos_mission_task DROP CONSTRAINT IF EXISTS ecos_mission_task_mission_id_fkey;
ALTER TABLE public.ecos_wm_causal_link DROP CONSTRAINT IF EXISTS ecos_wm_causal_link_source_goal_id_fkey;
ALTER TABLE public.ecos_wm_causal_link DROP CONSTRAINT IF EXISTS ecos_wm_causal_link_target_goal_id_fkey;
ALTER TABLE public.ecos_wm_goal DROP CONSTRAINT IF EXISTS ecos_wm_goal_parent_id_fkey;
ALTER TABLE public.ecos_wm_goal_log DROP CONSTRAINT IF EXISTS ecos_wm_goal_log_goal_id_fkey;
ALTER TABLE public.td_org_permission DROP CONSTRAINT IF EXISTS td_org_permission_org_id_fkey;
ALTER TABLE public.td_role_permission DROP CONSTRAINT IF EXISTS td_role_permission_role_id_fkey;
ALTER TABLE public.td_role_permission DROP CONSTRAINT IF EXISTS td_role_permission_permission_id_fkey;
ALTER TABLE public.td_user_organization DROP CONSTRAINT IF EXISTS td_user_organization_org_id_fkey;
ALTER TABLE public.td_user_organization DROP CONSTRAINT IF EXISTS td_user_organization_user_id_fkey;
ALTER TABLE public.td_user_role DROP CONSTRAINT IF EXISTS td_user_role_role_id_fkey;
ALTER TABLE public.td_user_role DROP CONSTRAINT IF EXISTS td_user_role_role_id_fkey;

-- 删除需要重建的视图（public 侧两个视图引用将迁移的表）
DROP VIEW IF EXISTS public.v_ecos_function_audit_log;
DROP VIEW IF EXISTS public.ecos_fc_caliber;
-- ecos_knowledge 两个静态视图保留（不引用将迁移的表）

-- ============================================================
-- Phase 3: 删除重复空表（ecos_identity 侧 25 张）
-- ============================================================
DO $$ BEGIN RAISE NOTICE '=== Phase 3: 删除重复空表 ==='; END $$;

-- 零数据重复表（DROP ecos_identity 侧）
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

-- 有数据但 public 侧更全/相同的重复表
DROP TABLE IF EXISTS ecos_identity.ecos_pipeline_function;  -- 120=120 同数据
DROP TABLE IF EXISTS ecos_identity.ecos_cron_job;           -- 2=2 同数据
DROP TABLE IF EXISTS ecos_identity.ecos_skill;              -- 3=3 同数据
DROP TABLE IF EXISTS ecos_identity.schema_changes;          -- 173 < public 191
DROP TABLE IF EXISTS ecos_identity.schema_snapshots;        -- 173 < public 191

-- public 侧 ecos_agent_registry（6 行）DROP，保留 ecos_identity 侧（9 行更多）
DROP TABLE IF EXISTS public.ecos_agent_registry;

-- 碰撞处理：ecos_ontology 已有空 ecos_workflow(0行)/ecos_workflow_approval(0行)
-- public.ecos_workflow 有 5 行数据 → DROP ecos_ontology 空表，迁入 public 表
DROP TABLE IF EXISTS ecos_ontology.ecos_workflow;
DROP TABLE IF EXISTS ecos_ontology.ecos_workflow_approval;

-- ============================================================
-- Phase 4: 合并 outbox_event → ecos_infra（全部 0 行）
-- ============================================================
DO $$ BEGIN RAISE NOTICE '=== Phase 4: 合并 outbox_event ==='; END $$;

-- 在 ecos_infra 建统一 outbox_event
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

-- DROP 11 个旧 outbox_event（全部 0 行）
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

-- 删除关联序列
DO $$
DECLARE seq_schema text;
BEGIN
    FOREACH seq_schema IN ARRAY ARRAY['ecos_agent','ecos_audit','ecos_catalog',
        'ecos_identity','ecos_knowledge','ecos_mission','ecos_object',
        'ecos_ontology','ecos_pipeline','ecos_rule','ecos_workflow']
    LOOP
        BEGIN
            EXECUTE format('DROP SEQUENCE IF EXISTS %I.outbox_event_id_seq', seq_schema);
        EXCEPTION WHEN OTHERS THEN
            RAISE NOTICE 'Sequence drop skipped for %: %', seq_schema, SQLERRM;
        END;
    END LOOP;
END $$;

-- ============================================================
-- Phase 5: 迁移表到 ecos_ai（火W）
-- ============================================================
DO $$ BEGIN RAISE NOTICE '=== Phase 5: 迁移表到 ecos_ai ==='; END $$;

-- 5.1 从 public 迁入
ALTER TABLE public.ecos_agent SET SCHEMA ecos_ai;
ALTER TABLE public.ecos_agent_alert SET SCHEMA ecos_ai;
ALTER TABLE public.ecos_agent_metrics SET SCHEMA ecos_ai;
ALTER TABLE public.ecos_cron_job SET SCHEMA ecos_ai;
ALTER TABLE public.ecos_cron_job_execution SET SCHEMA ecos_ai;
ALTER TABLE public.ecos_mission SET SCHEMA ecos_ai;
ALTER TABLE public.ecos_mission_task SET SCHEMA ecos_ai;
ALTER TABLE public.ecos_skill SET SCHEMA ecos_ai;
ALTER TABLE public.ecos_token_usage SET SCHEMA ecos_ai;
ALTER TABLE public.sys_agent_message SET SCHEMA ecos_ai;
ALTER TABLE public.sys_agent_session SET SCHEMA ecos_ai;

-- 5.2 从 ecos_identity 迁入
ALTER TABLE ecos_identity.ecos_agent_registry SET SCHEMA ecos_ai;  -- 9 行，比 public 6 行多
ALTER TABLE ecos_identity.ecos_business_scenario SET SCHEMA ecos_ai;
ALTER TABLE ecos_identity.ecos_scenario_binding SET SCHEMA ecos_ai;
ALTER TABLE ecos_identity.ecos_scenario_run SET SCHEMA ecos_ai;
ALTER TABLE ecos_identity.sys_agent_message SET SCHEMA ecos_ai;
ALTER TABLE ecos_identity.sys_agent_session SET SCHEMA ecos_ai;

-- 5.3 从 ecos_agent 整体迁入（18 表，outbox_event 已在 Phase 4 DROP）
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

-- ============================================================
-- Phase 6: 迁移表到 ecos_cognitive（木C）
-- ============================================================
DO $$ BEGIN RAISE NOTICE '=== Phase 6: 迁移表到 ecos_cognitive ==='; END $$;

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

-- ============================================================
-- Phase 7: 迁移表到 ecos_data（土D）
-- ============================================================
DO $$ BEGIN RAISE NOTICE '=== Phase 7: 迁移表到 ecos_data ==='; END $$;

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
ALTER TABLE ecos_identity.kb_lineage_event SET SCHEMA ecos_data;
ALTER TABLE ecos_identity.td_metadata_collect_log SET SCHEMA ecos_data;

-- ============================================================
-- Phase 8: 迁移表到 ecos_ontology（金I）
-- ============================================================
DO $$ BEGIN RAISE NOTICE '=== Phase 8: 迁移表到 ecos_ontology ==='; END $$;

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
ALTER TABLE public.ecos_workflow SET SCHEMA ecos_ontology;

-- 8.2 从 ecos_identity 迁入
ALTER TABLE ecos_identity.ecos_knowledge_graph_node SET SCHEMA ecos_ontology;
ALTER TABLE ecos_identity.kb_ontology_snapshot SET SCHEMA ecos_ontology;

-- ============================================================
-- Phase 9: 迁移表到 ecos_knowledge（水K）
-- ============================================================
DO $$ BEGIN RAISE NOTICE '=== Phase 9: 迁移表到 ecos_knowledge ==='; END $$;

-- 从 public 迁入
ALTER TABLE public.extraction_drafts SET SCHEMA ecos_knowledge;

-- 从 ecos_identity 迁入
ALTER TABLE ecos_identity.kb_cognitive_pipeline SET SCHEMA ecos_knowledge;
ALTER TABLE ecos_identity.sys_extraction_source SET SCHEMA ecos_knowledge;

-- ============================================================
-- Phase 10: 迁移演示数据到 ecos_dw（业务域）
-- ============================================================
DO $$ BEGIN RAISE NOTICE '=== Phase 10: 迁移演示数据到 ecos_dw ==='; END $$;

ALTER TABLE public.demo_customer SET SCHEMA ecos_dw;
ALTER TABLE public.demo_invoice SET SCHEMA ecos_dw;
ALTER TABLE public.demo_supplier SET SCHEMA ecos_dw;

-- ============================================================
-- Phase 11: 迁移横切表到 ecos_infra
-- ============================================================
DO $$ BEGIN RAISE NOTICE '=== Phase 11: 迁移横切表到 ecos_infra ==='; END $$;

-- 从 public 迁入
ALTER TABLE public.ecos_spans SET SCHEMA ecos_infra;
ALTER TABLE public.ecos_function_audit_log SET SCHEMA ecos_infra;
ALTER TABLE public.schema_changes SET SCHEMA ecos_infra;
ALTER TABLE public.schema_snapshots SET SCHEMA ecos_infra;

-- 从 ecos_config 迁入
ALTER TABLE ecos_config.saga_instance SET SCHEMA ecos_infra;

-- ============================================================
-- Phase 12: 剩余 public 表迁入 ecos_control（主控制 schema）
-- ============================================================
DO $$ BEGIN RAISE NOTICE '=== Phase 12: 迁入 ecos_control ==='; END $$;

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
ALTER TABLE public.ecos_workflow_approval SET SCHEMA ecos_control;
ALTER TABLE public.ecos_workflow_instance SET SCHEMA ecos_control;
ALTER TABLE public.ecos_workflow_log SET SCHEMA ecos_control;
ALTER TABLE public.ecos_workflow_task SET SCHEMA ecos_control;

-- 剩余 ecos_identity 表迁入 ecos_control
ALTER TABLE ecos_identity.sys_compliance_rule SET SCHEMA ecos_control;
ALTER TABLE ecos_identity.sys_rule_version SET SCHEMA ecos_control;

-- ============================================================
-- Phase 13: 清理空 schema
-- ============================================================
DO $$ BEGIN RAISE NOTICE '=== Phase 13: 清理空 schema ==='; END $$;

-- 13.1 验证 schema 已空
DO $$
DECLARE s text; cnt int;
BEGIN
    FOREACH s IN ARRAY ARRAY['ecos_identity','ecos_agent','ecos_dq',
        'ecos_catalog','ecos_pipeline','ecos_config','ecos_object',
        'ecos_audit','ecos_mission','ecos_workflow','ecos_rule']
    LOOP
        EXECUTE format('SELECT count(*) FROM pg_tables WHERE schemaname = %L', s) INTO cnt;
        IF cnt > 0 THEN
            RAISE WARNING '% still has % tables!', s, cnt;
        ELSE
            RAISE NOTICE '% is empty, ready to drop', s;
        END IF;
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

-- ============================================================
-- Phase 14: 处理 public schema
-- ============================================================
DO $$ BEGIN RAISE NOTICE '=== Phase 14: 处理 public schema ==='; END $$;

-- 确认 public 已空
DO $$
DECLARE cnt int;
BEGIN
    SELECT count(*) INTO cnt FROM pg_tables WHERE schemaname = 'public';
    RAISE NOTICE 'public schema remaining tables: %', cnt;
END $$;

-- 清空 public 的 search_path 权限，防止新连接自动使用
REVOKE ALL ON SCHEMA public FROM PUBLIC;
REVOKE ALL ON SCHEMA public FROM postgres;
GRANT USAGE ON SCHEMA public TO postgres;  -- 仅保留 USAGE 以备回退

-- 注：不 DROP public（PG 默认行为会在新连接时重建空 public）
-- 改为：public 保持空壳，所有代码使用 ecos_control 限定名

-- ============================================================
-- Phase 15: 重建外键约束
-- ============================================================
DO $$ BEGIN RAISE NOTICE '=== Phase 15: 重建外键 ==='; END $$;

-- dict_column → dict_table（都在 ecos_control）
ALTER TABLE ecos_control.dict_column
    ADD CONSTRAINT dict_column_table_id_fkey
    FOREIGN KEY (table_id) REFERENCES ecos_control.dict_table(id);

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

-- 本体域内部 FK（都在 ecos_ontology）
ALTER TABLE ecos_ontology.ecos_marketplace_access_request
    ADD CONSTRAINT ecos_marketplace_access_request_asset_id_fkey
    FOREIGN KEY (asset_id) REFERENCES ecos_ontology.ecos_marketplace_asset(id);

-- DQ 内部 FK（都在 ecos_data）
ALTER TABLE ecos_data.ecos_dq_issue
    ADD CONSTRAINT fk_dq_issue_rule
    FOREIGN KEY (rule_id) REFERENCES ecos_data.ecos_dq_rule(id);

-- sys_agent_message → sys_agent_session（都在 ecos_ai）
-- 注：需确认两表结构兼容
DO $$
BEGIN
    -- 尝试添加 FK，如果列不匹配则跳过
    BEGIN
        ALTER TABLE ecos_ai.ecos_agent_message
            ADD CONSTRAINT sys_agent_message_session_id_fkey
            FOREIGN KEY (session_id) REFERENCES ecos_ai.ecos_agent_session(id);
    EXCEPTION WHEN OTHERS THEN
        RAISE NOTICE 'Skipped sys_agent_message FK: %', SQLERRM;
    END;
END $$;

-- sys_rule_version → sys_compliance_rule
-- sys_compliance_rule 迁到了 ecos_knowledge，sys_rule_version 迁到了 ecos_control
-- 跨 schema FK 不再重建（ST09：跨引擎不直查他域表）

-- ============================================================
-- Phase 16: 重建视图
-- ============================================================
DO $$ BEGIN RAISE NOTICE '=== Phase 16: 重建视图 ==='; END $$;

-- v_ecos_function_audit_log → 迁到 ecos_control（引用 ecos_ontology.ecos_function_audit）
CREATE OR REPLACE VIEW ecos_control.v_ecos_function_audit_log AS
SELECT id,
    function_name,
    expression,
    object_type AS entity_name,
    result_summary AS result_value,
    duration_ms AS execution_time_ms,
    caller_id,
    status,
    error_message,
    create_time AS created_at,
    trace_id,
    domain,
    version_no,
    is_deleted
FROM ecos_ontology.ecos_function_audit;

-- ecos_fc_caliber → 迁到 ecos_control（引用 ecos_ontology.ecos_caliber + ecos_caliber_version）
CREATE OR REPLACE VIEW ecos_control.ecos_fc_caliber AS
SELECT c.id AS caliber_id,
    c.code AS caliber_code,
    c.name AS caliber_name,
    c.unit,
    c.currency,
    c.owner_role,
    c.domain,
    cv.id AS caliber_version_id,
    cv.version_no AS caliber_version,
    cv.formula AS formula_text,
    cv.formula AS formula_json,
    cv.additive,
    cv.period_granularity,
    cv.status AS caliber_status,
    cv.approved_by,
    cv.approved_at,
    cv.git_ref AS content_git_ref,
    cv.create_time,
    cv.update_time,
    cv.create_by,
    cv.update_by,
    cv.is_deleted
FROM ecos_ontology.ecos_caliber c
JOIN ecos_ontology.ecos_caliber_version cv ON (cv.caliber_id::text = c.id::text)
WHERE c.is_deleted = 0;

-- ============================================================
-- Phase 17: 最终验证
-- ============================================================
DO $$ BEGIN RAISE NOTICE '=== Phase 17: 最终验证 ==='; END $$;

-- 17.1 Schema 清单（预期 8+public 空壳）
SELECT schema_name FROM information_schema.schemata
WHERE schema_name NOT IN ('pg_catalog', 'information_schema', 'pg_toast')
ORDER BY schema_name;

-- 17.2 各 schema 表数
SELECT schemaname, count(*) AS table_count FROM pg_tables
WHERE schemaname NOT IN ('pg_catalog', 'information_schema')
GROUP BY schemaname ORDER BY schemaname;

-- 17.3 总表数
SELECT count(*) AS total_tables FROM pg_tables
WHERE schemaname NOT IN ('pg_catalog', 'information_schema');

-- 17.4 确认 public 已空
SELECT count(*) AS public_remaining FROM pg_tables WHERE schemaname = 'public';

-- 17.5 确认无空壳 schema（除 public）
SELECT s.schema_name, count(t.tablename) AS tables
FROM information_schema.schemata s
LEFT JOIN pg_tables t ON s.schema_name = t.schemaname
WHERE s.schema_name NOT IN ('pg_catalog', 'information_schema', 'pg_toast')
GROUP BY s.schema_name
HAVING count(t.tablename) = 0;

-- 17.6 外键完整性
SELECT tc.table_schema, tc.table_name, tc.constraint_name,
       ccu.table_schema AS ref_schema, ccu.table_name AS ref_table
FROM information_schema.table_constraints tc
JOIN information_schema.constraint_column_usage ccu
    ON tc.constraint_name = ccu.constraint_name
WHERE tc.constraint_type = 'FOREIGN KEY'
    AND tc.table_schema NOT IN ('pg_catalog', 'information_schema')
ORDER BY tc.table_schema, tc.table_name;

-- 17.7 视图清单
SELECT schemaname, viewname FROM pg_views
WHERE schemaname NOT IN ('pg_catalog', 'information_schema')
ORDER BY schemaname, viewname;

DO $$ BEGIN
    RAISE NOTICE '====================================';
    RAISE NOTICE 'Schema 治理迁移完成！';
    RAISE NOTICE '目标态: 8 schema (ecos_control/ecos_data/ecos_ontology/ecos_knowledge/ecos_ai/ecos_cognitive/ecos_infra/ecos_dw)';
    RAISE NOTICE '====================================';
END $$;
