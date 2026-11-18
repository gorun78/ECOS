-- =============================================================================
-- V302 — R7 / DQ-6 废弃版本表清退守卫（#147K 修订版）
-- 单源目录：docs/50-db_script/02-flyway-migration/gateway/
--
-- 修订说明（2026-11-01）：首版直接写 DROP TABLE，被 db-migration-lint 的 IR03
--   「只加不删」判为 FAIL（3 条 DROP）——该脚本的红线是 _win_tasks 只增不改，
--   本批无权为 DROP 开白名单。且 dev PG 各 schema 实查 ecos_agent_version 均不存在
--   （pg_class 0 行），DROP 本无对象。故改为**清退守卫**：
--     * 表不存在 → NOTICE 通过（零对象可删，清退=代码删除 + 缺席证据）；
--     * 表存在   → 显式 EXCEPTION 阻断，要求按 DQ-6 先在部署窗口 pg_dump 备份后人工 DROP。
--   这是 fail-closed：禁在无备份证据时由自动迁移删表。
--
-- 其余三张（ecos_ontology_version / ecos_object_version / sys_rule_version）
--   仍有版本读写腿消费，前置未满足，本批不触碰（见 DDL 台账 §十六）。
-- =============================================================================

DO $$
DECLARE
    target_schema TEXT;
BEGIN
    SELECT n.nspname INTO target_schema
    FROM pg_class c JOIN pg_namespace n ON n.oid = c.relnamespace
    WHERE c.relname = 'ecos_agent_version'
    LIMIT 1;

    IF target_schema IS NULL THEN
        RAISE NOTICE 'ecos_agent_version 不存在：零对象可删，清退以代码删除与缺席证据为准';
    ELSE
        RAISE EXCEPTION 'ecos_agent_version 仍存在于 schema %：请按 DQ-6 在部署窗口 pg_dump 备份后人工 DROP（禁自动迁移删表）', target_schema;
    END IF;
END $$;
