-- V334: sys_agent_message 补 thread_id 列（会话线程支持）
-- 追溯: PMO-74 L14（ai-engine src/main/resources/db/migration/V4__add_thread_id.sql 落在 impl 模块、复用旧号，
--       未入单源目录、且从未实跑）；2026-11-14 产品级逐功能验证批 D98 活体实证缺列
--       （PSQLException: column "thread_id" does not exist ⇒ POST /api/v1/agent-loop/sessions/{id}/chat 恒 502）
-- 纪律: IR03 只加不删；幂等（IF NOT EXISTS）
-- 归属: 实表在 ecos_control（2026-11-14 实测 information_schema；V202「真身 public」注释已过期，
--       全库 thread_id 列计数 = 0 即本脚本未落库的直接证据）。代码侧仍走裸表名靠 search_path（V47），
--       本脚本按 ST07 显式限定 schema，不依赖会话 search_path。
-- 上线: psql -U postgres -d sys_man -f V334__ai_agent_message_thread_id.sql

-- 1. thread_id（默认 'main'，与 AgentSessionDao 读写口径一致）
ALTER TABLE ecos_control.sys_agent_message
    ADD COLUMN IF NOT EXISTS thread_id VARCHAR(32) DEFAULT 'main' NOT NULL;

-- 2. (session_id, thread_id) 组合索引 —— AgentSessionDao.findContextMessages 按此二列查
CREATE INDEX IF NOT EXISTS idx_agent_message_thread
    ON ecos_control.sys_agent_message(session_id, thread_id);

COMMENT ON COLUMN ecos_control.sys_agent_message.thread_id
    IS '会话线程标识，默认 main，支持同一会话下多线程对话';
