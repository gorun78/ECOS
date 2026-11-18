-- V4.1: 本体变更提案持久化表
-- 将 OntologyProposalController 从 ConcurrentHashMap 迁移至 PostgreSQL

CREATE TABLE IF NOT EXISTS ecos_ontology_proposals (
    id              BIGSERIAL PRIMARY KEY,
    domain_code     VARCHAR(128) NOT NULL,
    proposal_type   VARCHAR(32) NOT NULL,        -- CREATE_ENTITY/ADD_PROPERTY/MODIFY_PROPERTY/DELETE_ENTITY/ADD_RELATIONSHIP/CREATE_FUNCTION
    target_entity   VARCHAR(256),
    payload         JSONB NOT NULL,               -- 变更内容
    snapshot        JSONB,                        -- 变更前快照（回滚用）
    status          VARCHAR(16) NOT NULL DEFAULT 'DRAFT',  -- DRAFT/PENDING/APPROVED/REJECTED/EXECUTED
    author          VARCHAR(64),
    reviewer        VARCHAR(64),
    reviewer_comment VARCHAR(512),
    version_id      BIGINT,                       -- 关联版本ID（approve-and-publish后回填）
    created_at      TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_proposals_domain ON ecos_ontology_proposals(domain_code, status);
CREATE INDEX IF NOT EXISTS idx_proposals_author ON ecos_ontology_proposals(author);
