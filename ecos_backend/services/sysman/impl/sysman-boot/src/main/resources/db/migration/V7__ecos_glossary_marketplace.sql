-- ============================================================
-- V7__ecos_glossary_marketplace.sql — 术语库 & 数据市场持久化
-- ============================================================

-- ── ecos_glossary_term: 术语库 ──────────────────────────────
CREATE TABLE IF NOT EXISTS ecos_glossary_term (
    id          BIGSERIAL PRIMARY KEY,
    code        VARCHAR(64)  DEFAULT '',
    name        VARCHAR(255) NOT NULL,
    definition  TEXT         DEFAULT '',
    domain      VARCHAR(128) DEFAULT '',
    owner       VARCHAR(128) DEFAULT '',
    status      VARCHAR(32)  DEFAULT 'DRAFT',   -- DRAFT / REVIEW / PUBLISHED / DEPRECATED
    created_by  VARCHAR(128) DEFAULT '',
    created_at  TIMESTAMP    DEFAULT NOW(),
    updated_at  TIMESTAMP    DEFAULT NOW()
);

COMMENT ON TABLE  ecos_glossary_term          IS '术语库 — Glossary terms';
COMMENT ON COLUMN ecos_glossary_term.id        IS '主键，自增';
COMMENT ON COLUMN ecos_glossary_term.code      IS '术语编码';
COMMENT ON COLUMN ecos_glossary_term.name      IS '术语名称';
COMMENT ON COLUMN ecos_glossary_term.definition IS '术语定义';
COMMENT ON COLUMN ecos_glossary_term.domain    IS '所属领域';
COMMENT ON COLUMN ecos_glossary_term.owner     IS '负责人';
COMMENT ON COLUMN ecos_glossary_term.status    IS '状态: DRAFT/REVIEW/PUBLISHED/DEPRECATED';
COMMENT ON COLUMN ecos_glossary_term.created_by IS '创建人';
COMMENT ON COLUMN ecos_glossary_term.created_at IS '创建时间';
COMMENT ON COLUMN ecos_glossary_term.updated_at IS '更新时间';

CREATE INDEX IF NOT EXISTS idx_glossary_term_domain ON ecos_glossary_term(domain);
CREATE INDEX IF NOT EXISTS idx_glossary_term_status ON ecos_glossary_term(status);

-- ── ecos_marketplace_asset: 数据市场资产 ───────────────────
CREATE TABLE IF NOT EXISTS ecos_marketplace_asset (
    id          BIGSERIAL PRIMARY KEY,
    name        VARCHAR(255) NOT NULL,
    description TEXT         DEFAULT '',
    category    VARCHAR(64)  DEFAULT '',       -- 数据集 / AI模型 / API / 报表
    owner       VARCHAR(128) DEFAULT '',
    rating      NUMERIC(3,2) DEFAULT 0.0,      -- 评分 0.00~5.00
    popularity  INTEGER      DEFAULT 0,        -- 综合热度值
    status      VARCHAR(32)  DEFAULT 'PUBLISHED', -- DRAFT / PUBLISHED / DEPRECATED
    created_at  TIMESTAMP    DEFAULT NOW()
);

COMMENT ON TABLE  ecos_marketplace_asset           IS '数据市场 — marketplace assets';
COMMENT ON COLUMN ecos_marketplace_asset.id         IS '主键，自增';
COMMENT ON COLUMN ecos_marketplace_asset.name       IS '资产名称';
COMMENT ON COLUMN ecos_marketplace_asset.description IS '资产描述';
COMMENT ON COLUMN ecos_marketplace_asset.category   IS '分类: 数据集/AI模型/API/报表';
COMMENT ON COLUMN ecos_marketplace_asset.owner      IS '所属部门/所有者';
COMMENT ON COLUMN ecos_marketplace_asset.rating     IS '评分 0.00~5.00';
COMMENT ON COLUMN ecos_marketplace_asset.popularity IS '综合热度值';
COMMENT ON COLUMN ecos_marketplace_asset.status     IS '状态: DRAFT/PUBLISHED/DEPRECATED';
COMMENT ON COLUMN ecos_marketplace_asset.created_at IS '创建时间';

CREATE INDEX IF NOT EXISTS idx_marketplace_asset_category ON ecos_marketplace_asset(category);
CREATE INDEX IF NOT EXISTS idx_marketplace_asset_status   ON ecos_marketplace_asset(status);
CREATE INDEX IF NOT EXISTS idx_marketplace_asset_popularity ON ecos_marketplace_asset(popularity DESC);

-- ── ecos_marketplace_access_request: 访问申请 ──────────────
CREATE TABLE IF NOT EXISTS ecos_marketplace_access_request (
    id          BIGSERIAL PRIMARY KEY,
    asset_id    BIGINT       NOT NULL REFERENCES ecos_marketplace_asset(id),
    reason      TEXT         DEFAULT '',
    applicant   VARCHAR(128) DEFAULT '',
    status      VARCHAR(32)  DEFAULT 'PENDING',  -- PENDING / APPROVED / REJECTED
    created_at  TIMESTAMP    DEFAULT NOW(),
    updated_at  TIMESTAMP    DEFAULT NOW()
);

COMMENT ON TABLE  ecos_marketplace_access_request         IS '数据市场访问申请记录';
COMMENT ON COLUMN ecos_marketplace_access_request.id       IS '主键，自增';
COMMENT ON COLUMN ecos_marketplace_access_request.asset_id IS '关联资产ID';
COMMENT ON COLUMN ecos_marketplace_access_request.reason   IS '申请理由';
COMMENT ON COLUMN ecos_marketplace_access_request.applicant IS '申请人';
COMMENT ON COLUMN ecos_marketplace_access_request.status   IS '状态: PENDING/APPROVED/REJECTED';
COMMENT ON COLUMN ecos_marketplace_access_request.created_at IS '创建时间';

CREATE INDEX IF NOT EXISTS idx_access_request_asset_id ON ecos_marketplace_access_request(asset_id);
CREATE INDEX IF NOT EXISTS idx_access_request_status   ON ecos_marketplace_access_request(status);
