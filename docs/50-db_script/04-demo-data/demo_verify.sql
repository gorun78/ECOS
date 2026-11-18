-- ============================================================================
-- demo_verify.sql — 演示数据校验（只读，不改任何行）
-- ============================================================================
-- 三段校验:
--   §1 逐表行数 vs 导出基线（58 张演示表，OK / EMPTY / DRIFT）
--   §2 系统种子面完好（27 张表逐表对账**种子册灌入行数** + admin 超管隶属=1）
--      基线不取实库 count(*)：实库 td_user 多一行 tdv01（登录迁移验证账号，已软删、0 角色隶属，
--      建账时刻晚于种子册导出），属测试残留，刻意不进系统种子 ⇒ 新库 td_user 基线 = 17。
--   §3 演示集 FK 完整性（孤儿行 = 0）+ 跨集休眠 FK 现状登记
--
-- 基线口径: 2026-11-18 从 sys_man 导出时的精确 count(*)。
--       DRIFT 不必然是缺陷——界面新增/修改业务数据属正常演进；
--       要回到基线请跑 demo_cleanup.sql + demo_seed.sql。
--
-- 运行: docker exec -i ecos-postgres psql -U postgres -d <db> -v ON_ERROR_STOP=1 -f - < demo_verify.sql
-- ============================================================================

\echo '=== §1 演示表逐行对账 ==='
SELECT 'ecos_ai.agent_registry' AS tbl, count(*) AS actual, 4 AS baseline,
       CASE WHEN count(*) = 4 THEN 'OK' WHEN count(*) = 0 THEN 'EMPTY' ELSE 'DRIFT' END AS verdict
FROM ecos_ai.agent_registry
UNION ALL
SELECT 'ecos_ai.ecos_agent_registry' AS tbl, count(*) AS actual, 13 AS baseline,
       CASE WHEN count(*) = 13 THEN 'OK' WHEN count(*) = 0 THEN 'EMPTY' ELSE 'DRIFT' END AS verdict
FROM ecos_ai.ecos_agent_registry
UNION ALL
SELECT 'ecos_ai.ecos_aip_pipeline' AS tbl, count(*) AS actual, 9 AS baseline,
       CASE WHEN count(*) = 9 THEN 'OK' WHEN count(*) = 0 THEN 'EMPTY' ELSE 'DRIFT' END AS verdict
FROM ecos_ai.ecos_aip_pipeline
UNION ALL
SELECT 'ecos_ai.ecos_cron_job' AS tbl, count(*) AS actual, 2 AS baseline,
       CASE WHEN count(*) = 2 THEN 'OK' WHEN count(*) = 0 THEN 'EMPTY' ELSE 'DRIFT' END AS verdict
FROM ecos_ai.ecos_cron_job
UNION ALL
SELECT 'ecos_ai.ecos_mission' AS tbl, count(*) AS actual, 1 AS baseline,
       CASE WHEN count(*) = 1 THEN 'OK' WHEN count(*) = 0 THEN 'EMPTY' ELSE 'DRIFT' END AS verdict
FROM ecos_ai.ecos_mission
UNION ALL
SELECT 'ecos_ai.ecos_skill' AS tbl, count(*) AS actual, 3 AS baseline,
       CASE WHEN count(*) = 3 THEN 'OK' WHEN count(*) = 0 THEN 'EMPTY' ELSE 'DRIFT' END AS verdict
FROM ecos_ai.ecos_skill
UNION ALL
SELECT 'ecos_ai.ecos_wagent_action' AS tbl, count(*) AS actual, 7 AS baseline,
       CASE WHEN count(*) = 7 THEN 'OK' WHEN count(*) = 0 THEN 'EMPTY' ELSE 'DRIFT' END AS verdict
FROM ecos_ai.ecos_wagent_action
UNION ALL
SELECT 'ecos_ai.ecos_wagent_candidate' AS tbl, count(*) AS actual, 134 AS baseline,
       CASE WHEN count(*) = 134 THEN 'OK' WHEN count(*) = 0 THEN 'EMPTY' ELSE 'DRIFT' END AS verdict
