-- ============================================================================
-- 企业经营管控演示数据集 · 二期 W8 —— 域 0「安全策略」活表 seed（行级读授权定义面）
--
-- 目标表：ecos_control.ecos_rls_policy
--   唯一活读面 = RowLevelSecurityDao.findPoliciesForUser()
--   （security-engine-impl dao/RowLevelSecurityDao.java:41-54）：
--     `(table_name = ? OR resource_id = ?) AND enabled = true
--      AND (user_id = ? OR user_id IS NULL)
--      AND (role_id IS NULL OR role_id IN (SELECT role_id FROM td_user_role WHERE user_id = ?))`
--   实跑前该表 0 行（`SELECT count(*) FROM ecos_control.ecos_rls_policy` = 0，2026-10-17 本机 dev PG）
--   ⇒ 所有经 RLS 咽喉的读端点零命中策略 → `RlsPredicateRenderer.java:117-120` 渲染拒绝
--   → 场景域 403 PERMISSION_DENIED、本体定义面 200+空数组（同一条生产链的两种表现）。
--
-- 为什么这批行属于「演示数据交付」而不是「安全绕过」：
--   1. 用户目标 0 号域原文要求「初始化…安全策略」，本表即安全策略的活载体；
--      表为空 = 域 0 的安全策略面从未交付，而非交付后被正确拒绝。
--   2. 零代码改动：PEP/PDP 的 fail-closed 默认（零命中即 DENY）与 OPA 基线原样保留，
--      未列入本批的主体/资源仍走默认拒绝。本批只向 PAP 数据面新增「谁可看什么」的定义行。
--   3. 表达形态沿用仓内既有单源口径：`filter_expr='1=1'` + `source_kind='legacy_text'`
--      即 V246__security_policy_seed.sql:86-95 的 `rls-finance-all`「唯一 1=1 主体」写法。
--   4. 最小授权面：主体只取两种已实测在位的形态（见下「双形态」），不写
--      `user_id IS NULL AND role_id IS NULL` 的匿名全量放行。
--   5. 不 seed CLS 策略：ColumnLevelSecurityServiceImpl.getColumns:35-79 在零策略时
--      返回全部列，补行反而引入 `visible_cols` 写漏即清空列（retainAll 交集）的风险。
--
-- 双形态（同资源两行，缺一即漏一条通路；本轮 live 实测取证）：
--   _usr：user_id='admin'，role_id NULL —— 独立进程（workspace :18090 / aiming :18084）
--          无 Spring Security 上下文，主体由信任头 X-ECOS-USER 还原（dev 值恒 'admin'）。
--   _rol：role_id='role_admin'，user_id NULL —— 经 Spring Security 的通路取 JWT `sub`，
--          实测 sub='u-admin'（roles=[ROLE_SUPER_ADMIN]）而非 'admin'；该形态经
--          `td_user_role`（二期 W3 seed：u-admin → role_admin）命中，属角色面授权。
--   ⚠ 同一登录主体在两种承流态下解析出不同标识符，是平台缺陷（主体标识无单源），
--     已登记 D-W8-2；本批不以其为豁免而放宽成匿名全量放行，只按实测两形态各自落一行。
--
-- 覆盖资源码来源（全部为代码字面量实测，非推定）：
--   场景域 10 枚 = workspace-impl 各 Controller 调用 ScenarioReadGuard.assertReadable(...)
--     scenario_list / scenario_detail / scenario_bindings / scenario_graph /
--     scenario_completeness / scenario_run / scenario_action / scenario_decision /
--     scenario_cognition / forecast_run
--   本体域 1 枚 = OntologySecurityInterceptor.resolveTableName():533-539 的兜底字面量
--     `ecos_ontology_data`（`GET /api/v1/ecos/entities` 行 Map 键集不含 objectTypeId ⇒ 恒落兜底值）
--
-- 幂等：ON CONFLICT (id) DO NOTHING；标记 create_by / created_by = 'seed-entmgmt-v2'
-- 回滚：DELETE FROM ecos_control.ecos_rls_policy WHERE create_by = 'seed-entmgmt-v2';
-- ============================================================================

-- ── [1] 用户形态：admin（信任头承流态）────────────────────────────────
INSERT INTO ecos_control.ecos_rls_policy
    (id, policy_name, table_name, resource_id, filter_expr, predicate_template,
     bindings_json, source_kind, role_id, user_id, priority, enabled,
     description, domain, created_by, create_by)
SELECT 'rls_ent2_' || c || '_usr',
       'ent2-demo-read-user', c, c, '1=1', '1=1',
       '[]', 'legacy_text', NULL, 'admin', 10, true,
       '经营管控演示-读授权（admin 用户，信任头承流态）', 'entmgmt',
       'seed-entmgmt-v2', 'seed-entmgmt-v2'
FROM unnest(ARRAY[
    'scenario_list', 'scenario_detail', 'scenario_bindings', 'scenario_graph',
    'scenario_completeness', 'scenario_run', 'scenario_action', 'scenario_decision',
    'scenario_cognition', 'forecast_run', 'ecos_ontology_data']::text[]) AS c
ON CONFLICT (id) DO NOTHING;

-- ── [2] 角色形态：role_admin（Spring Security 承流态，主体取 JWT sub）──
INSERT INTO ecos_control.ecos_rls_policy
    (id, policy_name, table_name, resource_id, filter_expr, predicate_template,
     bindings_json, source_kind, role_id, user_id, priority, enabled,
     description, domain, created_by, create_by)
SELECT 'rls_ent2_' || c || '_rol',
       'ent2-demo-read-role', c, c, '1=1', '1=1',
       '[]', 'legacy_text', 'role_admin', NULL, 20, true,
       '经营管控演示-读授权（系统管理员角色，经 td_user_role 命中 u-admin）', 'entmgmt',
       'seed-entmgmt-v2', 'seed-entmgmt-v2'
FROM unnest(ARRAY[
    'scenario_list', 'scenario_detail', 'scenario_bindings', 'scenario_graph',
    'scenario_completeness', 'scenario_run', 'scenario_action', 'scenario_decision',
    'scenario_cognition', 'forecast_run', 'ecos_ontology_data']::text[]) AS c
ON CONFLICT (id) DO NOTHING;

-- 落库对账：应返回 22 行（11 资源码 × 2 形态）
SELECT count(*) AS seeded_rows,
       count(*) FILTER (WHERE user_id = 'admin')  AS user_form,
       count(*) FILTER (WHERE role_id = 'role_admin') AS role_form
FROM ecos_control.ecos_rls_policy
WHERE create_by = 'seed-entmgmt-v2';

SELECT table_name, user_id, role_id, filter_expr, source_kind, enabled
FROM ecos_control.ecos_rls_policy
WHERE create_by = 'seed-entmgmt-v2'
ORDER BY table_name, role_id NULLS LAST;
