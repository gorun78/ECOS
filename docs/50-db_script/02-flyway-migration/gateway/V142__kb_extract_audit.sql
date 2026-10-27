-- T1: 结构化抽取审计表 — K1 数据导入增强 Wave 1
-- kb_extract_audit: 抽取作业审计记录（含 tier/mode/status/耗时/行计数/错误）
--
-- 【#146h knownLegacy 注记 2026-10-27】本脚本 dev PG 从未实跑（kb_extract_audit
-- 库面不存在，0 行历史；BIGSERIAL PK 不合 MC01）。live 承接表 =
-- V179 合规新表 ecos_knowledge.ecos_kb_extract_audit（VARCHAR(36) 应用侧 UUID PK，
-- tier→extract_tier / mode→extract_mode / created_at→create_time）。
-- #146h 起全部生产 DAO 读写端（KbExtractAuditDao 写腿 / StructuredExtractQueryDao 读腿）
-- 已切合规新表（映射台账 §D-7 裁决，kb-engine 回潮门禁 KbExtractBareTableGateTest 钉位）。
-- 本脚本判 knownLegacy 停再跑，不 DROP（IR03）；本头注只登记不改 DDL 本体。
CREATE TABLE IF NOT EXISTS ecos_knowledge.kb_extract_audit (
  id BIGSERIAL PRIMARY KEY,
  job_id VARCHAR(64) NOT NULL,
  task_id VARCHAR(64),
  tier VARCHAR(32) NOT NULL DEFAULT 'standard',
  mode VARCHAR(32),
  status VARCHAR(32),
  duration_ms BIGINT,
  rows_total BIGINT,
  rows_ok BIGINT,
  rows_failed BIGINT,
  mismatched BIGINT,
  error_message TEXT,
  suggestion TEXT,
  created_at TIMESTAMP DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_kbea_job ON ecos_knowledge.kb_extract_audit(job_id);
CREATE INDEX IF NOT EXISTS idx_kbea_ts ON ecos_knowledge.kb_extract_audit(created_at DESC);
