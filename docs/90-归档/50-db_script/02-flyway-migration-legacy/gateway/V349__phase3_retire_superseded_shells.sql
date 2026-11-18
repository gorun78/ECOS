-- ============================================================================
-- V349 — 阶段3 清退已被取代的平行壳表与废弃 schema（无争议面）
-- ============================================================================
-- 授权（2026-11-17 用户裁决，两轮）:
--   第一轮「授权全部清理面」；第二轮在收到反证后订正为「先做无争议面」——
--   引擎侧 0 行副本一律暂不动（它们可能是重名表族的搬迁目标，属 task #31 逐族裁定面）。
--   本脚本只处置下列四类**与重名表族裁定无关**的对象。
--
-- 反证订正（推翻本清单上一轮的「路线乙：认 control 为全部重名族权威」）:
--   V191 头部批准依据 = 需求检视报告 §14.1 R-13①「认知资产+心智状态全归 ecos_cognitive」；
--   V322 头部 = DD-4「ecos_ontology.ecos_ontology_entity 是单源锚点」；
--   CognitiveCrossDomainTest 例外依据 = PRD-05 §6.2 REQ-COG-07 规格5。
--   ⇒ 引擎域族的权威是引擎 schema，不是 control。故本脚本**不 DROP 任何引擎侧副本**。
--
-- 清退面（逐项活体复核，非凭旧账）:
--   A. public 被 ecos_control 同名副本遮蔽的 23 张壳 —— 逐张 count(*) 全 0。
--      search_path 首位恒为 ecos_control（8 个部署模块 JDBC URL 实测一致），
--      裸名永不落 public ⇒ 这 23 张是死壳。
--   B. ecos_control.users（2 行）—— 登录权威已由 V347 迁到 td_user，镜像层已删，
--      活体回归 52 PASS/0 FAIL 证实新账号不再写 users；无 FK/视图依赖（实测）。
--   C. ecos_sysman schema（8 表，全 0 行）—— main 代码零限定名引用（剥注释后实测 0 命中），
--      且不在任何部署模块的 currentSchema 链内。
--   D. ecos_security schema（7 父表 + 46 分区子表 + 1 序列，父表全 0 行）—— 同上零引用、
--      不在 search_path 链内；无外部 FK 指入、无视图依赖（实测）。
--      7 父表 = 5 张普通表（relkind='r'）+ 2 张**分区父表**（relkind='p'：
--      ecos_token_usage / td_audit_log）。只筛 relkind='r' 会漏掉后两张，
--      本轮 dry-run 因此被 DROP SCHEMA 的 RESTRICT 挡下，已补全。
--      注：旧账「51 张全 0 行」指的是 relkind='r' 的物理表数（5 非分区 + 46 分区子表），
--      口径正确但不含 2 张分区父表；本轮一度误算 249，系漏 relkind 过滤把分区索引计入，已订正。
--
-- 刻意不动（挂起登记，待裁定）:
--   * 48 个重名表族的引擎侧副本 —— 属 task #31 逐族按 DDL 意图裁定面。
--   * public 独有 7 张（ecos_decision_source_ref / ecos_fc_action_ext /
--     ecos_knowledge_document / ecos_ontology_domain / ecos_runtime_llm_usage /
--     ecos_scenario_active_mind / ecos_scenario_mind_ref）—— 单源 V211/V218/V47/V120/
--     V245/V208 均建在 public，零真 SQL 引用，无模块归属信号 ⇒ 不臆造归属。
--     其中 public.ecos_ontology_domain 是 D132 记载的空壳（真身 ecos_ontology.ecos_domain，5 行），
--     但单源 V120 仍建它，DROP 会与单源冲突，留待族裁定一并处置。
--
-- 前置接缝（V346 头部已登记）:
--   V346 把 public.ecos_glossary_term_relation 搬去 ecos_ontology 后，其两条外键
--   fk_glossary_relation_from / fk_glossary_relation_to 仍指向 public.ecos_glossary_term。
--   本脚本先把这两条改指权威副本 ecos_control.ecos_glossary_term，再 DROP 该 public 壳。
--
-- 纪律: IR02（手动 psql，Flyway 永久禁用）/ DR01（全 schema 限定）/
--       MC03（禁 ON CONFLICT、禁 RETURNING、禁 :: cast）/
--       IR03 例外 = 本次 DROP 经用户逐项授权，且备份已落
--       Temp/pre_phase3_backup.sql（9798 行 / 120 CREATE TABLE / 118 COPY 段）。
--
-- 运行:
--   dry-run（默认，事务回滚只打印）:
--     docker exec -i ecos-postgres psql -U postgres -d sys_man -v ON_ERROR_STOP=1 -f - \
--       < docs/50-db_script/02-flyway-migration/gateway/V349__phase3_retire_superseded_shells.sql
--   实跑（须先经用户逐项授权 + 全库备份）: 追加 -v ecos_commit=1
-- ============================================================================

