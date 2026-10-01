-- ============================================================
-- v47_reconverge_public.sql
-- 目的: 本机空库上 V47 "搬表" 全部生效, 造成与生产库 (V122 实证的
--       "public 为准、alias 为 ghost") 反向的 split brain。
--       本脚本按 V47 搬表清单 (76) 逆搬回 public, 排除 V126 已
--       DROP 的 8 张 ghost (ecos_ontology 6 + ecos_domain +
--       ecos_business_glossary) = 68 张。
-- 依据:
--   1) 生产事实 (V122 头注): 全部业务数据在 public, 无前缀 SQL
--      回退命中 public
--   2) 代码显式前缀引用核查 (2026-09-14): 命中表仅
--      ecos_knowledge.*(V51+ 原生建在 kbus, 不在搬回清单) /
--      ecos_dq.* (V111+, 不在清单) / outbox/saga/agent_memory
--      (V48/49/50 建在各自架构, 不在清单) → 68 张逆搬零破坏
-- 保留不动: 13 个 schema (V119+/kb/dq/saga 仍在其中建表)、
--       outbox_event ×10 (每 schema 一份, V48 设计)、
--       saga_instance (ecos_config)、agent_memory (ecos_agent)、
--       ecos_wm_* 中不在 V47 清单的成员 (如 ecos_wm_goal, V52 原生建)
-- 幂等: ALTER TABLE IF EXISTS, 重复执行无副作用
-- 回滚: 存在性确认后执行 V47__ecos_schema_isolation.sql 的搬表段
--       (08:20-127) 即可还原
-- 来源: 全栈运维 | 日期: 2026-09-14 | 授权: 用户指令 A
-- ============================================================

BEGIN;

-- ecos_identity (14)
ALTER TABLE IF EXISTS ecos_identity.td_user SET SCHEMA public;
ALTER TABLE IF EXISTS ecos_identity.td_role SET SCHEMA public;
ALTER TABLE IF EXISTS ecos_identity.td_permission SET SCHEMA public;
ALTER TABLE IF EXISTS ecos_identity.td_user_role SET SCHEMA public;
ALTER TABLE IF EXISTS ecos_identity.td_role_permission SET SCHEMA public;
ALTER TABLE IF EXISTS ecos_identity.td_organization SET SCHEMA public;
ALTER TABLE IF EXISTS ecos_identity.td_user_organization SET SCHEMA public;
ALTER TABLE IF EXISTS ecos_identity.td_org_permission SET SCHEMA public;
ALTER TABLE IF EXISTS ecos_identity.td_config SET SCHEMA public;
ALTER TABLE IF EXISTS ecos_identity.td_user_security_profile SET SCHEMA public;
ALTER TABLE IF EXISTS ecos_identity.td_role_security_profile SET SCHEMA public;
ALTER TABLE IF EXISTS ecos_identity.td_abac_policy SET SCHEMA public;
ALTER TABLE IF EXISTS ecos_identity.td_audit_log SET SCHEMA public;
ALTER TABLE IF EXISTS ecos_identity.users SET SCHEMA public;

-- ecos_catalog (4)
ALTER TABLE IF EXISTS ecos_catalog.td_datasource SET SCHEMA public;
ALTER TABLE IF EXISTS ecos_catalog.td_data_resource SET SCHEMA public;
ALTER TABLE IF EXISTS ecos_catalog.td_data_field SET SCHEMA public;
ALTER TABLE IF EXISTS ecos_catalog.td_catalog_item SET SCHEMA public;

-- ecos_ontology (0, 8 张已由 V126 DROP, 无需搬回; 名下现有 17 张活表保留)

-- ecos_object (8)
ALTER TABLE IF EXISTS ecos_object.ecos_object_state_machine SET SCHEMA public;
ALTER TABLE IF EXISTS ecos_object.ecos_object_timeline SET SCHEMA public;
ALTER TABLE IF EXISTS ecos_object.ecos_object_version SET SCHEMA public;
ALTER TABLE IF EXISTS ecos_object.ecos_object_relationship SET SCHEMA public;
ALTER TABLE IF EXISTS ecos_object.ecos_object_attachment SET SCHEMA public;
ALTER TABLE IF EXISTS ecos_object.ecos_object_links SET SCHEMA public;
ALTER TABLE IF EXISTS ecos_object.ecos_object_data SET SCHEMA public;
ALTER TABLE IF EXISTS ecos_object.ecos_object_relation SET SCHEMA public;

-- ecos_workflow (6)
ALTER TABLE IF EXISTS ecos_workflow.ecos_workflow SET SCHEMA public;
ALTER TABLE IF EXISTS ecos_workflow.ecos_workflow_instance SET SCHEMA public;
ALTER TABLE IF EXISTS ecos_workflow.ecos_workflow_task SET SCHEMA public;
ALTER TABLE IF EXISTS ecos_workflow.ecos_workflow_approval SET SCHEMA public;
ALTER TABLE IF EXISTS ecos_workflow.ecos_workflow_log SET SCHEMA public;
ALTER TABLE IF EXISTS ecos_workflow.ecos_task SET SCHEMA public;

