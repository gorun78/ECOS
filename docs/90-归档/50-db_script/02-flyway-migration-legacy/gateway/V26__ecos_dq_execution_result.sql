-- ============================================================
-- V26__ecos_dq_execution_result.sql — DQ规则执行结果表
-- ============================================================

CREATE TABLE IF NOT EXISTS ecos_dq_execution_result (
    id              VARCHAR(64)     PRIMARY KEY,
    rule_id         VARCHAR(64)     NOT NULL,
    passed          BOOLEAN         NOT NULL DEFAULT FALSE,
    total_rows      INTEGER         DEFAULT 0,
    failed_rows     INTEGER         DEFAULT 0,
    error_details   TEXT,
    executed_at     TIMESTAMP       DEFAULT NOW()
);

COMMENT ON TABLE  ecos_dq_execution_result                IS 'DQ规则执行结果表 — 记录每次规则执行的通过/失败情况';
COMMENT ON COLUMN ecos_dq_execution_result.id             IS '执行记录主键';
COMMENT ON COLUMN ecos_dq_execution_result.rule_id        IS '关联的规则ID，对应 ecos_dq_rule.id';
COMMENT ON COLUMN ecos_dq_execution_result.passed         IS '是否通过: true=全部通过, false=存在不满足的数据';
COMMENT ON COLUMN ecos_dq_execution_result.total_rows     IS '总行数';
COMMENT ON COLUMN ecos_dq_execution_result.failed_rows    IS '不满足规则的行数';
COMMENT ON COLUMN ecos_dq_execution_result.error_details  IS '错误详情(JSON)，包含失败行的样例数据';
COMMENT ON COLUMN ecos_dq_execution_result.executed_at    IS '执行时间';

CREATE INDEX IF NOT EXISTS idx_dq_exec_rule_id ON ecos_dq_execution_result(rule_id);
CREATE INDEX IF NOT EXISTS idx_dq_exec_executed_at ON ecos_dq_execution_result(executed_at DESC);
