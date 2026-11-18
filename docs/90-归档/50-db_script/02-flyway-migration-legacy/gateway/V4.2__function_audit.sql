-- V4.2: Function 沙箱执行引擎审计日志表
-- 每次 Function 调用记录到 ecos_function_audit_log

CREATE TABLE IF NOT EXISTS ecos_function_audit_log (
    id                  BIGSERIAL PRIMARY KEY,
    function_name       VARCHAR(256),
    expression          TEXT NOT NULL,
    entity_name         VARCHAR(128),
    result_value        TEXT,
    execution_time_ms   INTEGER,
    caller_id           VARCHAR(64),
    status              VARCHAR(16) NOT NULL,  -- SUCCESS/ERROR/TIMEOUT/FORBIDDEN
    error_message       TEXT,
    created_at          TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_func_audit_status ON ecos_function_audit_log(status);
CREATE INDEX IF NOT EXISTS idx_func_audit_caller ON ecos_function_audit_log(caller_id);
CREATE INDEX IF NOT EXISTS idx_func_audit_created ON ecos_function_audit_log(created_at DESC);
