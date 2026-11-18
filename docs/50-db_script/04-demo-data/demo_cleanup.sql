-- ============================================================================
-- demo_cleanup.sql — ECOS 演示数据清空（可重复执行）
-- ============================================================================
-- 作用: 把 58 张演示业务表清到 0 行，使 demo_seed.sql 能重置到导出基线。
--       **系统配置面（00_init/10_system_seed.sql 的 27 张表）一行不动。**
--       表级互斥是安全前提：两册零重叠，故按表清空不会误伤 IAM/配置/字典/策略。
--
-- 顺序: 演示集内部 FK 拓扑序的**逆序**（子表在前），避免外键阻塞。
--       另有 5 条跨集 FK（非演示表 → 演示表），先精确删除「指向演示行」的从属行：
--       只删这 5 张表里引用演示数据的行，不整表清运行时数据。
--
-- 守卫: 执行前后对 27 张系统种子表逐表计数，任一表行数变化即 RAISE EXCEPTION 回滚。
--       另断言待清表确实存在（结构不符即 fail-loud，禁静默跳过）。
--
-- 幂等: 全 DELETE，重复执行不报错、结果一致（0 行）。
--
-- 运行:
--   dry-run（默认，事务回滚）:
--     docker exec -i ecos-postgres psql -U postgres -d <db> -v ON_ERROR_STOP=1 -q < demo_cleanup.sql
--   实跑:
--     同上并追加 -v ecos_commit=1
-- ============================================================================

BEGIN;

-- ---------------------------------------------------------------------------
-- 守卫A：待清表必须存在；系统种子表快照
-- ---------------------------------------------------------------------------
DO $$
DECLARE
    v_demo TEXT[] := ARRAY['ecos_ai.agent_registry',
                        'ecos_ai.ecos_agent_registry',
                        'ecos_ai.ecos_aip_pipeline',
                        'ecos_ai.ecos_cron_job',
                        'ecos_ai.ecos_mission',
                        'ecos_ai.ecos_skill',
                        'ecos_ai.ecos_wagent_action',
                        'ecos_ai.ecos_wagent_candidate',
                        'ecos_ai.ecos_wagent_goal',
                        'ecos_ai.ecos_wagent_mission',
                        'ecos_ai.ecos_wagent_question',
                        'ecos_ai.ecos_wagent_readiness',
                        'ecos_ai.ecos_wagent_readiness_gap',
                        'ecos_ai.ecos_wagent_readiness_item',
                        'ecos_ai.ecos_wagent_tool_contract',
                        'ecos_cognitive.ecos_biz_project',
                        'ecos_cognitive.ecos_biz_contract',
                        'ecos_cognitive.ecos_biz_department',
                        'ecos_cognitive.ecos_cognitive_mind',
                        'ecos_cognitive.ecos_cognitive_scenario_mind',
                        'ecos_control.demo_customer',
                        'ecos_control.demo_supplier',
                        'ecos_control.ecos_agent',
                        'ecos_control.ecos_business_scenario',
                        'ecos_control.ecos_cognitive_belief',
                        'ecos_control.ecos_cognitive_evidence',
                        'ecos_control.ecos_cognitive_hypothesis',
                        'ecos_control.ecos_cognitive_model',
                        'ecos_control.ecos_dq_rule',
                        'ecos_control.ecos_object_links',
                        'ecos_control.ecos_object_state_machine',
                        'ecos_control.ecos_object_timeline',
                        'ecos_control.ecos_ontology_action',
                        'ecos_control.ecos_ontology_entity',
                        'ecos_control.ecos_ontology_property',
                        'ecos_control.ecos_ontology_relationship',
                        'ecos_control.ecos_pareto_problem',
                        'ecos_control.ecos_pipeline_node',
                        'ecos_control.ecos_scenario_binding',
                        'ecos_control.ecos_scenario_binding_link',
                        'ecos_control.ecos_scenario_blueprint',
                        'ecos_control.ecos_scenario_sandbox_layout',
                        'ecos_control.ecos_wm_goal',
                        'ecos_control.ecos_wm_causal_link',
                        'ecos_control.ecos_wm_scenario',
                        'ecos_control.ecos_workflow',
                        'ecos_control.td_data_resource',
                        'ecos_control.td_datasource',
                        'ecos_data.dq_rule',
                        'ecos_data.ecos_data_lineage_edge',
                        'ecos_data.ecos_data_lineage_node',
                        'ecos_data.ecos_pipeline_definition',
                        'ecos_knowledge.ecos_kb_ontology_snapshot',
                        'ecos_knowledge.kb_doc',
                        'ecos_knowledge.kb_scheduled_extract',
                        'ecos_ontology.action_definition',
                        'ecos_ontology.ecos_ontology_candidate',
                        'ecos_ontology.ecos_ontology_proposal'];
    v_sys  TEXT[] := ARRAY['ecos_ai.ecos_tool_definition',
                           'ecos_cognitive.ecos_cognitive_framework',
                           'ecos_control.dict_column',
                           'ecos_control.dict_table',
                           'ecos_control.ecos_git_repo',
                           'ecos_control.ecos_rls_policy',
                           'ecos_control.ecos_security_asset',
                           'ecos_control.ecos_tenant',
                           'ecos_control.ecos_tenant_quota',
                           'ecos_control.sys_config',
                           'ecos_control.sys_dict',
                           'ecos_control.td_abac_policy',
                           'ecos_control.td_organization',
                           'ecos_control.td_permission',
                           'ecos_control.td_role',
                           'ecos_control.td_role_permission',
                           'ecos_control.td_role_security_profile',
                           'ecos_control.td_user',
                           'ecos_control.td_user_organization',
                           'ecos_control.td_user_role',
                           'ecos_control.td_user_security_profile',
                           'ecos_data.ecos_data_category_tree',
                           'ecos_data.ecos_data_level_def',
                           'ecos_data.ecos_pipeline_function',
                           'ecos_data.td_data_category',
                           'ecos_knowledge.kb_nav_category',
                           'ecos_ontology.ecos_domain'];
    v_t    TEXT;
    v_n    BIGINT;
