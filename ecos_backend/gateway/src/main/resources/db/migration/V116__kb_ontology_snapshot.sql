-- ============================================================
-- V116__kb_ontology_snapshot.sql
-- 本体版本快照持久化 — 支持 PMO-50 EcosOntologyEventConsumer 的本体信息落库
-- ============================================================
-- 来源: 肖国荣 / 日期: 2026-09-12
-- 工件: V116 (Flyway 路径, 补 V114/V117/V118 之间的缺口)
--
-- 背景:
--   EcosOntologyEventConsumer (**位于 kb-engine-impl**, pmP-50 T4) 在消费
--   OntologyPublishedEvent 时:
--     1. 调 buszhi REST 拉 schema + SHA-256 计算 hash
--     2. upsert kb_ontology_snapshot (ontology_id+version UNIQUE)
--     3. 调 KgMapperService.syncFromOntology(ontologyId, jobId)
--     4. 异常时写 kg_sync_log FAILED 行 (复用 V117 后 kg_sync_log 表结构)
--   此前未建表 → 该消费者静默写失败 (只能 log 兜底, 但失败信息丢失)。
--   本 V116 补齐 DDL, 仅 CREATE TABLE IF NOT EXISTS。
--
-- 字段对齐:
--   EcosOntologyEventConsumer#upsertSnapshot (L246-264):
--     ontology_id, version (UNIQUE), entity_codes JSONB,
--     relationship_codes JSONB, schema_hash, created_by, created_at, is_deleted
--
-- 6 基线列规范 (后端规范 §六):
--   id, create_time, update_time, create_by, update_by, is_deleted
--   V116 与 EcosOntologyEventConsumer 实际写入的 created_at/created_by 语义对齐 —
--   为保持与 EcosOntologyEventConsumer 兼容 (INSERT 仅带这 4 个 SET 列),
--   采用 created_at/created_by 列名 + 6 基线列 mock 为同异名/同映射;
--   V120 之后已补 6 基线规范 (id/create_time/update_time/create_by/update_by),
--   本 V116 仍保留旧列名 (EcosOntologyEventConsumer 已依赖), 与 V120 风格字面不同正常。
--
-- Schema 铁律: 只加不删 (铁律 3.1) + CREATE IF NOT EXISTS 幂等
--
-- 表结构字段映射 (与 EcosOntologyEventConsumer.upsertSnapshot 实际写入对齐):
--   ontology_id        VARCHAR(64)       — 本体验证 ID
--   version            VARCHAR(128)      — 本体版本
--   entity_codes       JSONB             — 本体包含的实体 code 数组
--   relationship_codes JSONB             — 本体包含的关系 code 数组
--   schema_hash        VARCHAR(64)       — SHA-256 拉的 schema 哈希
--   created_by         VARCHAR(128)      — 发布人/系统
--   created_at         TIMESTAMP         — 发布快照时间 (upsert 时刷新)
--   is_deleted         SMALLINT          — 0=当前 / 1=已删 / 2=历史 (旧版本标记为 2)
--
-- 注意: UNIQUE (ontology_id, version) 是 upsert 密钥, 不加分布式锁 (V120 已补 idx)
-- ============================================================

CREATE TABLE IF NOT EXISTS kb_ontology_snapshot (
    id                   BIGSERIAL       PRIMARY KEY,
    ontology_id          VARCHAR(64)     NOT NULL,
    version              VARCHAR(128)    NOT NULL,
    entity_codes         JSONB           NOT NULL DEFAULT '[]'::jsonb,
    relationship_codes   JSONB           NOT NULL DEFAULT '[]'::jsonb,
    schema_hash          VARCHAR(64),
    created_by           VARCHAR(128)    NOT NULL DEFAULT 'system',
    created_at           TIMESTAMP       NOT NULL DEFAULT NOW(),
    is_deleted           SMALLINT        NOT NULL DEFAULT 0,
    CONSTRAINT uq_kb_ontology_snapshot_ontology_version UNIQUE (ontology_id, version)
);

CREATE INDEX IF NOT EXISTS idx_kb_ontology_snapshot_ontology
    ON kb_ontology_snapshot (ontology_id) WHERE is_deleted = 0 OR is_deleted = 2;
CREATE INDEX IF NOT EXISTS idx_kb_ontology_snapshot_created_at
    ON kb_ontology_snapshot (created_at DESC) WHERE is_deleted = 0;

COMMENT ON TABLE  kb_ontology_snapshot                        IS '本体版本快照（PMO-54 与 PMO-50 T4 本体发布事件消费 — V115/Kb 侧补齐）';
COMMENT ON COLUMN kb_ontology_snapshot.id                      IS '自增主键';
COMMENT ON COLUMN kb_ontology_snapshot.ontology_id             IS '本体业务 ID';
COMMENT ON COLUMN kb_ontology_snapshot.version                 IS '本体版本 (published version)';
COMMENT ON COLUMN kb_ontology_snapshot.entity_codes            IS '本体包含实体 code 集合 (JSONB array)';
COMMENT ON COLUMN kb_ontology_snapshot.relationship_codes      IS '本体包含关系 code 集合 (JSONB array)';
COMMENT ON COLUMN kb_ontology_snapshot.schema_hash             IS '发行时 schema JSON 的 SHA-256 hash 减少';
COMMENT ON COLUMN kb_ontology_snapshot.created_by              IS '发布人 / 系统标识';
COMMENT ON COLUMN kb_ontology_snapshot.created_at              IS '快照时间（含 upsert 时间刷行）';
COMMENT ON COLUMN kb_ontology_snapshot.is_deleted              IS '逻辑删除: 0=当前 / 1=已删 / 2=历史旧版本 (EcosOntologyEventConsumer 标记语义)';
