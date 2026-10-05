-- =============================================================================
-- ECOS 数据层规范化 · 可复现重置脚本 (2026-10-05)
-- 授权依据: 用户指令「彻底初始化、可删可改」— 显式覆盖 架构铁律 IR03(禁 DROP/ALTER)
-- 回滚依据: canonical-baseline/captured-live-2026-10-05/full-schema-only.sql (679 表)
-- 范围(G3 建议口径):
--   (1) rehome public 主控制 215 对象 -> ecos_control  (= 原 220 剔除 5 演示 stray 表)
--   (2) drop 演示域 ecos_demo 全 208 + public 5 演示 stray 表 (非业务契约)
--          保留不动: 五引擎 123 / knownLegacy 133 / ecos_dw 2 / 4 空占位 schema
-- 前置: _win_tasks/stop-backend.ps1 -WithFrontend   (释放 jar 锁 + PG in-use)
-- 状态: **待用户 G4 逐项授权后才执行** — 本文件是交付制品，未授权前不运行
-- =============================================================================
\set ON_ERROR_STOP on
BEGIN;
-- ---- 1) 建目标主控制 schema ----
CREATE SCHEMA IF NOT EXISTS ecos_control;
-- ---- 2) rehome: public 主控制表/分区 -> ecos_control (先表后视图，不含 5 演示 stray)
ALTER TABLE ONLY public."crypto_audit_ledger" SET SCHEMA ecos_control;
ALTER TABLE ONLY public."dict_column" SET SCHEMA ecos_control;
ALTER TABLE ONLY public."dict_table" SET SCHEMA ecos_control;
ALTER TABLE ONLY public."ecos_action_type" SET SCHEMA ecos_control;
ALTER TABLE ONLY public."ecos_agent" SET SCHEMA ecos_control;
ALTER TABLE ONLY public."ecos_agent_alert" SET SCHEMA ecos_control;
ALTER TABLE ONLY public."ecos_agent_execution" SET SCHEMA ecos_control;
ALTER TABLE ONLY public."ecos_agent_execution_step" SET SCHEMA ecos_control;
ALTER TABLE ONLY public."ecos_agent_memory" SET SCHEMA ecos_control;
ALTER TABLE ONLY public."ecos_agent_metrics" SET SCHEMA ecos_control;
ALTER TABLE ONLY public."ecos_agent_registry" SET SCHEMA ecos_control;
ALTER TABLE ONLY public."ecos_agent_version" SET SCHEMA ecos_control;
ALTER TABLE ONLY public."ecos_alert_history" SET SCHEMA ecos_control;
ALTER TABLE ONLY public."ecos_alert_rule" SET SCHEMA ecos_control;
ALTER TABLE ONLY public."ecos_audit_log" SET SCHEMA ecos_control;
ALTER TABLE ONLY public."ecos_biz_contract" SET SCHEMA ecos_control;
ALTER TABLE ONLY public."ecos_biz_department" SET SCHEMA ecos_control;
ALTER TABLE ONLY public."ecos_biz_metric" SET SCHEMA ecos_control;
ALTER TABLE ONLY public."ecos_biz_project" SET SCHEMA ecos_control;
ALTER TABLE ONLY public."ecos_biz_target" SET SCHEMA ecos_control;
ALTER TABLE ONLY public."ecos_business_glossary" SET SCHEMA ecos_control;
ALTER TABLE ONLY public."ecos_business_scenario" SET SCHEMA ecos_control;
ALTER TABLE ONLY public."ecos_cls_policy" SET SCHEMA ecos_control;
ALTER TABLE ONLY public."ecos_cognitive_belief" SET SCHEMA ecos_control;
ALTER TABLE ONLY public."ecos_cognitive_evidence" SET SCHEMA ecos_control;
ALTER TABLE ONLY public."ecos_cognitive_hypothesis" SET SCHEMA ecos_control;
ALTER TABLE ONLY public."ecos_cognitive_model" SET SCHEMA ecos_control;
ALTER TABLE ONLY public."ecos_cognitive_run_invalidation" SET SCHEMA ecos_control;
ALTER TABLE ONLY public."ecos_cron_job" SET SCHEMA ecos_control;
ALTER TABLE ONLY public."ecos_cron_job_execution" SET SCHEMA ecos_control;
ALTER TABLE ONLY public."ecos_data_lineage_edge" SET SCHEMA ecos_control;
ALTER TABLE ONLY public."ecos_data_lineage_node" SET SCHEMA ecos_control;
ALTER TABLE ONLY public."ecos_data_pipeline" SET SCHEMA ecos_control;
ALTER TABLE ONLY public."ecos_data_pipeline_edge" SET SCHEMA ecos_control;
ALTER TABLE ONLY public."ecos_data_pipeline_node" SET SCHEMA ecos_control;
ALTER TABLE ONLY public."ecos_data_request" SET SCHEMA ecos_control;
ALTER TABLE ONLY public."ecos_decision" SET SCHEMA ecos_control;
ALTER TABLE ONLY public."ecos_decision_approval" SET SCHEMA ecos_control;
ALTER TABLE ONLY public."ecos_decision_case" SET SCHEMA ecos_control;
ALTER TABLE ONLY public."ecos_decision_causal_link" SET SCHEMA ecos_control;
ALTER TABLE ONLY public."ecos_decision_exception" SET SCHEMA ecos_control;
ALTER TABLE ONLY public."ecos_decision_policy" SET SCHEMA ecos_control;
ALTER TABLE ONLY public."ecos_decision_precedent" SET SCHEMA ecos_control;
ALTER TABLE ONLY public."ecos_decision_record" SET SCHEMA ecos_control;
ALTER TABLE ONLY public."ecos_decision_source_ref" SET SCHEMA ecos_control;
ALTER TABLE ONLY public."ecos_domain" SET SCHEMA ecos_control;
ALTER TABLE ONLY public."ecos_dq_execution_result" SET SCHEMA ecos_control;
ALTER TABLE ONLY public."ecos_dq_issue" SET SCHEMA ecos_control;
ALTER TABLE ONLY public."ecos_dq_rule" SET SCHEMA ecos_control;
ALTER TABLE ONLY public."ecos_dq_rule_v2" SET SCHEMA ecos_control;
ALTER TABLE ONLY public."ecos_entity_table_mapping" SET SCHEMA ecos_control;
ALTER TABLE ONLY public."ecos_function_audit_log" SET SCHEMA ecos_control;
ALTER TABLE ONLY public."ecos_glossary_term" SET SCHEMA ecos_control;
ALTER TABLE ONLY public."ecos_glossary_term_relation" SET SCHEMA ecos_control;
ALTER TABLE ONLY public."ecos_goal_tracking" SET SCHEMA ecos_control;
ALTER TABLE ONLY public."ecos_knowledge_document" SET SCHEMA ecos_control;
ALTER TABLE ONLY public."ecos_knowledge_graph_edge" SET SCHEMA ecos_control;
ALTER TABLE ONLY public."ecos_knowledge_graph_node" SET SCHEMA ecos_control;
ALTER TABLE ONLY public."ecos_marketplace_access_request" SET SCHEMA ecos_control;
ALTER TABLE ONLY public."ecos_marketplace_asset" SET SCHEMA ecos_control;
ALTER TABLE ONLY public."ecos_mission" SET SCHEMA ecos_control;
ALTER TABLE ONLY public."ecos_mission_task" SET SCHEMA ecos_control;
ALTER TABLE ONLY public."ecos_object_attachment" SET SCHEMA ecos_control;
ALTER TABLE ONLY public."ecos_object_data" SET SCHEMA ecos_control;
ALTER TABLE ONLY public."ecos_object_links" SET SCHEMA ecos_control;
ALTER TABLE ONLY public."ecos_object_relation" SET SCHEMA ecos_control;
ALTER TABLE ONLY public."ecos_object_relationship" SET SCHEMA ecos_control;
ALTER TABLE ONLY public."ecos_object_state_machine" SET SCHEMA ecos_control;
ALTER TABLE ONLY public."ecos_object_timeline" SET SCHEMA ecos_control;
ALTER TABLE ONLY public."ecos_object_version" SET SCHEMA ecos_control;
ALTER TABLE ONLY public."ecos_ontology" SET SCHEMA ecos_control;
ALTER TABLE ONLY public."ecos_ontology_action" SET SCHEMA ecos_control;
ALTER TABLE ONLY public."ecos_ontology_data" SET SCHEMA ecos_control;
ALTER TABLE ONLY public."ecos_ontology_entity" SET SCHEMA ecos_control;
ALTER TABLE ONLY public."ecos_ontology_property" SET SCHEMA ecos_control;
ALTER TABLE ONLY public."ecos_ontology_proposals" SET SCHEMA ecos_control;
ALTER TABLE ONLY public."ecos_ontology_relationship" SET SCHEMA ecos_control;
ALTER TABLE ONLY public."ecos_ontology_rule" SET SCHEMA ecos_control;
ALTER TABLE ONLY public."ecos_ontology_version" SET SCHEMA ecos_control;
ALTER TABLE ONLY public."ecos_pipeline_definition" SET SCHEMA ecos_control;
ALTER TABLE ONLY public."ecos_pipeline_execution" SET SCHEMA ecos_control;
ALTER TABLE ONLY public."ecos_pipeline_function" SET SCHEMA ecos_control;
ALTER TABLE ONLY public."ecos_pipeline_node" SET SCHEMA ecos_control;
ALTER TABLE ONLY public."ecos_pipeline_run" SET SCHEMA ecos_control;
ALTER TABLE ONLY public."ecos_pipeline_step" SET SCHEMA ecos_control;
ALTER TABLE ONLY public."ecos_pipeline_step_run" SET SCHEMA ecos_control;
ALTER TABLE ONLY public."ecos_pipeline_task" SET SCHEMA ecos_control;
ALTER TABLE ONLY public."ecos_pipeline_test_out" SET SCHEMA ecos_control;
ALTER TABLE ONLY public."ecos_pipeline_test_out_marker" SET SCHEMA ecos_control;
ALTER TABLE ONLY public."ecos_pipeline_udf" SET SCHEMA ecos_control;
ALTER TABLE ONLY public."ecos_provenance_entry" SET SCHEMA ecos_control;
ALTER TABLE ONLY public."ecos_quality_evaluation" SET SCHEMA ecos_control;
ALTER TABLE ONLY public."ecos_quality_rule" SET SCHEMA ecos_control;
ALTER TABLE ONLY public."ecos_query_history" SET SCHEMA ecos_control;
ALTER TABLE ONLY public."ecos_query_template" SET SCHEMA ecos_control;
ALTER TABLE ONLY public."ecos_rls_policy" SET SCHEMA ecos_control;
ALTER TABLE ONLY public."ecos_scenario_active_mind" SET SCHEMA ecos_control;
ALTER TABLE ONLY public."ecos_scenario_asset_binding" SET SCHEMA ecos_control;
ALTER TABLE ONLY public."ecos_scenario_binding" SET SCHEMA ecos_control;
ALTER TABLE ONLY public."ecos_scenario_binding_edge" SET SCHEMA ecos_control;
ALTER TABLE ONLY public."ecos_scenario_binding_link" SET SCHEMA ecos_control;
ALTER TABLE ONLY public."ecos_scenario_canvas_layout" SET SCHEMA ecos_control;
ALTER TABLE ONLY public."ecos_scenario_decision_record" SET SCHEMA ecos_control;
ALTER TABLE ONLY public."ecos_scenario_definition" SET SCHEMA ecos_control;
ALTER TABLE ONLY public."ecos_scenario_execution" SET SCHEMA ecos_control;
ALTER TABLE ONLY public."ecos_scenario_mind" SET SCHEMA ecos_control;
ALTER TABLE ONLY public."ecos_scenario_mind_ref" SET SCHEMA ecos_control;
ALTER TABLE ONLY public."ecos_scenario_mind_variant" SET SCHEMA ecos_control;
ALTER TABLE ONLY public."ecos_scenario_query_history" SET SCHEMA ecos_control;
ALTER TABLE ONLY public."ecos_scenario_run" SET SCHEMA ecos_control;
ALTER TABLE ONLY public."ecos_scenario_sandbox_layout" SET SCHEMA ecos_control;
ALTER TABLE ONLY public."ecos_skill" SET SCHEMA ecos_control;
ALTER TABLE ONLY public."ecos_spans" SET SCHEMA ecos_control;
ALTER TABLE ONLY public."ecos_tenant" SET SCHEMA ecos_control;
ALTER TABLE ONLY public."ecos_tenant_quota" SET SCHEMA ecos_control;
ALTER TABLE ONLY public."ecos_tenant_usage" SET SCHEMA ecos_control;
ALTER TABLE ONLY public."ecos_term_entity_binding" SET SCHEMA ecos_control;
ALTER TABLE ONLY public."ecos_token_blacklist" SET SCHEMA ecos_control;
ALTER TABLE ONLY public."ecos_token_usage" SET SCHEMA ecos_control;
ALTER TABLE ONLY public."ecos_tool_definition" SET SCHEMA ecos_control;
ALTER TABLE ONLY public."ecos_warn_log" SET SCHEMA ecos_control;
ALTER TABLE ONLY public."ecos_wm_causal_link" SET SCHEMA ecos_control;
ALTER TABLE ONLY public."ecos_wm_goal" SET SCHEMA ecos_control;
ALTER TABLE ONLY public."ecos_wm_goal_log" SET SCHEMA ecos_control;
ALTER TABLE ONLY public."ecos_wm_scenario" SET SCHEMA ecos_control;
ALTER TABLE ONLY public."ecos_workflow" SET SCHEMA ecos_control;
ALTER TABLE ONLY public."ecos_workflow_edge" SET SCHEMA ecos_control;
ALTER TABLE ONLY public."ecos_workflow_instance" SET SCHEMA ecos_control;
ALTER TABLE ONLY public."ecos_workflow_log" SET SCHEMA ecos_control;
ALTER TABLE ONLY public."ecos_workflow_node" SET SCHEMA ecos_control;
ALTER TABLE ONLY public."ecos_workflow_task" SET SCHEMA ecos_control;
ALTER TABLE ONLY public."ecos_workflow_v2" SET SCHEMA ecos_control;
ALTER TABLE ONLY public."ecos_working_memory" SET SCHEMA ecos_control;
ALTER TABLE ONLY public."ecos_world_causal_link" SET SCHEMA ecos_control;
ALTER TABLE ONLY public."ecos_world_goal" SET SCHEMA ecos_control;
ALTER TABLE ONLY public."ecos_world_scenario" SET SCHEMA ecos_control;
ALTER TABLE ONLY public."ecos_world_scenario_impact" SET SCHEMA ecos_control;
ALTER TABLE ONLY public."ecos_world_scenarios" SET SCHEMA ecos_control;
ALTER TABLE ONLY public."extraction_drafts" SET SCHEMA ecos_control;
ALTER TABLE ONLY public."flyway_schema_history" SET SCHEMA ecos_control;
ALTER TABLE ONLY public."kb_cognitive_pipeline" SET SCHEMA ecos_control;
ALTER TABLE ONLY public."kb_lineage_event" SET SCHEMA ecos_control;
ALTER TABLE ONLY public."permissions" SET SCHEMA ecos_control;
ALTER TABLE ONLY public."roles" SET SCHEMA ecos_control;
ALTER TABLE ONLY public."schema_changes" SET SCHEMA ecos_control;
ALTER TABLE ONLY public."schema_snapshots" SET SCHEMA ecos_control;
ALTER TABLE ONLY public."sys_agent_call_log" SET SCHEMA ecos_control;
ALTER TABLE ONLY public."sys_agent_message" SET SCHEMA ecos_control;
ALTER TABLE ONLY public."sys_agent_profile" SET SCHEMA ecos_control;
ALTER TABLE ONLY public."sys_agent_session" SET SCHEMA ecos_control;
ALTER TABLE ONLY public."sys_audit_log" SET SCHEMA ecos_control;
ALTER TABLE ONLY public."sys_compliance_rule" SET SCHEMA ecos_control;
ALTER TABLE ONLY public."sys_config" SET SCHEMA ecos_control;
ALTER TABLE ONLY public."sys_dict" SET SCHEMA ecos_control;
ALTER TABLE ONLY public."sys_token_blacklist" SET SCHEMA ecos_control;
ALTER TABLE ONLY public."tb_menu_module" SET SCHEMA ecos_control;
ALTER TABLE ONLY public."td_abac_policy" SET SCHEMA ecos_control;
ALTER TABLE ONLY public."td_audit_log" SET SCHEMA ecos_control;
ALTER TABLE ONLY public."td_catalog_item" SET SCHEMA ecos_control;
ALTER TABLE ONLY public."td_compliance_policy" SET SCHEMA ecos_control;
ALTER TABLE ONLY public."td_config" SET SCHEMA ecos_control;
ALTER TABLE ONLY public."td_config_version" SET SCHEMA ecos_control;
ALTER TABLE ONLY public."td_cross_border_transfer" SET SCHEMA ecos_control;
ALTER TABLE ONLY public."td_crypto_key" SET SCHEMA ecos_control;
ALTER TABLE ONLY public."td_crypto_key_audit" SET SCHEMA ecos_control;
ALTER TABLE ONLY public."td_crypto_master_key" SET SCHEMA ecos_control;
ALTER TABLE ONLY public."td_data_category" SET SCHEMA ecos_control;
ALTER TABLE ONLY public."td_data_description" SET SCHEMA ecos_control;
ALTER TABLE ONLY public."td_data_field" SET SCHEMA ecos_control;
ALTER TABLE ONLY public."td_data_permission_policy" SET SCHEMA ecos_control;
ALTER TABLE ONLY public."td_data_residency" SET SCHEMA ecos_control;
ALTER TABLE ONLY public."td_data_resource" SET SCHEMA ecos_control;
ALTER TABLE ONLY public."td_data_security_policy" SET SCHEMA ecos_control;
ALTER TABLE ONLY public."td_datasource" SET SCHEMA ecos_control;
ALTER TABLE ONLY public."td_git_repository" SET SCHEMA ecos_control;
ALTER TABLE ONLY public."td_ip_access" SET SCHEMA ecos_control;
ALTER TABLE ONLY public."td_metadata_collect_log" SET SCHEMA ecos_control;
ALTER TABLE ONLY public."td_org_permission" SET SCHEMA ecos_control;
ALTER TABLE ONLY public."td_organization" SET SCHEMA ecos_control;
ALTER TABLE ONLY public."td_organization_backup" SET SCHEMA ecos_control;
ALTER TABLE ONLY public."td_permission" SET SCHEMA ecos_control;
ALTER TABLE ONLY public."td_permission_backup" SET SCHEMA ecos_control;
ALTER TABLE ONLY public."td_role" SET SCHEMA ecos_control;
ALTER TABLE ONLY public."td_role_backup" SET SCHEMA ecos_control;
ALTER TABLE ONLY public."td_role_permission" SET SCHEMA ecos_control;
ALTER TABLE ONLY public."td_role_security_profile" SET SCHEMA ecos_control;
ALTER TABLE ONLY public."td_runtime_task" SET SCHEMA ecos_control;
ALTER TABLE ONLY public."td_runtime_task_execution" SET SCHEMA ecos_control;
ALTER TABLE ONLY public."td_runtime_task_log" SET SCHEMA ecos_control;
ALTER TABLE ONLY public."td_runtime_task_plan" SET SCHEMA ecos_control;
ALTER TABLE ONLY public."td_runtime_task_status" SET SCHEMA ecos_control;
ALTER TABLE ONLY public."td_schema_registry" SET SCHEMA ecos_control;
ALTER TABLE ONLY public."td_schema_version" SET SCHEMA ecos_control;
ALTER TABLE ONLY public."td_sm_user" SET SCHEMA ecos_control;
ALTER TABLE ONLY public."td_system_param" SET SCHEMA ecos_control;
ALTER TABLE ONLY public."td_system_variable" SET SCHEMA ecos_control;
ALTER TABLE ONLY public."td_tenant" SET SCHEMA ecos_control;
ALTER TABLE ONLY public."td_tenant_config" SET SCHEMA ecos_control;
ALTER TABLE ONLY public."td_user" SET SCHEMA ecos_control;
ALTER TABLE ONLY public."td_user_backup" SET SCHEMA ecos_control;
ALTER TABLE ONLY public."td_user_organization" SET SCHEMA ecos_control;
ALTER TABLE ONLY public."td_user_role" SET SCHEMA ecos_control;
ALTER TABLE ONLY public."td_user_security_profile" SET SCHEMA ecos_control;
ALTER TABLE ONLY public."user_security_configs" SET SCHEMA ecos_control;
ALTER TABLE ONLY public."users" SET SCHEMA ecos_control;
ALTER TABLE ONLY public."员工" SET SCHEMA ecos_control;
ALTER TABLE ONLY public."数据目录" SET SCHEMA ecos_control;
ALTER TABLE ONLY public."经营数据" SET SCHEMA ecos_control;
-- ---- 3) rehome: public 主控制视图/物化视图 -> ecos_control (依赖表已迁)
ALTER TABLE public."v_legacy_ecos_business_scenario" SET SCHEMA ecos_control;
ALTER TABLE public."v_legacy_ecos_decision_record" SET SCHEMA ecos_control;
ALTER TABLE public."v_legacy_ecos_scenario_binding" SET SCHEMA ecos_control;
ALTER TABLE public."v_legacy_ecos_scenario_binding_link" SET SCHEMA ecos_control;
ALTER TABLE public."v_legacy_ecos_scenario_mind" SET SCHEMA ecos_control;
ALTER TABLE public."v_legacy_ecos_scenario_run" SET SCHEMA ecos_control;
ALTER TABLE public."v_legacy_ecos_scenario_sandbox_layout" SET SCHEMA ecos_control;
-- ---- 4) 校验: public 遗留对象(应只剩 0 条; 若非 0 需人工定性) ----
DO $$
DECLARE cnt int;
BEGIN
  SELECT count(*) INTO cnt FROM pg_class c JOIN pg_namespace n ON n.oid=c.relnamespace
   WHERE n.nspname='public' AND c.relkind IN ('r','v','m','p') AND c.relname !~ '^pg_';
  IF cnt > 5 THEN
    RAISE EXCEPTION 'rehome 后 public 仍余 % 对象 > 5 预期 stray — 中止事务需人工复核', cnt;
  END IF;
  IF cnt > 0 THEN
    RAISE WARNING 'public 恰余 % 个演示 stray — 下一步 drop', cnt;
  ELSE
    RAISE NOTICE 'public 已清空';
  END IF;
