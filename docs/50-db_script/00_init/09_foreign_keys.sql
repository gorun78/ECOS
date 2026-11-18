-- ============================================================================
-- 09_foreign_keys.sql — 全库外键（延后统一施加）
-- ============================================================================
-- 由 pg_dump --schema-only 从真实库 sys_man 反向导出后清洗装配（2026-11-17，V349 清理后重导）。
-- 共 36 条（含 public 挂起表 0 条）。必须在 01~08 与 11 全部建表后执行：存在跨 schema 外键，内联会因文件顺序失败。术语关系外键改指权威副本 0 处。
--
-- 清洗口径:
--   · 剥离 pg_dump 噪音（\restrict / SET / set_config / CREATE SCHEMA / OWNER）
--   · 分区表只保父表 + DEFAULT 分区，60 张按月日期子表由运行时创建
--   · 冗余 ::character varying cast 去除（MC03 口径）
--   · 外键全部延后到 09_foreign_keys.sql（跨 schema 依赖，禁按文件顺序内联）
--   · public 归位表已由 V345/V346 实库搬入归属 schema，本册直接反映搬后落点
--   · 平行误会表 users 已由 V347 统一登录权威到 td_user、V349 实库清退，本册不含
--
-- 前置: 空库 + 已执行 00_create_schemas.sql。本文件不含 IF NOT EXISTS，
--       结构不符即报错（刻意 fail-loud，禁静默跳过）。
-- ============================================================================

-- ecos_control.dict_column dict_column_table_id_fkey
ALTER TABLE ONLY ecos_control.dict_column
    ADD CONSTRAINT dict_column_table_id_fkey FOREIGN KEY (table_id) REFERENCES ecos_control.dict_table(id) ON DELETE CASCADE;

--

-- ecos_control.ecos_glossary_term ecos_glossary_term_domain_id_fkey
ALTER TABLE ONLY ecos_control.ecos_glossary_term
    ADD CONSTRAINT ecos_glossary_term_domain_id_fkey FOREIGN KEY (domain_id) REFERENCES ecos_ontology.ecos_domain(id);

--

-- ecos_control.ecos_goal_tracking ecos_goal_tracking_goal_id_fkey
ALTER TABLE ONLY ecos_control.ecos_goal_tracking
    ADD CONSTRAINT ecos_goal_tracking_goal_id_fkey FOREIGN KEY (goal_id) REFERENCES ecos_control.ecos_wm_goal(id) ON DELETE CASCADE;

--

-- ecos_control.ecos_marketplace_access_request ecos_marketplace_access_request_asset_id_fkey
ALTER TABLE ONLY ecos_control.ecos_marketplace_access_request
    ADD CONSTRAINT ecos_marketplace_access_request_asset_id_fkey FOREIGN KEY (asset_id) REFERENCES ecos_control.ecos_marketplace_asset(id);

--

-- ecos_control.ecos_ontology_entity ecos_ontology_entity_domain_id_fkey
ALTER TABLE ONLY ecos_control.ecos_ontology_entity
    ADD CONSTRAINT ecos_ontology_entity_domain_id_fkey FOREIGN KEY (domain_id) REFERENCES ecos_ontology.ecos_domain(id);

--

-- ecos_control.ecos_wm_causal_link ecos_wm_causal_link_source_goal_id_fkey
ALTER TABLE ONLY ecos_control.ecos_wm_causal_link
    ADD CONSTRAINT ecos_wm_causal_link_source_goal_id_fkey FOREIGN KEY (source_goal_id) REFERENCES ecos_control.ecos_wm_goal(id) ON DELETE CASCADE;

--

-- ecos_control.ecos_wm_causal_link ecos_wm_causal_link_target_goal_id_fkey
ALTER TABLE ONLY ecos_control.ecos_wm_causal_link
    ADD CONSTRAINT ecos_wm_causal_link_target_goal_id_fkey FOREIGN KEY (target_goal_id) REFERENCES ecos_control.ecos_wm_goal(id) ON DELETE CASCADE;

--

-- ecos_control.ecos_wm_goal ecos_wm_goal_domain_id_fkey
ALTER TABLE ONLY ecos_control.ecos_wm_goal
    ADD CONSTRAINT ecos_wm_goal_domain_id_fkey FOREIGN KEY (domain_id) REFERENCES ecos_ontology.ecos_domain(id);

--

-- ecos_control.ecos_wm_goal_log ecos_wm_goal_log_goal_id_fkey
ALTER TABLE ONLY ecos_control.ecos_wm_goal_log
    ADD CONSTRAINT ecos_wm_goal_log_goal_id_fkey FOREIGN KEY (goal_id) REFERENCES ecos_control.ecos_wm_goal(id) ON DELETE CASCADE;