FROM ecos_ai.ecos_wagent_candidate
UNION ALL
SELECT 'ecos_ai.ecos_wagent_goal' AS tbl, count(*) AS actual, 2 AS baseline,
       CASE WHEN count(*) = 2 THEN 'OK' WHEN count(*) = 0 THEN 'EMPTY' ELSE 'DRIFT' END AS verdict
FROM ecos_ai.ecos_wagent_goal
UNION ALL
SELECT 'ecos_ai.ecos_wagent_mission' AS tbl, count(*) AS actual, 4 AS baseline,
       CASE WHEN count(*) = 4 THEN 'OK' WHEN count(*) = 0 THEN 'EMPTY' ELSE 'DRIFT' END AS verdict
FROM ecos_ai.ecos_wagent_mission
UNION ALL
SELECT 'ecos_ai.ecos_wagent_question' AS tbl, count(*) AS actual, 16 AS baseline,
       CASE WHEN count(*) = 16 THEN 'OK' WHEN count(*) = 0 THEN 'EMPTY' ELSE 'DRIFT' END AS verdict
FROM ecos_ai.ecos_wagent_question
UNION ALL
SELECT 'ecos_ai.ecos_wagent_readiness' AS tbl, count(*) AS actual, 10 AS baseline,
       CASE WHEN count(*) = 10 THEN 'OK' WHEN count(*) = 0 THEN 'EMPTY' ELSE 'DRIFT' END AS verdict
FROM ecos_ai.ecos_wagent_readiness
UNION ALL
SELECT 'ecos_ai.ecos_wagent_readiness_gap' AS tbl, count(*) AS actual, 80 AS baseline,
       CASE WHEN count(*) = 80 THEN 'OK' WHEN count(*) = 0 THEN 'EMPTY' ELSE 'DRIFT' END AS verdict
FROM ecos_ai.ecos_wagent_readiness_gap
UNION ALL
SELECT 'ecos_ai.ecos_wagent_readiness_item' AS tbl, count(*) AS actual, 80 AS baseline,
       CASE WHEN count(*) = 80 THEN 'OK' WHEN count(*) = 0 THEN 'EMPTY' ELSE 'DRIFT' END AS verdict
FROM ecos_ai.ecos_wagent_readiness_item
UNION ALL
SELECT 'ecos_ai.ecos_wagent_tool_contract' AS tbl, count(*) AS actual, 12 AS baseline,
       CASE WHEN count(*) = 12 THEN 'OK' WHEN count(*) = 0 THEN 'EMPTY' ELSE 'DRIFT' END AS verdict
FROM ecos_ai.ecos_wagent_tool_contract
UNION ALL
SELECT 'ecos_cognitive.ecos_biz_project' AS tbl, count(*) AS actual, 3 AS baseline,
       CASE WHEN count(*) = 3 THEN 'OK' WHEN count(*) = 0 THEN 'EMPTY' ELSE 'DRIFT' END AS verdict
FROM ecos_cognitive.ecos_biz_project
UNION ALL
SELECT 'ecos_cognitive.ecos_biz_contract' AS tbl, count(*) AS actual, 5 AS baseline,
       CASE WHEN count(*) = 5 THEN 'OK' WHEN count(*) = 0 THEN 'EMPTY' ELSE 'DRIFT' END AS verdict
FROM ecos_cognitive.ecos_biz_contract
UNION ALL
SELECT 'ecos_cognitive.ecos_biz_department' AS tbl, count(*) AS actual, 3 AS baseline,
       CASE WHEN count(*) = 3 THEN 'OK' WHEN count(*) = 0 THEN 'EMPTY' ELSE 'DRIFT' END AS verdict
FROM ecos_cognitive.ecos_biz_department
UNION ALL
SELECT 'ecos_cognitive.ecos_cognitive_mind' AS tbl, count(*) AS actual, 1 AS baseline,
       CASE WHEN count(*) = 1 THEN 'OK' WHEN count(*) = 0 THEN 'EMPTY' ELSE 'DRIFT' END AS verdict
