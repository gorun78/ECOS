-- T1: 结构化抽取审计表 — K1 数据导入增强 Wave 1
-- kb_extract_audit: 抽取作业审计记录（含 tier/mode/status/耗时/行计数/错误）
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