-- ecos_rule (4)
ALTER TABLE IF EXISTS ecos_rule.ecos_dq_rule SET SCHEMA public;
ALTER TABLE IF EXISTS ecos_rule.ecos_dq_issue SET SCHEMA public;
ALTER TABLE IF EXISTS ecos_rule.ecos_dq_execution_result SET SCHEMA public;
ALTER TABLE IF EXISTS ecos_rule.ecos_cognitive_rule SET SCHEMA public;

-- ecos_agent (5)
-- ecos_tool_definition 不搬回: agent-service (ai-engine-impl 传递依赖) 的
-- ToolRouterServiceImpl 运行时 SQL 显式带 `ecos_agent.` 前缀, 搬到
-- public 会导致运行期 relation-not-found (启动期不报警, 运行才炸); 生产
-- fact 上 identity-service 亦不在 fat-jar 也就不冲突。
ALTER TABLE IF EXISTS ecos_agent.ecos_agent SET SCHEMA public;
ALTER TABLE IF EXISTS ecos_agent.ecos_agent_registry SET SCHEMA public;
ALTER TABLE IF EXISTS ecos_agent.ecos_mission SET SCHEMA public;
ALTER TABLE IF EXISTS ecos_agent.ecos_mission_task SET SCHEMA public;
ALTER TABLE IF EXISTS ecos_agent.ecos_decision_case SET SCHEMA public;

-- ecos_knowledge (4)
ALTER TABLE IF EXISTS ecos_knowledge.ecos_knowledge_document SET SCHEMA public;
ALTER TABLE IF EXISTS ecos_knowledge.ecos_glossary_term SET SCHEMA public;
ALTER TABLE IF EXISTS ecos_knowledge.ecos_marketplace_asset SET SCHEMA public;
ALTER TABLE IF EXISTS ecos_knowledge.ecos_marketplace_access_request SET SCHEMA public;
-- 注: outbox 及 kb 家族 (V51+ 原生建在 ecos_knowledge) 保留

-- ecos_mission (11, 全部搬回; V47 将 wm 家族移入, 生产 fact 全在 public)
ALTER TABLE IF EXISTS ecos_mission.ecos_wm_goal SET SCHEMA public;
ALTER TABLE IF EXISTS ecos_mission.ecos_wm_causal_link SET SCHEMA public;
ALTER TABLE IF EXISTS ecos_mission.ecos_wm_scenario SET SCHEMA public;
ALTER TABLE IF EXISTS ecos_mission.ecos_wm_goal_log SET SCHEMA public;
ALTER TABLE IF EXISTS ecos_mission.ecos_goal_tracking SET SCHEMA public;
ALTER TABLE IF EXISTS ecos_mission.ecos_world_scenarios SET SCHEMA public;
ALTER TABLE IF EXISTS ecos_mission.ecos_biz_department SET SCHEMA public;
ALTER TABLE IF EXISTS ecos_mission.ecos_biz_project SET SCHEMA public;
ALTER TABLE IF EXISTS ecos_mission.ecos_biz_contract SET SCHEMA public;
ALTER TABLE IF EXISTS ecos_mission.ecos_biz_metric SET SCHEMA public;
ALTER TABLE IF EXISTS ecos_mission.ecos_biz_target SET SCHEMA public;

-- ecos_audit (2)
ALTER TABLE IF EXISTS ecos_audit.ecos_spans SET SCHEMA public;
ALTER TABLE IF EXISTS ecos_audit.ecos_token_usage SET SCHEMA public;

-- ecos_config (2)
ALTER TABLE IF EXISTS ecos_config.sys_dict SET SCHEMA public;
ALTER TABLE IF EXISTS ecos_config.sys_config SET SCHEMA public;
-- 注: outbox_event / saga_instance (V48/49 原生建, 不在 V47 move 清单) 保留原地

-- ecos_pipeline (4)
ALTER TABLE IF EXISTS ecos_pipeline.ecos_pipeline_node SET SCHEMA public;
ALTER TABLE IF EXISTS ecos_pipeline.ecos_pipeline_edge SET SCHEMA public;
ALTER TABLE IF EXISTS ecos_pipeline.ecos_pipeline_definition SET SCHEMA public;
ALTER TABLE IF EXISTS ecos_pipeline.ecos_pipeline_execution SET SCHEMA public;

-- 租户三件套补齐 (V47 L113-115, 此前脚本遗漏)
ALTER TABLE IF EXISTS ecos_identity.ecos_tenant SET SCHEMA public;
ALTER TABLE IF EXISTS ecos_identity.ecos_tenant_quota SET SCHEMA public;
ALTER TABLE IF EXISTS ecos_identity.ecos_tenant_usage SET SCHEMA public;

COMMIT;

-- 手动补建表的定位修正: extraction_drafts (KnowledgeExtractionService @PostConstruct 建表在
-- ecos_identity), 生产 fact 在 public, 一并回迁
ALTER TABLE IF EXISTS ecos_identity.extraction_drafts SET SCHEMA public;

-- 期望值: sys_man 库 database 级 search_path 由 V47 L130 设置, 业务
-- 表回归 public 后别名架构不再是唯一命中源 (先命中 public);
-- database RESET 仅改变新会话默认, 不回收已有表, 执行于业务方外,
-- 暂不执行, 由人工复核后再定 (保留 RESTORE 杠杆: SET search_path 原值)。
