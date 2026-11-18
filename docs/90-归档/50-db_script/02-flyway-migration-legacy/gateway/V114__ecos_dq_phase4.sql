-- ============================================================
-- V114__ecos_dq_phase4.sql
-- PMO-48-D T15 + T16 + T17: DQ 数据质量报告 + 知识沉淀 + 工单驳回原因
-- ============================================================
-- 来源: docs/3-data/PMO-48-D-数据质量报告与知识沉淀.md
--
-- 产出:
--   T15: dq_report          — 报告（日报/周报/月报，HTML+PDF，AI 摘要）
--   T16: dq_knowledge_entry — 知识条目（规则/告警/工单沉淀，供 RCA + RAG 相似检索）
--   T17: ALTER dq_work_order ADD reject_note
--
-- 铁律 3.1 遵循:
--   - schema 只加不删: 所有 CREATE 用 IF NOT EXISTS
--   - pgvector 依赖未启用（V107 仅 pg_trgm），embedding 列降级 TEXT(DIM=1024 JSON 数组字符串)
-- ============================================================

CREATE SCHEMA IF NOT EXISTS ecos_dq;

-- ============================================================
-- 1. dq_report — DQ 报告（PMO-48-D T15）
-- ============================================================
-- 一行 = 一份报告 (report_type=DAILY|WEEKLY|MONTHLY, scope=ALL|NATIVE|OFI|DOMAIN:xxx)
-- payload_html 内嵌 HTML（小文件，<= 200KB 直接存 PG TEXT）
-- pdf_object_key: 大文件 (>200KB) 存 MinIO dq-reports bucket，key 指向对象
CREATE TABLE IF NOT EXISTS ecos_dq.dq_report (
    id              VARCHAR(36)     PRIMARY KEY,
    report_type     VARCHAR(16)     NOT NULL,             -- DAILY / WEEKLY / MONTHLY
    scope           VARCHAR(32)     NOT NULL DEFAULT 'ALL',  -- ALL / NATIVE / OFI / DOMAIN:xxx
    start_date      DATE            NOT NULL,
    end_date        DATE            NOT NULL,
    payload_html    TEXT            NOT NULL,             -- 内嵌 HTML 渲染结果
    pdf_object_key  VARCHAR(512),                         -- MinIO dq-reports bucket 内 object key
    llm_summary     TEXT,                                 -- LLM 生成的摘要 (Phase 5 接 cognitive 后非空)
    row_count       INT             DEFAULT 0,            -- 报告覆盖的数据行
    alert_count     INT             DEFAULT 0,            -- 报告覆盖的告警数
    avg_score       DOUBLE PRECISION,                     -- 报告覆盖范围内的平均分 (0.0-1.0)
    created_by      VARCHAR(64),
    created_at      TIMESTAMP       NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_dq_report_type_date
    ON ecos_dq.dq_report (report_type, end_date DESC);
CREATE INDEX IF NOT EXISTS idx_dq_report_scope
    ON ecos_dq.dq_report (scope, end_date DESC);

COMMENT ON TABLE  ecos_dq.dq_report IS 'DQ 报告（PMO-48-D T15，日报/周报/月报，HTML+PDF，AI 摘要）';
COMMENT ON COLUMN ecos_dq.dq_report.id IS '报告 ID (VARCHAR(36) 主键)';
COMMENT ON COLUMN ecos_dq.dq_report.report_type IS '报告类型: DAILY/WEEKLY/MONTHLY';
COMMENT ON COLUMN ecos_dq.dq_report.scope IS '报告范围: ALL/NATIVE/OFI/DOMAIN:xxx';
COMMENT ON COLUMN ecos_dq.dq_report.start_date IS '报告起始日期（含）';
COMMENT ON COLUMN ecos_dq.dq_report.end_date IS '报告截止日期（含）';
COMMENT ON COLUMN ecos_dq.dq_report.payload_html IS '内嵌 HTML（小文件 <= 200KB，直接存 TEXT）';
COMMENT ON COLUMN ecos_dq.dq_report.pdf_object_key IS 'MinIO bucket=dq-reports 内 object key（PDF > 200KB 走对象存储）';
COMMENT ON COLUMN ecos_dq.dq_report.llm_summary IS 'LLM 生成摘要（PMO-48-D Phase 5 接 cognitive-engine 后非空）';
COMMENT ON COLUMN ecos_dq.dq_report.row_count IS '报告覆盖的数据行数';
COMMENT ON COLUMN ecos_dq.dq_report.alert_count IS '报告覆盖的告警位数';
COMMENT ON COLUMN ecos_dq.dq_report.avg_score IS '报告覆盖范围内平均分 0.0-1.0';

-- ============================================================
-- 2. dq_knowledge_entry — DQ 知识条目（PMO-48-D T16）
-- ============================================================
-- 知识条目：从 dq_rule_check / dq_alert_record / dq_work_order 沉淀，
-- 供 RCA + RAG 相似检索（embedding 维度 1024，pgvector 依赖未启用 → TEXT JSON 数组降级）
CREATE TABLE IF NOT EXISTS ecos_dq.dq_knowledge_entry (
    id             VARCHAR(36)    PRIMARY KEY,
    source_type    VARCHAR(16)    NOT NULL,               -- RULE / ALERT / WORK_ORDER
    source_id      VARCHAR(64)    NOT NULL,               -- 来源记录 ID
    category       VARCHAR(32),                           -- rule-hit / alert-pattern / fix-pattern
    title          VARCHAR(191)   NOT NULL,
    summary        TEXT,
    content_md     TEXT,
    entity_json    JSONB          DEFAULT '{}'::jsonb,    -- 结构化实体 (ruleName/assetId/ruleType 等)
    embedding      TEXT,                                  -- TEXT(DIM=1024)，存 JSON 数组字符串（pgvector 未启用降级）
    score_hint     DOUBLE PRECISION,                      -- 预计算相似分参考 (0.0-1.0)
    created_at     TIMESTAMP      NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_dq_knowledge_type_source
    ON ecos_dq.dq_knowledge_entry (source_type, source_id);
CREATE INDEX IF NOT EXISTS idx_dq_knowledge_category
    ON ecos_dq.dq_knowledge_entry (category);

COMMENT ON TABLE  ecos_dq.dq_knowledge_entry IS 'DQ 知识条目（PMO-48-D T16，规则/告警/工单沉淀，供 RCA + RAG 相似检索）';
COMMENT ON COLUMN ecos_dq.dq_knowledge_entry.id IS '知识条目 ID (VARCHAR(36))';
COMMENT ON COLUMN ecos_dq.dq_knowledge_entry.source_type IS '来源类型: RULE/ALERT/WORK_ORDER';
COMMENT ON COLUMN ecos_dq.dq_knowledge_entry.source_id IS '来源记录 ID（对应 dq_rule.id / dq_alert_record.id / dq_work_order.id）';
COMMENT ON COLUMN ecos_dq.dq_knowledge_entry.category IS '分类: rule-hit/alert-pattern/fix-pattern';
COMMENT ON COLUMN ecos_dq.dq_knowledge_entry.title IS '知识条目标题 (<= 191)';
COMMENT ON COLUMN ecos_dq.dq_knowledge_entry.summary IS '摘要（供列表展示）';
COMMENT ON COLUMN ecos_dq.dq_knowledge_entry.content_md IS '知识全文 Markdown';
COMMENT ON COLUMN ecos_dq.dq_knowledge_entry.entity_json IS '结构化实体 JSONB (ruleName/assetId/ruleType 等)';
COMMENT ON COLUMN ecos_dq.dq_knowledge_entry.embedding IS '向量列（pgvector 未装，本版本降级 TEXT 存 JSON 数组字符串 DIM=1024）';
COMMENT ON COLUMN ecos_dq.dq_knowledge_entry.score_hint IS '预计算相似分参考 (0.0-1.0)';

-- ============================================================
-- 3. dq_work_order ADD reject_note (PMO-48-D T17)
-- ============================================================
-- Phase 3 原 reject 文本拼入 description，Phase 4 独立列
ALTER TABLE ecos_dq.dq_work_order ADD COLUMN IF NOT EXISTS reject_note TEXT;
COMMENT ON COLUMN ecos_dq.dq_work_order.reject_note IS '驳回原因（PMO-48-D T17，Phase 3 原文拼入 description，Phase 4 独立列）';

-- ============================================================
-- END V114
-- ============================================================