--

-- ecos_control.ecos_wm_goal ecos_wm_goal_parent_id_fkey
ALTER TABLE ONLY ecos_control.ecos_wm_goal
    ADD CONSTRAINT ecos_wm_goal_parent_id_fkey FOREIGN KEY (parent_id) REFERENCES ecos_control.ecos_wm_goal(id) ON DELETE SET NULL;

--

-- ecos_control.ecos_dq_issue fk_dq_issue_rule
ALTER TABLE ONLY ecos_control.ecos_dq_issue
    ADD CONSTRAINT fk_dq_issue_rule FOREIGN KEY (rule_id) REFERENCES ecos_control.ecos_dq_rule(id) ON DELETE CASCADE;

--

-- ecos_control.td_org_permission fk_sysman_orgperm_org
ALTER TABLE ONLY ecos_control.td_org_permission
    ADD CONSTRAINT fk_sysman_orgperm_org FOREIGN KEY (org_id) REFERENCES ecos_control.td_organization(org_id);

--

-- ecos_control.td_role_permission fk_sysman_role_perm_perm
ALTER TABLE ONLY ecos_control.td_role_permission
    ADD CONSTRAINT fk_sysman_role_perm_perm FOREIGN KEY (permission_id) REFERENCES ecos_control.td_permission(permission_id);

--

-- ecos_control.td_role_permission fk_sysman_role_perm_role
ALTER TABLE ONLY ecos_control.td_role_permission
    ADD CONSTRAINT fk_sysman_role_perm_role FOREIGN KEY (role_id) REFERENCES ecos_control.td_role(role_id);

--

-- ecos_control.td_user_role fk_sysman_user_role_role
ALTER TABLE ONLY ecos_control.td_user_role
    ADD CONSTRAINT fk_sysman_user_role_role FOREIGN KEY (role_id) REFERENCES ecos_control.td_role(role_id);

--

-- ecos_control.td_user_role fk_sysman_user_role_user
ALTER TABLE ONLY ecos_control.td_user_role
    ADD CONSTRAINT fk_sysman_user_role_user FOREIGN KEY (user_id) REFERENCES ecos_control.td_user(user_id);

--

-- ecos_control.sys_agent_message sys_agent_message_session_id_fkey
ALTER TABLE ONLY ecos_control.sys_agent_message
    ADD CONSTRAINT sys_agent_message_session_id_fkey FOREIGN KEY (session_id) REFERENCES ecos_control.sys_agent_session(id);

--
-- PostgreSQL database dump complete
--

-- ecos_data.ecos_pipeline_execution fk_data_pexec_def
ALTER TABLE ONLY ecos_data.ecos_pipeline_execution
    ADD CONSTRAINT fk_data_pexec_def FOREIGN KEY (pipeline_id) REFERENCES ecos_data.ecos_pipeline_definition(id);

--
-- PostgreSQL database dump complete
--

-- ecos_ontology.ecos_glossary_term_relation fk_glossary_relation_from
ALTER TABLE ONLY ecos_ontology.ecos_glossary_term_relation
    ADD CONSTRAINT fk_glossary_relation_from FOREIGN KEY (from_term_id) REFERENCES ecos_control.ecos_glossary_term(id) ON DELETE CASCADE;

--

-- ecos_ontology.ecos_glossary_term_relation fk_glossary_relation_to
ALTER TABLE ONLY ecos_ontology.ecos_glossary_term_relation
    ADD CONSTRAINT fk_glossary_relation_to FOREIGN KEY (to_term_id) REFERENCES ecos_control.ecos_glossary_term(id) ON DELETE CASCADE;

--
-- PostgreSQL database dump complete
--

-- ecos_knowledge.graph_edge fk_kb_gedge_src
ALTER TABLE ONLY ecos_knowledge.graph_edge
    ADD CONSTRAINT fk_kb_gedge_src FOREIGN KEY (source_id) REFERENCES ecos_knowledge.graph_node(id);

--

-- ecos_knowledge.graph_edge fk_kb_gedge_tgt
ALTER TABLE ONLY ecos_knowledge.graph_edge
    ADD CONSTRAINT fk_kb_gedge_tgt FOREIGN KEY (target_id) REFERENCES ecos_knowledge.graph_node(id);

--
-- PostgreSQL database dump complete
--

