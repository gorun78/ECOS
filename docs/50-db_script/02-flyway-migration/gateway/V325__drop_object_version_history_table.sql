-- ============================================================
-- V325__drop_object_version_history_table.sql
-- DS-4 对象运行时域收口：ecos_object_version 停写并清退（铁律 §3.1 例外）
-- ============================================================
-- 前置（均已达成）：
--   ① 端口：common-api `ObjectVersionArchivePort` 由 workspace 实现（Git 归档），
--      对象版本写/读在端口装配时零库写；
--   ② 归档：对象是本体实例，落 ontology-model 仓 `objects/<objectId>/<ver>.json`，
--      提交信息 `Object snapshot <objectId> v<ver> entity=<entityCode>`；
--   ③ 活体：2026-11-08 新建→更新→版本查询 200（v1/v2 均来自 Git 提交），
--      ecos_object_version 行数恒 0；
--   ④ 备份：执行前 pg_dump 快照（ecos_object_version 单表，0 行）。
-- 未装配端口的进程（测试/未接 Git）保留库内回退；生产 workspace/gateway 均已装配端口。
--
-- 幂等：IF EXISTS。
-- 回滚：按备份 SQL 恢复空表结构（历史内容在 Git）。
-- ============================================================

DROP TABLE IF EXISTS ecos_control.ecos_object_version;

-- ── DDL Lint Self-audit ──────────────────────────────────
-- [IR03] 本条属铁律 §3.1 明列例外（设计资产历史版本表，备份 + 代码切 Git 后可 DROP）
-- [DR01] schema 限定 ecos_control ✓  [ST07] 主控制 schema ✓
