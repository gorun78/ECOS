-- ============================================================================
-- ECOS Schema Migration: 12-schema → 8-schema
-- Moves tables from legacy 12-schema layout to 7+1 domain schema layout
-- Architecture Constraint: ADD-ONLY — only moves tables, never drops
-- ============================================================================

-- Prerequisites:
-- 1. Run 00_init_database.sql to create 8 target schemas
-- 2. Backup database before executing this migration
-- 3. This script uses ALTER TABLE SET SCHEMA which moves tables atomically

-- ============================================
-- Phase 1: ecos_identity → ecos_sysman + ecos_security
-- ============================================

-- IAM tables → ecos_sysman
ALTER TABLE ecos_identity.td_user SET SCHEMA ecos_sysman;
ALTER TABLE ecos_identity.td_role SET SCHEMA ecos_sysman;
ALTER TABLE ecos_identity.td_permission SET SCHEMA ecos_sysman;
ALTER TABLE ecos_identity.td_user_role SET SCHEMA ecos_sysman;
ALTER TABLE ecos_identity.td_role_permission SET SCHEMA ecos_sysman;
ALTER TABLE ecos_identity.td_organization SET SCHEMA ecos_sysman;
ALTER TABLE ecos_identity.td_user_organization SET SCHEMA ecos_sysman;
ALTER TABLE ecos_identity.td_org_permission SET SCHEMA ecos_sysman;
ALTER TABLE ecos_identity.td_config SET SCHEMA ecos_sysman;
ALTER TABLE ecos_identity.ecos_tenant SET SCHEMA ecos_sysman;
ALTER TABLE ecos_identity.ecos_tenant_quota SET SCHEMA ecos_sysman;
ALTER TABLE ecos_identity.ecos_tenant_usage SET SCHEMA ecos_sysman;
ALTER TABLE ecos_identity.users SET SCHEMA ecos_sysman;

-- Security profile tables → ecos_security
ALTER TABLE ecos_identity.td_user_security_profile SET SCHEMA ecos_security;
ALTER TABLE ecos_identity.td_role_security_profile SET SCHEMA ecos_security;
ALTER TABLE ecos_identity.td_abac_policy SET SCHEMA ecos_security;
ALTER TABLE ecos_identity.td_audit_log SET SCHEMA ecos_security;
ALTER TABLE ecos_identity.outbox_event SET SCHEMA ecos_security;

-- Audit/telemetry tables from ecos_audit → ecos_security
ALTER TABLE ecos_audit.ecos_spans SET SCHEMA ecos_security;
ALTER TABLE ecos_audit.ecos_token_usage SET SCHEMA ecos_security;
ALTER TABLE ecos_audit.outbox_event SET SCHEMA ecos_security;

-- ============================================
-- Phase 2: ecos_catalog → ecos_data
-- ============================================
ALTER TABLE ecos_catalog.td_datasource SET SCHEMA ecos_data;
ALTER TABLE ecos_catalog.td_data_resource SET SCHEMA ecos_data;
ALTER TABLE ecos_catalog.td_data_field SET SCHEMA ecos_data;
ALTER TABLE ecos_catalog.td_catalog_item SET SCHEMA ecos_data;
ALTER TABLE ecos_catalog.outbox_event SET SCHEMA ecos_data;

-- ============================================
-- Phase 3: ecos_config → ecos_sysman
-- ============================================
ALTER TABLE ecos_config.sys_config SET SCHEMA ecos_sysman;
ALTER TABLE ecos_config.sys_dict SET SCHEMA ecos_sysman;

-- ============================================
-- Phase 4: ecos_pipeline → ecos_data
-- ============================================
ALTER TABLE ecos_pipeline.ecos_pipeline_definition SET SCHEMA ecos_data;
ALTER TABLE ecos_pipeline.ecos_pipeline_execution SET SCHEMA ecos_data;
ALTER TABLE ecos_pipeline.ecos_pipeline_node SET SCHEMA ecos_data;
ALTER TABLE ecos_pipeline.ecos_pipeline_edge SET SCHEMA ecos_data;
ALTER TABLE ecos_pipeline.outbox_event SET SCHEMA ecos_data;

-- ============================================
-- Phase 5: ecos_rule → ecos_data (DQ) + ecos_data (cognitive)
-- ============================================
ALTER TABLE ecos_rule.ecos_dq_rule SET SCHEMA ecos_data;
ALTER TABLE ecos_rule.ecos_dq_issue SET SCHEMA ecos_data;
ALTER TABLE ecos_rule.ecos_dq_execution_result SET SCHEMA ecos_data;
ALTER TABLE ecos_rule.ecos_cognitive_rule SET SCHEMA ecos_data;
ALTER TABLE ecos_rule.outbox_event SET SCHEMA ecos_data;

