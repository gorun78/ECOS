-- ============================================================
-- V6__ecos_data_quality.sql — 数据质量规则 & 问题持久化表
-- ============================================================

CREATE TABLE IF NOT EXISTS ecos_dq_rule (
    id              BIGSERIAL PRIMARY KEY,
    name            VARCHAR(255) NOT NULL,
    description     TEXT DEFAULT '',
    rule_type       VARCHAR(64) NOT NULL DEFAULT 'NOT_NULL',
    config_json     TEXT DEFAULT '{}',   -- JSON: {"threshold":95.0, "passRate":95.0}
    severity        VARCHAR(32) NOT NULL DEFAULT 'HIGH',
    enabled         BOOLEAN NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMP DEFAULT NOW(),
    updated_at      TIMESTAMP DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS ecos_dq_issue (
    id              BIGSERIAL PRIMARY KEY,
    rule_id         BIGINT NOT NULL,
    asset_id        VARCHAR(255) NOT NULL DEFAULT '',   -- "entity:entityId" e.g. "Customer:c005"
    description     TEXT DEFAULT '',
    status          VARCHAR(32) NOT NULL DEFAULT 'open',
    severity        VARCHAR(32) NOT NULL DEFAULT 'HIGH',
    created_at      TIMESTAMP DEFAULT NOW(),
    resolved_at     TIMESTAMP,
    CONSTRAINT fk_dq_issue_rule FOREIGN KEY (rule_id) REFERENCES ecos_dq_rule(id) ON DELETE CASCADE
);

-- Seed data (only insert if empty)
INSERT INTO ecos_dq_rule (name, description, rule_type, config_json, severity, enabled)
SELECT * FROM (VALUES
    ('非空检查','所有必填字段必须有值','NOT_NULL','{"threshold":100.0,"passRate":95.0}','HIGH',TRUE),
    ('唯一性检查','主键及业务唯一键不允许重复','UNIQUE','{"threshold":100.0,"passRate":98.0}','CRITICAL',TRUE),
    ('格式校验','日期/邮箱/手机号等字段格式合规','FORMAT','{"threshold":100.0,"passRate":88.0}','MEDIUM',TRUE),
    ('值域检查','数值字段在合法范围内','RANGE','{"threshold":100.0,"passRate":93.0}','HIGH',TRUE),
    ('一致性检查','关联表数据逻辑一致','CONSISTENCY','{"threshold":100.0,"passRate":91.0}','HIGH',TRUE)
) AS t
WHERE NOT EXISTS (SELECT 1 FROM ecos_dq_rule);

INSERT INTO ecos_dq_issue (rule_id, asset_id, description, status, severity, created_at)
SELECT r.id, t.asset_id, t.description, t.status, t.severity, t.created_at
FROM (VALUES
    ('非空检查','Customer:c005','credit_score 为空','open','HIGH','2026-06-10T08:00:00'::TIMESTAMP),
    ('值域检查','Supplier:s003','supply_capacity 超出范围','open','MEDIUM','2026-06-12T14:30:00'::TIMESTAMP),
    ('一致性检查','Invoice:inv008','customer_id 引用不存在','open','HIGH','2026-06-14T09:15:00'::TIMESTAMP),
    ('非空检查','Customer:c012','name 为空','resolved','HIGH','2026-06-08T10:00:00'::TIMESTAMP)
) AS t(rule_name, asset_id, description, status, severity, created_at)
JOIN ecos_dq_rule r ON r.name = t.rule_name
WHERE NOT EXISTS (SELECT 1 FROM ecos_dq_issue);
