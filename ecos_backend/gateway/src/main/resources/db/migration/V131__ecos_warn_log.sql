-- ============================================================
-- V131__ecos_warn_log.sql — runtime-monitor 告警日志落库表（PMO-59 P4a T1）
-- 定位: 关闭 PMO-59 P2b 残留风险 #1 —— IWarnLogService（WarnLogServiceImpl）原为纯内存
--       logStore，进程重启即失，认知失效告警（COGNITIVE_HYPOTHESIS_INVALIDATED）无据可查。
--       本表为该告警通道的持久化承接表，与内存态 logStore 并存（内存态保留，只增持久化分支）。
-- 口径: fault_context = 告警结构化上下文（与 ecos.cognitive 事件 faultContext 同口径）；
--       review_tag   = 复盘聚合键（与 MentalEventPublisher.REVIEW_TAG 同源，供
--                      GET /api/v1/cognitive/mental-reviews 只读 join 复盘维度）。
-- 只加不删（铁律）：单张新表 + 唯一/普通索引，0 触碰既有表/列。
-- ============================================================

CREATE TABLE IF NOT EXISTS ecos_warn_log (
    id             VARCHAR(64) PRIMARY KEY,
    log_id         VARCHAR(64)  NOT NULL,                 -- 告警日志业务键（幂等去重）
    warn_type      VARCHAR(64),                           -- 告警类型（如 COGNITIVE_HYPOTHESIS_INVALIDATED）
    warn_level     VARCHAR(16)  NOT NULL DEFAULT 'WARN',  -- 告警级别 INFO/WARN/ERROR
    warn_objid     VARCHAR(128),                          -- 告警对象 id（如假设主键）
    warn_objname   VARCHAR(256),                          -- 告警对象名/来源模块
    warn_message   TEXT,                                  -- 告警正文
    fault_context  JSONB,                                 -- 结构化故障上下文
    review_tag     VARCHAR(64),                           -- 复盘聚合标签
    warn_hand      VARCHAR(64),                           -- 处理方式（触发链路标识）
    warn_result    TEXT,                                  -- 处理结果（legacy updateLogResult 落库）
    ishanded       VARCHAR(4)   NOT NULL DEFAULT '0',     -- 是否已处理 0/1
    warn_time      TIMESTAMP    NOT NULL DEFAULT NOW(),   -- 告警时刻
    create_time    TIMESTAMP    NOT NULL DEFAULT NOW(),
    update_time    TIMESTAMP    NOT NULL DEFAULT NOW(),
    create_by      VARCHAR(64)  DEFAULT 'system',
    update_by      VARCHAR(64)  DEFAULT 'system',
    is_deleted     SMALLINT     NOT NULL DEFAULT 0
);

-- 幂等键：同一 log_id 只落一行（重复投递不重复留痕）
CREATE UNIQUE INDEX IF NOT EXISTS uniq_ecos_warn_log_log_id ON ecos_warn_log(log_id);
-- 复盘聚合热路径：按 review_tag + 时间倒序
CREATE INDEX IF NOT EXISTS idx_ecos_warn_log_review_tag ON ecos_warn_log(review_tag);
-- 告警类型 / 时间倒序查询
CREATE INDEX IF NOT EXISTS idx_ecos_warn_log_type ON ecos_warn_log(warn_type);
CREATE INDEX IF NOT EXISTS idx_ecos_warn_log_time ON ecos_warn_log(warn_time DESC);
CREATE INDEX IF NOT EXISTS idx_ecos_warn_log_objid ON ecos_warn_log(warn_objid);

COMMENT ON TABLE  ecos_warn_log IS 'runtime-monitor 告警日志落库表 — PMO-59 P4a（关闭 P2b 告警内存态残留风险）';
COMMENT ON COLUMN ecos_warn_log.log_id        IS '告警日志业务键（幂等去重，uniq 索引）';
COMMENT ON COLUMN ecos_warn_log.warn_type     IS '告警类型（如 COGNITIVE_HYPOTHESIS_INVALIDATED）';
COMMENT ON COLUMN ecos_warn_log.fault_context IS '结构化故障上下文 JSONB（与 ecos.cognitive 事件 faultContext 同口径）';
COMMENT ON COLUMN ecos_warn_log.review_tag    IS '复盘聚合标签（与 MentalEventPublisher.REVIEW_TAG 同源）';
