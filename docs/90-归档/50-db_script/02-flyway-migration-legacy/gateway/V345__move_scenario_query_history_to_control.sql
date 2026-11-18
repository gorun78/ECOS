-- ============================================================================
-- V345 — 场景查询历史表归位：public → ecos_control
-- ============================================================================
-- 依据:
--   1. ST07 schema 归属红线（《数据库访问规范》v1.4）——「横切引擎（security/runtime）
--      与 sysman/workspace/service 层新表一律落 ecos_control」；
--   2. V212 脚本头部自述的既定目标：「落点 (MC06): 控制域现基线 = public（目标 ecos_control,
--      ADR-12）⇒ 新表落 public.，迁 ecos_control 后由 `ecos.db.control-schema` 配置注入改前缀，
--      本批不预置」—— 本脚本即兑现该条；
--   3. 代码侧同批改造：`JdbcQueryHistoryRepository` 由硬编码 `public.` 改为
--      `@Value("${ecos.db.control-schema:ecos_control}")` 注入（2026-11-17）。
--
-- 纪律: IR03（只加不删：本脚本用 SET SCHEMA 搬迁，不 DROP TABLE 不删列）
--       / IR02（手动 psql，非 Flyway 运行时）/ MC03（无 PG 特有语法）
--
-- 前置检查:
--   · 源表存在且非空 → 搬迁；源表存在但为空 → 搬迁（保持结构一致）
--   · 源表不存在 → 跳过（新库由 00_init/01_ecos_control.sql 直接建在目标 schema）
--   · 目标表已存在 → 中止（说明已搬迁或存在冲突，需人工确认）
--
-- 运行:
--   dry-run（事务回滚，只打印）:
--     docker exec -i ecos-postgres psql -U postgres -d sys_man -v ON_ERROR_STOP=1 -f - \
--       < docs/50-db_script/02-flyway-migration/gateway/V345__move_scenario_query_history_to_control.sql
--   实跑（须先经用户授权 + 全库备份）:
--     psql -U postgres -d sys_man -c "..." 逐条，或传 -v ecos_commit=1
-- ============================================================================

BEGIN;

DO $$
DECLARE
    v_src_exists BOOLEAN;
    v_dst_exists BOOLEAN;
    v_row_count  BIGINT;
BEGIN
    -- 1) 前置检查
    SELECT EXISTS (
        SELECT 1 FROM information_schema.tables
        WHERE table_schema = 'public' AND table_name = 'ecos_scenario_query_history'
    ) INTO v_src_exists;

    SELECT EXISTS (
        SELECT 1 FROM information_schema.tables
        WHERE table_schema = 'ecos_control' AND table_name = 'ecos_scenario_query_history'
    ) INTO v_dst_exists;

    IF v_dst_exists THEN
        RAISE EXCEPTION
            'ecos_control.ecos_scenario_query_history 已存在 ⇒ 已搬迁过或存在冲突，中止';
    END IF;

    IF NOT v_src_exists THEN
        RAISE NOTICE
            'public.ecos_scenario_query_history 不存在 ⇒ 新库场景，跳过搬迁（表由 00_init/01_ecos_control.sql 建）';
        RETURN;
    END IF;

    -- 2) 行数取证
    EXECUTE 'SELECT count(*) FROM public.ecos_scenario_query_history' INTO v_row_count;
    RAISE NOTICE '搬迁 public.ecos_scenario_query_history → ecos_control（当前 % 行）', v_row_count;

    -- 3) 搬迁（SET SCHEMA 保留数据、索引、约束、序列归属）
    ALTER TABLE public.ecos_scenario_query_history SET SCHEMA ecos_control;

    -- 4) 后置校验
    SELECT EXISTS (
        SELECT 1 FROM information_schema.tables
        WHERE table_schema = 'public' AND table_name = 'ecos_scenario_query_history'
    ) INTO v_src_exists;

    IF v_src_exists THEN
        RAISE EXCEPTION '搬迁后 public 侧表仍存在 ⇒ 搬迁失败';
    END IF;

    EXECUTE 'SELECT count(*) FROM ecos_control.ecos_scenario_query_history' INTO v_row_count;
    RAISE NOTICE '搬迁完成，ecos_control.ecos_scenario_query_history 现 % 行', v_row_count;
END $$;

-- 5) 事务开关：默认回滚（dry-run），传 -v ecos_commit=1 才提交
\if :{?ecos_commit}
    \if :ecos_commit
        COMMIT;
        \echo 'V345 已提交（ecos_commit=1）'
    \else
        ROLLBACK;
        \echo 'V345 已回滚（dry-run，未传 ecos_commit=1）'
    \endif
\else
    ROLLBACK;
    \echo 'V345 已回滚（dry-run，未传 ecos_commit=1）'
\endif
