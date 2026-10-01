-- ============================================================
-- V118__kb_lineage_event.sql
-- 血缘事件持久化 — 将 LineageController 的
-- ConcurrentHashMap 内存存储迁入 PostgreSQL
-- ============================================================
-- 来源: PMO 内存态 PG 化任务
--
-- 背景:
--   LineageController (ontology-engine-impl) 使用 ConcurrentHashMap
--   在内存中保存血缘事件（parse 产生的 nodes/edges），进程重启后
--   全部丢失。
--
-- 产出:
--   kb_lineage_event 表 — 一行 = 一次 parse 事件
--   nodes / edges 列以 JSONB 存储血缘节点和边数组
--
-- 铁律 3.1 遵循:
--   - schema 只加不删: 使用 CREATE TABLE IF NOT EXISTS
--   - 逻辑删除: is_deleted SMALLINT DEFAULT 0
-- ============================================================

CREATE TABLE IF NOT EXISTS kb_lineage_event (
    id         BIGSERIAL     PRIMARY KEY,
    event_id   VARCHAR(64)   NOT NULL UNIQUE,
    query      TEXT,
    format     VARCHAR(32),
    nodes      JSONB         NOT NULL DEFAULT '[]'::jsonb,
    edges      JSONB         NOT NULL DEFAULT '[]'::jsonb,
    parse_at   TIMESTAMP     NOT NULL DEFAULT NOW(),
    is_deleted SMALLINT      NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_kb_lineage_event_format
    ON kb_lineage_event (format) WHERE is_deleted = 0;
CREATE INDEX IF NOT EXISTS idx_kb_lineage_event_parse_at
    ON kb_lineage_event (parse_at DESC) WHERE is_deleted = 0;

COMMENT ON TABLE  kb_lineage_event          IS '血缘事件（Lineage parse 持久化，原 ConcurrentHashMap 内存态）';
COMMENT ON COLUMN kb_lineage_event.id        IS '自增主键';
COMMENT ON COLUMN kb_lineage_event.event_id  IS '事件业务 ID (UUID)';
COMMENT ON COLUMN kb_lineage_event.query     IS '解析时使用的查询/输入描述';
COMMENT ON COLUMN kb_lineage_event.format    IS '血缘格式: openlineage/atlas';
COMMENT ON COLUMN kb_lineage_event.nodes     IS '血缘节点列表 (JSONB: [{id, label, type}, ...])';
COMMENT ON COLUMN kb_lineage_event.edges     IS '血缘边列表 (JSONB: [{source, target, type}, ...])';
COMMENT ON COLUMN kb_lineage_event.parse_at  IS '解析时间';
COMMENT ON COLUMN kb_lineage_event.is_deleted IS '逻辑删除标记: 0=未删除, 1=已删除';

-- 说明: 原内存数据无法迁移（纯进程内态，无持久化），
-- 故本 DDL 仅建表，不做数据迁移。
