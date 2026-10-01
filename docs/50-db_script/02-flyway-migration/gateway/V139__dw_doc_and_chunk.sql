-- V139: DW 层非结构化文档表（B6-2 / ADR-2 A1 目标态 · 路线 B）
--
-- 背景（knowledge-workbench-replan-v1 §3.3 ADR-2 / §4 F1~F8）：
--   A1 目标态下「非结构化 → DW 表」是数据工作台的管道能力：
--   原文二进制留近源层 raw/unstructured/（长期保留），解析文本与分块落 DW 层。
--   本脚本创建解析节点产物表 doc / doc_chunk，写入权归数据工作台（铁律 §0.5-1 单一事实源）。
--   A3 过渡表 ecos_knowledge.kb_doc / kb_doc_chunk 暂保留，待 A1 稳定后 1 个批次内退出。
--
-- 分层口径（数据湖存储分层规范 §一 / §五）：
--   本 schema 内表均为 DW 层（CURATED）资产 → td_data_resource.layer='CURATED'、zone=NULL
--   （合法性矩阵：非 RAW 层 zone 必须为 NULL）。
--
-- schema 选择理由：项目无「通用 DW schema」，ecos_demo 仅为本体演示 schema（且用后即 DROP CASCADE）；
--   故新建独立 ecos_dw 作为 DW 层业务表的通用宿主（镜像 ecos_demo 的 DW-schema 模式，
--   表名 doc/doc_chunk 与方案 §3.1 路线 B 命名一致），避免 DW 业务表混入 public 元数据表空间。
--
-- 迁移方式：Flyway 已禁用（spring.flyway.enabled: false），本脚本手工执行。
-- 兼容性：仅新增，CREATE ... IF NOT EXISTS / INSERT ... WHERE NOT EXISTS（遵守「schema 只加不删」）。

CREATE SCHEMA IF NOT EXISTS ecos_dw;
COMMENT ON SCHEMA ecos_dw IS 'DW 层（CURATED）业务表 schema — 数据工作台唯一写入，本体/知识只读';

-- ── 文档级表（F2 状态机：queued -> parsing -> extracting -> done/failed） ──
CREATE TABLE IF NOT EXISTS ecos_dw.doc (
    id                 VARCHAR(64)  PRIMARY KEY,
    doc_id             VARCHAR(128) NOT NULL,
    source             VARCHAR(128),
    original_file_name VARCHAR(512),
    object_key         VARCHAR(512),
    content_type       VARCHAR(128),
    size_bytes         BIGINT,
    parse_status       VARCHAR(16)  NOT NULL DEFAULT 'queued',
    error_message      TEXT,
    chunk_count        INTEGER      NOT NULL DEFAULT 0,
    parsed_at          TIMESTAMP,
    create_time        TIMESTAMP    NOT NULL DEFAULT NOW(),
    update_time        TIMESTAMP    NOT NULL DEFAULT NOW(),
    create_by          VARCHAR(64),
    update_by          VARCHAR(64),
    is_deleted         BOOLEAN      NOT NULL DEFAULT FALSE
);

COMMENT ON TABLE ecos_dw.doc IS
    'DW 层非结构化文档表（A1）：文档级解析状态机，写入权归数据工作台';
COMMENT ON COLUMN ecos_dw.doc.doc_id IS '文档 ID（业务唯一键，幂等重解析按此 upsert）';
COMMENT ON COLUMN ecos_dw.doc.object_key IS
    '近源层对象 key: raw/unstructured/{source}/{docId}/{originalFileName}';
COMMENT ON COLUMN ecos_dw.doc.parse_status IS
    '解析状态: queued/parsing/extracting/done/failed';

-- ── 分块级表（F3 文本抽取与分块） ──
CREATE TABLE IF NOT EXISTS ecos_dw.doc_chunk (
    id           VARCHAR(64)  PRIMARY KEY,
    chunk_id     VARCHAR(128) NOT NULL,
    doc_id       VARCHAR(128) NOT NULL,
    chunk_index  INTEGER      NOT NULL,
    content      TEXT         NOT NULL,
    char_start   INTEGER,
    char_end     INTEGER,
    metadata     JSONB        DEFAULT '{}',
    status       VARCHAR(16)  NOT NULL DEFAULT 'active',
    embedding_id VARCHAR(64),
    create_time  TIMESTAMP    NOT NULL DEFAULT NOW(),
    update_time  TIMESTAMP    NOT NULL DEFAULT NOW(),
    create_by    VARCHAR(64),
    update_by    VARCHAR(64),
    is_deleted   BOOLEAN      NOT NULL DEFAULT FALSE
);

COMMENT ON TABLE ecos_dw.doc_chunk IS
    'DW 层非结构化文档分块表（A1）：解析文本 + 序号 + 偏移 + 元数据，写入权归数据工作台';
COMMENT ON COLUMN ecos_dw.doc_chunk.embedding_id IS '关联知识向量索引 ID（知识工作台消费后回填），可空';
COMMENT ON COLUMN ecos_dw.doc_chunk.status IS '分块状态: active/deprecated';

-- ── 唯一约束 / 索引 ──
CREATE UNIQUE INDEX IF NOT EXISTS uniq_dw_doc_doc_id
    ON ecos_dw.doc(doc_id);
CREATE INDEX IF NOT EXISTS idx_dw_doc_parse_status
    ON ecos_dw.doc(parse_status);
CREATE UNIQUE INDEX IF NOT EXISTS uniq_dw_doc_chunk_doc_idx
    ON ecos_dw.doc_chunk(doc_id, chunk_index);
CREATE UNIQUE INDEX IF NOT EXISTS uniq_dw_doc_chunk_chunk_id
    ON ecos_dw.doc_chunk(chunk_id);
CREATE INDEX IF NOT EXISTS idx_dw_doc_chunk_doc
    ON ecos_dw.doc_chunk(doc_id);

-- ── 数据资源登记（§六 写入与标记责任；数据工作台为 DW 层唯一写入者） ──
-- 依据《数据湖存储分层规范》§五：layer='CURATED'、zone=NULL。
-- 后续解析节点每次写入后再调 DataLakeResourceService.markCurated 幂等复核（失败仅 warn）。
INSERT INTO td_data_resource
    (resource_id, resource_name, resource_type, datasource_id, source_path,
     description, status, field_count, record_count, layer, zone)
SELECT 'res-dw-doc', 'DW 文档表（ecos_dw.doc）', 'TABLE', 'data-lake', 'ecos_dw.doc',
       'A1 目标态：数据工作台文档解析节点产出的文档级表', 'ACTIVE', 0, 0, 'CURATED', NULL
WHERE NOT EXISTS (SELECT 1 FROM td_data_resource WHERE source_path = 'ecos_dw.doc');

INSERT INTO td_data_resource
    (resource_id, resource_name, resource_type, datasource_id, source_path,
     description, status, field_count, record_count, layer, zone)
SELECT 'res-dw-doc-chunk', 'DW 文档分块表（ecos_dw.doc_chunk）', 'TABLE', 'data-lake', 'ecos_dw.doc_chunk',
       'A1 目标态：数据工作台文档解析节点产出的分块表（知识工作台只读消费）', 'ACTIVE', 0, 0, 'CURATED', NULL
WHERE NOT EXISTS (SELECT 1 FROM td_data_resource WHERE source_path = 'ecos_dw.doc_chunk');