-- ============================================
-- Phase 6: ecos_ontology + ecos_object + ecos_workflow → ecos_ontology
-- ============================================
ALTER TABLE ecos_ontology.ecos_ontology_entity SET SCHEMA ecos_ontology;
ALTER TABLE ecos_ontology.ecos_ontology_property SET SCHEMA ecos_ontology;
ALTER TABLE ecos_ontology.ecos_ontology_relationship SET SCHEMA ecos_ontology;
ALTER TABLE ecos_ontology.ecos_ontology_action SET SCHEMA ecos_ontology;
ALTER TABLE ecos_ontology.ecos_ontology_rule SET SCHEMA ecos_ontology;
ALTER TABLE ecos_ontology.ecos_ontology_version SET SCHEMA ecos_ontology;
ALTER TABLE ecos_ontology.ecos_domain SET SCHEMA ecos_ontology;
ALTER TABLE ecos_ontology.ecos_business_glossary SET SCHEMA ecos_ontology;
ALTER TABLE ecos_ontology.outbox_event SET SCHEMA ecos_ontology;

-- Object runtime tables
ALTER TABLE ecos_object.ecos_object_state_machine SET SCHEMA ecos_ontology;
ALTER TABLE ecos_object.ecos_object_timeline SET SCHEMA ecos_ontology;
ALTER TABLE ecos_object.ecos_object_version SET SCHEMA ecos_ontology;
ALTER TABLE ecos_object.ecos_object_relationship SET SCHEMA ecos_ontology;
ALTER TABLE ecos_object.ecos_object_attachment SET SCHEMA ecos_ontology;
ALTER TABLE ecos_object.ecos_object_links SET SCHEMA ecos_ontology;
ALTER TABLE ecos_object.ecos_object_data SET SCHEMA ecos_ontology;
ALTER TABLE ecos_object.ecos_object_relation SET SCHEMA ecos_ontology;
ALTER TABLE ecos_object.outbox_event SET SCHEMA ecos_ontology;

-- Workflow tables
ALTER TABLE ecos_workflow.ecos_workflow SET SCHEMA ecos_ontology;
ALTER TABLE ecos_workflow.ecos_workflow_instance SET SCHEMA ecos_ontology;
ALTER TABLE ecos_workflow.ecos_workflow_task SET SCHEMA ecos_ontology;
ALTER TABLE ecos_workflow.ecos_workflow_approval SET SCHEMA ecos_ontology;
ALTER TABLE ecos_workflow.ecos_workflow_log SET SCHEMA ecos_ontology;
ALTER TABLE ecos_workflow.ecos_task SET SCHEMA ecos_data;
ALTER TABLE ecos_workflow.outbox_event SET SCHEMA ecos_ontology;

-- ============================================
-- Phase 7: ecos_knowledge → ecos_knowledge
-- ============================================
ALTER TABLE ecos_knowledge.ecos_glossary_term SET SCHEMA ecos_knowledge;
ALTER TABLE ecos_knowledge.ecos_knowledge_document SET SCHEMA ecos_knowledge;
ALTER TABLE ecos_knowledge.knowledge_article SET SCHEMA ecos_knowledge;
ALTER TABLE ecos_knowledge.knowledge_embedding SET SCHEMA ecos_knowledge;
ALTER TABLE ecos_knowledge.graph_node SET SCHEMA ecos_knowledge;
ALTER TABLE ecos_knowledge.graph_edge SET SCHEMA ecos_knowledge;
ALTER TABLE ecos_knowledge.graph_subgraph SET SCHEMA ecos_knowledge;
ALTER TABLE ecos_knowledge.expert_rule SET SCHEMA ecos_knowledge;
ALTER TABLE ecos_knowledge.ecos_knowledge_graph_node SET SCHEMA ecos_knowledge;
ALTER TABLE ecos_knowledge.ecos_knowledge_graph_edge SET SCHEMA ecos_knowledge;
ALTER TABLE ecos_knowledge.ecos_marketplace_asset SET SCHEMA ecos_knowledge;
ALTER TABLE ecos_knowledge.ecos_marketplace_access_request SET SCHEMA ecos_knowledge;
ALTER TABLE ecos_knowledge.outbox_event SET SCHEMA ecos_infra;

-- ============================================
-- Phase 8: ecos_agent → ecos_ai + ecos_cognitive
-- ============================================

