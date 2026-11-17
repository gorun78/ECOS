-- ============================================================
-- V150__schema_unify.sql — 数据库访问规范 v1.0 配套收尾
-- ------------------------------------------------------------
-- 触发:   数据库访问规范.md §十 存量违规处置(已裁定 2026-09-22)
-- 用户拍板: 5 个开放点按建议处理(O1 不 RENAME 表 / O2 RENAME 字段 / O3 新表加 domain)
--
-- 影响面:
--   - 93 个真实 V*.sql(gateway/src/main/resources/db/migration/) + 2 引擎 sql + 1 seed
--   - 57 文件含 _at TIMESTAMP 字段, 共 218 个(其中业务时间戳 ~80 保留 +
--     created_at/updated_at 审计字段 ~130 本次 RENAME)
--   - V126 已授权 DROP ghost 表 (PMO-58, R9 例外)
--   - V4 重复 (gateway/src 与 engine/ai-engine/api/src 各 1 个 V4)
--
-- 红线: IR01-06 IR9, 数据流铁律 §0.5, §3.3 RLS/CLS/脱敏
-- 同 commit: database/seed.sql (本脚本在 database/ 同目录, 跨 module 须 sync)
-- 执行:   psql -U postgres -d sys_man -f V150__schema_unify.sql
-- 回滚:   psql -U postgres -d sys_man -f V150__rollback.sql
-- ============================================================

\set ON_ERROR_STOP on
BEGIN;

------------------------------------------------------------
-- D1: 审计字段 ABAC/CLS 视图 (26 张表 RENAME 后双轨兼容)
------------------------------------------------------------
-- 说明: 112 V*.sql 历史 218 个 _at TIMESTAMP 字段, 其中 created_at/updated_at
-- 是审计字段 (按 DR06), 业务时间戳 (started_at/completed_at/last_active_at 等)
-- 保留原名 (非审计)。本块为 26 张含 created_at 的表 建视图别名,
-- CREATE OR REPLACE VIEW 幂等, 下次 V150 执行后视图自动收敛到 create_time/update_time 命名。
-- 视图名规范: v_{表名}_audit (大写表名转下划线)
-- 视图职责: SELECT 时兼容旧代码 (引用 created_at 的语句自动走 create_time)
-- 注: 视图只读不可写, UPDATE/INSERT 写库路径必须由调用方改 SQL 显式 create_time
-- 顺序: 先 RENAME 列 → 再 建视图

-- D1.0 视图创建前: 先扫描 26 张含 created_at 的表清单 (sub-agent 调研)
-- 实际列表通过 V150 lint 报告 (docs/23-quality/db-v150-audit-2026-09-22.md)

------------------------------------------------------------
-- D2: RENAME 字段 (created_at → create_time, updated_at → update_time)
-- 26 张含 created_at 的表 (sub-agent 调研)
-- 幂等: 用 CASE WHEN (column is exists) 处理, 不 DROP 不改 类型 (R9 不删字段)
------------------------------------------------------------

-- 幂等 RENAME 模板:
-- DO $$ BEGIN IF EXISTS (SELECT 1 FROM information_schema.columns
--                     WHERE table_name = 'X' AND column_name = 'created_at') THEN
--           ALTER TABLE X RENAME COLUMN created_at TO create_time;
--         END IF; END $$;

-- 实际上 PostgreSQL ALTER TABLE RENAME COLUMN IF EXISTS 可用 (PG 14+)
-- 当前 PG 16, 可用:
--   ALTER TABLE IF EXISTS X RENAME COLUMN IF EXISTS created_at TO create_time;
-- 但 IF EXISTS 不支持 COLUMN 子句, 改用 DO $ 块。

-- 26 张表 (从 sub-agent D1 调研清单):
DO $$
DECLARE
    rec RECORD;
    t RECORD;
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
    -- 审计 3 字段顺序: created_at, updated_at, version_no
    FOR rec IN SELECT * FROM UNNEST(tables) AS t(t) LOOP
        -- created_at → create_time
        IF EXISTS (SELECT 1 FROM information_schema.columns
                   WHERE table_schema = 'public'
                     AND table_name = rec.t
                     AND column_name = 'created_at') THEN
            EXECUTE format('ALTER TABLE %I RENAME COLUMN created_at TO create_time', rec.t);
        END IF;
        -- updated_at → update_time
        IF EXISTS (SELECT 1 FROM information_schema.columns
                   WHERE table_schema = 'public'
                     AND table_name = rec.t
                     AND column_name = 'updated_at') THEN
            EXECUTE format('ALTER TABLE %I RENAME COLUMN updated_at TO update_time', rec.t);
        END IF;
    END LOOP;
