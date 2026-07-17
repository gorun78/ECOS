-- ECOS Schema Isolation: Migrate tables from public schema to domain-specific schemas
-- Sprint 1.1: Database Per Service alignment with design doc 06

-- Step 1: Create domain schemas
CREATE SCHEMA IF NOT EXISTS ecos_identity;
CREATE SCHEMA IF NOT EXISTS ecos_catalog;
CREATE SCHEMA IF NOT EXISTS ecos_ontology;
CREATE SCHEMA IF NOT EXISTS ecos_object;
CREATE SCHEMA IF NOT EXISTS ecos_workflow;
CREATE SCHEMA IF NOT EXISTS ecos_rule;
CREATE SCHEMA IF NOT EXISTS ecos_agent;
CREATE SCHEMA IF NOT EXISTS ecos_knowledge;
CREATE SCHEMA IF NOT EXISTS ecos_mission;
CREATE SCHEMA IF NOT EXISTS ecos_audit;
CREATE SCHEMA IF NOT EXISTS ecos_config;
CREATE SCHEMA IF NOT EXISTS ecos_pipeline;

-- Step 2: Migrate tables to their domain schemas
-- ecos_identity: IAM, Organization, Tenant, Security profiles
ALTER TABLE IF EXISTS td_user SET SCHEMA ecos_identity;
ALTER TABLE IF EXISTS td_role SET SCHEMA ecos_identity;
ALTER TABLE IF EXISTS td_permission SET SCHEMA ecos_identity;
ALTER TABLE IF EXISTS td_user_role SET SCHEMA ecos_identity;
ALTER TABLE IF EXISTS td_role_permission SET SCHEMA ecos_identity;
ALTER TABLE IF EXISTS td_organization SET SCHEMA ecos_identity;
ALTER TABLE IF EXISTS td_user_organization SET SCHEMA ecos_identity;
ALTER TABLE IF EXISTS td_org_permission SET SCHEMA ecos_identity;
ALTER TABLE IF EXISTS td_config SET SCHEMA ecos_identity;
ALTER TABLE IF EXISTS td_user_security_profile SET SCHEMA ecos_identity;
ALTER TABLE IF EXISTS td_role_security_profile SET SCHEMA ecos_identity;
ALTER TABLE IF EXISTS td_abac_policy SET SCHEMA ecos_identity;
ALTER TABLE IF EXISTS td_audit_log SET SCHEMA ecos_identity;
ALTER TABLE IF EXISTS users SET SCHEMA ecos_identity;

-- ecos_catalog: Data sources, metadata, catalog
ALTER TABLE IF EXISTS td_datasource SET SCHEMA ecos_catalog;
ALTER TABLE IF EXISTS td_data_resource SET SCHEMA ecos_catalog;
ALTER TABLE IF EXISTS td_data_field SET SCHEMA ecos_catalog;
ALTER TABLE IF EXISTS td_catalog_item SET SCHEMA ecos_catalog;

-- ecos_ontology: Ontology definitions, entities, properties, relationships
ALTER TABLE IF EXISTS ecos_ontology_entity SET SCHEMA ecos_ontology;
ALTER TABLE IF EXISTS ecos_ontology_property SET SCHEMA ecos_ontology;
ALTER TABLE IF EXISTS ecos_ontology_relationship SET SCHEMA ecos_ontology;
ALTER TABLE IF EXISTS ecos_ontology_action SET SCHEMA ecos_ontology;
ALTER TABLE IF EXISTS ecos_ontology_rule SET SCHEMA ecos_ontology;
ALTER TABLE IF EXISTS ecos_ontology_version SET SCHEMA ecos_ontology;
ALTER TABLE IF EXISTS ecos_domain SET SCHEMA ecos_ontology;
ALTER TABLE IF EXISTS ecos_business_glossary SET SCHEMA ecos_ontology;

-- ecos_object: Object runtime, state machines, relationships, links
ALTER TABLE IF EXISTS ecos_object_state_machine SET SCHEMA ecos_object;
ALTER TABLE IF EXISTS ecos_object_timeline SET SCHEMA ecos_object;
ALTER TABLE IF EXISTS ecos_object_version SET SCHEMA ecos_object;
ALTER TABLE IF EXISTS ecos_object_relationship SET SCHEMA ecos_object;
ALTER TABLE IF EXISTS ecos_object_attachment SET SCHEMA ecos_object;
ALTER TABLE IF EXISTS ecos_object_links SET SCHEMA ecos_object;
ALTER TABLE IF EXISTS ecos_object_data SET SCHEMA ecos_object;
ALTER TABLE IF EXISTS ecos_object_relation SET SCHEMA ecos_object;

-- ecos_workflow: Workflow definitions, instances, tasks, approvals
ALTER TABLE IF EXISTS ecos_workflow SET SCHEMA ecos_workflow;
ALTER TABLE IF EXISTS ecos_workflow_instance SET SCHEMA ecos_workflow;
ALTER TABLE IF EXISTS ecos_workflow_task SET SCHEMA ecos_workflow;
ALTER TABLE IF EXISTS ecos_workflow_approval SET SCHEMA ecos_workflow;
ALTER TABLE IF EXISTS ecos_workflow_log SET SCHEMA ecos_workflow;