BEGIN
    FOREACH v_t IN ARRAY v_demo LOOP
        IF to_regclass(v_t) IS NULL THEN
            RAISE EXCEPTION '演示表缺失: %（结构不符，禁静默跳过）', v_t;
        END IF;
    END LOOP;
    CREATE TEMP TABLE _sys_guard (t text PRIMARY KEY, n bigint) ON COMMIT DROP;
    FOREACH v_t IN ARRAY v_sys LOOP
        IF to_regclass(v_t) IS NULL THEN
            RAISE EXCEPTION '系统种子表缺失: %（00_init 未跑完？）', v_t;
        END IF;
        EXECUTE format('SELECT count(*) FROM %s', v_t) INTO v_n;
        INSERT INTO _sys_guard (t, n) VALUES (v_t, v_n);
    END LOOP;
    RAISE NOTICE 'demo_cleanup 守卫A：% 张演示表齐备，% 张系统种子表已快照 ✓', array_length(v_demo,1), array_length(v_sys,1);
END $$;

-- ---------------------------------------------------------------------------
-- 第一步：精确清除跨集从属行（5 条 FK，非演示表 → 演示表）
-- ---------------------------------------------------------------------------
-- 质量Issue（运行时产物，只删指向演示规则的行）
DELETE FROM ecos_control.ecos_dq_issue WHERE rule_id IN (SELECT id FROM ecos_control.ecos_dq_rule);
-- 目标日志（0 行）
DELETE FROM ecos_control.ecos_wm_goal_log WHERE goal_id IN (SELECT id FROM ecos_control.ecos_wm_goal);
-- 目标跟踪（0 行）
DELETE FROM ecos_control.ecos_goal_tracking WHERE goal_id IN (SELECT id FROM ecos_control.ecos_wm_goal);
-- 管道执行流水（只删指向演示管道的行）
DELETE FROM ecos_data.ecos_pipeline_execution WHERE pipeline_id IN (SELECT id FROM ecos_data.ecos_pipeline_definition);
-- 任务子项（0 行）
DELETE FROM ecos_ai.ecos_mission_task WHERE mission_id IN (SELECT id FROM ecos_ai.ecos_mission);