FROM ecos_cognitive.ecos_cognitive_mind
UNION ALL
SELECT 'ecos_cognitive.ecos_cognitive_scenario_mind' AS tbl, count(*) AS actual, 1 AS baseline,
       CASE WHEN count(*) = 1 THEN 'OK' WHEN count(*) = 0 THEN 'EMPTY' ELSE 'DRIFT' END AS verdict
FROM ecos_cognitive.ecos_cognitive_scenario_mind
UNION ALL
SELECT 'ecos_control.demo_customer' AS tbl, count(*) AS actual, 15 AS baseline,
       CASE WHEN count(*) = 15 THEN 'OK' WHEN count(*) = 0 THEN 'EMPTY' ELSE 'DRIFT' END AS verdict
FROM ecos_control.demo_customer
UNION ALL
SELECT 'ecos_control.demo_supplier' AS tbl, count(*) AS actual, 3 AS baseline,
       CASE WHEN count(*) = 3 THEN 'OK' WHEN count(*) = 0 THEN 'EMPTY' ELSE 'DRIFT' END AS verdict
FROM ecos_control.demo_supplier
UNION ALL
SELECT 'ecos_control.ecos_agent' AS tbl, count(*) AS actual, 5 AS baseline,
       CASE WHEN count(*) = 5 THEN 'OK' WHEN count(*) = 0 THEN 'EMPTY' ELSE 'DRIFT' END AS verdict
FROM ecos_control.ecos_agent
UNION ALL
SELECT 'ecos_control.ecos_business_scenario' AS tbl, count(*) AS actual, 245 AS baseline,
       CASE WHEN count(*) = 245 THEN 'OK' WHEN count(*) = 0 THEN 'EMPTY' ELSE 'DRIFT' END AS verdict
FROM ecos_control.ecos_business_scenario
UNION ALL
SELECT 'ecos_control.ecos_cognitive_belief' AS tbl, count(*) AS actual, 2 AS baseline,
       CASE WHEN count(*) = 2 THEN 'OK' WHEN count(*) = 0 THEN 'EMPTY' ELSE 'DRIFT' END AS verdict
FROM ecos_control.ecos_cognitive_belief
UNION ALL
SELECT 'ecos_control.ecos_cognitive_evidence' AS tbl, count(*) AS actual, 2 AS baseline,
       CASE WHEN count(*) = 2 THEN 'OK' WHEN count(*) = 0 THEN 'EMPTY' ELSE 'DRIFT' END AS verdict
FROM ecos_control.ecos_cognitive_evidence
UNION ALL
SELECT 'ecos_control.ecos_cognitive_hypothesis' AS tbl, count(*) AS actual, 1 AS baseline,
       CASE WHEN count(*) = 1 THEN 'OK' WHEN count(*) = 0 THEN 'EMPTY' ELSE 'DRIFT' END AS verdict
FROM ecos_control.ecos_cognitive_hypothesis
UNION ALL
SELECT 'ecos_control.ecos_cognitive_model' AS tbl, count(*) AS actual, 1 AS baseline,
       CASE WHEN count(*) = 1 THEN 'OK' WHEN count(*) = 0 THEN 'EMPTY' ELSE 'DRIFT' END AS verdict
FROM ecos_control.ecos_cognitive_model
UNION ALL
SELECT 'ecos_control.ecos_dq_rule' AS tbl, count(*) AS actual, 5 AS baseline,
       CASE WHEN count(*) = 5 THEN 'OK' WHEN count(*) = 0 THEN 'EMPTY' ELSE 'DRIFT' END AS verdict
FROM ecos_control.ecos_dq_rule
UNION ALL
SELECT 'ecos_control.ecos_object_links' AS tbl, count(*) AS actual, 7 AS baseline,
       CASE WHEN count(*) = 7 THEN 'OK' WHEN count(*) = 0 THEN 'EMPTY' ELSE 'DRIFT' END AS verdict
FROM ecos_control.ecos_object_links
UNION ALL
SELECT 'ecos_control.ecos_object_state_machine' AS tbl, count(*) AS actual, 13 AS baseline,
       CASE WHEN count(*) = 13 THEN 'OK' WHEN count(*) = 0 THEN 'EMPTY' ELSE 'DRIFT' END AS verdict
