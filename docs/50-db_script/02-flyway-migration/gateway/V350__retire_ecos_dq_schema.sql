-- ============================================================================
-- V350 — ecos_dq 并入数据引擎：清退 ecos_dq schema
-- ============================================================================
-- 授权（2026-11-18 用户裁决原话）:
--   「数据质量 dq 属于数据引擎的内容，应该并入 dataengine」
--   据此把此前挂起的 C-1/C-2/C-3 三选一钉死为 C-1：补 V350 执行 DROP。
--
-- 为什么需要本脚本（规范与实现的历史脱节）:
--   `.trae/rules/数据库访问规范.md` §二 一直写着「已消灭 schema（V200，2026-10-01）：
--   ecos_dq（11 表迁入 ecos_data 后 DROP）」。但归档的 V200__ai_tool_registry.sql
--   实为 AI 工具声明式注册表（F06-11），全文 0 处 ecos_dq；归档里也不存在任何
--   真正的 schema 治理脚本。⇒ 表确实按 R-1b② 迁进了 ecos_data（V165.1 逐张补建），
--   而 schema 容器从未被 DROP。本脚本补齐这一步。
--
-- 活体复核（2026-11-18 实测，非凭旧账）:
--   * ecos_dq schema 下 pg_class 全 relkind 计数 = 0（真空壳，本脚本对活库是 no-op）
--   * 零外部 FK 指向 ecos_dq（pg_constraint 实测 0）
--   * 零依赖对象（pg_depend 实测 0）
--   * 生产代码 ecos_dq.<table> 真 SQL = 0 处（24 处均为注释，已由 dcc54d05 订正为 ecos_data.*）
--   * ecos_data.dq_* 11 张全在；仅 dq_rule 有 37 行数据，其余 0 行
--   * 00_init/ 已不建 ecos_dq，已建 11 张 ecos_data.dq_*（目标态早已固化）
--
-- 净落点语义（本脚本同时是「文档表达」与「真实清理」）:
--   V111~V114 建 11 张 ecos_dq.dq_*，V342 建同名 11 张 ecos_data.dq_*。
--   DdlCodeSchemaAlignmentGateTest 按「单源跑完后的净落点」判定，
--   因此本脚本的逐表 DROP 会把这些表从净落点擦掉 ⇒ 门禁的 11 个 dq_* 重名告警消失。
--   注意：该门禁只认 `DROP TABLE <schema>.<table>`，不认 `DROP SCHEMA`
--   （DROP_TABLE 正则见门禁 :72），故必须逐表 DROP，不能只写 DROP SCHEMA。
--
-- IR03-EXEMPT: 用户裁决 2026-11-18「数据质量 dq 属于数据引擎的内容，应该并入 dataengine」
--               本脚本的清退动作据此授权；lint 按本行标记豁免（见 _win_tasks/db-migration-lint.ps1 §8）
--
-- 纪律: DR01 全 schema 限定 / 逐表 IF EXISTS 幂等 / 事后 DO $$ 断言
-- ============================================================================

-- ── 1. 逐表清退 ecos_dq（11 张，V111~V114 建，已被 V342 的 ecos_data.* 取代）──
-- 按 V111(5) / V112(2) / V113(2) / V114(2) 分组建表顺序列出，便于对账。
-- CASCADE 仅用于带走依赖；已实测无外部 FK 指入、无视图/函数依赖。

-- V111__ecos_dq_governance_v1.sql
DROP TABLE IF EXISTS ecos_dq.dq_alert_record  CASCADE;
DROP TABLE IF EXISTS ecos_dq.dq_rule          CASCADE;
DROP TABLE IF EXISTS ecos_dq.dq_rule_check    CASCADE;
DROP TABLE IF EXISTS ecos_dq.dq_rule_version  CASCADE;
DROP TABLE IF EXISTS ecos_dq.dq_work_order    CASCADE;

-- V112__ecos_dq_scoring_and_version.sql
DROP TABLE IF EXISTS ecos_dq.dq_score_asset    CASCADE;
DROP TABLE IF EXISTS ecos_dq.dq_score_snapshot CASCADE;

