-- V232 (卷10 §6.2): 就绪缺口 ecos_ai.ecos_wagent_readiness_gap——缺口·解锁值·补齐循环承载
-- 追溯: W245~W277 ↔ C227~C259（卷 10 §七）；需求依据 REQ-WAG-09（F10-09 缺口·解锁值·补齐循环）
-- 批准: 需求检视报告 §十四 14.1（2026-09-29）之 R-33=①、R-35=①；执行边界 §14.4 = 脚本文件落地
-- 状态: 仅落迁移脚本文件，**未对 ecos-postgres 实跑**（实跑属 §14.4 未授权项，需逐项再确认）
-- 纪律: DR01~DR08 / MC01~MC03 / ST06 / ST07 / ST09 / IR02 / IR03(只加不删)
-- 命名口径: 未列类型者按 §6.1 通用列 + ECOS 类型映射展开（suggested_owner→VARCHAR(100)、effort_estimate→VARCHAR(64) 文本估计、*_time→TIMESTAMP）

CREATE TABLE IF NOT EXISTS ecos_ai.ecos_wagent_readiness_gap (
    id                  VARCHAR(36) PRIMARY KEY,              -- MC01 应用侧 UUID
    tenant_id           VARCHAR(64) NOT NULL,
    org_id              VARCHAR(64),
    assessment_id       VARCHAR(36) NOT NULL,                 -- 指向 ecos_wagent_readiness.id（逻辑引用）
    layer               CHAR(1),                              -- D/I/K/C
    severity            VARCHAR(10),
    description_text    TEXT,
    impact_text         TEXT,
    suggested_action_text TEXT,
    suggested_owner     VARCHAR(100),                         -- owner 语义→VARCHAR(100)
    effort_estimate     VARCHAR(64),                          -- 工作量估计（文本口径，如 "3d"；映射未定者按 summary 类处理）
    unlock_from         CHAR(1),                              -- 解锁档位起
    unlock_to           CHAR(1),                              -- 解锁档位止
    status              VARCHAR(20),
    resolved_time       TIMESTAMP,
    domain              VARCHAR(50) NOT NULL DEFAULT 'default',
    version_no          VARCHAR(20) NOT NULL,
    create_time         TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time         TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    create_by           VARCHAR(100),
    update_by           VARCHAR(100),
    is_deleted          SMALLINT NOT NULL DEFAULT 0
);
COMMENT ON TABLE ecos_ai.ecos_wagent_readiness_gap IS '就绪评估缺口明细（附件 §3.1 gap 分表；补齐循环走任务中心 runtime-task 单一来源）';
CREATE INDEX IF NOT EXISTS idx_wagent_gap_assess  ON ecos_ai.ecos_wagent_readiness_gap(assessment_id, status);
CREATE INDEX IF NOT EXISTS idx_wagent_gap_tenant  ON ecos_ai.ecos_wagent_readiness_gap(tenant_id, severity);

-- ── DDL Lint Self-audit ──────────────────────────────────
-- [DR01] ecos_ai. ✓ [DR02] ecos_ ✓ [DR05/06/07/08] 通用列齐 ✓ [MC01] UUID ✓ [MC02] JSON 全 TEXT
-- [ST07] ecos_ai ✓ [ST09] 无跨 schema FK ✓ [IR03] 只加 ✓ [§14.4] 未实跑 ✓