FROM ecos_control.ecos_object_state_machine
UNION ALL
SELECT 'ecos_control.ecos_object_timeline' AS tbl, count(*) AS actual, 18 AS baseline,
       CASE WHEN count(*) = 18 THEN 'OK' WHEN count(*) = 0 THEN 'EMPTY' ELSE 'DRIFT' END AS verdict
FROM ecos_control.ecos_object_timeline
UNION ALL
SELECT 'ecos_control.ecos_ontology_action' AS tbl, count(*) AS actual, 3 AS baseline,
       CASE WHEN count(*) = 3 THEN 'OK' WHEN count(*) = 0 THEN 'EMPTY' ELSE 'DRIFT' END AS verdict
FROM ecos_control.ecos_ontology_action
UNION ALL
SELECT 'ecos_control.ecos_ontology_entity' AS tbl, count(*) AS actual, 18 AS baseline,
       CASE WHEN count(*) = 18 THEN 'OK' WHEN count(*) = 0 THEN 'EMPTY' ELSE 'DRIFT' END AS verdict
FROM ecos_control.ecos_ontology_entity
UNION ALL
SELECT 'ecos_control.ecos_ontology_property' AS tbl, count(*) AS actual, 9 AS baseline,
       CASE WHEN count(*) = 9 THEN 'OK' WHEN count(*) = 0 THEN 'EMPTY' ELSE 'DRIFT' END AS verdict
FROM ecos_control.ecos_ontology_property
UNION ALL
SELECT 'ecos_control.ecos_ontology_relationship' AS tbl, count(*) AS actual, 9 AS baseline,
       CASE WHEN count(*) = 9 THEN 'OK' WHEN count(*) = 0 THEN 'EMPTY' ELSE 'DRIFT' END AS verdict
FROM ecos_control.ecos_ontology_relationship
UNION ALL
SELECT 'ecos_control.ecos_pareto_problem' AS tbl, count(*) AS actual, 27 AS baseline,
       CASE WHEN count(*) = 27 THEN 'OK' WHEN count(*) = 0 THEN 'EMPTY' ELSE 'DRIFT' END AS verdict
FROM ecos_control.ecos_pareto_problem
UNION ALL
SELECT 'ecos_control.ecos_pipeline_node' AS tbl, count(*) AS actual, 12 AS baseline,
       CASE WHEN count(*) = 12 THEN 'OK' WHEN count(*) = 0 THEN 'EMPTY' ELSE 'DRIFT' END AS verdict
FROM ecos_control.ecos_pipeline_node
UNION ALL
SELECT 'ecos_control.ecos_scenario_binding' AS tbl, count(*) AS actual, 8 AS baseline,
       CASE WHEN count(*) = 8 THEN 'OK' WHEN count(*) = 0 THEN 'EMPTY' ELSE 'DRIFT' END AS verdict
FROM ecos_control.ecos_scenario_binding
UNION ALL
SELECT 'ecos_control.ecos_scenario_binding_link' AS tbl, count(*) AS actual, 7 AS baseline,
       CASE WHEN count(*) = 7 THEN 'OK' WHEN count(*) = 0 THEN 'EMPTY' ELSE 'DRIFT' END AS verdict
FROM ecos_control.ecos_scenario_binding_link
UNION ALL
SELECT 'ecos_control.ecos_scenario_blueprint' AS tbl, count(*) AS actual, 13 AS baseline,
       CASE WHEN count(*) = 13 THEN 'OK' WHEN count(*) = 0 THEN 'EMPTY' ELSE 'DRIFT' END AS verdict
FROM ecos_control.ecos_scenario_blueprint
UNION ALL
SELECT 'ecos_control.ecos_scenario_sandbox_layout' AS tbl, count(*) AS actual, 1 AS baseline,
       CASE WHEN count(*) = 1 THEN 'OK' WHEN count(*) = 0 THEN 'EMPTY' ELSE 'DRIFT' END AS verdict
FROM ecos_control.ecos_scenario_sandbox_layout
UNION ALL
SELECT 'ecos_control.ecos_wm_goal' AS tbl, count(*) AS actual, 21 AS baseline,
       CASE WHEN count(*) = 21 THEN 'OK' WHEN count(*) = 0 THEN 'EMPTY' ELSE 'DRIFT' END AS verdict