END $$;
-- ---- 5) drop 演示域 (G3 授权口径: 非业务契约) ----
DROP SCHEMA IF EXISTS ecos_demo CASCADE;
-- ---- 6) drop public 的 5 演示 stray 表 ----
DROP TABLE IF EXISTS public.demo_customer CASCADE;
DROP TABLE IF EXISTS public.demo_invoice CASCADE;
DROP TABLE IF EXISTS public.demo_project CASCADE;
DROP TABLE IF EXISTS public.demo_supplier CASCADE;
DROP TABLE IF EXISTS public.dw_supplier_agg CASCADE;
COMMIT;
-- =============================================================================
-- 终验: SELECT s,count(*) FROM (SELECT 'public' s,c.oid FROM pg_class c JOIN pg_namespace n ON n.oid=c.relnamespace WHERE n.nspname='public' AND c.relkind IN('r','v','m','p')
--        UNION ALL SELECT 'ecos_control',c.oid FROM pg_class c JOIN pg_namespace n ON n.oid=c.relnamespace WHERE n.nspname='ecos_control' AND c.relkind IN('r','v','m','p')) x GROUP BY s;
--        期望: public=0, ecos_control=220 (原 215 主控制 + ...); ecos_demo schema 消失
-- 回滚: psql -f canonical-baseline/captured-live-2026-10-05/full-schema-only.sql
-- =============================================================================
