-- ============================================================
-- V323__drop_ontology_version_history_table.sql
-- DS-4 本体域收口：版本历史表停写并清退（铁律 §3.1 例外：设计资产历史版本表）
-- ============================================================
-- 前置（均已达成）：
--   ① 读写切 Git：Draft/Publish/Deprecate 全为 ontology-model 仓提交，
--      create/publish/rollback/deprecate/列表/详情/diff 读面已走 Git；
--   ② 停写：2026-11-08 活体 1.0.2 全链（Draft→Publish→Deprecate）期间本表行数恒为 2；
--   ③ 备份：/tmp/ds4-version-tables-bak-20261108-123453.sql（另于执行前复备）。
-- 历史内容已可回溯：ont_entmgmt 1.0.0/1.0.1/1.0.2 均在 Git（commit + canonical/drafts/status）。
--
-- 幂等：IF EXISTS。
-- 回滚：按备份 SQL 恢复表与数据。
-- ============================================================

DROP TABLE IF EXISTS ecos_ontology.ecos_ontology_version;

-- ── DDL Lint Self-audit ──────────────────────────────────
-- [IR03] 本条属铁律 §3.1 明列例外（设计资产历史版本表，备份 + 代码切 Git 后可 DROP）
-- [DR01] schema 限定 ecos_ontology ✓  [ST07] 本体域 schema ✓