FROM ecos_control.ecos_wm_goal
UNION ALL
SELECT 'ecos_control.ecos_wm_causal_link' AS tbl, count(*) AS actual, 15 AS baseline,
       CASE WHEN count(*) = 15 THEN 'OK' WHEN count(*) = 0 THEN 'EMPTY' ELSE 'DRIFT' END AS verdict
FROM ecos_control.ecos_wm_causal_link
UNION ALL
SELECT 'ecos_control.ecos_wm_scenario' AS tbl, count(*) AS actual, 5 AS baseline,
       CASE WHEN count(*) = 5 THEN 'OK' WHEN count(*) = 0 THEN 'EMPTY' ELSE 'DRIFT' END AS verdict
FROM ecos_control.ecos_wm_scenario
UNION ALL
SELECT 'ecos_control.ecos_workflow' AS tbl, count(*) AS actual, 5 AS baseline,
       CASE WHEN count(*) = 5 THEN 'OK' WHEN count(*) = 0 THEN 'EMPTY' ELSE 'DRIFT' END AS verdict
FROM ecos_control.ecos_workflow
UNION ALL
SELECT 'ecos_control.td_data_resource' AS tbl, count(*) AS actual, 2 AS baseline,
       CASE WHEN count(*) = 2 THEN 'OK' WHEN count(*) = 0 THEN 'EMPTY' ELSE 'DRIFT' END AS verdict
FROM ecos_control.td_data_resource
UNION ALL
SELECT 'ecos_control.td_datasource' AS tbl, count(*) AS actual, 1 AS baseline,
       CASE WHEN count(*) = 1 THEN 'OK' WHEN count(*) = 0 THEN 'EMPTY' ELSE 'DRIFT' END AS verdict
FROM ecos_control.td_datasource
UNION ALL
SELECT 'ecos_data.dq_rule' AS tbl, count(*) AS actual, 37 AS baseline,
       CASE WHEN count(*) = 37 THEN 'OK' WHEN count(*) = 0 THEN 'EMPTY' ELSE 'DRIFT' END AS verdict
FROM ecos_data.dq_rule
UNION ALL
SELECT 'ecos_data.ecos_data_lineage_edge' AS tbl, count(*) AS actual, 2 AS baseline,
       CASE WHEN count(*) = 2 THEN 'OK' WHEN count(*) = 0 THEN 'EMPTY' ELSE 'DRIFT' END AS verdict
FROM ecos_data.ecos_data_lineage_edge
UNION ALL
SELECT 'ecos_data.ecos_data_lineage_node' AS tbl, count(*) AS actual, 3 AS baseline,
       CASE WHEN count(*) = 3 THEN 'OK' WHEN count(*) = 0 THEN 'EMPTY' ELSE 'DRIFT' END AS verdict
FROM ecos_data.ecos_data_lineage_node
UNION ALL
SELECT 'ecos_data.ecos_pipeline_definition' AS tbl, count(*) AS actual, 41 AS baseline,
       CASE WHEN count(*) = 41 THEN 'OK' WHEN count(*) = 0 THEN 'EMPTY' ELSE 'DRIFT' END AS verdict
FROM ecos_data.ecos_pipeline_definition
UNION ALL
SELECT 'ecos_knowledge.ecos_kb_ontology_snapshot' AS tbl, count(*) AS actual, 1 AS baseline,
       CASE WHEN count(*) = 1 THEN 'OK' WHEN count(*) = 0 THEN 'EMPTY' ELSE 'DRIFT' END AS verdict
FROM ecos_knowledge.ecos_kb_ontology_snapshot
UNION ALL
SELECT 'ecos_knowledge.kb_doc' AS tbl, count(*) AS actual, 36 AS baseline,
       CASE WHEN count(*) = 36 THEN 'OK' WHEN count(*) = 0 THEN 'EMPTY' ELSE 'DRIFT' END AS verdict
