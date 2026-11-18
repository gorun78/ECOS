-- ============================================================
-- V155__kb_nav_category.sql
-- 知识导航（kb-nav）— 业务目录树（≤3 级，含叶子），与 data 分级分类完全解耦
-- ============================================================
-- 来源: 肖国荣 | 日期: 2026-09-23 | 批次: PMO-A A1
-- 依据: docs/plans/kb-nav 设计 + 架构铁律 §3.1（schema 只加不删）
-- Schema: ecos_knowledge（与 knowledge_article 同 schema）
-- 孤立约束: depth ≤ 3（level 1/2/3），与 data 分级分类 level_code 无关（不加 level_code 列）
-- 幂等: 全文 CREATE TABLE IF NOT EXISTS / CREATE INDEX IF NOT EXISTS
-- 执行方式: Flyway 已禁用（spring.flyway.enabled=false），人工 psql -U postgres -d sys_man -f V155__kb_nav_category.sql
-- ============================================================

CREATE SCHEMA IF NOT EXISTS ecos_knowledge;

CREATE TABLE IF NOT EXISTS ecos_knowledge.kb_nav_category (
    id            VARCHAR(36)    PRIMARY KEY DEFAULT gen_random_uuid(),
    domain        VARCHAR(50)    NOT NULL DEFAULT 'default',
    parent_id     VARCHAR(36),
    name          VARCHAR(128)   NOT NULL,
    path          VARCHAR(512),
    level         SMALLINT       NOT NULL DEFAULT 1 CHECK (level BETWEEN 1 AND 3),
    sort_order    INTEGER        NOT NULL DEFAULT 0,
    version_no    VARCHAR(20)    NOT NULL DEFAULT '1',
    create_time   TIMESTAMP      NOT NULL DEFAULT NOW(),
    update_time   TIMESTAMP      NOT NULL DEFAULT NOW(),
    create_by     VARCHAR(64),
    update_by     VARCHAR(64),
    is_deleted    SMALLINT       NOT NULL DEFAULT 0
);

-- domain: 一等过滤器（多租户隔离预留，DR08）
CREATE INDEX IF NOT EXISTS idx_nav_cat_domain   ON ecos_knowledge.kb_nav_category(domain) WHERE is_deleted = 0;
-- parent_id: 子树展开 / 移动合法性校验
CREATE INDEX IF NOT EXISTS idx_nav_cat_parent   ON ecos_knowledge.kb_nav_category(parent_id) WHERE is_deleted = 0;
-- 同 domain 下 name 唯一（不含软删行）
CREATE UNIQUE INDEX IF NOT EXISTS uniq_nav_cat_name_domain
    ON ecos_knowledge.kb_nav_category(domain, name) WHERE is_deleted = 0;

COMMENT ON TABLE  ecos_knowledge.kb_nav_category            IS '知识导航·业务目录树（≤3 级含叶子）— kb-engine 自有表，与 data 分级分类完全解耦（不带 level_code）';
COMMENT ON COLUMN ecos_knowledge.kb_nav_category.domain     IS '多租户预留（DR08）— NOT NULL DEFAULT default';
COMMENT ON COLUMN ecos_knowledge.kb_nav_category.parent_id  IS '父目录 ID；根目录 = NULL';
COMMENT ON COLUMN ecos_knowledge.kb_nav_category.path       IS '从根到本节点的路径（/name1/name2/...），冗余提升子树查询性能';
COMMENT ON COLUMN ecos_knowledge.kb_nav_category.level      IS '层级 1/2/3（1 = 域下一级目录，3 = 叶子极限）';
COMMENT ON COLUMN ecos_knowledge.kb_nav_category.sort_order IS '同级排序（小者先）';
COMMENT ON COLUMN ecos_knowledge.kb_nav_category.version_no IS '乐观锁版本号（V150 DR07 规范）';