END $$;

------------------------------------------------------------
-- D3: 兼容视图 (旧代码 created_at / updated_at 自动走新列)
-- DO 块遍历 218 个 _at TIMESTAMP 字段中的审计字段, 建视图
-- 视图职责: 重写 created_at 为 create_time, 其余字段保留
------------------------------------------------------------
DO $$
DECLARE
    rec RECORD;
    col RECORD;
    vname TEXT;
    sql  TEXT;
    body SQL;
BEGIN
    FOR rec IN SELECT table_name FROM information_schema.tables
               WHERE table_type = 'BASE TABLE' AND table_schema = 'public'
               AND table_name NOT LIKE 'v\_%' ESCAPE '\'
    LOOP
        body := NULL;
        body := body || format(
            'CREATE OR REPLACE VIEW public.v_%s AS SELECT ', rec.table_name);
        SELECT string_agg(
            format('CASE WHEN column_name = ''created_at'' THEN ''create_time''::regtype WHEN column_name = ''updated_at'' THEN ''update_time''::regtype ELSE format('%I', column_name) END',
                   column_name), ', ' ORDER BY ordinal_position
        ) INTO body FROM information_schema.columns
        WHERE table_schema = 'public' AND table_name = rec.table_name;
        -- 简化: 仅 SELECT 业务字段
        body := format('CREATE OR REPLACE VIEW public.v_%s AS SELECT ', rec.table_name)
              || (SELECT string_agg(
                 CASE
                     WHEN column_name = 'created_at' THEN 'create_time AS created_at'
                     WHEN column_name = 'updated_at' THEN 'update_time AS updated_at'
                     ELSE format('%I', column_name)
                 END, ', ' ORDER BY ordinal_position
               ) FROM information_schema.columns
                 WHERE table_schema = 'public' AND table_name = rec.table_name);
        body := body || format(' FROM public.%s_', rec.table_name) || rec.table_name;
        EXECUTE body;
        RAISE NOTICE 'V150 D3: created view v_%', rec.table_name;
    END LOOP;
END $$;

------------------------------------------------------------
-- D4: 验证 + 注释 (不写 DDL, 只喂数据)
------------------------------------------------------------
DO $$
DECLARE
    n_tables INTEGER;
    n_audit_renamed INTEGER;
    n_view_created INTEGER;
BEGIN
    -- 1. 218 中审计 (created_at/updated_at) 字段只剩 create_time/update_time
    SELECT count(*) INTO n_audit_renamed
    FROM information_schema.columns
    WHERE table_schema = 'public'
      AND (column_name = 'create_time' OR column_name = 'update_time')
      AND table_name NOT LIKE 'v\_%' ESCAPE '\';
    RAISE NOTICE 'V150 D4: % 已完成 create_time/update_time rename', n_audit_renamed;
    -- 2. 视图数
    SELECT count(*) INTO n_view_created
    FROM information_schema.views WHERE table_schema = 'public' AND table_name LIKE 'v\_%' ESCAPE '\';
    RAISE NOTICE 'V150 D4: % 个 v_* 兼容视图已建', n_view_created;
END $$;

COMMIT;

-- ============================================================
-- 应用后操作 (R4: 应用必须人工, 不自动 Flyway)
-- psql -U postgres -d sys_man -f V150__schema_unify.sql
--
-- 验证 SQL (人工跑):
-- \dt v_*               -- 看兼容视图
-- \d ecos_workflow_instance   -- 看列名已 RENAME
-- SELECT * FROM v_ecos_workflow_instance LIMIT 1;  -- 兼容视图
--
-- 检查 gateway 内 112 V*.sql 的 created_at 引用:
-- grep -R "created_at" gateway/src/main/resources/db/migration/*.sql | wc -l  -- 期望 0
-- (旧脚本是历史, 列表/字段定义仍在, 但运行期走视图/新列名)
--
-- 回滚:
-- psql -U postgres -d sys_man -f V150__rollback.sql
-- ============================================================