FROM ecos_knowledge.kb_doc
UNION ALL
SELECT 'ecos_knowledge.kb_scheduled_extract' AS tbl, count(*) AS actual, 1 AS baseline,
       CASE WHEN count(*) = 1 THEN 'OK' WHEN count(*) = 0 THEN 'EMPTY' ELSE 'DRIFT' END AS verdict
FROM ecos_knowledge.kb_scheduled_extract
UNION ALL
SELECT 'ecos_ontology.action_definition' AS tbl, count(*) AS actual, 3 AS baseline,
       CASE WHEN count(*) = 3 THEN 'OK' WHEN count(*) = 0 THEN 'EMPTY' ELSE 'DRIFT' END AS verdict
FROM ecos_ontology.action_definition
UNION ALL
SELECT 'ecos_ontology.ecos_ontology_candidate' AS tbl, count(*) AS actual, 16 AS baseline,
       CASE WHEN count(*) = 16 THEN 'OK' WHEN count(*) = 0 THEN 'EMPTY' ELSE 'DRIFT' END AS verdict
FROM ecos_ontology.ecos_ontology_candidate
UNION ALL
SELECT 'ecos_ontology.ecos_ontology_proposal' AS tbl, count(*) AS actual, 11 AS baseline,
       CASE WHEN count(*) = 11 THEN 'OK' WHEN count(*) = 0 THEN 'EMPTY' ELSE 'DRIFT' END AS verdict
FROM ecos_ontology.ecos_ontology_proposal

ORDER BY 1;

\echo '=== §2 系统种子面完好性（27 表逐表对账种子册基线）==='
SELECT 'ecos_ai.ecos_tool_definition' AS tbl, count(*) AS actual, 23 AS baseline,
       CASE WHEN count(*) = 23 THEN 'OK' ELSE 'DRIFT' END AS verdict
FROM ecos_ai.ecos_tool_definition
UNION ALL SELECT 'ecos_cognitive.ecos_cognitive_framework' AS tbl, count(*) AS actual, 16 AS baseline,
       CASE WHEN count(*) = 16 THEN 'OK' ELSE 'DRIFT' END AS verdict
FROM ecos_cognitive.ecos_cognitive_framework
UNION ALL SELECT 'ecos_control.dict_column' AS tbl, count(*) AS actual, 2 AS baseline,
       CASE WHEN count(*) = 2 THEN 'OK' ELSE 'DRIFT' END AS verdict
FROM ecos_control.dict_column
UNION ALL SELECT 'ecos_control.dict_table' AS tbl, count(*) AS actual, 3 AS baseline,
       CASE WHEN count(*) = 3 THEN 'OK' ELSE 'DRIFT' END AS verdict
FROM ecos_control.dict_table
UNION ALL SELECT 'ecos_control.ecos_git_repo' AS tbl, count(*) AS actual, 6 AS baseline,
       CASE WHEN count(*) = 6 THEN 'OK' ELSE 'DRIFT' END AS verdict
FROM ecos_control.ecos_git_repo
UNION ALL SELECT 'ecos_control.ecos_rls_policy' AS tbl, count(*) AS actual, 28 AS baseline,
       CASE WHEN count(*) = 28 THEN 'OK' ELSE 'DRIFT' END AS verdict
FROM ecos_control.ecos_rls_policy
UNION ALL SELECT 'ecos_control.ecos_security_asset' AS tbl, count(*) AS actual, 5 AS baseline,
       CASE WHEN count(*) = 5 THEN 'OK' ELSE 'DRIFT' END AS verdict
FROM ecos_control.ecos_security_asset
UNION ALL SELECT 'ecos_control.ecos_tenant' AS tbl, count(*) AS actual, 2 AS baseline,
       CASE WHEN count(*) = 2 THEN 'OK' ELSE 'DRIFT' END AS verdict
FROM ecos_control.ecos_tenant
UNION ALL SELECT 'ecos_control.ecos_tenant_quota' AS tbl, count(*) AS actual, 2 AS baseline,
       CASE WHEN count(*) = 2 THEN 'OK' ELSE 'DRIFT' END AS verdict
