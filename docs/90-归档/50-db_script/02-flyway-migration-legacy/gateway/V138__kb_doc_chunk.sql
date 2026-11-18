-- V138: 知识工作台非结构化文档过渡表（B5-1 / ADR-2 A3 过渡态）
--
-- 背景（knowledge-workbench-replan-v1 §3.3 ADR-2 / §8 Q1）：
--   非结构化采用 A3 过渡折中 —— 解析暂留 kb-engine 写「自有」chunk 表；
--   原文经数据工作台登记端点登记为数据资源（RAW/UNSTRUCTURED/LAKE_OBJECT）；
--   解析文本经数据工作台登记端点登记为 CURATED 资源。
--   A2（kb 直写 DW 层 CURATED 表）永久否决；A1（SOURCE_MINIO + 解析节点，doc/doc_chunk 落 DW 层）
--   目标态落地时，本表迁入 DW 层（技术债退出条件：SOURCE_MINIO + 解析节点落地后 1 个批次内）。
--
-- 表归属：ecos_knowledge（kb 自有 schema）——本表是 kb 过渡表，不是数据工作台的 td_data_* 表。
--
-- 迁移方式：Flyway 已禁用（spring.flyway.enabled: false），本脚本手工执行。
-- 兼容性：仅新增，CREATE TABLE/INDEX IF NOT EXISTS（遵守「schema 只加不删」）。

-- 文档级状态机（F2: queued -> parsing -> extracting -> done/failed）
CREATE TABLE IF NOT EXISTS ecos_knowledge.kb_doc (
    doc_id             VARCHAR(128) PRIMARY KEY,
    source             VARCHAR(128),
    original_file_name VARCHAR(512),
    object_key         VARCHAR(512),
    content_type       VARCHAR(128),
    size_bytes         BIGINT,
    parse_status       VARCHAR(16) NOT NULL DEFAULT 'queued',
    chunk_size         INTEGER,
    chunk_overlap      INTEGER,
    chunk_count        INTEGER DEFAULT 0,
    text_source_path   VARCHAR(512),
    error_message      TEXT,
    created_at         TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at         TIMESTAMP NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE ecos_knowledge.kb_doc IS
    '非结构化文档过渡表（A3）：文档级解析状态机，A1 落地时迁入 DW 层';
COMMENT ON COLUMN ecos_knowledge.kb_doc.parse_status IS
    '解析状态: queued/parsing/extracting/done/failed';
COMMENT ON COLUMN ecos_knowledge.kb_doc.object_key IS
    '近源层对象 key: raw/unstructured/{source}/{docId}/{originalFileName}';
COMMENT ON COLUMN ecos_knowledge.kb_doc.text_source_path IS
    '解析文本登记为 CURATED 资源时的 source_path';

-- 分块表（F3：文本 + 序号 + 字符偏移 + 元数据）
CREATE TABLE IF NOT EXISTS ecos_knowledge.kb_doc_chunk (
    id           VARCHAR(64) PRIMARY KEY,
    doc_id       VARCHAR(128) NOT NULL,
    source       VARCHAR(128),
    chunk_index  INTEGER NOT NULL,
    content      TEXT NOT NULL,
    char_start   INTEGER,
    char_end     INTEGER,
    metadata     JSONB DEFAULT '{}',
    status       VARCHAR(16) NOT NULL DEFAULT 'active',
    embedding_id VARCHAR(64),
    created_at   TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at   TIMESTAMP NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE ecos_knowledge.kb_doc_chunk IS
    '非结构化文档分块表（A3 过渡）：kb 自有表，A1 落地时迁入 DW 层 doc_chunk';
COMMENT ON COLUMN ecos_knowledge.kb_doc_chunk.status IS '分块状态: active/deprecated';
COMMENT ON COLUMN ecos_knowledge.kb_doc_chunk.embedding_id IS
    '关联 ecos_knowledge.knowledge_embedding.id（B4 向量写入），可空';

-- 幂等重解析：同 (doc_id, chunk_index) 唯一，重解析走 upsert 不产生重复行
CREATE UNIQUE INDEX IF NOT EXISTS uniq_kb_doc_chunk_doc_idx
    ON ecos_knowledge.kb_doc_chunk(doc_id, chunk_index);
CREATE INDEX IF NOT EXISTS idx_kb_doc_chunk_doc
    ON ecos_knowledge.kb_doc_chunk(doc_id);
CREATE INDEX IF NOT EXISTS idx_kb_doc_chunk_status
    ON ecos_knowledge.kb_doc_chunk(status);
CREATE INDEX IF NOT EXISTS idx_kb_doc_parse_status
    ON ecos_knowledge.kb_doc(parse_status);
