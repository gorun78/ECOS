-- ============================================================
-- reconverge_phase2: V47 之后落进 alias schema 的业务表全部回迁 public
-- 依据: V122 实证生产 canonical=public; 代码显式前缀核查 (2026-09-14):
--   仅 outbox_event (V48 每 schema 一份) / saga_instance (api-gateway 显式
--   ecos_config. 前缀) 为合法保留; 其余 V48+ 散表全部回迁 public。
-- 幂等: IF EXISTS
-- 来源: ops / 2026-09-14 / 用户指令 A (回迁公共 canonical)
-- ============================================================
BEGIN;

-- ecos_identity 下 V48+ era 散表 (认知心智层 V127~129 / 决议家族 /
-- pipeline-task 新体系 / 智能体家族 / 平台运维家族)
ALTER TABLE IF EXISTS ecos_identity.dict_column SET SCHEMA public;
ALTER TABLE IF EXISTS ecos_identity.dict_table SET SCHEMA public;
ALTER TABLE IF EXISTS ecos_identity.ecos_agent_alert SET SCHEMA public;
ALTER TABLE IF EXISTS ecos_identity.ecos_agent_metrics SET SCHEMA public;
ALTER TABLE IF EXISTS ecos_identity.ecos_agent_registry SET SCHEMA public;
ALTER TABLE IF EXISTS ecos_identity.ecos_business_scenario SET SCHEMA public;
ALTER TABLE IF EXISTS ecos_identity.ecos_cognitive_belief SET SCHEMA public;
ALTER TABLE IF EXISTS ecos_identity.ecos_cognitive_evidence SET SCHEMA public;
ALTER TABLE IF EXISTS ecos_identity.ecos_cognitive_hypothesis SET SCHEMA public;
ALTER TABLE IF EXISTS ecos_identity.ecos_cognitive_model SET SCHEMA public;
ALTER TABLE IF EXISTS ecos_identity.ecos_cognitive_run_invalidation SET SCHEMA public;
ALTER TABLE IF EXISTS ecos_identity.ecos_cron_job SET SCHEMA public;
ALTER TABLE IF EXISTS ecos_identity.ecos_cron_job_execution SET SCHEMA public;
ALTER TABLE IF EXISTS ecos_identity.ecos_decision SET SCHEMA public;
ALTER TABLE IF EXISTS ecos_identity.ecos_decision_approval SET SCHEMA public;
ALTER TABLE IF EXISTS ecos_identity.ecos_decision_causal_link SET SCHEMA public;
ALTER TABLE IF EXISTS ecos_identity.ecos_decision_exception SET SCHEMA public;
ALTER TABLE IF EXISTS ecos_identity.ecos_decision_policy SET SCHEMA public;
ALTER TABLE IF EXISTS ecos_identity.ecos_decision_precedent SET SCHEMA public;
ALTER TABLE IF EXISTS ecos_identity.ecos_knowledge_graph_node SET SCHEMA public;
ALTER TABLE IF EXISTS ecos_identity.ecos_mission SET SCHEMA public;
ALTER TABLE IF EXISTS ecos_identity.ecos_mission_task SET SCHEMA public;
ALTER TABLE IF EXISTS ecos_identity.ecos_ontology_proposals SET SCHEMA public;
ALTER TABLE IF EXISTS ecos_identity.ecos_pipeline_function SET SCHEMA public;
ALTER TABLE IF EXISTS ecos_identity.ecos_pipeline_run SET SCHEMA public;
ALTER TABLE IF EXISTS ecos_identity.ecos_pipeline_step SET SCHEMA public;
ALTER TABLE IF EXISTS ecos_identity.ecos_pipeline_step_run SET SCHEMA public;
ALTER TABLE IF EXISTS ecos_identity.ecos_pipeline_task SET SCHEMA public;
ALTER TABLE IF EXISTS ecos_identity.ecos_pipeline_udf SET SCHEMA public;
ALTER TABLE IF EXISTS ecos_identity.ecos_provenance_entry SET SCHEMA public;
ALTER TABLE IF EXISTS ecos_identity.ecos_quality_evaluation SET SCHEMA public;
ALTER TABLE IF EXISTS ecos_identity.ecos_quality_rule SET SCHEMA public;
ALTER TABLE IF EXISTS ecos_identity.ecos_scenario_binding SET SCHEMA public;
ALTER TABLE IF EXISTS ecos_identity.ecos_scenario_run SET SCHEMA public;
ALTER TABLE IF EXISTS ecos_identity.ecos_skill SET SCHEMA public;
ALTER TABLE IF EXISTS ecos_identity.ecos_spans SET SCHEMA public;
ALTER TABLE IF EXISTS ecos_identity.ecos_token_usage SET SCHEMA public;
ALTER TABLE IF EXISTS ecos_identity.ecos_workflow_log SET SCHEMA public;
ALTER TABLE IF EXISTS ecos_identity.kb_cognitive_pipeline SET SCHEMA public;
ALTER TABLE IF EXISTS ecos_identity.kb_lineage_event SET SCHEMA public;
ALTER TABLE IF EXISTS ecos_identity.kb_ontology_snapshot SET SCHEMA public;
ALTER TABLE IF EXISTS ecos_identity.schema_changes SET SCHEMA public;
ALTER TABLE IF EXISTS ecos_identity.schema_snapshots SET SCHEMA public;
ALTER TABLE IF EXISTS ecos_identity.sys_agent_message SET SCHEMA public;
ALTER TABLE IF EXISTS ecos_identity.sys_agent_session SET SCHEMA public;
ALTER TABLE IF EXISTS ecos_identity.sys_compliance_rule SET SCHEMA public;
ALTER TABLE IF EXISTS ecos_identity.sys_extraction_source SET SCHEMA public;
ALTER TABLE IF EXISTS ecos_identity.sys_rule_version SET SCHEMA public;
ALTER TABLE IF EXISTS ecos_identity.sys_token_blacklist SET SCHEMA public;
ALTER TABLE IF EXISTS ecos_identity.td_metadata_collect_log SET SCHEMA public;
-- 注: ecos_identity.outbox_event 保留原地 (V48 契约, 每 schema 一份,
--     代码通过 search_path 或显式 per-service 前缀引用)

COMMIT;
