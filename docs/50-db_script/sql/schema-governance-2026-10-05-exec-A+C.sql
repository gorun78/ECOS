-- ECOS Schema 彻底治理 · Batch A+C · 2026-10-05
-- 49 引擎侧 0 行幻影 home DROP + 1 control 侧 stale DROP (td_data_category, home 保 5 行)
-- 前置: RECHECK A (49 home 全 0 行) PASS · RECHECK B (td_data_category ctrl=0/home=5) PASS
-- 已排除: lineage_node/lineage_edge (Batch B 结构分歧, 单独决策) · td_data_category home 侧 (保 5 行)
-- 单事务 · ON_ERROR_STOP · 末尾 count 断言 423 对象
\set ON_ERROR_STOP on
BEGIN;

-- ========== ecos_ai (8) ==========
DROP TABLE IF EXISTS ecos_ai.ecos_agent;
DROP TABLE IF EXISTS ecos_ai.ecos_agent_registry;
DROP TABLE IF EXISTS ecos_ai.ecos_decision_case;
DROP TABLE IF EXISTS ecos_ai.ecos_mission;
DROP TABLE IF EXISTS ecos_ai.ecos_mission_task;
DROP TABLE IF EXISTS ecos_ai.ecos_tool_definition;
DROP TABLE IF EXISTS ecos_ai.sys_agent_call_log;
DROP TABLE IF EXISTS ecos_ai.sys_agent_profile;

-- ========== ecos_cognitive (11) ==========
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

-- ========== ecos_data (12, 除 lineage 2 + td_data_category home) ==========
DROP TABLE IF EXISTS ecos_data.ecos_dq_execution_result;
DROP TABLE IF EXISTS ecos_data.ecos_dq_issue;
DROP TABLE IF EXISTS ecos_data.ecos_dq_rule;
DROP TABLE IF EXISTS ecos_data.ecos_pipeline_definition;
DROP TABLE IF EXISTS ecos_data.ecos_pipeline_execution;
DROP TABLE IF EXISTS ecos_data.ecos_pipeline_node;
DROP TABLE IF EXISTS ecos_data.ecos_query_history;
DROP TABLE IF EXISTS ecos_data.ecos_query_template;
DROP TABLE IF EXISTS ecos_data.td_catalog_item;
DROP TABLE IF EXISTS ecos_data.td_data_field;
DROP TABLE IF EXISTS ecos_data.td_data_resource;
DROP TABLE IF EXISTS ecos_data.td_datasource;

-- ========== ecos_knowledge (6) ==========
DROP TABLE IF EXISTS ecos_knowledge.ecos_glossary_term;
DROP TABLE IF EXISTS ecos_knowledge.ecos_knowledge_document;
DROP TABLE IF EXISTS ecos_knowledge.ecos_knowledge_graph_edge;
DROP TABLE IF EXISTS ecos_knowledge.ecos_knowledge_graph_node;
DROP TABLE IF EXISTS ecos_knowledge.ecos_marketplace_access_request;
DROP TABLE IF EXISTS ecos_knowledge.ecos_marketplace_asset;

-- ========== ecos_ontology (12) ==========
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

-- ========== ecos_control 侧 1 stale (home 保 5 行) ==========
DROP TABLE IF EXISTS ecos_control.td_data_category;

-- ========== 断言: 全库对象 = 473 - 50 = 423 ==========
DO $$
DECLARE n BIGINT;
BEGIN
  SELECT count(*) INTO n FROM pg_class c JOIN pg_namespace ns ON ns.oid=c.relnamespace
   WHERE c.relkind IN ('r','v','p') AND ns.nspname NOT IN ('pg_catalog','information_schema');
  IF n <> 423 THEN
    RAISE EXCEPTION 'ASSERT object count: expected 423, got %', n;
  END IF;
  RAISE NOTICE 'ASSERT PASS: 423 objects retained';
END $$;

COMMIT;