BEGIN;

-- ── 0. 前置守卫：清退目标必须与授权时的实测形态一致，不符即 fail-loud ──────
DO $$
DECLARE
    v_shell     TEXT;
    v_n         BIGINT;
    v_shells    TEXT[] := ARRAY[
        'dict_column','dict_table','ecos_audit_log','ecos_cls_policy','ecos_glossary_term',
        'ecos_marketplace_access_request','ecos_marketplace_asset','ecos_mask_strategy',
        'ecos_ontology_entity','ecos_ontology_property','ecos_ontology_relationship',
        'ecos_rls_policy','ecos_security_crypto_key','ecos_spans','ecos_token_usage',
        'schema_changes','schema_snapshots','sys_token_blacklist','td_runtime_task',
        'td_runtime_task_execution','td_runtime_task_log','td_runtime_task_plan',
        'td_runtime_task_status'
    ];
    v_sysman    TEXT[] := ARRAY[
        'demo_customer','demo_invoice','demo_supplier','ecos_tenant',
        'ecos_tenant_quota','ecos_tenant_usage','sys_config','sys_dict'
    ];
    v_security  TEXT[] := ARRAY[
        'ecos_alert_history','ecos_spans','td_abac_policy',
        'td_role_security_profile','td_user_security_profile',
        'ecos_token_usage','td_audit_log'
    ];
    v_tbl       TEXT;
BEGIN
    -- A. 23 张 public 壳：必须存在、必须被 control 同名遮蔽、必须 0 行
    FOREACH v_shell IN ARRAY v_shells LOOP
        IF NOT EXISTS (SELECT 1 FROM pg_class c JOIN pg_namespace n ON n.oid = c.relnamespace
                       WHERE n.nspname = 'public' AND c.relname = v_shell AND c.relkind = 'r') THEN
            RAISE EXCEPTION 'V349 守卫失败：public.% 不存在（形态已变，需重新取证）', v_shell;
        END IF;
        IF NOT EXISTS (SELECT 1 FROM pg_class c JOIN pg_namespace n ON n.oid = c.relnamespace
                       WHERE n.nspname = 'ecos_control' AND c.relname = v_shell AND c.relkind = 'r') THEN
            RAISE EXCEPTION 'V349 守卫失败：ecos_control.% 权威副本不存在，public.% 不是死壳', v_shell, v_shell;
        END IF;
        EXECUTE format('SELECT count(*) FROM public.%I', v_shell) INTO v_n;
        IF v_n <> 0 THEN
            RAISE EXCEPTION 'V349 守卫失败：public.% 有 % 行（授权时实测 0 行），禁 DROP', v_shell, v_n;
        END IF;
    END LOOP;
    RAISE NOTICE 'V349 守卫A：23 张 public 壳均存在、均被 control 遮蔽、均 0 行 ✓';

    -- B. users：登录权威必须已在 td_user，否则不得清退
    IF NOT EXISTS (SELECT 1 FROM pg_class c JOIN pg_namespace n ON n.oid = c.relnamespace
                   WHERE n.nspname = 'ecos_control' AND c.relname = 'td_user' AND c.relkind = 'r') THEN
        RAISE EXCEPTION 'V349 守卫失败：td_user 不存在，登录权威未就位，禁 DROP users';
    END IF;
    SELECT count(*) INTO v_n FROM ecos_control.td_user WHERE username = 'admin';
    IF v_n <> 1 THEN
        RAISE EXCEPTION 'V349 守卫失败：td_user 里 admin 不唯一（% 行），禁 DROP users', v_n;
    END IF;
    SELECT count(*) INTO v_n FROM ecos_control.users;
    RAISE NOTICE 'V349 守卫B：td_user 权威就位，users 存量 % 行（已备份，授权清退）✓', v_n;

    -- C/D. 两个废弃 schema：父表必须全 0 行
    FOREACH v_tbl IN ARRAY v_sysman LOOP
        EXECUTE format('SELECT count(*) FROM ecos_sysman.%I', v_tbl) INTO v_n;
        IF v_n <> 0 THEN
            RAISE EXCEPTION 'V349 守卫失败：ecos_sysman.% 有 % 行，禁 DROP', v_tbl, v_n;
        END IF;
    END LOOP;
    RAISE NOTICE 'V349 守卫C：ecos_sysman 8 表全 0 行 ✓';

    FOREACH v_tbl IN ARRAY v_security LOOP
        EXECUTE format('SELECT count(*) FROM ecos_security.%I', v_tbl) INTO v_n;
        IF v_n <> 0 THEN
            RAISE EXCEPTION 'V349 守卫失败：ecos_security.% 有 % 行，禁 DROP', v_tbl, v_n;
        END IF;
    END LOOP;
    RAISE NOTICE 'V349 守卫D：ecos_security 7 父表（含 2 张分区父表）全 0 行 ✓';

    -- 外键改指的前提：权威副本必须有 id 上的主键/唯一约束
    IF NOT EXISTS (SELECT 1 FROM pg_constraint con
                   JOIN pg_class c ON c.oid = con.conrelid
                   JOIN pg_namespace n ON n.oid = c.relnamespace
                   WHERE n.nspname = 'ecos_control' AND c.relname = 'ecos_glossary_term'
                     AND con.contype IN ('p','u')) THEN
        RAISE EXCEPTION 'V349 守卫失败：ecos_control.ecos_glossary_term 无主键/唯一约束，外键无法改指';
    END IF;
    RAISE NOTICE 'V349 守卫E：ecos_control.ecos_glossary_term 可作外键目标 ✓';
