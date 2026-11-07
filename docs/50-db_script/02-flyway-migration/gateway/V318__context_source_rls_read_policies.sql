-- ============================================================
-- V318__context_source_rls_read_policies.sql
-- B39：Context Builder 权威源读面（资产/知识文章）RLS 演示策略补位
-- ============================================================
-- 背景（实证）：
--   POST /api/v1/cognitive/context/build 对必需源以
--   `security-checkpoint-unavailable(fail-closed)` 403 拒绝（ContextSecurityTrimmer 无生产实现，
--   P1-9）。实现裁剪器后，裁决面仍需来源表存在命中策略，否则 `/security/decision` 返回
--   `noPolicyMatched`（denyIfEmpty=true）⇒ 仍 DENY。
--
-- 载体与口径：与 B36（`caliber-demo-read-role`）及既有 `ent2-demo-read-role` 同形——
--   只授 role_admin、predicate_template='1=1'、不绑定具体 user、不触及其它表。
--
-- 幂等：按 (policy_name, table_name) 不存在才插入。
-- 回滚：DELETE FROM ecos_control.ecos_rls_policy
--        WHERE policy_name='context-source-demo-read-role'
--          AND table_name IN ('ecos_data.ecos_data_asset','ecos_knowledge.knowledge_article');
-- ============================================================

INSERT INTO ecos_control.ecos_rls_policy
    (id, policy_name, table_name, filter_expr, role_id, priority, enabled,
     description, created_by, predicate_template, bindings_json)
SELECT v.id, v.policy_name, v.table_name, '1=1', 'role_admin', 10, TRUE, v.descr,
       'seed-entmgmt', '1=1', '[]'
  FROM (VALUES
        ('rls_ctx_asset_read_role', 'context-source-demo-read-role', 'ecos_data.ecos_data_asset',
         'B39 演示口径：管理/分析角色可读数据资产元数据（Context Builder DATA_ASSET 源）'),
        ('rls_ctx_article_read_role', 'context-source-demo-read-role', 'ecos_knowledge.knowledge_article',
         'B39 演示口径：管理/分析角色可读知识文章元数据（Context Builder KNOWLEDGE 源）')
       ) AS v(id, policy_name, table_name, descr)
 WHERE NOT EXISTS (
     SELECT 1 FROM ecos_control.ecos_rls_policy p
      WHERE p.policy_name = v.policy_name AND p.table_name = v.table_name);

-- ── DDL Lint Self-audit ──────────────────────────────────
-- [IR03] 零 DROP / 零 ALTER；新增 2 行策略数据 ✓
-- [DR01] schema 限定 ecos_control ✓  [ST07] 主控制 schema ✓
-- [安全] 仅 role_admin、恒真谓词仅限两张来源表；未绑定具体账号；未改其它策略 ✓
