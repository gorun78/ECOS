-- ============================================================
-- V126__drop_ecos_ontology_ghost_tables.sql
-- PMO-58 T2/T3: ecos_ontology schema 8 张 0 行 ghost 表物理清理
-- 来源: 全栈开发工程师 / 日期: 2026-09-13 / 责任人: fullstack-implementer
-- 授权: docs/09-PMO指令/ (PMO-58) + PM 授权裁定 (T3 续批):
--   允许 ALTER TABLE ecos_cognitive.ecos_wm_goal DROP CONSTRAINT
--   fk_cog_goal_domain — 该 FK 指向 0 行 ghost 表 ecos_domain, 参照语义已死
--
-- § Step 0 二次终验 (执行时刻新鲜数据, 2026-09-13 13:22:13Z,
--   REPEATABLE READ 单事务, 容器 ecos-postgres / sys_man):
--   1. 8 表 count(*) 全 = 0
--   2. FK 全扫描 (含跨 schema) 恰 4 条, 无第 5 条:
--      fk_onto_prop_ent     ecos_ontology_property → ecos_ontology_entity (内部)
--      fk_onto_ent_domain   ecos_ontology_entity   → ecos_domain (内部)
--      fk_onto_gloss_domain ecos_business_glossary → ecos_domain (内部)
--      fk_cog_goal_domain   ecos_cognitive.ecos_wm_goal → ecos_domain (跨引擎, PM 授权解除)
--   3. FK 精确定义快照已入回滚档 docs/11-运维/rollback-ddl-pmo58.sql §(d)
--
-- § 执行顺序 (FK 依赖序推导):
--   1) 先解除跨引擎死 FK (唯一跨 schema 依赖, 必须先行)
--   2) DROP 8 表 — 3 条内部 FK 附着在被 DROP 的表自身上, 随表消解;
--      本顺序虽非拓扑强制序 (FK 目标一侧在被 DROP 集合内即不拦),
--      保留显式 DROP CONSTRAINT 以明确意图并兼容任何残留依赖
--   3) 8 表 + 21 索引 + 1 序列 = 30 对象一并消解,
--      pg_class ecos_ontology 对象数: 89 → 预期 59
-- ============================================================

-- 1) 跨引擎死 FK 解除 (PM 授权项; 指向对象 ecos_domain 为本次 DROP 目标,
--    0 行 ghost, 参照语义已死; 永久废弃不重建, 见回滚档 §(d) 标注)
ALTER TABLE ecos_cognitive.ecos_wm_goal
    DROP CONSTRAINT IF EXISTS fk_cog_goal_domain;

-- 2) 内部 3 FK 显式解除 (双保险; 表 DROP 时亦可随表消解)
ALTER TABLE ecos_ontology.ecos_ontology_property
    DROP CONSTRAINT IF EXISTS fk_onto_prop_ent;
ALTER TABLE ecos_ontology.ecos_ontology_entity
    DROP CONSTRAINT IF EXISTS fk_onto_ent_domain;
ALTER TABLE ecos_ontology.ecos_business_glossary
    DROP CONSTRAINT IF EXISTS fk_onto_gloss_domain;

-- 3) DROP 8 张 ghost 表 (表名以 T1 pg_class 实录为准;
--    21 个 pkey/idx 索引 + 1 个 ecos_business_glossary_id_seq 随表自动消解)
DROP TABLE IF EXISTS ecos_ontology.ecos_ontology_entity;
DROP TABLE IF EXISTS ecos_ontology.ecos_ontology_property;
DROP TABLE IF EXISTS ecos_ontology.ecos_ontology_relationship;
DROP TABLE IF EXISTS ecos_ontology.ecos_ontology_action;
DROP TABLE IF EXISTS ecos_ontology.ecos_ontology_rule;
DROP TABLE IF EXISTS ecos_ontology.ecos_ontology_version;
DROP TABLE IF EXISTS ecos_ontology.ecos_domain;
DROP TABLE IF EXISTS ecos_ontology.ecos_business_glossary;

-- 不 DROP SCHEMA: schema 下 17 张非 ghost 活表在案 (outbox_event /
-- ecos_object_* / ecos_workflow* / *_definition), PMO-58 前置决策 2
-- 条件不满足, schema 保留。
