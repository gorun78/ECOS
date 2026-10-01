-- V181 (卷04 §E.3 V181 行): kb_extract_audit / kg_sync_log 增 trace_id 链路列
-- 追溯: 卷04 §C.7（审计/日志链路可观测性缺口）→ W110/C92；门禁 = 列存在性
-- 批准: 需求检视报告 §十四 14.1（2026-09-29）之 R-12=①+②；执行边界 §14.4 = 脚本文件落地
-- 状态: 仅落迁移脚本文件，**未对 ecos-postgres 实跑**（实跑属 §14.4 未授权项，需逐项再确认）
-- 纪律: DR01~DR08 / MC01~MC03 / ST03-A / ST07 / IR02(手动 psql) / IR03(只加不删；R-12 DROP 例外前置未闭环 ⇒ 本批零 DROP)
--
-- 说明: V179/V180 并建的合规新表（ecos_knowledge.ecos_kb_extract_audit / ecos_kg_sync_log）已直接带
--   trace_id VARCHAR(64)；本脚本负责把列补到**旧表**（双写过渡期旧表仍可能被只读/回填工具消费）。
--   ADD COLUMN IF NOT EXISTS 幂等；若 live 表不存在（单源与 live 漂移）则报错前先探测跳过。

DO $$
DECLARE
    pairs RECORD;
BEGIN
    FOR pairs IN SELECT * FROM (VALUES
        ('ecos_knowledge', 'kb_extract_audit'),
        ('ecos_knowledge', 'kg_sync_log')
    ) AS t(tbl_schema, tbl_name) LOOP
        IF EXISTS (SELECT 1 FROM information_schema.tables
                   WHERE table_schema = pairs.tbl_schema AND table_name = pairs.tbl_name) THEN
            EXECUTE format('ALTER TABLE %I.%I ADD COLUMN IF NOT EXISTS trace_id VARCHAR(64)',
                           pairs.tbl_schema, pairs.tbl_name);
            EXECUTE format('COMMENT ON COLUMN %I.%I.trace_id IS %L',
                           pairs.tbl_schema, pairs.tbl_name,
                           'V181/§C.7: 全链路追踪 ID（与审计链/事件总线 ecos.audit 同键），可空（历史行为 NULL）');
            EXECUTE format(
                'CREATE INDEX IF NOT EXISTS idx_%s_trace_id ON %I.%I (trace_id)',
                pairs.tbl_name, pairs.tbl_schema, pairs.tbl_name);
        END IF;
    END LOOP;
END $$;

-- ── 回滚说明 ────────────────────────────────────────────────
-- 仅新增可空列 + 索引；回滚 = 应用侧不写 trace_id（列保留，IR03 不 DROP）。幂等可重复执行。

-- ── DDL Lint Self-audit ──────────────────────────────────
-- [DR01] schema 限定 ✓  [MC02/MC03] 零 JSONB/零 partial index/零 `::` ✓  trace_id VARCHAR(64) ✓
-- [ST07] 仅知识域 ecos_knowledge 表 ✓  [IR03] 只加不删 ✓