-- ---------------------------------------------------------------------------
-- 第二步：按 FK 逆拓扑序清空演示表
-- ---------------------------------------------------------------------------
DELETE FROM ecos_ontology.ecos_ontology_proposal;   -- 导出基线 11 行
DELETE FROM ecos_ontology.ecos_ontology_candidate;   -- 导出基线 16 行
DELETE FROM ecos_ontology.action_definition;   -- 导出基线 3 行
DELETE FROM ecos_knowledge.kb_scheduled_extract;   -- 导出基线 1 行
DELETE FROM ecos_knowledge.kb_doc;   -- 导出基线 36 行
DELETE FROM ecos_knowledge.ecos_kb_ontology_snapshot;   -- 导出基线 1 行
DELETE FROM ecos_data.ecos_pipeline_definition;   -- 导出基线 41 行
DELETE FROM ecos_data.ecos_data_lineage_node;   -- 导出基线 3 行
DELETE FROM ecos_data.ecos_data_lineage_edge;   -- 导出基线 2 行
DELETE FROM ecos_data.dq_rule;   -- 导出基线 37 行
DELETE FROM ecos_control.td_datasource;   -- 导出基线 1 行
DELETE FROM ecos_control.td_data_resource;   -- 导出基线 2 行
DELETE FROM ecos_control.ecos_workflow;   -- 导出基线 5 行
DELETE FROM ecos_control.ecos_wm_scenario;   -- 导出基线 5 行
DELETE FROM ecos_control.ecos_wm_causal_link;   -- 导出基线 15 行
DELETE FROM ecos_control.ecos_wm_goal;   -- 导出基线 21 行
DELETE FROM ecos_control.ecos_scenario_sandbox_layout;   -- 导出基线 1 行
DELETE FROM ecos_control.ecos_scenario_blueprint;   -- 导出基线 13 行
DELETE FROM ecos_control.ecos_scenario_binding_link;   -- 导出基线 7 行
DELETE FROM ecos_control.ecos_scenario_binding;   -- 导出基线 8 行
DELETE FROM ecos_control.ecos_pipeline_node;   -- 导出基线 12 行
DELETE FROM ecos_control.ecos_pareto_problem;   -- 导出基线 27 行
DELETE FROM ecos_control.ecos_ontology_relationship;   -- 导出基线 9 行
DELETE FROM ecos_control.ecos_ontology_property;   -- 导出基线 9 行
DELETE FROM ecos_control.ecos_ontology_entity;   -- 导出基线 18 行
DELETE FROM ecos_control.ecos_ontology_action;   -- 导出基线 3 行
DELETE FROM ecos_control.ecos_object_timeline;   -- 导出基线 18 行
DELETE FROM ecos_control.ecos_object_state_machine;   -- 导出基线 13 行
DELETE FROM ecos_control.ecos_object_links;   -- 导出基线 7 行
DELETE FROM ecos_control.ecos_dq_rule;   -- 导出基线 5 行
DELETE FROM ecos_control.ecos_cognitive_model;   -- 导出基线 1 行
DELETE FROM ecos_control.ecos_cognitive_hypothesis;   -- 导出基线 1 行
DELETE FROM ecos_control.ecos_cognitive_evidence;   -- 导出基线 2 行
DELETE FROM ecos_control.ecos_cognitive_belief;   -- 导出基线 2 行
DELETE FROM ecos_control.ecos_business_scenario;   -- 导出基线 245 行
DELETE FROM ecos_control.ecos_agent;   -- 导出基线 5 行
DELETE FROM ecos_control.demo_supplier;   -- 导出基线 3 行
DELETE FROM ecos_control.demo_customer;   -- 导出基线 15 行
DELETE FROM ecos_cognitive.ecos_cognitive_scenario_mind;   -- 导出基线 1 行
DELETE FROM ecos_cognitive.ecos_cognitive_mind;   -- 导出基线 1 行
DELETE FROM ecos_cognitive.ecos_biz_department;   -- 导出基线 3 行
DELETE FROM ecos_cognitive.ecos_biz_contract;   -- 导出基线 5 行
DELETE FROM ecos_cognitive.ecos_biz_project;   -- 导出基线 3 行
DELETE FROM ecos_ai.ecos_wagent_tool_contract;   -- 导出基线 12 行
DELETE FROM ecos_ai.ecos_wagent_readiness_item;   -- 导出基线 80 行
DELETE FROM ecos_ai.ecos_wagent_readiness_gap;   -- 导出基线 80 行
DELETE FROM ecos_ai.ecos_wagent_readiness;   -- 导出基线 10 行
DELETE FROM ecos_ai.ecos_wagent_question;   -- 导出基线 16 行
DELETE FROM ecos_ai.ecos_wagent_mission;   -- 导出基线 4 行
DELETE FROM ecos_ai.ecos_wagent_goal;   -- 导出基线 2 行
DELETE FROM ecos_ai.ecos_wagent_candidate;   -- 导出基线 134 行
DELETE FROM ecos_ai.ecos_wagent_action;   -- 导出基线 7 行
DELETE FROM ecos_ai.ecos_skill;   -- 导出基线 3 行
DELETE FROM ecos_ai.ecos_mission;   -- 导出基线 1 行
DELETE FROM ecos_ai.ecos_cron_job;   -- 导出基线 2 行
DELETE FROM ecos_ai.ecos_aip_pipeline;   -- 导出基线 9 行
DELETE FROM ecos_ai.ecos_agent_registry;   -- 导出基线 13 行
DELETE FROM ecos_ai.agent_registry;   -- 导出基线 4 行

