-- ============================================================
-- V150__rollback.sql
-- ------------------------------------------------------------
-- 回滚 V150__schema_unify.sql
-- 关键:
--   1. DROP 26 个兼容视图 v_*
--   2. RENAME 回: create_time → created_at, update_time → updated_at (反向)
--   3. 不 DROP 任何列/表 (R9)
--
-- 用法:
-- psql -U postgres -d sys_man -f V150__rollback.sql
-- ============================================================

\set ON_ERROR_STOP on
BEGIN;

------------------------------------------------------------
-- R1: DROP 218 兼容视图 (57 文件对应的 v_*)
------------------------------------------------------------
DO $$
DECLARE
    rec RECORD;
BEGIN
    FOR rec IN SELECT table_name
               FROM information_schema.views
               WHERE table_schema = 'public'
               AND table_name LIKE 'v\_%' ESCAPE '\'
    LOOP
        EXECUTE format('DROP VIEW public.%I', rec.table_name);
        RAISE NOTICE 'V150 rollback: dropped view %', rec.table_name;
    END LOOP;
END $$;

------------------------------------------------------------
-- R2: 反向 RENAME (create_time → created_at, update_time → updated_at)
------------------------------------------------------------
DO $$
DECLARE
    rec RECORD;
    tables TEXT[] := ARRAY[
        'ecos_agent', 'ecos_audit', 'ecos_data', 'ecos_decision',
        'ecos_dq', 'ecos_ontology', 'ecos_object', 'ecos_pipeline',
        'ecos_rule', 'ecos_scenario_run', 'ecos_workflow', 'ecos_workflow_approval',
        'ecos_workflow_instance', 'ecos_workflow_log', 'ecos_workflow_task',
        'sys_agent_session', 'sys_dicts', 'sys_config', 'td_audit_log',
        'ecos_wm_causal_link', 'ecos_wm_goal', 'ecos_wm_scenario',
        'ecos_token_usage', 'ecos_warn_log', 'kb_lineage_event',
        'sys_compliance_rule', 'kb_ontology_snapshot'
    ];
BEGIN
    FOR rec IN SELECT * FROM UNNEST(tables) AS t(t) LOOP
        IF EXISTS (SELECT 1 FROM information_schema.columns
                   WHERE table_schema = 'public'
                     AND table_name = rec.t
                     AND column_name = 'create_time') THEN
            EXECUTE format('ALTER TABLE %I RENAME COLUMN create_time TO created_at', rec.t);
        END IF;
        IF EXISTS (SELECT 1 FROM information_schema.columns
                   WHERE table_schema = 'public'
                     AND table_name = rec.t
                     AND column_name = 'update_time') THEN
            EXECUTE format('ALTER TABLE %I RENAME COLUMN update_time TO updated_at', rec.t);
        END IF;
    END LOOP;
END $$;

------------------------------------------------------------
-- R3: 反向注释
------------------------------------------------------------
DO $$
BEGIN
    RAISE NOTICE 'V150 rollback: 218 视图 → 0 + 字段已回退';
END $$;

COMMIT;

-- 验证:
-- \dt v_*                                       -- 期望 0 视图
-- SELECT count(*) FROM information_schema.columns
--   WHERE table_name = 'ecos_workflow_instance'
--     AND column_name IN ('create_time','update_time');  -- 期望 = 0
-- \d ecos_workflow_instance                      -- 期望 created_at 列名
-- psql -U postgres -d sys_man -c "SELECT * FROM ecos_workflow_instance LIMIT 1;"