-- ecos_rule: Data quality rules, cognitive rules
ALTER TABLE IF EXISTS ecos_dq_rule SET SCHEMA ecos_rule;
ALTER TABLE IF EXISTS ecos_dq_issue SET SCHEMA ecos_rule;
ALTER TABLE IF EXISTS ecos_dq_execution_result SET SCHEMA ecos_rule;
ALTER TABLE IF EXISTS ecos_cognitive_rule SET SCHEMA ecos_rule;

-- ecos_agent: Agent definitions, registry, missions, tools
ALTER TABLE IF EXISTS ecos_agent SET SCHEMA ecos_agent;
ALTER TABLE IF EXISTS ecos_agent_registry SET SCHEMA ecos_agent;
ALTER TABLE IF EXISTS ecos_mission SET SCHEMA ecos_agent;
ALTER TABLE IF EXISTS ecos_mission_task SET SCHEMA ecos_agent;
ALTER TABLE IF EXISTS ecos_tool_definition SET SCHEMA ecos_agent;
ALTER TABLE IF EXISTS ecos_decision_case SET SCHEMA ecos_agent;

-- ecos_knowledge: Knowledge graph, glossary, documents
ALTER TABLE IF EXISTS ecos_glossary_term SET SCHEMA ecos_knowledge;
ALTER TABLE IF EXISTS ecos_knowledge_document SET SCHEMA ecos_knowledge;

-- ecos_mission: World model, goals, scenarios
ALTER TABLE IF EXISTS ecos_wm_goal SET SCHEMA ecos_mission;
ALTER TABLE IF EXISTS ecos_wm_causal_link SET SCHEMA ecos_mission;
ALTER TABLE IF EXISTS ecos_wm_scenario SET SCHEMA ecos_mission;
ALTER TABLE IF EXISTS ecos_wm_goal_log SET SCHEMA ecos_mission;
ALTER TABLE IF EXISTS ecos_goal_tracking SET SCHEMA ecos_mission;
ALTER TABLE IF EXISTS ecos_world_scenarios SET SCHEMA ecos_mission;

-- ecos_audit: Telemetry, spans, token usage
ALTER TABLE IF EXISTS ecos_spans SET SCHEMA ecos_audit;
ALTER TABLE IF EXISTS ecos_token_usage SET SCHEMA ecos_audit;

-- ecos_config: System configuration, dictionaries
ALTER TABLE IF EXISTS sys_config SET SCHEMA ecos_config;
ALTER TABLE IF EXISTS sys_dict SET SCHEMA ecos_config;

-- ecos_pipeline: Pipeline definitions, nodes, edges, executions
ALTER TABLE IF EXISTS ecos_pipeline_node SET SCHEMA ecos_pipeline;
ALTER TABLE IF EXISTS ecos_pipeline_edge SET SCHEMA ecos_pipeline;
ALTER TABLE IF EXISTS ecos_pipeline_definition SET SCHEMA ecos_pipeline;
ALTER TABLE IF EXISTS ecos_pipeline_execution SET SCHEMA ecos_pipeline;

-- Marketplace stays in ecos_knowledge
ALTER TABLE IF EXISTS ecos_marketplace_asset SET SCHEMA ecos_knowledge;
ALTER TABLE IF EXISTS ecos_marketplace_access_request SET SCHEMA ecos_knowledge;

-- Tenant management stays in ecos_identity
ALTER TABLE IF EXISTS ecos_tenant SET SCHEMA ecos_identity;
ALTER TABLE IF EXISTS ecos_tenant_quota SET SCHEMA ecos_identity;
ALTER TABLE IF EXISTS ecos_tenant_usage SET SCHEMA ecos_identity;

-- Task management stays in ecos_workflow
ALTER TABLE IF EXISTS ecos_task SET SCHEMA ecos_workflow;

-- Demo tables remain in public schema (demo data, not domain tables)

-- Business scenario demo tables → ecos_mission (business scenario seeds)
ALTER TABLE IF EXISTS ecos_biz_department SET SCHEMA ecos_mission;
ALTER TABLE IF EXISTS ecos_biz_project SET SCHEMA ecos_mission;
ALTER TABLE IF EXISTS ecos_biz_contract SET SCHEMA ecos_mission;
ALTER TABLE IF EXISTS ecos_biz_metric SET SCHEMA ecos_mission;
ALTER TABLE IF EXISTS ecos_biz_target SET SCHEMA ecos_mission;

-- Step 3: Set default search_path for the database to include all schemas
ALTER DATABASE sys_man SET search_path TO ecos_identity, ecos_catalog, ecos_ontology, ecos_object, ecos_workflow, ecos_rule, ecos_agent, ecos_knowledge, ecos_mission, ecos_audit, ecos_config, ecos_pipeline, public;
