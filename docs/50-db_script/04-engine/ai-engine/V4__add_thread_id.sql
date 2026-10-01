-- ============================================================
-- T4a: 会话线程支持 — 为 sys_agent_message 添加 thread_id 字段
-- 描述: 支持同一会话下多线程对话，默认值为 'main'
-- 执行: psql -U postgres -d sys_man -f V4__add_thread_id.sql
-- ============================================================

-- 1. 添加 thread_id 列（默认 'main'）
ALTER TABLE sys_agent_message
    ADD COLUMN IF NOT EXISTS thread_id VARCHAR(32) DEFAULT 'main' NOT NULL;

-- 2. 为 thread_id + session_id 组合创建索引（加速按线程查询消息）
CREATE INDEX IF NOT EXISTS idx_agent_message_thread
    ON sys_agent_message(session_id, thread_id);

-- 3. 注释
COMMENT ON COLUMN sys_agent_message.thread_id
    IS '会话线程标识，默认 main，支持同一会话下多线程对话';
