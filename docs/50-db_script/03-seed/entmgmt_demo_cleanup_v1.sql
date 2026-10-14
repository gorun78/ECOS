-- =============================================================================
-- ECOS dev 演示数据清理脚本 v1 —— 为「企业经营管控」场景数据集腾位
-- =============================================================================
-- 依据  : docs/30-设计/ECOS详细设计说明书-从零实施版-2026-10-12.md §12（场景年度经营预测闭环）
-- 批准  : 指令 ECOS-DATA-01 v2 门 G1 = B（2026-10-14 用户裁决，含航空场景 sc001/sc002/sc003 一并清空）
-- 库    : 仅本地 dev（容器 ecos-postgres / 库 sys_man）。生产不适用。
-- 备份  : %TEMP%/ecos-pgbackup-20261014-t0.dump（pg_dump -Fc，TOC 1510 条，PG 16.14，仓外不入库）
-- 基线  : 全库 214 表 / 55 表非空 / 966 行；本脚本删 32 表 171 行，保留 23 表 795 行
--         （逐表精确 count 取证于 2026-10-14，见本册 §验收 与 03-seed/entmgmt_demo_verify_v1.sql）
--
-- 运行模式（同一文件，零改动切换）：
--   dry-run（默认，必然回滚，只打印每表 DELETE 行数）：
--     cat docs/50-db_script/03-seed/entmgmt_demo_cleanup_v1.sql \
--       | docker exec -i ecos-postgres psql -U postgres -d sys_man -v ON_ERROR_STOP=1 -f -
--   真跑（显式传变量才提交）：
--     cat ... | docker exec -i ecos-postgres psql -U postgres -d sys_man -v ON_ERROR_STOP=1 \
--       -v ecos_commit=1 -f -
--
-- 规范：IR03 只约束 DDL（列/表不删），本脚本为 DML 数据清理；删除前有整库备份 + 用户批准。
--       Flyway 永久禁用（IR02），本脚本不进 02-flyway-migration，落 03-seed 与 seed 配对。
-- 外键依据（全库实数 14 条 FK，落在本集合内的仅两条）：
--   ecos_data.ecos_dq_issue.rule_id        -> ecos_data.ecos_dq_rule.id           （父表保留，只删子）
--   ecos_cognitive.ecos_wm_causal_link.*_goal_id -> ecos_wm_goal.id               （子先删）
--   ecos_cognitive.ecos_goal_tracking / ecos_wm_goal_log -> ecos_wm_goal.id       （现 0 行，仍先删占位）
--
-- ─── 保留白名单（23 表，删了会导致登录失败 / 配置丢失 / 治理记录断裂）─────────────
--   ecos_control: td_user, users, td_user_role, td_role, td_permission, td_role_permission,
--                 td_role_security_profile, td_user_security_profile, td_abac_policy,
--                 td_organization, td_audit_log(审计哈希链不可删), ecos_tenant, ecos_tenant_quota,
--                 ecos_tenant_usage, sys_config(112), sys_dict(49)
--   ecos_data   : td_datasource, ecos_pipeline_function(120 函数目录基线),
--                 dq_rule(10), ecos_dq_rule(5), ecos_dq_legacy_migration_report(5)
--   ecos_infra  : schema_changes(191), schema_snapshots(191)
-- =============================================================================

\set ON_ERROR_STOP on
BEGIN;

-- ── 1. ecos_ai：场景与智能体面（含航空 AOC 场景 sc001/sc002/sc003）──────────────
--    代码权威：workspace/ScenarioService.java:134 读写 ecos_business_scenario；
--    WorkspaceDataDao.java:191 实读 ecos_ai.*（V200 迁库遗留，非 ecos_control.ecos_scenario_definition）
DELETE FROM ecos_ai.ecos_scenario_binding;      -- 8 行，binding_type 六值词表见 ScenarioService.java:41-46
DELETE FROM ecos_ai.ecos_business_scenario;     -- 3 行，sc001/sc002/sc003（航空 AOC 贯通口径）
DELETE FROM ecos_ai.ecos_skill;                 -- 3 行
DELETE FROM ecos_ai.ecos_agent_registry;        -- 9 行
DELETE FROM ecos_ai.ecos_agent;                 -- 4 行
DELETE FROM ecos_ai.agent_registry;             -- 4 行（与上两张同族重复登记面，一并清）
DELETE FROM ecos_ai.ecos_cron_job;              -- 2 行