FROM ecos_control.ecos_tenant_quota
UNION ALL SELECT 'ecos_control.sys_config' AS tbl, count(*) AS actual, 178 AS baseline,
       CASE WHEN count(*) = 178 THEN 'OK' ELSE 'DRIFT' END AS verdict
FROM ecos_control.sys_config
UNION ALL SELECT 'ecos_control.sys_dict' AS tbl, count(*) AS actual, 64 AS baseline,
       CASE WHEN count(*) = 64 THEN 'OK' ELSE 'DRIFT' END AS verdict
FROM ecos_control.sys_dict
UNION ALL SELECT 'ecos_control.td_abac_policy' AS tbl, count(*) AS actual, 10 AS baseline,
       CASE WHEN count(*) = 10 THEN 'OK' ELSE 'DRIFT' END AS verdict
FROM ecos_control.td_abac_policy
UNION ALL SELECT 'ecos_control.td_organization' AS tbl, count(*) AS actual, 12 AS baseline,
       CASE WHEN count(*) = 12 THEN 'OK' ELSE 'DRIFT' END AS verdict
FROM ecos_control.td_organization
UNION ALL SELECT 'ecos_control.td_permission' AS tbl, count(*) AS actual, 79 AS baseline,
       CASE WHEN count(*) = 79 THEN 'OK' ELSE 'DRIFT' END AS verdict
FROM ecos_control.td_permission
UNION ALL SELECT 'ecos_control.td_role' AS tbl, count(*) AS actual, 16 AS baseline,
       CASE WHEN count(*) = 16 THEN 'OK' ELSE 'DRIFT' END AS verdict
FROM ecos_control.td_role
UNION ALL SELECT 'ecos_control.td_role_permission' AS tbl, count(*) AS actual, 135 AS baseline,
       CASE WHEN count(*) = 135 THEN 'OK' ELSE 'DRIFT' END AS verdict
FROM ecos_control.td_role_permission
UNION ALL SELECT 'ecos_control.td_role_security_profile' AS tbl, count(*) AS actual, 8 AS baseline,
       CASE WHEN count(*) = 8 THEN 'OK' ELSE 'DRIFT' END AS verdict
FROM ecos_control.td_role_security_profile
UNION ALL SELECT 'ecos_control.td_user' AS tbl, count(*) AS actual, 17 AS baseline,
       CASE WHEN count(*) = 17 THEN 'OK' ELSE 'DRIFT' END AS verdict
FROM ecos_control.td_user
UNION ALL SELECT 'ecos_control.td_user_organization' AS tbl, count(*) AS actual, 12 AS baseline,
       CASE WHEN count(*) = 12 THEN 'OK' ELSE 'DRIFT' END AS verdict
FROM ecos_control.td_user_organization
UNION ALL SELECT 'ecos_control.td_user_role' AS tbl, count(*) AS actual, 20 AS baseline,
       CASE WHEN count(*) = 20 THEN 'OK' ELSE 'DRIFT' END AS verdict
FROM ecos_control.td_user_role
UNION ALL SELECT 'ecos_control.td_user_security_profile' AS tbl, count(*) AS actual, 16 AS baseline,
       CASE WHEN count(*) = 16 THEN 'OK' ELSE 'DRIFT' END AS verdict
FROM ecos_control.td_user_security_profile
UNION ALL SELECT 'ecos_data.ecos_data_category_tree' AS tbl, count(*) AS actual, 5 AS baseline,
       CASE WHEN count(*) = 5 THEN 'OK' ELSE 'DRIFT' END AS verdict
FROM ecos_data.ecos_data_category_tree
UNION ALL SELECT 'ecos_data.ecos_data_level_def' AS tbl, count(*) AS actual, 4 AS baseline,
       CASE WHEN count(*) = 4 THEN 'OK' ELSE 'DRIFT' END AS verdict
FROM ecos_data.ecos_data_level_def
UNION ALL SELECT 'ecos_data.ecos_pipeline_function' AS tbl, count(*) AS actual, 120 AS baseline,
       CASE WHEN count(*) = 120 THEN 'OK' ELSE 'DRIFT' END AS verdict
