-- =============================================================================
-- V302 — R7 / DQ-6 废弃版本表清退（#147K）
-- 单源目录：docs/50-db_script/02-flyway-migration/gateway/
--
-- 处置口径（§2.9.6④ / DQ-6：停写 → 切 git 归档 → 备份后 DROP）：
--   * ecos_agent_version：全仓零读写（AgentVersionService/Dao/Mapper 已随本批删除），
--     且 dev PG 各 schema 均不存在该表 → DROP IF EXISTS 幂等清退（唯一已满足前置的一张）。
--   * ecos_ontology_version / ecos_object_version / sys_rule_version：仍被各域
--     版本读写腿使用（计数见台账 §十六），<b>前置未满足，本批不 DROP</b>；
--     禁以"空表"为由提前删除（空表 ≠ 无人依赖：读腿仍按表取历史）。
-- =============================================================================

DROP TABLE IF EXISTS ecos_control.ecos_agent_version;
DROP TABLE IF EXISTS ecos_ai.ecos_agent_version;
DROP TABLE IF EXISTS public.ecos_agent_version;