-- ── 2. ecos_cognitive：心智状态与世界模型（航空目标树）──────────────────────────
--    子先于父：causal_link / goal_tracking / goal_log 均 FK 引用 ecos_wm_goal
DELETE FROM ecos_cognitive.ecos_wm_causal_link;  -- 3 行（FK -> ecos_wm_goal）
DELETE FROM ecos_cognitive.ecos_cognitive_evidence;  -- 2 行
DELETE FROM ecos_cognitive.ecos_cognitive_belief;    -- 2 行
DELETE FROM ecos_cognitive.ecos_cognitive_hypothesis;-- 1 行
DELETE FROM ecos_cognitive.ecos_cognitive_model;     -- 1 行
DELETE FROM ecos_cognitive.ecos_wm_scenario;         -- 2 行
DELETE FROM ecos_cognitive.ecos_wm_goal_log;         -- 0 行（FK 子表，占位先删）
DELETE FROM ecos_cognitive.ecos_goal_tracking;       -- 0 行（FK 子表，占位先删）
DELETE FROM ecos_cognitive.ecos_wm_goal;             -- 16 行（含 parent_id 自引用）

-- ── 3. ecos_control：场景装配与对象运行时 ───────────────────────────────────────
DELETE FROM ecos_control.ecos_scenario_binding_link; -- 2 行
DELETE FROM ecos_control.ecos_scenario_blueprint;    -- 6 行（SUP-D 蓝图态）
DELETE FROM ecos_control.ecos_object_links;          -- 7 行
DELETE FROM ecos_control.ecos_object_state_machine;  -- 13 行

-- ── 4. ecos_data：管道编排与目录登记（演示管道）────────────────────────────────
--    注意：ecos_dq_issue 有 FK 指向 ecos_dq_rule（保留），故只删 issue
DELETE FROM ecos_data.ecos_dq_issue;            -- 4 行（FK 子表，先于规则；规则表保留）
DELETE FROM ecos_data.ecos_pipeline_node;       -- 27 行（子）
DELETE FROM ecos_data.ecos_pipeline_definition; -- 9 行（父）
DELETE FROM ecos_data.td_catalog_item;          -- 3 行
DELETE FROM ecos_data.td_data_resource;         -- 3 行

-- ── 5. ecos_dw：v1 遗留演示三表 ─────────────────────────────────────────────────
DELETE FROM ecos_dw.demo_invoice;               -- 3 行
DELETE FROM ecos_dw.demo_customer;              -- 5 行
DELETE FROM ecos_dw.demo_supplier;              -- 3 行

-- ── 6. ecos_ontology：本体设计资产（口径/术语/实体/关系/工作流）─────────────────
--    ecos_metric_definition 现 0 行，仍显式清空以保证重跑幂等
DELETE FROM ecos_ontology.ecos_metric_definition;      -- 0 行
DELETE FROM ecos_ontology.ecos_glossary_term;          -- 8 行
DELETE FROM ecos_ontology.ecos_ontology_relationship;  -- 2 行
DELETE FROM ecos_ontology.ecos_ontology_property;      -- 7 行
DELETE FROM ecos_ontology.ecos_ontology_entity;        -- 3 行
DELETE FROM ecos_ontology.ecos_workflow;               -- 5 行
DELETE FROM ecos_ontology.ecos_caliber;                -- 1 行（domain='default' 历史口径）

-- ── 影响面自检：删除集应全部归零 ───────────────────────────────────────────────
SELECT 'deleted-scope 残留行数' AS check_item,
       (SELECT count(*) FROM ecos_ai.ecos_business_scenario)
     + (SELECT count(*) FROM ecos_cognitive.ecos_wm_goal)
     + (SELECT count(*) FROM ecos_control.ecos_scenario_blueprint)
     + (SELECT count(*) FROM ecos_data.ecos_pipeline_definition)
     + (SELECT count(*) FROM ecos_dw.demo_customer)
     + (SELECT count(*) FROM ecos_ontology.ecos_caliber) AS must_be_zero;

-- ── 保留面自检：平台基线应仍在位 ───────────────────────────────────────────────
SELECT 'kept-baseline td_user' AS check_item, count(*) AS must_stay FROM ecos_control.td_user
UNION ALL SELECT 'kept-baseline sys_config', count(*) FROM ecos_control.sys_config
UNION ALL SELECT 'kept-baseline sys_dict', count(*) FROM ecos_control.sys_dict
UNION ALL SELECT 'kept-baseline td_abac_policy', count(*) FROM ecos_control.td_abac_policy
UNION ALL SELECT 'kept-baseline td_audit_log', count(*) FROM ecos_control.td_audit_log
UNION ALL SELECT 'kept-baseline pipeline_function', count(*) FROM ecos_data.ecos_pipeline_function;

-- ── 提交门：未传 -v ecos_commit=1 一律回滚（默认 dry-run）────────────────────────
\if :{?ecos_commit}
  \echo '>>> MODE = COMMIT（真跑，清理将落库）'
COMMIT;
\else
  \echo '>>> MODE = DRY-RUN（回滚，库未改变）'
ROLLBACK;
\endif
