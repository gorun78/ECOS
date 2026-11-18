-- V235 (卷10 §6.2): 引用型运行事件 ecos_ai.ecos_wagent_run_event——append-only 过程事件（不分区、不建审计表）
-- 追溯: W245~W277 ↔ C227~C259（卷 10 §七）；需求依据 REQ-WAG-11（F10-11）
-- 批准: 需求检视报告 §十四 14.1（2026-09-29）之 R-33=①、R-35=①（X-6：禁表分区；事件与冷数据由 runtime-monitor 归档）；执行边界 §14.4 = 脚本文件落地
-- 状态: 仅落迁移脚本文件，**未对 ecos-postgres 实跑**（实跑属 §14.4 未授权项，需逐项再确认）
-- 纪律: DR01~DR08 / MC01~MC03 / ST06 / ST07 / ST09 / IR02 / IR03(只加不删)
-- 边界: 本表仅存**引用型**事件（payload 只放 ref，不放正文，§6.3）；审计事件**不建** wagent_audit_event，一律 Kafka `ecos.audit`（ST06/X-20）；跨进程事件走 runtime-event + Outbox。append-only：应用层禁 UPDATE/DELETE（DDL 不设 update_by 语义变更，通用列仍随 §6.1 模板保留）。

CREATE TABLE IF NOT EXISTS ecos_ai.ecos_wagent_run_event (
    id                  VARCHAR(36) PRIMARY KEY,              -- MC01 应用侧 UUID
    tenant_id           VARCHAR(64) NOT NULL,
    org_id              VARCHAR(64),
    run_id              VARCHAR(36) NOT NULL,                 -- V233 逻辑引用
    step_id             VARCHAR(36),                          -- V234 逻辑引用
    event_type          VARCHAR(32),                          -- 信封/路由单源 = PRD-01 v1.3 REQ-PLT-29（R-68~R-70 批准），本表不复制词表
    payload_ref_text    TEXT,                                 -- 引用型载荷（对象存储/表行指针，禁正文）
    occur_time          TIMESTAMP,
    domain              VARCHAR(50) NOT NULL DEFAULT 'default',
    version_no          VARCHAR(20) NOT NULL,
    create_time         TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time         TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    create_by           VARCHAR(100),
    update_by           VARCHAR(100),
    is_deleted          SMALLINT NOT NULL DEFAULT 0
);
COMMENT ON TABLE ecos_ai.ecos_wagent_run_event IS 'W Agent 引用型运行事件（append-only，无分区 X-6；审计走 Kafka ecos.audit，ST06 不建 wagent_audit_event）';
CREATE INDEX IF NOT EXISTS idx_wagent_event_run ON ecos_ai.ecos_wagent_run_event(run_id, occur_time);

-- ── DDL Lint Self-audit ──────────────────────────────────
-- [DR01] ecos_ai. ✓ [DR02] ecos_ ✓ [DR05/06/07/08] 通用列齐 ✓ [MC01] UUID ✓ [MC02] JSON 全 TEXT
-- [X-6] 不分区 ✓  [ST06] 无审计表 ✓  [ST07] ecos_ai ✓  [ST09] 无 FK ✓  [IR03] 只加 ✓ [§14.4] 未实跑 ✓
