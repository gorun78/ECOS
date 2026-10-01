-- V101: Agent运行时 — 会话+消息持久化
-- 引擎: ai-engine | 库: sys_man

-- 1. Agent会话表
CREATE TABLE IF NOT EXISTS sys_agent_session (
    id              VARCHAR(64)   NOT NULL PRIMARY KEY,
    agent_id        VARCHAR(64)   NOT NULL,
    user_id         VARCHAR(64),
    tenant_id       VARCHAR(64),
    status          VARCHAR(16)   DEFAULT 'ACTIVE',   -- ACTIVE/IDLE/EXPIRED/ARCHIVED
    message_count   INT           DEFAULT 0,
    created_at      BIGINT,
    last_active_at  BIGINT
);

-- 2. 消息表
CREATE TABLE IF NOT EXISTS sys_agent_message (
    id              BIGSERIAL     PRIMARY KEY,
    session_id      VARCHAR(64)   REFERENCES sys_agent_session(id),
    role            VARCHAR(16),       -- system/user/assistant/tool
    content         TEXT,
    tool_calls      JSONB,             -- LLM请求的工具调用
    tool_results    JSONB,             -- 工具执行结果
    tokens          INT,               -- 该消息消耗token数
    created_at      BIGINT
);

-- 索引
CREATE INDEX IF NOT EXISTS idx_agent_session_agent    ON sys_agent_session(agent_id);
CREATE INDEX IF NOT EXISTS idx_agent_session_status   ON sys_agent_session(status);
CREATE INDEX IF NOT EXISTS idx_agent_message_session  ON sys_agent_message(session_id);
