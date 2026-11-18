-- ============================================================
-- V157__kb_nav_article_rel.sql
-- 知识导航（kb-nav）— 文章 × (目录 | 标签) 多对多关联
-- ============================================================
-- 来源: 肖国荣 | 日期: 2026-09-23 | 批次: PMO-A A1
-- 依据: docs/plans/kb-nav 设计 + 架构铁律 §3.1（schema 只加不删）
-- 说明: scope 维度区分 category / tag，一张表承载两种关联；
--       与 knowledge_article 弱关联（外键不强制，避免硬绑定）。
-- Schema: ecos_knowledge（与 knowledge_article 同 schema）
-- 幂等: 全文 CREATE TABLE IF NOT EXISTS / CREATE INDEX IF NOT EXISTS
-- 执行方式: Flyway 已禁用（spring.flyway.enabled=false），人工 psql -U postgres -d sys_man -f V157__kb_nav_article_rel.sql
-- ============================================================

CREATE SCHEMA IF NOT EXISTS ecos_knowledge;

CREATE TABLE IF NOT EXISTS ecos_knowledge.kb_nav_article_rel (
    id            BIGSERIAL      PRIMARY KEY,
    article_id    VARCHAR(64)    NOT NULL,
    node_id       VARCHAR(64)    NOT NULL,
    scope         VARCHAR(16)    NOT NULL CHECK (scope IN ('category', 'tag')),
    create_time   TIMESTAMP      NOT NULL DEFAULT NOW(),
    update_time   TIMESTAMP      NOT NULL DEFAULT NOW(),
    create_by     VARCHAR(64),
    update_by     VARCHAR(64),
    is_deleted    SMALLINT       NOT NULL DEFAULT 0
);

-- 文章 → 导航目录/标签 扇出（ Paginated article list 反查）
CREATE INDEX IF NOT EXISTS idx_rel_article ON ecos_knowledge.kb_nav_article_rel(article_id) WHERE is_deleted = 0;
-- 导航节点（目录或标签） → 关联文章（统计子树用量）
CREATE INDEX IF NOT EXISTS idx_rel_node    ON ecos_knowledge.kb_nav_article_rel(node_id) WHERE is_deleted = 0;
-- 同一篇文章对同一节点在同一 scope 下唯一（防重复打标）
CREATE UNIQUE INDEX IF NOT EXISTS uniq_rel_article_node_scope
    ON ecos_knowledge.kb_nav_article_rel(article_id, node_id, scope) WHERE is_deleted = 0;

COMMENT ON TABLE  ecos_knowledge.kb_nav_article_rel IS '知识导航·文章 ↔ (目录|标签) 多对多关联 — scope 字段区分 category/tag 两类节点';
COMMENT ON COLUMN ecos_knowledge.kb_nav_article_rel.article_id IS '关联 knowledge_article.id（VARCHAR 64，弱 FK 不强制约束）';
COMMENT ON COLUMN ecos_knowledge.kb_nav_article_rel.node_id    IS '节点 ID：scope=category → kb_nav_category.id；scope=tag → kb_nav_tag.id';
COMMENT ON COLUMN ecos_knowledge.kb_nav_article_rel.scope      IS '关联维度：category 或 tag（CHECK 约束）';

-- ============================================================
-- Seed：'default' 域下示例一级目录（幂等，2 行）
-- ============================================================
INSERT INTO ecos_knowledge.kb_nav_category (id, domain, parent_id, name, path, level, sort_order, create_by, update_by)
VALUES
    ('nav-cat-default-001', 'default', NULL, '业务文档',   '/业务文档',   1, 10, 'boot', 'boot'),
    ('nav-cat-default-002', 'default', NULL, '操作手册',   '/操作手册',   1, 20, 'boot', 'boot')
ON CONFLICT (id) DO NOTHING;
