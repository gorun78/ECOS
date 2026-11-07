-- ============================================================
-- V317__caliber_definition_rls_demo_policy.sql
-- B36：场景正式口径读面（scenario:read:caliber_definition）RLS 策略补位
-- ============================================================
-- 背景（实证）：
--   GET /api/v1/workspace/calibers 恒 403
--   {"permission":"scenario:read:caliber_definition","reason":"RLS_NO_POLICY_MATCHED"}——
--   ecos_control.ecos_rls_policy 全表无 table_name='caliber_definition' 的命中行，
--   安全引擎按 fail-closed 拒绝（行为正确，缺的是策略数据）。
--
-- 载体与口径（与既有演示策略同形，不发明新机制）：
--   对齐 `ent2-demo-read-role`（role_admin、predicate_template='1=1'）与
--   `rls-finance-all`（filter_expr='1=1'）两处既有形态；本行只授予 **role_admin**，
--   不写 user_id（避免绑定到具体账号），不放开其他角色。
--
-- 语义边界：这是「管理/分析角色可读口径定义」的授权事实，不是数据结果；
--   策略命中后仍由 CLS/OPA 继续裁决（本次不放宽任何其它实体）。
--
-- 幂等：按 (policy_name, table_name) 不存在才插入；重复执行无副作用。
-- 回滚：DELETE FROM ecos_control.ecos_rls_policy WHERE policy_name='caliber-demo-read-role'
--       AND table_name='caliber_definition';（IR03 不主动执行）
-- ============================================================

INSERT INTO ecos_control.ecos_rls_policy
    (id, policy_name, table_name, filter_expr, role_id, priority, enabled,
     description, created_by, predicate_template, bindings_json)
SELECT 'rls_caliber_demo_read_role',
       'caliber-demo-read-role',
       'caliber_definition',
       '1=1',
       'role_admin',
       10,
       TRUE,
       'B36 演示口径：管理/分析角色可读正式口径定义（admin→role_admin 映射，与 ent2-demo-read-role 同形）',
       'seed-entmgmt',
       '1=1',
       '[]'
 WHERE NOT EXISTS (
     SELECT 1 FROM ecos_control.ecos_rls_policy p
      WHERE p.policy_name = 'caliber-demo-read-role'
        AND p.table_name = 'caliber_definition');

-- ── DDL Lint Self-audit ──────────────────────────────────
-- [IR03] 零 DROP / 零 ALTER；新增 1 行策略数据 ✓
-- [DR01] schema 限定 ecos_control ✓  [ST07] 主控制 schema ✓
-- [安全] 只授 role_admin、predicate 恒真但仅该表；未绑定具体 user；未改其它策略 ✓