END
$$;

-- ── 1. 解 V346 遗留接缝：两条外键改指权威副本 ─────────────────────────────
ALTER TABLE ecos_ontology.ecos_glossary_term_relation
    DROP CONSTRAINT IF EXISTS fk_glossary_relation_from;
ALTER TABLE ecos_ontology.ecos_glossary_term_relation
    ADD CONSTRAINT fk_glossary_relation_from
    FOREIGN KEY (from_term_id) REFERENCES ecos_control.ecos_glossary_term (id) ON DELETE CASCADE;

ALTER TABLE ecos_ontology.ecos_glossary_term_relation
    DROP CONSTRAINT IF EXISTS fk_glossary_relation_to;
ALTER TABLE ecos_ontology.ecos_glossary_term_relation
    ADD CONSTRAINT fk_glossary_relation_to
    FOREIGN KEY (to_term_id) REFERENCES ecos_control.ecos_glossary_term (id) ON DELETE CASCADE;

-- ── 2. 清退 A：23 张被遮蔽的 public 死壳 ──────────────────────────────────
-- 逐张显式列出（不用动态拼 DROP，便于审计与回放）；IF EXISTS 保证重跑幂等。
DROP TABLE IF EXISTS public.dict_column;
DROP TABLE IF EXISTS public.dict_table;
DROP TABLE IF EXISTS public.ecos_audit_log;
DROP TABLE IF EXISTS public.ecos_cls_policy;
DROP TABLE IF EXISTS public.ecos_glossary_term;
DROP TABLE IF EXISTS public.ecos_marketplace_access_request;
DROP TABLE IF EXISTS public.ecos_marketplace_asset;
DROP TABLE IF EXISTS public.ecos_mask_strategy;
DROP TABLE IF EXISTS public.ecos_ontology_entity;
DROP TABLE IF EXISTS public.ecos_ontology_property;
DROP TABLE IF EXISTS public.ecos_ontology_relationship;
DROP TABLE IF EXISTS public.ecos_rls_policy;
DROP TABLE IF EXISTS public.ecos_security_crypto_key;
DROP TABLE IF EXISTS public.ecos_spans;
DROP TABLE IF EXISTS public.ecos_token_usage;
DROP TABLE IF EXISTS public.schema_changes;
DROP TABLE IF EXISTS public.schema_snapshots;
DROP TABLE IF EXISTS public.sys_token_blacklist;
DROP TABLE IF EXISTS public.td_runtime_task;
DROP TABLE IF EXISTS public.td_runtime_task_execution;
DROP TABLE IF EXISTS public.td_runtime_task_log;
DROP TABLE IF EXISTS public.td_runtime_task_plan;
DROP TABLE IF EXISTS public.td_runtime_task_status;

-- ── 3. 清退 B：登录误会表 users（权威已迁 td_user）────────────────────────
DROP TABLE IF EXISTS ecos_control.users;

