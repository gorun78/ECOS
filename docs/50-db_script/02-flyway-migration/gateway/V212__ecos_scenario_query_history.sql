-- V212 (卷07 §六 E 章 E-2 目标形态): 场景查询历史表（纯新建，替换内存态实现，W185）
-- 追溯: W185/C167；需求依据 REQ-NF-04 / REQ-WS-02
-- 批准: 需求检视报告 §十四 14.1（2026-09-29）之 R-25=①、R-30=①+②；执行边界 §14.4 = 脚本文件落地
-- 状态: 仅落迁移脚本文件，**未对 ecos-postgres 实跑**（实跑属 §14.4 未授权项，需逐项再确认）
-- 纪律: DR01~DR08 / MC01~MC03 / ST03-A / ST07 / ST09 / IR02(手动 psql) / IR03(只加不删)
-- 背景: 现状 QueryHistoryService 用 CopyOnWriteArrayList 纯内存、MAX=200、无 traceId（X-55）
--   ⇒ 本表为唯一持久化落点；旧内存实现随 F07-21 改造废弃（代码侧，不属本批 DDL）。
-- 落点 (MC06): 控制域现基线 = public（目标 ecos_control，ADR-12）⇒ 新表落 public.，
--   迁 ecos_control 后由 `ecos.db.control-schema` 配置注入改前缀，本批不预置。
-- 安全红线（E-5/X-55 定版）: `sql_digest VARCHAR(128)` **禁存 SQL 原文与结果集**（防审计面变敏感面，
--   与分册 06 F06-03 同规则）；subject_id 为执行主体用户 ID（非 PII）。
-- 上线: psql -U postgres -d sys_man -f V212__ecos_scenario_query_history.sql

-- ── 1. 新表 ──────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS public.ecos_scenario_query_history (
    id          VARCHAR(36) PRIMARY KEY,          -- MC01: 应用侧生成 UUID，DDL 无默认值
    trace_id    VARCHAR(64),                      -- W185: MDC 贯通 / X-Request-Id 接力
    subject_id  VARCHAR(36) NOT NULL,             -- 执行主体（用户 ID）
    sql_digest  VARCHAR(128) NOT NULL,            -- 查询摘要（哈希/规范化串），**禁 SQL 原文**（E-5）
    row_count   INTEGER,                          -- 返回行数（仅计数，不存结果集）
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,  -- DR06 审计五列
    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    create_by   VARCHAR(64) NOT NULL DEFAULT 'system',
    update_by   VARCHAR(64) NOT NULL DEFAULT 'system',
    is_deleted  SMALLINT  NOT NULL DEFAULT 0,     -- DR05
    domain      VARCHAR(50) NOT NULL DEFAULT 'default',  -- DR08
    version_no  VARCHAR(20) NOT NULL DEFAULT '1'         -- DR07
);
COMMENT ON TABLE  public.ecos_scenario_query_history IS '场景查询历史台账（替换 X-55 内存态；sql_digest 摘要形态，禁存 SQL 原文/结果集）';
COMMENT ON COLUMN public.ecos_scenario_query_history.sql_digest IS '查询规范化摘要（防审计面变敏感面，与分册 06 F06-03 同规则）';

-- ── 2. 索引（MC03：无 partial index）──────────────────────
CREATE INDEX IF NOT EXISTS idx_esqh_subject ON public.ecos_scenario_query_history(subject_id, create_time);
CREATE INDEX IF NOT EXISTS idx_esqh_trace   ON public.ecos_scenario_query_history(trace_id);

-- ── 回滚说明 ──────────────────────────────────────────────
-- 纯新建表，无存量数据改动（内存态历史无处可迁）；回滚 = 应用侧不读写本表即可（表保留为空表，IR03 禁 DROP）。

-- ── DDL Lint Self-audit ──────────────────────────────────
-- [DR01] public 限定 ✓  [DR02] ecos_ 前缀 ✓  [DR03] 单数（history 为不可数名词，文档定版）✓
-- [DR04] 无 JSON 列 ✓  [DR05] is_deleted SMALLINT ✓  [DR06/07/08] 七基线列齐 ✓
-- [MC01] VARCHAR(36) UUID ✓  [MC02] 零 JSONB ✓
-- [MC03] 无 partial index/RLS/PARTITION/text[]/timestamptz/裸 cast ✓
-- [ST03-A] 无金额列；sql_digest 非敏感存储（摘要形态）✓  [ST07] 控制域落 public（MC06 注记）✓
-- [IR03] 零 DROP/零 ALTER 既有表 ✓