FROM ecos_data.ecos_pipeline_function
UNION ALL SELECT 'ecos_data.td_data_category' AS tbl, count(*) AS actual, 5 AS baseline,
       CASE WHEN count(*) = 5 THEN 'OK' ELSE 'DRIFT' END AS verdict
FROM ecos_data.td_data_category
UNION ALL SELECT 'ecos_knowledge.kb_nav_category' AS tbl, count(*) AS actual, 2 AS baseline,
       CASE WHEN count(*) = 2 THEN 'OK' ELSE 'DRIFT' END AS verdict
FROM ecos_knowledge.kb_nav_category
UNION ALL SELECT 'ecos_ontology.ecos_domain' AS tbl, count(*) AS actual, 5 AS baseline,
       CASE WHEN count(*) = 5 THEN 'OK' ELSE 'DRIFT' END AS verdict
FROM ecos_ontology.ecos_domain
ORDER BY 1;

\echo '=== §2b 超管授权面（期望 1）==='
SELECT 'admin_role_super_admin' AS item, count(*) AS n, CASE WHEN count(*) = 1 THEN 'OK' ELSE 'FAIL' END AS verdict
  FROM ecos_control.td_user_role ur JOIN ecos_control.td_role r ON r.role_id = ur.role_id
  JOIN ecos_control.td_user u ON u.user_id = ur.user_id
 WHERE u.username = 'admin' AND r.role_code = 'ROLE_SUPER_ADMIN';

\echo '=== §3 演示集 FK 孤儿行（期望全 0）==='
SELECT 'ecos_wm_causal_link.source_goal_id' AS fk, count(*) AS orphans
  FROM ecos_control.ecos_wm_causal_link c WHERE c.source_goal_id IS NOT NULL
    AND NOT EXISTS (SELECT 1 FROM ecos_control.ecos_wm_goal g WHERE g.id = c.source_goal_id)
UNION ALL SELECT 'ecos_wm_causal_link.target_goal_id', count(*)
  FROM ecos_control.ecos_wm_causal_link c WHERE c.target_goal_id IS NOT NULL
    AND NOT EXISTS (SELECT 1 FROM ecos_control.ecos_wm_goal g WHERE g.id = c.target_goal_id)
UNION ALL SELECT 'ecos_biz_contract.project_id', count(*)
  FROM ecos_cognitive.ecos_biz_contract c WHERE c.project_id IS NOT NULL
    AND NOT EXISTS (SELECT 1 FROM ecos_cognitive.ecos_biz_project p WHERE p.id = c.project_id)
UNION ALL SELECT 'ecos_ontology_entity.domain_id', count(*)
  FROM ecos_control.ecos_ontology_entity e WHERE e.domain_id IS NOT NULL
    AND NOT EXISTS (SELECT 1 FROM ecos_ontology.ecos_domain d WHERE d.id = e.domain_id)
UNION ALL SELECT 'ecos_wm_goal.domain_id', count(*)
  FROM ecos_control.ecos_wm_goal g WHERE g.domain_id IS NOT NULL
    AND NOT EXISTS (SELECT 1 FROM ecos_ontology.ecos_domain d WHERE d.id = g.domain_id)
UNION ALL SELECT 'ecos_wm_goal.parent_id（自引用）', count(*)
  FROM ecos_control.ecos_wm_goal g WHERE g.parent_id IS NOT NULL
    AND NOT EXISTS (SELECT 1 FROM ecos_control.ecos_wm_goal p WHERE p.id = g.parent_id)
ORDER BY 1;

\echo '=== §3b 跨集休眠 FK 现状（重名表族待裁定面，登记非缺陷判定）==='
SELECT 'ecos_cognitive.ecos_wm_goal（FK 锚，0 行空壳）' AS item, count(*) AS n FROM ecos_cognitive.ecos_wm_goal
UNION ALL SELECT 'ecos_control.ecos_wm_goal（真实数据副本）', count(*) FROM ecos_control.ecos_wm_goal
UNION ALL SELECT 'ecos_biz_project.goal_id 非空行（期望 0=休眠）', count(*) FROM ecos_cognitive.ecos_biz_project WHERE goal_id IS NOT NULL
ORDER BY 1;
