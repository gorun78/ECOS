-- 详细设计-00 §6.2 (token 计量, REQ-AI-03 前置)
CREATE TABLE IF NOT EXISTS public.ecos_runtime_llm_usage (
    id            VARCHAR(36) PRIMARY KEY,
    provider      VARCHAR(32)  NOT NULL,
    model         VARCHAR(64)  NOT NULL,
    purpose       VARCHAR(32)  NOT NULL,
    prompt_tokens INTEGER      NOT NULL DEFAULT 0,
    completion_tokens INTEGER  NOT NULL DEFAULT 0,
    cost_amount   NUMERIC(18,6),
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
