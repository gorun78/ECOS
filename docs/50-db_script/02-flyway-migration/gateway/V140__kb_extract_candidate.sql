-- ============================================================
-- V140__kb_extract_candidate.sql
-- K1 非结构化通道 LLM 抽取候选池（暂存，K2 融合消费）
-- ============================================================
-- 来源: 肖国荣 / 日期: 2026-09-20 / 批次: K1（非结构化通道 LLM 抽取候选池）
-- 依据: docs/plans/knowledge-workbench-replan-v1.md（K1 批次）
-- 背景:
--   知识工作台非结构化快路径开启后（extract.allow_direct_upload），
--   上传的临时文件经 LLM 解析抽取实体/关系候选，暂存于本表；
--   K2 融合批次再消费本表（审核/入图/与映射驱动实例合并）
--
-- 溯源列一致性（铁律 §2.1 / 数据湖存储分层规范 §五）:
--   与 graph_node / graph_edge 的溯源列同名同义：
--   ontology_id / ontology_version / source_resource_id / source_pk
--
-- Schema 铁律: 只加不删（铁律 3.1）+ IF NOT EXISTS 幂等
-- 执行方式: Flyway 已禁用（spring.flyway.enabled=false），本脚本为规范记录，
--           需人工执行到目标库（sys_man）后方可生效。
-- ============================================================

CREATE TABLE IF NOT EXISTS ecos_knowledge.kb_extract_candidate (
    id                  BIGSERIAL    PRIMARY KEY,
    doc_id              VARCHAR(64),
    entity_name         VARCHAR(256),
    entity_type         VARCHAR(128),
    relation            VARCHAR(128),
    subject_id          VARCHAR(128),
    object_id           VARCHAR(128),
    confidence          NUMERIC(5,4),
    status              VARCHAR(32)  NOT NULL DEFAULT 'PENDING',
    review_note         TEXT,
    ontology_id         VARCHAR(64),
    ontology_version    VARCHAR(64),
    source_resource_id  VARCHAR(128),
    source_pk           VARCHAR(256),
    created_at          TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMP    NOT NULL DEFAULT NOW(),
    is_deleted          SMALLINT     NOT NULL DEFAULT 0
);

-- doc_id：按文档维度批量审核 / 批量入图
CREATE INDEX IF NOT EXISTS idx_kbeic_doc ON ecos_knowledge.kb_extract_candidate (doc_id);
-- status：K2 融合批次按 PENDING/APPROVED/REJECTED 过滤
CREATE INDEX IF NOT EXISTS idx_kbeic_status ON ecos_knowledge.kb_extract_candidate (status);

COMMENT ON TABLE  ecos_knowledge.kb_extract_candidate                    IS 'K1 非结构化通道 LLM 抽取候选池（暂存，K2 融合消费；溯源列与 graph_node 对齐）';
COMMENT ON COLUMN ecos_knowledge.kb_extract_candidate.doc_id             IS '关联文档 ID（上传临时文件的 fileId，同 extraction_drafts.id）';
COMMENT ON COLUMN ecos_knowledge.kb_extract_candidate.entity_name        IS 'LLM 抽取的实体名称（自然语言）';
COMMENT ON COLUMN ecos_knowledge.kb_extract_candidate.entity_type        IS 'LLM 推断的实体类型（可选映射到本体实体 code）';
COMMENT ON COLUMN ecos_knowledge.kb_extract_candidate.relation           IS 'LLM 抽取的关系 code（可选映射到本体关系 code）';
COMMENT ON COLUMN ecos_knowledge.kb_extract_candidate.subject_id         IS '关系源节点 ID（无关系时为空）';
COMMENT ON COLUMN ecos_knowledge.kb_extract_candidate.object_id          IS '关系目标节点 ID（无关系时为空）';
COMMENT ON COLUMN ecos_knowledge.kb_extract_candidate.confidence         IS 'LLM 置信度（0.0000 - 1.0000）';
COMMENT ON COLUMN ecos_knowledge.kb_extract_candidate.status             IS '候选状态：PENDING / APPROVED / REJECTED / MERGED';
COMMENT ON COLUMN ecos_knowledge.kb_extract_candidate.review_note        IS '审核意见 / LLM 解释';
COMMENT ON COLUMN ecos_knowledge.kb_extract_candidate.ontology_id        IS '关联本体业务 ID（同 graph_node.ontology_id，溯源对齐）';
COMMENT ON COLUMN ecos_knowledge.kb_extract_candidate.ontology_version   IS '关联本体版本（同 graph_node.ontology_version，溯源对齐）';
COMMENT ON COLUMN ecos_knowledge.kb_extract_candidate.source_resource_id IS '来源资源 ID（上传文档登记 ID，溯源对齐）';
COMMENT ON COLUMN ecos_knowledge.kb_extract_candidate.source_pk          IS '来源主键（同 graph_node.source_pk，溯源对齐）';