-- ── 4. 清退 D：ecos_security（7 父表 + 46 分区子表 + 1 序列）──────────────
-- 必须先于 ecos_sysman：fk_security_spans_tenant 由 ecos_security.ecos_spans
-- 指向 ecos_sysman.ecos_tenant，反序会被 RESTRICT 挡下（dry-run 实证）。
-- CASCADE 只用于带走各自的分区子表；已实测无外部 FK 指入、无视图依赖。
-- 注：ecos_token_usage / td_audit_log 是**分区父表**（pg_class.relkind='p'），
--     只筛 relkind='r' 会漏掉它们（dry-run 实证：DROP SCHEMA 被 RESTRICT 挡下）。
DROP TABLE IF EXISTS ecos_security.ecos_alert_history CASCADE;
DROP TABLE IF EXISTS ecos_security.ecos_spans CASCADE;
DROP TABLE IF EXISTS ecos_security.td_abac_policy CASCADE;
DROP TABLE IF EXISTS ecos_security.td_role_security_profile CASCADE;
DROP TABLE IF EXISTS ecos_security.td_user_security_profile CASCADE;
DROP TABLE IF EXISTS ecos_security.ecos_token_usage CASCADE;
DROP TABLE IF EXISTS ecos_security.td_audit_log CASCADE;
DROP SCHEMA IF EXISTS ecos_security;

-- ── 5. 清退 C：ecos_sysman（knownLegacy，只停写不迁移 ⇒ 本次经授权清退）──
-- 先子后父：quota/usage 外键指 tenant。
DROP TABLE IF EXISTS ecos_sysman.ecos_tenant_quota;
DROP TABLE IF EXISTS ecos_sysman.ecos_tenant_usage;
DROP TABLE IF EXISTS ecos_sysman.demo_customer;
DROP TABLE IF EXISTS ecos_sysman.demo_invoice;
DROP TABLE IF EXISTS ecos_sysman.demo_supplier;
DROP TABLE IF EXISTS ecos_sysman.sys_config;
DROP TABLE IF EXISTS ecos_sysman.sys_dict;
DROP TABLE IF EXISTS ecos_sysman.ecos_tenant;
DROP SCHEMA IF EXISTS ecos_sysman;

-- ── 6. 事后复核：清退净落点必须与授权面一致 ───────────────────────────────
DO $$
DECLARE
    v_n BIGINT;
BEGIN
    SELECT count(*) INTO v_n FROM pg_class c JOIN pg_namespace n ON n.oid = c.relnamespace
    WHERE n.nspname = 'public' AND c.relkind = 'r' AND NOT c.relispartition;
    IF v_n <> 7 THEN
        RAISE EXCEPTION 'V349 复核失败：public 剩 % 张基表（预期 7 张独有表）', v_n;
    END IF;
    RAISE NOTICE 'V349 复核：public 剩 7 张独有表（挂起待裁定面）✓';

    IF EXISTS (SELECT 1 FROM pg_namespace WHERE nspname IN ('ecos_sysman','ecos_security')) THEN
        RAISE EXCEPTION 'V349 复核失败：废弃 schema 未清净';
    END IF;
    RAISE NOTICE 'V349 复核：ecos_sysman / ecos_security 已消灭 ✓';

    IF EXISTS (SELECT 1 FROM pg_class c JOIN pg_namespace n ON n.oid = c.relnamespace
               WHERE n.nspname = 'ecos_control' AND c.relname = 'users') THEN
        RAISE EXCEPTION 'V349 复核失败：users 仍在';
    END IF;
    SELECT count(*) INTO v_n FROM ecos_control.td_user;
    IF v_n < 1 THEN
        RAISE EXCEPTION 'V349 复核失败：td_user 被误伤（% 行）', v_n;
    END IF;
    RAISE NOTICE 'V349 复核：users 已清退，td_user 保全 % 行 ✓', v_n;

    SELECT count(*) INTO v_n FROM ecos_control.ecos_glossary_term;
    RAISE NOTICE 'V349 复核：权威 ecos_control.ecos_glossary_term 保全 % 行 ✓', v_n;

    SELECT count(*) INTO v_n FROM pg_constraint con
    JOIN pg_class c ON c.oid = con.conrelid
    JOIN pg_namespace n ON n.oid = c.relnamespace
    JOIN pg_class t ON t.oid = con.confrelid
    JOIN pg_namespace tn ON tn.oid = t.relnamespace
    WHERE con.contype = 'f' AND tn.nspname = 'public';
    IF v_n <> 0 THEN
        RAISE EXCEPTION 'V349 复核失败：仍有 % 条外键指向 public', v_n;
    END IF;
    RAISE NOTICE 'V349 复核：零外键指向 public ✓';
END
$$;

-- 事务开关：默认回滚（dry-run），传 -v ecos_commit=1 才提交
\if :{?ecos_commit}
    \if :ecos_commit
        COMMIT;
        \echo 'V349 已提交（ecos_commit=1）'
    \else
        ROLLBACK;
        \echo 'V349 已回滚（dry-run，未传 ecos_commit=1）'
    \endif
\else
    ROLLBACK;
    \echo 'V349 已回滚（dry-run，未传 ecos_commit=1）'
\endif