-- ---------------------------------------------------------------------------
-- 守卫B：演示表全 0 行 + 系统种子表行数逐表未变
-- ---------------------------------------------------------------------------
DO $$
DECLARE
    v_demo TEXT[] := ARRAY['ecos_ai.agent_registry',
                        'ecos_ai.ecos_agent_registry',
                        'ecos_ai.ecos_aip_pipeline',
                        'ecos_ai.ecos_cron_job',
                        'ecos_ai.ecos_mission',
                        'ecos_ai.ecos_skill',
                        'ecos_ai.ecos_wagent_action',
                        'ecos_ai.ecos_wagent_candidate',
                        'ecos_ai.ecos_wagent_goal',
                        'ecos_ai.ecos_wagent_mission',
                        'ecos_ai.ecos_wagent_question',
                        'ecos_ai.ecos_wagent_readiness',
                        'ecos_ai.ecos_wagent_readiness_gap',
                        'ecos_ai.ecos_wagent_readiness_item',
                        'ecos_ai.ecos_wagent_tool_contract',
                        'ecos_cognitive.ecos_biz_project',
                        'ecos_cognitive.ecos_biz_contract',
                        'ecos_cognitive.ecos_biz_department',
                        'ecos_cognitive.ecos_cognitive_mind',
                        'ecos_cognitive.ecos_cognitive_scenario_mind',
                        'ecos_control.demo_customer',
                        'ecos_control.demo_supplier',
                        'ecos_control.ecos_agent',
                        'ecos_control.ecos_business_scenario',
                        'ecos_control.ecos_cognitive_belief',
                        'ecos_control.ecos_cognitive_evidence',
                        'ecos_control.ecos_cognitive_hypothesis',
                        'ecos_control.ecos_cognitive_model',
                        'ecos_control.ecos_dq_rule',
                        'ecos_control.ecos_object_links',
                        'ecos_control.ecos_object_state_machine',
                        'ecos_control.ecos_object_timeline',
                        'ecos_control.ecos_ontology_action',
                        'ecos_control.ecos_ontology_entity',
                        'ecos_control.ecos_ontology_property',
                        'ecos_control.ecos_ontology_relationship',
                        'ecos_control.ecos_pareto_problem',
                        'ecos_control.ecos_pipeline_node',
                        'ecos_control.ecos_scenario_binding',
                        'ecos_control.ecos_scenario_binding_link',
                        'ecos_control.ecos_scenario_blueprint',
                        'ecos_control.ecos_scenario_sandbox_layout',
                        'ecos_control.ecos_wm_goal',
                        'ecos_control.ecos_wm_causal_link',
                        'ecos_control.ecos_wm_scenario',
                        'ecos_control.ecos_workflow',
                        'ecos_control.td_data_resource',
                        'ecos_control.td_datasource',
                        'ecos_data.dq_rule',
                        'ecos_data.ecos_data_lineage_edge',
                        'ecos_data.ecos_data_lineage_node',
                        'ecos_data.ecos_pipeline_definition',
                        'ecos_knowledge.ecos_kb_ontology_snapshot',
                        'ecos_knowledge.kb_doc',
                        'ecos_knowledge.kb_scheduled_extract',
                        'ecos_ontology.action_definition',
                        'ecos_ontology.ecos_ontology_candidate',
                        'ecos_ontology.ecos_ontology_proposal'];
    v_sys  TEXT[] := ARRAY['ecos_ai.ecos_tool_definition',
                           'ecos_cognitive.ecos_cognitive_framework',
                           'ecos_control.dict_column',
                           'ecos_control.dict_table',
                           'ecos_control.ecos_git_repo',
                           'ecos_control.ecos_rls_policy',
                           'ecos_control.ecos_security_asset',
                           'ecos_control.ecos_tenant',
                           'ecos_control.ecos_tenant_quota',
                           'ecos_control.sys_config',
                           'ecos_control.sys_dict',
                           'ecos_control.td_abac_policy',
                           'ecos_control.td_organization',
                           'ecos_control.td_permission',
                           'ecos_control.td_role',
                           'ecos_control.td_role_permission',
                           'ecos_control.td_role_security_profile',
                           'ecos_control.td_user',
                           'ecos_control.td_user_organization',
                           'ecos_control.td_user_role',
                           'ecos_control.td_user_security_profile',
                           'ecos_data.ecos_data_category_tree',
                           'ecos_data.ecos_data_level_def',
                           'ecos_data.ecos_pipeline_function',
                           'ecos_data.td_data_category',
                           'ecos_knowledge.kb_nav_category',
                           'ecos_ontology.ecos_domain'];
    v_t    TEXT;
    v_n    BIGINT;
    v_was  BIGINT;
    v_bad  TEXT := '';
