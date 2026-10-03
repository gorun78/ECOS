-- V242 (详细设计-00 §6.2): Kafka DLQ 落库表（W13 / C.5.2 事件流含 DLQ，控制域主控制 schema）
-- 追溯: 详细设计-00-平台入口与横切底座-2026-09-28.md §6.2 V242 段（原样 SQL）；舞台=public
-- 写方唯一: runtime-event DltConsumer（消费 <topic>.DLT 全 11 代码 topic）；重放: DltReplayService.replay(id)
-- 红线: MC01 应用侧 UUID；payload 原始 JSON 串 TEXT，禁 JSONB 直操（MC02）；schema 只加不删（IR03）

CREATE TABLE IF NOT EXISTS public.ecos_runtime_event_dlq (
    id            VARCHAR(36) PRIMARY KEY,
    topic         VARCHAR(128) NOT NULL,
    dlt_topic     VARCHAR(160) NOT NULL,
    payload       TEXT         NOT NULL,            -- 原始 JSON 串，禁 JSONB 直操（MC02）
    error_message VARCHAR(1000),
    attempts      SMALLINT     NOT NULL DEFAULT 0,
    first_seen_at TIMESTAMP    NOT NULL,
    replayed_at   TIMESTAMP,
    status        VARCHAR(16)  NOT NULL DEFAULT 'pending',  -- pending|replayed|discarded
    trace_id      VARCHAR(64),
    create_time   TIMESTAMP    NOT NULL DEFAULT NOW(),
    update_time   TIMESTAMP    NOT NULL DEFAULT NOW(),
    version_no    VARCHAR(20)  NOT NULL,
    is_deleted    SMALLINT     NOT NULL DEFAULT 0,
    domain        VARCHAR(50)  NOT NULL DEFAULT 'default',
    tenant_id     VARCHAR(36)
);
CREATE INDEX IF NOT EXISTS idx_dlq_status ON public.ecos_runtime_event_dlq (status, first_seen_at);
