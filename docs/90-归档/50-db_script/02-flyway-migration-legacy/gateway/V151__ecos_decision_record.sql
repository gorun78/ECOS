-- V151__ecos_decision_record.sql — 场景决策回执表（PMO-60 v2.0 P3b T26）
-- 策略(policy)四件套输出的可选落盘回执，支持 action_plan 存档 + source_refs hash 溯源
CREATE TABLE IF NOT EXISTS ecos_decision_record (
    id              VARCHAR(64) PRIMARY KEY,              -- dec_xxxxxxxx
    scenario_id     VARCHAR(64) NOT NULL,
    mind_id         BIGINT,
    action_plan     JSONB NOT NULL DEFAULT '[]'::jsonb,   -- 处置方案数组
    source_refs     JSONB NOT NULL DEFAULT '[]'::jsonb,   -- 推理输出 hash 溯源 [{ep, hash, mind_id}]
    proposed_by     VARCHAR(64),
    accepted_by     VARCHAR(64),
    accepted_at     TIMESTAMP,
    compliance_check JSONB NOT NULL DEFAULT '{}'::jsonb,  -- 合规校验结果
    create_time     TIMESTAMP NOT NULL DEFAULT NOW(),
    update_time     TIMESTAMP NOT NULL DEFAULT NOW(),
    create_by       VARCHAR(64) NOT NULL DEFAULT 'system',
    update_by       VARCHAR(64) NOT NULL DEFAULT 'system',
    is_deleted      SMALLINT NOT NULL DEFAULT 0
);
CREATE INDEX IF NOT EXISTS idx_decision_scenario ON ecos_decision_record (scenario_id, is_deleted);
CREATE INDEX IF NOT EXISTS idx_decision_mind ON ecos_decision_record (mind_id, is_deleted);

COMMENT ON TABLE ecos_decision_record IS '场景决策回执（策略端点可选落盘 + 推理输出 hash 溯源）';
COMMENT ON COLUMN ecos_decision_record.action_plan IS '处置方案 JSON：[{action, justification, tradeoff_cost}]';
COMMENT ON COLUMN ecos_decision_record.source_refs IS '推理输出 hash：[{ep, hash, mind_id, timestamp}]';
