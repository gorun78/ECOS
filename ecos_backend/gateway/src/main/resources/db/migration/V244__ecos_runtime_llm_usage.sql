-- V244 (详细设计-00 §6.2): LLM token 计量表（F00-07 / REQ-AI-03 前置 = C1 修复，控制域主控制 schema）
-- 追溯: 详细设计-00-平台入口与横切底座-2026-09-28.md §6.2 V244 段（原样 SQL）；舞台=public
-- 写方唯一: llm-gateway LlmUsageRecorder（唯一 LLM 出口计量落库；挂接位见该类 TODO 注释）
-- 红线: cost_amount 金额唯一形态 NUMERIC(18,6)；MC01 应用侧 UUID；schema 只加不删（IR03）

CREATE TABLE IF NOT EXISTS public.ecos_runtime_llm_usage (
    id            VARCHAR(36) PRIMARY KEY,
    provider      VARCHAR(32)  NOT NULL,
    model         VARCHAR(64)  NOT NULL,
    purpose       VARCHAR(32)  NOT NULL,            -- chat|embedding|rerank
    prompt_tokens INTEGER      NOT NULL DEFAULT 0,
    completion_tokens INTEGER  NOT NULL DEFAULT 0,
    cost_amount   NUMERIC(18,6),                    -- 金额唯一形态 NUMERIC(p,s)
    caller_module VARCHAR(64),
    caller_id     VARCHAR(36),
    scenario_id   VARCHAR(36),
    trace_id      VARCHAR(64),
    create_time   TIMESTAMP    NOT NULL DEFAULT NOW(),
    update_time   TIMESTAMP    NOT NULL DEFAULT NOW(),
    version_no    VARCHAR(20)  NOT NULL,
    is_deleted    SMALLINT     NOT NULL DEFAULT 0,
    domain        VARCHAR(50)  NOT NULL DEFAULT 'default',
    tenant_id     VARCHAR(36)
);
CREATE INDEX IF NOT EXISTS idx_llm_usage_time ON public.ecos_runtime_llm_usage (create_time, caller_module);
