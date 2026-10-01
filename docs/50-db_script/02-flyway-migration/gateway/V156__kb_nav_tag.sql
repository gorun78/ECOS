-- ============================================================
-- V156__kb_nav_tag.sql
-- 知识导航（kb-nav）— 标签字典（独立于 data 分级分类）
-- ============================================================
-- 来源: 肖国荣 | 日期: 2026-09-23 | 批次: PMO-A A1
-- 依据: docs/plans/kb-nav 设计 + 架构铁律 §3.1（schema 只加不删）
-- Schema: ecos_knowledge
-- 幂等: CREATE TABLE IF NOT EXISTS / CREATE UNIQUE INDEX IF NOT EXISTS
-- 执行方式: Flyway 已禁用（spring.flyway.enabled=false），人工 psql -U postgres -d sys_man -f V156__kb_nav_tag.sql
-- ============================================================

CREATE SCHEMA IF NOT EXISTS ecos_knowledge;

CREATE TABLE IF NOT EXISTS ecos_knowledge.kb_nav_tag (
    id            VARCHAR(36)    PRIMARY KEY DEFAULT gen_random_uuid(),
    domain        VARCHAR(50)    NOT NULL DEFAULT 'default',
    tag_name      VARCHAR(128)   NOT NULL,
    version_no    VARCHAR(20)    NOT NULL DEFAULT '1',
    create_time   TIMESTAMP      NOT NULL DEFAULT NOW(),
    update_time   TIMESTAMP      NOT NULL DEFAULT NOW(),
    create_by     VARCHAR(64),
    update_by     VARCHAR(64),
    is_deleted    SMALLINT       NOT NULL DEFAULT 0
);

-- tag_name 在同 domain 下唯一（不含软删行）
CREATE UNIQUE INDEX IF NOT EXISTS uniq_nav_tag_name_domain
    ON ecos_knowledge.kb_nav_tag(domain, tag_name) WHERE is_deleted = 0;

COMMENT ON TABLE  ecos_knowledge.kb_nav_tag IS '知识导航·标签字典 — 文章多对多关联（kb_nav_article_rel, scope=tag）';
COMMENT ON COLUMN ecos_knowledge.kb_nav_tag.domain   IS '多租户预留（DR08）— NOT NULL DEFAULT default';
COMMENT ON COLUMN ecos_knowledge.kb_nav_tag.tag_name IS '标签名（同 domain 下唯一）';