-- ecos_ai.agent_execution fk_ai_exec_agent
ALTER TABLE ONLY ecos_ai.agent_execution
    ADD CONSTRAINT fk_ai_exec_agent FOREIGN KEY (agent_id) REFERENCES ecos_ai.agent_definition(id);

--

-- ecos_ai.ecos_mission_task fk_ai_mtask_mission
ALTER TABLE ONLY ecos_ai.ecos_mission_task
    ADD CONSTRAINT fk_ai_mtask_mission FOREIGN KEY (mission_id) REFERENCES ecos_ai.ecos_mission(id);

--

-- ecos_ai.simulation fk_ai_sim_scenario
ALTER TABLE ONLY ecos_ai.simulation
    ADD CONSTRAINT fk_ai_sim_scenario FOREIGN KEY (scenario_id) REFERENCES ecos_ai.scenario(id);

--

-- ecos_ai.simulation_result fk_ai_simres_sim
ALTER TABLE ONLY ecos_ai.simulation_result
    ADD CONSTRAINT fk_ai_simres_sim FOREIGN KEY (simulation_id) REFERENCES ecos_ai.simulation(id);

--

-- ecos_ai.agent_execution_step fk_ai_step_exec
ALTER TABLE ONLY ecos_ai.agent_execution_step
    ADD CONSTRAINT fk_ai_step_exec FOREIGN KEY (execution_id) REFERENCES ecos_ai.agent_execution(id);

--
-- PostgreSQL database dump complete
--

-- ecos_cognitive.ecos_wm_causal_link fk_cog_causal_src
ALTER TABLE ONLY ecos_cognitive.ecos_wm_causal_link
    ADD CONSTRAINT fk_cog_causal_src FOREIGN KEY (source_goal_id) REFERENCES ecos_cognitive.ecos_wm_goal(id);

--

-- ecos_cognitive.ecos_wm_causal_link fk_cog_causal_tgt
ALTER TABLE ONLY ecos_cognitive.ecos_wm_causal_link
    ADD CONSTRAINT fk_cog_causal_tgt FOREIGN KEY (target_goal_id) REFERENCES ecos_cognitive.ecos_wm_goal(id) ON DELETE CASCADE;

--

-- ecos_cognitive.ecos_biz_contract fk_cog_contract_proj
ALTER TABLE ONLY ecos_cognitive.ecos_biz_contract
    ADD CONSTRAINT fk_cog_contract_proj FOREIGN KEY (project_id) REFERENCES ecos_cognitive.ecos_biz_project(id);

--

-- ecos_cognitive.ecos_wm_goal fk_cog_goal_parent
ALTER TABLE ONLY ecos_cognitive.ecos_wm_goal
    ADD CONSTRAINT fk_cog_goal_parent FOREIGN KEY (parent_id) REFERENCES ecos_cognitive.ecos_wm_goal(id);

--

-- ecos_cognitive.ecos_wm_goal_log fk_cog_goallog_goal
ALTER TABLE ONLY ecos_cognitive.ecos_wm_goal_log
    ADD CONSTRAINT fk_cog_goallog_goal FOREIGN KEY (goal_id) REFERENCES ecos_cognitive.ecos_wm_goal(id) ON DELETE CASCADE;

--

-- ecos_cognitive.ecos_biz_metric fk_cog_metric_goal
ALTER TABLE ONLY ecos_cognitive.ecos_biz_metric
    ADD CONSTRAINT fk_cog_metric_goal FOREIGN KEY (goal_id) REFERENCES ecos_cognitive.ecos_wm_goal(id);

--

-- ecos_cognitive.ecos_biz_project fk_cog_proj_goal
ALTER TABLE ONLY ecos_cognitive.ecos_biz_project
    ADD CONSTRAINT fk_cog_proj_goal FOREIGN KEY (goal_id) REFERENCES ecos_cognitive.ecos_wm_goal(id);

--

-- ecos_cognitive.ecos_biz_target fk_cog_target_goal
ALTER TABLE ONLY ecos_cognitive.ecos_biz_target
    ADD CONSTRAINT fk_cog_target_goal FOREIGN KEY (goal_id) REFERENCES ecos_cognitive.ecos_wm_goal(id);

--

-- ecos_cognitive.ecos_goal_tracking fk_cog_tracking_goal
ALTER TABLE ONLY ecos_cognitive.ecos_goal_tracking
    ADD CONSTRAINT fk_cog_tracking_goal FOREIGN KEY (goal_id) REFERENCES ecos_cognitive.ecos_wm_goal(id) ON DELETE CASCADE;

--
-- PostgreSQL database dump complete
--

