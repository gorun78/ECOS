-- ============================================================
-- V130__ecos_cognitive_run_invalidation.sql — 失效→作废推演结论 关联留痕表（PMO-59 P3b T2 / ADR-9）
-- 定位: 假设失效事件（ecos.cognitive COGNITIVE_HYPOTHESIS_INVALIDATED）联动作废
--       引用该假设的未决推演结论（ecos_scenario_run）时的事件↔run 关联留痕，
--       支撑"作废联动"幂等去重（eventId+runId 唯一）与复盘溯源（P3b T4 聚合输入之一）。
-- 口径: ADR-9 心智状态落盘 —— 本表=心智事件触发的推演作废旧数据，随事件链留痕。
-- 只加不删（铁律）：单张新表 + 唯一/普通索引，0 触碰既有表/列。
-- ============================================================

CREATE TABLE IF NOT EXISTS ecos_cognitive_run_invalidation (
    id                  VARCHAR(64) PRIMARY KEY,
    event_id            VARCHAR(64)  NOT NULL,      -- 触发作废的 ecos.cognitive 事件 id（cog_evt_ 前缀）
    hypothesis_id       VARCHAR(64)  NOT NULL,      -- 失效假设主键（引用 ecos_cognitive_hypothesis.id）
    run_id              VARCHAR(64)  NOT NULL,      -- 被作废推演 run（引用 ecos_scenario_run.id）
    auto_detected       BOOLEAN      NOT NULL DEFAULT TRUE, -- 失效是否自动检测触发
    superseded_at       TIMESTAMP    NOT NULL DEFAULT NOW(), -- 作废时刻
    detail              TEXT,                        -- 作废旧化摘要（事件 invalidReason 摘要等）
    create_time         TIMESTAMP    NOT NULL DEFAULT NOW(),
    update_time         TIMESTAMP    NOT NULL DEFAULT NOW(),
    create_by           VARCHAR(64)  DEFAULT 'system',
    update_by           VARCHAR(64)  DEFAULT 'system',
    is_deleted          SMALLINT     NOT NULL DEFAULT 0
);

-- 幂等键：同一事件对同一 run 只作废一次（重复投递不重复留痕）
CREATE UNIQUE INDEX IF NOT EXISTS uniq_ecos_cog_run_inv_evt_run
    ON ecos_cognitive_run_invalidation(event_id, run_id);
-- 复盘聚合热路径：按 run 反查被何事件作废 / 按事件查影响面
CREATE INDEX IF NOT EXISTS idx_ecos_cog_run_inv_run   ON ecos_cognitive_run_invalidation(run_id);
CREATE INDEX IF NOT EXISTS idx_ecos_cog_run_inv_hyp   ON ecos_cognitive_run_invalidation(hypothesis_id);
CREATE INDEX IF NOT EXISTS idx_ecos_cog_run_inv_time  ON ecos_cognitive_run_invalidation(superseded_at DESC);

COMMENT ON TABLE  ecos_cognitive_run_invalidation IS '失效→作废推演结论关联留痕表 — ADR-9 心智状态（PMO-59 P3b T2）';
COMMENT ON COLUMN ecos_cognitive_run_invalidation.event_id      IS '触发事件 id（ecos.cognitive 事件 cog_evt_ 前缀）';
COMMENT ON COLUMN ecos_cognitive_run_invalidation.hypothesis_id IS '失效假设主键';
COMMENT ON COLUMN ecos_cognitive_run_invalidation.run_id        IS '被作废推演 run 主键（ecos_scenario_run.id）';
COMMENT ON COLUMN ecos_cognitive_run_invalidation.auto_detected IS '失效是否自动检测触发（false=人工兜底）';