-- AI tables
ALTER TABLE ecos_agent.ecos_agent SET SCHEMA ecos_ai;
ALTER TABLE ecos_agent.ecos_agent_registry SET SCHEMA ecos_ai;
ALTER TABLE ecos_agent.agent_definition SET SCHEMA ecos_ai;
ALTER TABLE ecos_agent.agent_execution SET SCHEMA ecos_ai;
ALTER TABLE ecos_agent.agent_execution_step SET SCHEMA ecos_ai;
ALTER TABLE ecos_agent.agent_memory SET SCHEMA ecos_ai;
ALTER TABLE ecos_agent.agent_cost SET SCHEMA ecos_ai;
ALTER TABLE ecos_agent.agent_evaluation SET SCHEMA ecos_ai;
ALTER TABLE ecos_agent.agent_governance_policy SET SCHEMA ecos_ai;
ALTER TABLE ecos_agent.agent_approval SET SCHEMA ecos_ai;
ALTER TABLE ecos_agent.ecos_tool_definition SET SCHEMA ecos_ai;
ALTER TABLE ecos_agent.ecos_decision_case SET SCHEMA ecos_ai;
ALTER TABLE ecos_agent.sys_agent_profile SET SCHEMA ecos_ai;
ALTER TABLE ecos_agent.sys_agent_call_log SET SCHEMA ecos_ai;
ALTER TABLE ecos_agent.outbox_event SET SCHEMA ecos_ai;

-- ============================================
-- Phase 9: ecos_mission → ecos_cognitive
-- ============================================
ALTER TABLE ecos_mission.ecos_wm_goal SET SCHEMA ecos_cognitive;
ALTER TABLE ecos_mission.ecos_wm_causal_link SET SCHEMA ecos_cognitive;
ALTER TABLE ecos_mission.ecos_wm_scenario SET SCHEMA ecos_cognitive;
ALTER TABLE ecos_mission.ecos_wm_goal_log SET SCHEMA ecos_cognitive;
ALTER TABLE ecos_mission.ecos_goal_tracking SET SCHEMA ecos_cognitive;
ALTER TABLE ecos_mission.ecos_world_scenarios SET SCHEMA ecos_cognitive;
ALTER TABLE ecos_mission.ecos_biz_department SET SCHEMA ecos_cognitive;
ALTER TABLE ecos_mission.ecos_biz_project SET SCHEMA ecos_cognitive;
ALTER TABLE ecos_mission.ecos_biz_contract SET SCHEMA ecos_cognitive;
ALTER TABLE ecos_mission.ecos_biz_metric SET SCHEMA ecos_cognitive;
ALTER TABLE ecos_mission.ecos_biz_target SET SCHEMA ecos_cognitive;
ALTER TABLE ecos_mission.ecos_mission SET SCHEMA ecos_ai;
ALTER TABLE ecos_mission.ecos_mission_task SET SCHEMA ecos_ai;
ALTER TABLE ecos_mission.outbox_event SET SCHEMA ecos_cognitive;

-- ============================================
-- Phase 10: Saga → ecos_infra
-- ============================================
ALTER TABLE ecos_config.saga_instance SET SCHEMA ecos_infra;

-- ============================================
-- Phase 11: V52/V53 new tables (already in public or wrong schema)
-- ============================================
-- These tables were created after V47, need manual placement:
-- entity_definition, relationship_definition, metric_definition,
-- action_definition, policy_definition, event_definition → ecos_ontology
-- world_state, world_snapshot, scenario, simulation, simulation_result,
-- forecast, optimization_job, strategy_recommendation, causal_edge → ecos_ai

-- ============================================
-- Phase 12: Update search_path
-- ============================================
ALTER DATABASE sys_man SET search_path TO
    ecos_sysman, ecos_security, ecos_data, ecos_ontology,
    ecos_knowledge, ecos_ai, ecos_cognitive, ecos_infra, public;

-- ============================================
-- Phase 13: Drop empty legacy schemas (optional, uncomment after verification)
-- ============================================
-- DROP SCHEMA IF EXISTS ecos_identity;
-- DROP SCHEMA IF EXISTS ecos_catalog;
-- DROP SCHEMA IF EXISTS ecos_config;
-- DROP SCHEMA IF EXISTS ecos_pipeline;
-- DROP SCHEMA IF EXISTS ecos_rule;
-- DROP SCHEMA IF EXISTS ecos_object;
-- DROP SCHEMA IF EXISTS ecos_workflow;
-- DROP SCHEMA IF EXISTS ecos_audit;
-- DROP SCHEMA IF EXISTS ecos_agent;
-- DROP SCHEMA IF EXISTS ecos_mission;
