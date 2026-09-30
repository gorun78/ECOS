-- V204 (卷06 §六 E 章 6.2 新增 DDL): Guardrail 裁决台账表（F06-03 对账可查询面，例外可先落：纯补建、无归属争议）
-- 追溯: W143/C125；需求依据 REQ-AI-01
-- 批准: 需求检视报告 §十四 14.1（2026-09-29）之 R-18=②、R-19=①+② 并行；执行边界 §14.4 = 脚本文件落地
--   （分册 06 §6.2 注：V204 与分册 04 V175 同属"纯补建缺失表、无归属争议"例外，同批落地）
-- 状态: 仅落迁移脚本文件，**未对 ecos-postgres 实跑**（实跑属 §14.4 未授权项，需逐项再确认）
-- 纪律: DR01~DR08 / MC01~MC03 / ST03-A / ST07 / ST09 / IR02(手动 psql) / IR03(只加不删)
-- 定性: 这不是"新业务表"，而是裁决事件的**索引化落点**，与 Kafka ecos.audit 同批双写
--   （PRD §3.2 禁的是把计量落业务表；裁决台账属审计面，经安全）。
-- R-18 ② 口径（DDL 侧）: 取密/ABAC 一律经 security-engine REST ⇒ **不建策略副本表**。
--   policy_id / obligations 两列是 security-engine 裁决响应的**时点缓存**（裁决权威与策略本体在
--   security-engine / OPA，本表列仅供本地对账与检索，不作为任何运行期判定输入）。
--   subject_snapshot 仅存 userId/roles/department 三要素快照，**禁入参原文与任何凭据**（X-23 教训）。
-- 上线: psql -U postgres -d sys_man -f V204__ai_guardrail_decision_ledger.sql

-- ── 1. 新表 ──────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS ecos_ai.ecos_ai_guardrail_decision_ledger (
  id VARCHAR(36) NOT NULL,                       -- MC01: 应用侧生成 UUID，DDL 无默认值
  trace_id VARCHAR(64) NOT NULL,                 -- 全链 traceId（MDC 贯通，与 SSE 诊断码同源）
  session_id VARCHAR(64),                        -- 外部业务键（public.sys_agent_session.id 真身），无 FK
  tool_key VARCHAR(120) NOT NULL,                -- 逻辑引用 ecos_ai.ecos_ai_tool_registry.tool_key
  decision VARCHAR(20) NOT NULL,                 -- ALLOW | DENY | FAIL_CLOSED
  policy_id VARCHAR(120),                        -- security-engine 裁决响应缓存（权威在 security-engine，非策略副本）
  obligations TEXT,                              -- mask/rowFilter 义务形态缓存（验收对账用，非运行期判定输入）
  latency_ms INTEGER,                            -- 裁决耗时（对齐 2s 预算，W145）
  subject_snapshot TEXT,                         -- 仅 userId/roles/department，禁入参原文
  domain VARCHAR(50) NOT NULL DEFAULT 'default', -- DR08（文档 64/'DEFAULT' 收敛为规范形态）
  version_no VARCHAR(20) NOT NULL DEFAULT '1',   -- DR07（文档 INTEGER 收敛为 VARCHAR(20)）
  is_deleted SMALLINT NOT NULL DEFAULT 0,        -- DR05
  create_by VARCHAR(64) NOT NULL,
  update_by VARCHAR(64),
  create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,  -- DR06 审计五列
  update_time TIMESTAMP,
  CONSTRAINT pk_agdl PRIMARY KEY (id),
  CONSTRAINT ck_agdl_decision CHECK (decision IN ('ALLOW','DENY','FAIL_CLOSED'))
);
COMMENT ON TABLE ecos_ai.ecos_ai_guardrail_decision_ledger IS 'Guardrail 裁决台账（F06-03 对账可查询面；policy/obligations 仅为 security-engine 裁决响应缓存，权威在 security-engine）';

-- ── 2. 索引（MC03：无 partial index）──────────────────────
CREATE INDEX IF NOT EXISTS idx_agdl_trace ON ecos_ai.ecos_ai_guardrail_decision_ledger (trace_id);
CREATE INDEX IF NOT EXISTS idx_agdl_tool  ON ecos_ai.ecos_ai_guardrail_decision_ledger (tool_key, decision, create_time);

-- ── 回滚说明 ──────────────────────────────────────────────
-- 纯新建表，无存量数据改动；回滚 = 应用侧不读写本表即可（表保留为空表，IR03 禁 DROP）。

-- ── DDL Lint Self-audit ──────────────────────────────────
-- [DR01] ecos_ai 限定 ✓  [DR02] 新表 ecos_ai_guardrail_decision_ledger 带 ecos_ 前缀 ✓（文档字面缺前缀，已收口，见 V200 头注）
-- [DR03] 单数 ✓  [DR04] obligations/subject_snapshot 为文本快照非 JSON 检索列（无 WHERE/JOIN 依赖）✓
-- [DR05] is_deleted SMALLINT ✓  [DR06/07/08] 审计五列 + version_no VARCHAR(20) + domain 'default' ✓
-- [MC01] VARCHAR(36) 应用侧 UUID ✓  [MC02] 零 JSONB、零裸 NUMERIC ✓
-- [MC03] 无 partial index/RLS/PARTITION/text[]/timestamptz/裸 cast ✓
-- [R-18②] 未建策略副本表；policy_id/obligations 已注释"权威在 security-engine，本列仅缓存" ✓
-- [IR03] 零 DROP/零 ALTER 既有表 ✓  [ST07] 落 ecos_ai ✓
