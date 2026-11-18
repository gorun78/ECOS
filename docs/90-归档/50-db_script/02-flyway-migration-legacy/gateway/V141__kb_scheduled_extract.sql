-- T1: 结构化抽取任务表 — K1 数据导入增强 Wave 1
-- kb_scheduled_extract: 结构化抽取任务调度表
CREATE TABLE IF NOT EXISTS ecos_knowledge.kb_scheduled_extract (
  id BIGSERIAL PRIMARY KEY,
  schedule_id VARCHAR(64) UNIQUE NOT NULL,
  name VARCHAR(128) NOT NULL,
  ontology_ids JSONB NOT NULL,
  mode VARCHAR(32) NOT NULL DEFAULT 'INCREMENTAL',
  period VARCHAR(16) NOT NULL,
  cron_expression VARCHAR(64) NOT NULL,
  next_run_at TIMESTAMP,
  enabled SMALLINT NOT NULL DEFAULT 1,
  last_run_at TIMESTAMP,
  last_status VARCHAR(32),
  created_by VARCHAR(64),
  created_at TIMESTAMP DEFAULT NOW(),
  updated_at TIMESTAMP DEFAULT NOW(),
  is_deleted SMALLINT DEFAULT 0
);
CREATE INDEX IF NOT EXISTS idx_kbsched_enabled ON ecos_knowledge.kb_scheduled_extract(enabled);
