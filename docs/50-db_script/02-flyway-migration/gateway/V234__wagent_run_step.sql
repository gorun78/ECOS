-- V234 (卷10 §6.2): 运行步骤 ecos_ai.ecos_wagent_run_step——step×attempt 粒度执行留痕
-- 追溯: W245~W277 ↔ C227~C259（卷 10 §七）；需求依据 REQ-WAG-11/12（F10-11/F10-12）
-- 批准: 需求检视报告 §十四 14.1（2026-09-29）之 R-33=①、R-35=①、R-43=推荐项批准（工具/端点前缀单源）；执行边界 §14.4 = 脚本文件落地
-- 状态: 仅落迁移脚本文件，**未对 ecos-postgres 实跑**（实跑属 §14.4 未授权项，需逐项再确认）
-- 纪律: DR01~DR08 / MC01~MC03 / ST06 / ST07 / ST09 / IR02 / IR03(只加不删)
-- 命名口径: 未列类型者按 §6.1 通用列 + ECOS 类型映射展开（*_hash→VARCHAR(64)、task_id→VARCHAR(64) runtime-task 真值外部键、tool_name→VARCHAR(255)）

CREATE TABLE IF NOT EXISTS ecos_ai.ecos_wagent_run_step (
    id                  VARCHAR(36) PRIMARY KEY,              -- MC01 应用侧 UUID
    tenant_id           VARCHAR(64) NOT NULL,
    org_id              VARCHAR(64),
    run_id              VARCHAR(36) NOT NULL,                 -- V233 逻辑引用
    step_key            VARCHAR(60),
    attempt             SMALLINT,
    step_type           VARCHAR(16),
    tool_name           VARCHAR(255),                         -- 指向 V236 工具契约 name（版本闭合由应用校验）
    tool_version        VARCHAR(20),
    prompt_id           VARCHAR(36),                          -- Prompt 资产引用（历史走 Git）
    prompt_version      VARCHAR(20),
    automation_level    SMALLINT,                             -- 本步自动化档位（≤ run.automation_ceiling）
    status              VARCHAR(16),
    input_ref_text      TEXT,                                 -- 引用型（不存内容正文，§6.3）
    input_hash          VARCHAR(64),
    output_ref_text     TEXT,
    output_hash         VARCHAR(64),
    task_id             VARCHAR(64),                          -- runtime-task 真值（禁自造）
    policy_effect       VARCHAR(20),                          -- security-engine PEP 裁决效果（allow/deny/…）
    policy_rule_id      VARCHAR(36),                          -- 命中规则引用（OPA 裁决留痕，ST06 审计走 Kafka）
    metrics_json        TEXT,                                 -- 步骤度量（JSON 语义 TEXT）
    error_code          VARCHAR(24),                          -- §5.4 错误码（E-VALIDATION/E-POLICY/…）
    error_text          TEXT,
    start_time          TIMESTAMP,
    end_time            TIMESTAMP,
    domain              VARCHAR(50) NOT NULL DEFAULT 'default',
    version_no          VARCHAR(20) NOT NULL,
    create_time         TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time         TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    create_by           VARCHAR(100),
    update_by           VARCHAR(100),
    is_deleted          SMALLINT NOT NULL DEFAULT 0
);
COMMENT ON TABLE ecos_ai.ecos_wagent_run_step IS 'W Agent 运行步骤（附件 agt_step 重写；agent_execution_step 语义上位，停写口径见卷 10 §6.2 收编段）';

-- UNIQUE(run_id, step_key, attempt)（红线：禁 partial ⇒ 含 is_deleted）
CREATE UNIQUE INDEX IF NOT EXISTS uniq_wagent_step_attempt
    ON ecos_ai.ecos_wagent_run_step(run_id, step_key, attempt, is_deleted);
CREATE INDEX IF NOT EXISTS idx_wagent_step_run ON ecos_ai.ecos_wagent_run_step(run_id, status);

-- ── DDL Lint Self-audit ──────────────────────────────────
-- [DR01] ecos_ai. ✓ [DR02] ecos_ ✓ [DR05/06/07/08] 通用列齐 ✓ [MC01] UUID ✓ [MC02] JSON 全 TEXT
-- [唯一索引] 非 partial、含 is_deleted ✓  [ST07] ecos_ai ✓ [ST09] 无 FK ✓ [IR03] 只加 ✓ [§14.4] 未实跑 ✓