-- V113__ecos_dq_monitoring.sql
DROP TABLE IF EXISTS ecos_dq.dq_schedule CASCADE;
DROP TABLE IF EXISTS ecos_dq.dq_throttle CASCADE;

-- V114__ecos_dq_phase4.sql
DROP TABLE IF EXISTS ecos_dq.dq_knowledge_entry CASCADE;
DROP TABLE IF EXISTS ecos_dq.dq_report          CASCADE;

-- ── 2. 清退 schema 容器 ────────────────────────────────────────────────────
-- 前置：上方 11 张表已 DROP，且活库实测 0 对象 ⇒ 无 RESTRICT 风险。
DROP SCHEMA IF EXISTS ecos_dq;

-- ── 3. 事后复核：清退净落点必须与授权面一致 ─────────────────────────────────
DO $$
DECLARE
    v_n BIGINT;
    v_missing TEXT;
BEGIN
    -- a) ecos_dq schema 必须不存在
    IF EXISTS (SELECT 1 FROM pg_namespace WHERE nspname = 'ecos_dq') THEN
        RAISE EXCEPTION 'V350 复核失败：ecos_dq schema 仍在';
    END IF;
    RAISE NOTICE 'V350 复核：ecos_dq schema 已消灭 ✓';

    -- b) ecos_data 下 11 张 dq_* 必须全在（真身未误伤）
    SELECT count(*) INTO v_n
    FROM pg_class c JOIN pg_namespace n ON n.oid = c.relnamespace
    WHERE n.nspname = 'ecos_data' AND c.relkind = 'r'
      AND c.relname IN ('dq_alert_record','dq_rule','dq_rule_check','dq_rule_version',
                        'dq_work_order','dq_score_asset','dq_score_snapshot',
                        'dq_schedule','dq_throttle','dq_knowledge_entry','dq_report');
    IF v_n <> 11 THEN
        SELECT string_agg(t, ', ') INTO v_missing
        FROM unnest(ARRAY['dq_alert_record','dq_rule','dq_rule_check','dq_rule_version',
                          'dq_work_order','dq_score_asset','dq_score_snapshot',
                          'dq_schedule','dq_throttle','dq_knowledge_entry','dq_report']) t
        WHERE to_regclass('ecos_data.' || t) IS NULL;
        RAISE EXCEPTION 'V350 复核失败：ecos_data.dq_* 只找到 % 张（预期 11），缺失: %', v_n, v_missing;
    END IF;
    RAISE NOTICE 'V350 复核：ecos_data.dq_* 11 张真身保全 ✓';

    -- c) 零外键指向已消失的 schema
    SELECT count(*) INTO v_n
    FROM pg_constraint con
    JOIN pg_class c  ON c.oid  = con.confrelid
    JOIN pg_namespace n ON n.oid = c.relnamespace
    WHERE con.contype = 'f' AND n.nspname = 'ecos_dq';
    IF v_n <> 0 THEN
        RAISE EXCEPTION 'V350 复核失败：仍有 % 条外键指向 ecos_dq', v_n;
    END IF;
    RAISE NOTICE 'V350 复核：零外键指向 ecos_dq ✓';
END $$;

-- ── 4. 数据保全旁证（只打印，不阻断：dq_rule 37 行是当前 dev 基线）────────────
DO $$
DECLARE
    v_n BIGINT;
BEGIN
    SELECT count(*) INTO v_n FROM ecos_data.dq_rule;
    IF v_n < 1 THEN
        RAISE WARNING 'V350 提示：ecos_data.dq_rule 为 % 行（若从零建库后尚未灌种子，这是正常）', v_n;
    ELSE
        RAISE NOTICE 'V350 复核：ecos_data.dq_rule 保全 % 行 ✓', v_n;
    END IF;

    SELECT count(*) INTO v_n FROM ecos_data.ecos_dq_score_asset;
    RAISE NOTICE 'V350 旁证：ecos_data.ecos_dq_score_asset % 行（V165.1 建的同族另一侧，不在本次范围）', v_n;
END $$;
