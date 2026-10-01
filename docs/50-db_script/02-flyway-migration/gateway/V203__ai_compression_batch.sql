-- V203 (卷06 §六 E 章 6.2 新增 DDL): 会话压缩批次头表（F06-09 摘要批次，供状态条与降级可查）
-- 追溯: W151/C133；需求依据 REQ-AI-02
-- 批准: 需求检视报告 §十四 14.1（2026-09-29）之 R-19=①+② 并行、R-20=①；执行边界 §14.4 = 脚本文件落地
-- 状态: 仅落迁移脚本文件，**未对 ecos-postgres 实跑**（实跑属 §14.4 未授权项，需逐项再确认）
-- 纪律: DR01~DR08 / MC01~MC03 / ST03-A / ST07 / ST09 / IR02(手动 psql) / IR03(只加不删)
-- 归属口径: 文档以占位 <AI_SCHEMA>. 书写。**新表**按 R-19 ①"新表按 MC/DR 合规建于 ecos_ai"落
--   `ecos_ai.`（与 V202 不同：V202 是 ALTER 现有 public 真身，本表是纯新建合规表）；
--   session_id 为跨 schema 逻辑引用（指向 public.sys_agent_session.id，真身载体随 R-19 归位批迁移），
--   依 ST07 **不写跨 schema FOREIGN KEY**。
-- R-20 ① 关联: 本表是"token+逻辑归档"新语义的批次台账，仅记录切换后的新压缩事件；
--   存量 6 行消息不产生批次行、不重建。
-- 上线: psql -U postgres -d sys_man -f V203__ai_compression_batch.sql

-- ── 1. 新表 ──────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS ecos_ai.ecos_ai_compression_batch (
  id VARCHAR(36) NOT NULL,                       -- MC01: 应用侧生成 UUID，DDL 无默认值
  session_id VARCHAR(64) NOT NULL,               -- 外部业务键（现真身 public.sys_agent_session.id），跨 schema 无 FK
  mode VARCHAR(24) NOT NULL,                     -- LLM_SUMMARY | TRUNCATE_FALLBACK
  compressed_from_seq INTEGER,
  compressed_to_seq INTEGER,
  estimated_tokens_before INTEGER,
  estimated_tokens_after INTEGER,
  kept_key_refs TEXT,                            -- 摘要保留的关键引用
  dropped_key_refs TEXT,                         -- dropped 非空即降级证据（§2.2-2 抽检口径）
  failure_reason VARCHAR(120),
  trace_id VARCHAR(64),
  domain VARCHAR(50) NOT NULL DEFAULT 'default', -- DR08（文档 64/'DEFAULT' 收敛为规范形态）
  version_no VARCHAR(20) NOT NULL DEFAULT '1',   -- DR07（文档 INTEGER 收敛为 VARCHAR(20)）
  is_deleted SMALLINT NOT NULL DEFAULT 0,        -- DR05
  create_by VARCHAR(64) NOT NULL,
  update_by VARCHAR(64),
  create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,  -- DR06 审计五列
  update_time TIMESTAMP,
  CONSTRAINT pk_ecos_ai_compression_batch PRIMARY KEY (id),
  CONSTRAINT ck_acb_mode CHECK (mode IN ('LLM_SUMMARY','TRUNCATE_FALLBACK'))
);
COMMENT ON TABLE ecos_ai.ecos_ai_compression_batch IS '会话压缩批次头（F06-09；R-19 并建合规新表落 ecos_ai；session_id 跨 schema 逻辑引用不建 FK）';

-- ── 2. 索引（MC03：无 partial index）──────────────────────
CREATE INDEX IF NOT EXISTS idx_acb_session ON ecos_ai.ecos_ai_compression_batch (session_id, create_time);
CREATE INDEX IF NOT EXISTS idx_acb_mode    ON ecos_ai.ecos_ai_compression_batch (mode, create_time);

-- ── 回滚说明 ──────────────────────────────────────────────
-- 纯新建表，无存量数据改动；回滚 = 应用侧不读写本表即可（表保留为空表，IR03 禁 DROP）。

-- ── DDL Lint Self-audit ──────────────────────────────────
-- [DR01] ecos_ai 限定 ✓  [DR02] 新表 ecos_ai_compression_batch 带 ecos_ 前缀 ✓（文档字面缺前缀，已收口，见 V200 头注）
-- [DR03] 单数 ✓  [DR04] kept/dropped_key_refs 为引用串文本非 JSON 检索列 ✓  [DR05] is_deleted SMALLINT ✓
-- [DR06/07/08] 审计五列 + version_no VARCHAR(20) + domain 'default' ✓
-- [MC01] VARCHAR(36) 应用侧 UUID ✓  [MC02] 零 JSONB ✓
-- [MC03] 无 partial index/RLS/PARTITION/text[]/timestamptz/裸 cast ✓
-- [IR03] 零 DROP/零 ALTER 既有表 ✓  [ST07] 新表落 ecos_ai、跨 schema 引用不建 FK ✓
