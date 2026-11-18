-- ============================================================
-- V322__ecos_ontology_entity_single_source_restore.sql
-- DD-4 延伸：补建 ecos_ontology.ecos_ontology_entity 单源锚点
-- ============================================================
-- 背景（2026-11-07 DdlCodeSchemaAlignmentGateTest 实红）：
--   代码读端 SecurityEngineContextTrimmer 以 ecos_ontology.ecos_ontology_entity
--   作为 ONTOLOGY 源的表名常量，但单源迁移里该 schema 限定表只在 V126 被 DROP、
--   从未重建（V3/V122 建的是 public 侧）；live 库中的 ecos_ontology 侧实体表
--   属单源外存量。⇒ 从零建库必断（读端有表、DDL 无建表）。
--
-- 本脚本：按 live 结构在单源补齐建表（IF NOT EXISTS，幂等），使「V126 DROP → V322 CREATE」
-- 在全新库上得到与 live 一致的净落点；public 侧历史表按 R9 不动。
-- ============================================================

CREATE TABLE IF NOT EXISTS ecos_ontology.ecos_ontology_entity (
    id              VARCHAR(36)  PRIMARY KEY,
    ontology_id     VARCHAR(36)  NOT NULL,
    code            VARCHAR(100) NOT NULL,
    name            VARCHAR(200) NOT NULL,
    description     TEXT,
    entity_type     VARCHAR(50),
    sort_order      INTEGER,
    created_at      TIMESTAMP,
    updated_at      TIMESTAMP,
    domain_id       VARCHAR(50),
    tenant_id       VARCHAR(32),
    is_deleted      SMALLINT     NOT NULL DEFAULT 0,
    status          VARCHAR(32)  NOT NULL DEFAULT 'ACTIVE',
    update_by       VARCHAR(128),
    create_time     TIMESTAMP,
    update_time     TIMESTAMP,
    create_by       VARCHAR(100),
    version_no      VARCHAR(20)  NOT NULL DEFAULT '1',
    domain          VARCHAR(50)  NOT NULL DEFAULT 'default'
);

CREATE INDEX IF NOT EXISTS idx_ecos_ontology_entity_ontology
    ON ecos_ontology.ecos_ontology_entity (ontology_id);

-- ── DDL Lint Self-audit ──────────────────────────────────
-- [IR03] 零 DROP / 零 ALTER；仅幂等 CREATE TABLE/INDEX ✓
-- [DR01] schema 限定 ecos_ontology ✓  [ST07] 本体域 schema ✓
-- [DR06] 基线列 create_time/update_time/create_by/version_no/domain 齐备 ✓