BEGIN
    FOREACH v_t IN ARRAY v_demo LOOP
        EXECUTE format('SELECT count(*) FROM %s', v_t) INTO v_n;
        IF v_n <> 0 THEN v_bad := v_bad || v_t || '=' || v_n || ' '; END IF;
    END LOOP;
    IF v_bad <> '' THEN RAISE EXCEPTION '清空后仍有行: %', v_bad; END IF;
    FOR v_t, v_was IN SELECT t, n FROM _sys_guard LOOP
        EXECUTE format('SELECT count(*) FROM %s', v_t) INTO v_n;
        IF v_n <> v_was THEN
            RAISE EXCEPTION '系统种子表被误伤: % 由 % 行变为 % 行', v_t, v_was, v_n;
        END IF;
    END LOOP;
    RAISE NOTICE 'demo_cleanup 守卫B：% 张演示表全 0 行，% 张系统种子表行数逐表未变 ✓', array_length(v_demo,1), array_length(v_sys,1);
END $$;

\if :{?ecos_commit}
    \if :ecos_commit
        COMMIT;
        \echo 'demo_cleanup 已提交（ecos_commit=1）'
    \else
        ROLLBACK;
        \echo 'demo_cleanup 已回滚（dry-run，未传 ecos_commit=1）'
    \endif
\else
    ROLLBACK;
    \echo 'demo_cleanup 已回滚（dry-run，未传 ecos_commit=1）'
\endif
