-- ============================================================
-- V324__drop_rule_version_history_table.sql
-- DS-4 规则版本域收口：sys_rule_version 停写并清退（铁律 §3.1 例外）
-- ============================================================
-- 前置（均已达成）：
--   ① 写面：规则更新快照改为 Git 提交（kb-asset-tree，rules/<ruleId>/<ver>.json，
--      提交信息 Snapshot compliance rule <id> v<ver>）；
--   ② 读面：/compliance-rules/{id}/versions 从 Git 提交重建，content_source=GIT；
--   ③ 活体：2026-11-08 新建→更新→版本查询 200，sys_rule_version 行数恒 0；
--   ④ 备份：执行前 pg_dump 快照（sys_rule_version 单表，0 行）。
-- 依赖该表的统计腿已同批改：KnowledgeStatsAggregator 去掉 sys_rule_version 的
-- 计数与最近变更时间（ruleVersionCount 置 null=未知），最近变更由 sys_compliance_rule 承接。
--
-- 幂等：IF EXISTS。
-- 回滚：按备份 SQL 恢复空表结构（历史内容在 Git）。
-- ============================================================

DROP TABLE IF EXISTS ecos_control.sys_rule_version;

-- ── DDL Lint Self-audit ──────────────────────────────────
-- [IR03] 本条属铁律 §3.1 明列例外（设计资产历史版本表，备份 + 代码切 Git 后可 DROP）
-- [DR01] schema 限定 ecos_control ✓  [ST07] 主控制 schema ✓
