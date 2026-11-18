-- ============================================================================
-- V346 — public 独有表批量归位到 ST07 权威 schema（28 张）
-- ============================================================================
-- 背景（2026-11-17 活体取证，推翻「public 唯一表皆空壳可 DROP」的旧判断）:
--   public 共 59 张基表，分三类：
--     A. 23 张被 ecos_control 同名副本遮蔽（search_path 首位）⇒ 死壳，属清退面；
--     B. 36 张 public 独有 —— 其中 4 张带数据（ecos_pipeline_function 120 /
--        ecos_scenario_query_history 18 / ecos_skill 3 / ecos_cron_job 2），
--        25 张虽 0 行但**有活 SQL 引用且是唯一载体**（空≠无用），
--        仅 3 张零引用（ecos_runtime_llm_usage / ecos_scenario_active_mind /
--        ecos_scenario_mind_ref）+ 4 张仅注释提及。
--   ⇒ B 类中 29 张必须**搬迁**而非 DROP，否则打断管道/场景/本体/知识功能。
--      其中 ecos_scenario_query_history 已由 V345 处置，本脚本承接其余 28 张。
--
-- 归属判据（不臆造）: 逐表统计 ecos_backend/*/src/main 内**剥注释后含 SQL 关键字**
--   的引用行，按引用方所属模块映射 ST07 schema：
--     engine/data-engine、services/datanet        → ecos_data
--     engine/ontology-engine、services/buszhi     → ecos_ontology
--     engine/kb-engine、services/dccheng          → ecos_knowledge
--     engine/ai-engine、services/aiming           → ecos_ai
--     engine/cognitive-engine                     → ecos_cognitive
--     security-engine / runtime / sysman / workspace / gateway → ecos_control
--   28 张表每张均有单一主导模块（次要跨域读 ≤1 行），归属无歧义。
--
-- 为什么搬迁后代码无需改: 这些表的 DAO 全部用**不带 schema 前缀**的表名，
--   靠 JDBC currentSchema=ecos_control,ecos_data,ecos_ontology,ecos_ai,
--   ecos_cognitive,ecos_knowledge,ecos_infra,ecos_dw,public 解析。
--   搬到目标 schema 后仍在该链内且更靠前 ⇒ 命中不变，public 兜底不再被依赖。
--
-- 纪律: IR03（只加不删：纯 SET SCHEMA，不 DROP TABLE / 不删列 / 不动约束）
--       / IR02（手动 psql，Flyway 永久禁用）/ DR01（全 schema 限定）
--
-- 遗留接缝（属阶段 3 清退面，本脚本刻意不动）:
--   public.ecos_glossary_term_relation 搬去 ecos_ontology 后，其两条外键
--   fk_glossary_relation_from / fk_glossary_relation_to 仍指向 public.ecos_glossary_term
--   （跨 schema 外键，PG 合法且数据 0 行）。阶段 3 DROP public.ecos_glossary_term 前
--   必须先把这两条外键改指 ecos_control.ecos_glossary_term（权威副本），否则清退被阻塞。
--
-- 运行:
--   dry-run（默认，事务回滚只打印）:
--     docker exec -i ecos-postgres psql -U postgres -d sys_man -v ON_ERROR_STOP=1 -f - \
--       < docs/50-db_script/02-flyway-migration/gateway/V346__relocate_public_unique_tables.sql
--   实跑（须先经用户逐项授权 + 全库备份）: 追加 -v ecos_commit=1
-- ============================================================================

BEGIN;

DO $$
DECLARE
    v_map TEXT[][] := ARRAY[
        -- ecos_ai（ai-engine 火W：定时任务 / 技能包 / Agent 告警与指标）
        ['ecos_cron_job',                'ecos_ai'],
        ['ecos_cron_job_execution',      'ecos_ai'],
        ['ecos_skill',                   'ecos_ai'],
        ['ecos_agent_alert',             'ecos_ai'],
        ['ecos_agent_metrics',           'ecos_ai'],
        -- ecos_control（workspace 场景层 + runtime 横切告警）
        ['ecos_scenario_definition',     'ecos_control'],
        ['ecos_scenario_asset_binding',  'ecos_control'],
        ['ecos_scenario_binding_edge',   'ecos_control'],
        ['ecos_scenario_canvas_layout',  'ecos_control'],
        ['ecos_scenario_decision_record','ecos_control'],
        ['ecos_scenario_execution',      'ecos_control'],
        ['ecos_scenario_mind_variant',   'ecos_control'],
        ['ecos_runtime_alert_rule',      'ecos_control'],
        ['ecos_runtime_alert_record',    'ecos_control'],
        -- ecos_data（data-engine 土D：管道 / 函数 / 质量）
        ['ecos_pipeline_task',           'ecos_data'],
        ['ecos_pipeline_run',            'ecos_data'],
        ['ecos_pipeline_step',           'ecos_data'],
        ['ecos_pipeline_step_run',       'ecos_data'],
        ['ecos_pipeline_function',       'ecos_data'],
        ['ecos_pipeline_udf',            'ecos_data'],
        ['ecos_quality_rule',            'ecos_data'],
        ['ecos_quality_evaluation',      'ecos_data'],
        ['ecos_dq_rule_v2',              'ecos_data'],
        -- ecos_knowledge（kb-engine 水K：抽取草稿）
        ['extraction_drafts',            'ecos_knowledge'],
        -- ecos_ontology（ontology-engine 金I：版本 / 术语关系 / 规则 / 工作台对象）
        ['ecos_ontology_version',        'ecos_ontology'],
        ['ecos_glossary_term_relation',  'ecos_ontology'],
        ['ecos_ontology_rule',           'ecos_ontology'],
        ['ecos_ontology_workbench_object','ecos_ontology']
    ];
    v_tbl        TEXT;
    v_dst_schema TEXT;
    v_src_exists BOOLEAN;
    v_dst_exists BOOLEAN;
    v_rows       BIGINT;
    v_moved      INT := 0;
    v_skipped    INT := 0;
    v_total_rows BIGINT := 0;
    i            INT;
BEGIN
    FOR i IN 1 .. array_length(v_map, 1) LOOP
        v_tbl        := v_map[i][1];
        v_dst_schema := v_map[i][2];

        SELECT EXISTS (SELECT 1 FROM information_schema.tables
                       WHERE table_schema = 'public' AND table_name = v_tbl)
          INTO v_src_exists;
        SELECT EXISTS (SELECT 1 FROM information_schema.tables
                       WHERE table_schema = v_dst_schema AND table_name = v_tbl)
          INTO v_dst_exists;

        -- 目标已存在 ⇒ 冲突，fail-loud（禁静默覆盖，可能是他人已搬或双源）
        IF v_dst_exists THEN
            RAISE EXCEPTION '% .% 已存在 ⇒ 已搬迁过或存在双源冲突，中止（须人工确认）',
                v_dst_schema, v_tbl;
        END IF;

        -- 源不存在 ⇒ 新库场景，跳过（表由 00_init/ 直接建在目标 schema）
        IF NOT v_src_exists THEN
            RAISE NOTICE '跳过 public.%（不存在，新库由 00_init/ 建于 %）', v_tbl, v_dst_schema;
            v_skipped := v_skipped + 1;
            CONTINUE;
        END IF;

        EXECUTE format('SELECT count(*) FROM public.%I', v_tbl) INTO v_rows;
        RAISE NOTICE '搬迁 public.% → %（当前 % 行）', v_tbl, v_dst_schema, v_rows;

        -- SET SCHEMA 保留数据、索引、约束、序列归属与外键连接
        EXECUTE format('ALTER TABLE public.%I SET SCHEMA %I', v_tbl, v_dst_schema);

        SELECT EXISTS (SELECT 1 FROM information_schema.tables
                       WHERE table_schema = 'public' AND table_name = v_tbl)
          INTO v_src_exists;
        IF v_src_exists THEN
            RAISE EXCEPTION '搬迁后 public.% 仍存在 ⇒ 搬迁失败', v_tbl;
        END IF;

        EXECUTE format('SELECT count(*) FROM %I.%I', v_dst_schema, v_tbl) INTO v_rows;
        v_total_rows := v_total_rows + v_rows;
        v_moved := v_moved + 1;
    END LOOP;

    RAISE NOTICE 'V346 汇总：搬迁 % 张 / 跳过 % 张 / 迁移数据合计 % 行', v_moved, v_skipped, v_total_rows;

    -- 收口断言：public 侧不得再残留本脚本认领的任何表
    FOR i IN 1 .. array_length(v_map, 1) LOOP
        IF EXISTS (SELECT 1 FROM information_schema.tables
                   WHERE table_schema = 'public' AND table_name = v_map[i][1]) THEN
            RAISE EXCEPTION '收口失败：public.% 仍在', v_map[i][1];
        END IF;
    END LOOP;
END $$;

-- 事务开关：默认回滚（dry-run），传 -v ecos_commit=1 才提交
\if :{?ecos_commit}
    \if :ecos_commit
        COMMIT;
        \echo 'V346 已提交（ecos_commit=1）'
    \else
        ROLLBACK;
        \echo 'V346 已回滚（dry-run，未传 ecos_commit=1）'
    \endif
\else
    ROLLBACK;
    \echo 'V346 已回滚（dry-run，未传 ecos_commit=1）'
\endif