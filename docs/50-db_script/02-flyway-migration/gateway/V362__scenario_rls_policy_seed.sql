-- ============================================================================
-- V362 — 场景工作台新读端点的 RLS 策略种子（2026-11-23）
-- ============================================================================
-- 背景：V361 新增 4 个读端点（definition / analyses / scenario_template /
--       scenario_algorithm），其 ScenarioReadGuard 实体编码此前无匹配 RLS 策略 ⇒
--       安全引擎按 fail-closed 返回 403 RLS_NO_POLICY_MATCHED（活体实测）。
--       既有 9 个场景读端点均以「admin 1=1」策略放行（00_init/10_system_seed.sql
--       的 rls_scenario_*_admin 条目），本脚本按同一形态补齐 4 条。
--
-- 纪律：IR03 只加不删；逐条 WHERE NOT EXISTS 幂等（不用 ON CONFLICT，见 Mc03 方言债口径）；
--       role_id / table_name / resource_id 与 seed 面同形；不播其他角色的放行（最小授权）。
-- ============================================================================

INSERT INTO ecos_control.ecos_rls_policy
    (id, policy_name, table_name, filter_expr, role_id, user_id, priority, enabled,
     description, created_by, created_at, updated_at, resource_id,
     source_kind, version_no, is_deleted, domain)
SELECT 'rls_scenario_definition_admin', 'scenario-demo-read-admin', 'scenario_definition', '1=1',
       'role_admin', NULL, '10', 'true',
       '场景定义聚合读 admin 放行（V362，场景工作台简化 v0.3）',
       'V362', NOW(), NOW(), 'scenario_definition',
       'legacy_text', '1', '0', 'default'
WHERE NOT EXISTS (SELECT 1 FROM ecos_control.ecos_rls_policy WHERE id = 'rls_scenario_definition_admin');

INSERT INTO ecos_control.ecos_rls_policy
    (id, policy_name, table_name, filter_expr, role_id, user_id, priority, enabled,
     description, created_by, created_at, updated_at, resource_id,
     source_kind, version_no, is_deleted, domain)
SELECT 'rls_scenario_analyses_admin', 'scenario-demo-read-admin', 'scenario_analysis', '1=1',
       'role_admin', NULL, '10', 'true',
       '场景分析运行记录读 admin 放行（V362）',
       'V362', NOW(), NOW(), 'scenario_analysis',
       'legacy_text', '1', '0', 'default'
WHERE NOT EXISTS (SELECT 1 FROM ecos_control.ecos_rls_policy WHERE id = 'rls_scenario_analyses_admin');

INSERT INTO ecos_control.ecos_rls_policy
    (id, policy_name, table_name, filter_expr, role_id, user_id, priority, enabled,
     description, created_by, created_at, updated_at, resource_id,
     source_kind, version_no, is_deleted, domain)
SELECT 'rls_scenario_analyses_list_admin', 'scenario-demo-read-admin', 'scenario_analyses', '1=1',
       'role_admin', NULL, '10', 'true',
       '场景分析列表读 admin 放行（V362）',
       'V362', NOW(), NOW(), 'scenario_analyses',
       'legacy_text', '1', '0', 'default'
WHERE NOT EXISTS (SELECT 1 FROM ecos_control.ecos_rls_policy WHERE id = 'rls_scenario_analyses_list_admin');

INSERT INTO ecos_control.ecos_rls_policy
    (id, policy_name, table_name, filter_expr, role_id, user_id, priority, enabled,
     description, created_by, created_at, updated_at, resource_id,
     source_kind, version_no, is_deleted, domain)
SELECT 'rls_scenario_analysis_compare_admin', 'scenario-demo-read-admin', 'scenario_analysis_compare', '1=1',
       'role_admin', NULL, '10', 'true',
       '场景分析对比读 admin 放行（V362）',
       'V362', NOW(), NOW(), 'scenario_analysis_compare',
       'legacy_text', '1', '0', 'default'
WHERE NOT EXISTS (SELECT 1 FROM ecos_control.ecos_rls_policy WHERE id = 'rls_scenario_analysis_compare_admin');

INSERT INTO ecos_control.ecos_rls_policy
    (id, policy_name, table_name, filter_expr, role_id, user_id, priority, enabled,
     description, created_by, created_at, updated_at, resource_id,
     source_kind, version_no, is_deleted, domain)
SELECT 'rls_scenario_template_admin', 'scenario-demo-read-admin', 'scenario_template', '1=1',
       'role_admin', NULL, '10', 'true',
       '场景模板配置读 admin 放行（V362）',
       'V362', NOW(), NOW(), 'scenario_template',
       'legacy_text', '1', '0', 'default'
WHERE NOT EXISTS (SELECT 1 FROM ecos_control.ecos_rls_policy WHERE id = 'rls_scenario_template_admin');

INSERT INTO ecos_control.ecos_rls_policy
    (id, policy_name, table_name, filter_expr, role_id, user_id, priority, enabled,
     description, created_by, created_at, updated_at, resource_id,
     source_kind, version_no, is_deleted, domain)
SELECT 'rls_scenario_algorithm_admin', 'scenario-demo-read-admin', 'scenario_algorithm', '1=1',
       'role_admin', NULL, '10', 'true',
       '算法库配置读 admin 放行（V362）',
       'V362', NOW(), NOW(), 'scenario_algorithm',
       'legacy_text', '1', '0', 'default'
WHERE NOT EXISTS (SELECT 1 FROM ecos_control.ecos_rls_policy WHERE id = 'rls_scenario_algorithm_admin');
