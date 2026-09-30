-- V246 (卷01 §6.3): 敏感数据目录首批 + 预测场景策略种子（F01-09, M1）
-- 追溯: F01-09→REQ-SEC-01（六类敏感对象目录）、随 DDL 同 commit；预测场景首批 6 条种子
-- 前置: V164（ecos_rls_policy / ecos_cls_policy / ecos_security_asset 建表单源）
-- 纪律: DR01~DR08 / MC01~MC03 / ST07 / IR03（只加不删）
-- 状态: 仅落迁移脚本文件，**未对 ecos-postgres 实跑**（实跑属设计边界未授权项，需逐项再确认）
-- 幂等: 全部 INSERT ... ON CONFLICT DO NOTHING，可重复执行不重复播种。
-- 数据契约注: 预测场景表名以 fc_* 前缀落业务域 ecos_dw（与列级加密豁免登记表 4 张预登记表一致）；
--   PRD-07 §1.2 的 ecos_biz_stage_fact 属业务域命名，最终以 02 册 E 章数据契约为准，本批种子在 02 册定稿后回填（§6.3 注）。
-- 说明: 策略表 id 采用可读固定键（非 UUID），保证跨 seed/测试可预期；NOT NULL 存量列 filter_expr/visible_cols 以合规占位满足。

-- ── 1. 敏感数据目录（六类对象落库：fc_* 事实表的敏感列 + 整表）─────────────
INSERT INTO public.ecos_security_asset
    (id, asset_key, kind, sensitivity, guard_combo, exemption_ref, owner_role,
     description, version_no, is_deleted, domain, tenant_id, create_by)
VALUES
    ('sec-asset-period-fact', 'ecos_dw.fc_project_period_fact', 'TABLE', 'S2', 'rls',
     NULL, 'project_owner', '预测项目周期事实表(部门归属行级限定)', '1', 0, 'predictivity', NULL, 'seed-v246'),
    ('sec-asset-cost-allocation-hourly', 'ecos_dw.fc_cost_allocation_fact.hourly_rate', 'COLUMN', 'S3', 'cls+mask',
     'ST03A-EX-001', 'finance_lead', '成本分摊-时薪(列级隐藏+掩码, 仅财务可见)', '1', 0, 'predictivity', NULL, 'seed-v246'),
    ('sec-asset-cost-allocation-amount', 'ecos_dw.fc_cost_allocation_fact.amount', 'COLUMN', 'S3', 'cls+mask',
     'ST03A-EX-001', 'finance_lead', '成本分摊-金额(列级隐藏+脱敏)', '1', 0, 'predictivity', NULL, 'seed-v246'),
    ('sec-asset-resource-input-rate', 'ecos_dw.fc_resource_input_fact.hourly_rate', 'COLUMN', 'S3', 'cls',
     'ST03A-EX-001', 'finance_lead', '资源投入-时薪(非财务隐藏)', '1', 0, 'predictivity', NULL, 'seed-v246'),
    ('sec-asset-resource-input-staffref', 'ecos_dw.fc_resource_input_fact.staff_ref', 'COLUMN', 'S3', 'mask',
     'ST03A-EX-002', 'all_roles', '资源投入-人员引用(工号哈希+展示掩码)', '1', 0, 'predictivity', NULL, 'seed-v246')
ON CONFLICT ON CONSTRAINT uniq_sec_asset_key DO NOTHING;

-- ── 2. RLS 策略种子（参数化谓词, source_kind='parameterized'）─────────────
-- rls-project-owner: 项目负责人看本项目(部门归属子查询)
INSERT INTO public.ecos_rls_policy
    (id, policy_name, table_name, filter_expr, role_id, priority, enabled, description,
     predicate_template, bindings_json, source_kind, create_by)
VALUES
    ('rls-project-owner-pcf', 'rls-project-owner', 'ecos_dw.fc_project_period_fact', '1=1',
     'project_owner', 100, true, '项目负责人-本部门项目归属',
     'project_id IN (SELECT project_id FROM ecos_dw.biz_project_attribution WHERE department_id = :userDeptId)',
     '[{"name":"userDeptId","source":"CONTEXT_ORG","value":null}]', 'parameterized', 'seed-v246')
ON CONFLICT (id) DO NOTHING;
INSERT INTO public.ecos_rls_policy
    (id, policy_name, table_name, filter_expr, role_id, priority, enabled, description,
     predicate_template, bindings_json, source_kind, create_by)
VALUES
    ('rls-project-owner-caf', 'rls-project-owner', 'ecos_dw.fc_cost_allocation_fact', '1=1',
     'project_owner', 100, true, '项目负责人-本部门项目归属',
     'project_id IN (SELECT project_id FROM ecos_dw.biz_project_attribution WHERE department_id = :userDeptId)',
     '[{"name":"userDeptId","source":"CONTEXT_ORG","value":null}]', 'parameterized', 'seed-v246')
ON CONFLICT (id) DO NOTHING;
INSERT INTO public.ecos_rls_policy
    (id, policy_name, table_name, filter_expr, role_id, priority, enabled, description,
     predicate_template, bindings_json, source_kind, create_by)
VALUES
    ('rls-project-owner-rii', 'rls-project-owner', 'ecos_dw.fc_resource_input_fact', '1=1',
     'project_owner', 100, true, '项目负责人-本部门项目归属',
     'project_id IN (SELECT project_id FROM ecos_dw.biz_project_attribution WHERE department_id = :userDeptId)',
     '[{"name":"userDeptId","source":"CONTEXT_ORG","value":null}]', 'parameterized', 'seed-v246')
ON CONFLICT (id) DO NOTHING;

-- rls-dept-scope: 部门用户按部门维度行级限定
INSERT INTO public.ecos_rls_policy
    (id, policy_name, table_name, filter_expr, role_id, priority, enabled, description,
     predicate_template, bindings_json, source_kind, create_by)
VALUES
    ('rls-dept-scope-pcf', 'rls-dept-scope', 'ecos_dw.fc_project_period_fact', '1=1',
     'dept_user', 90, true, '部门用户-本部门维度',
     'department_id = :userDeptId',
     '[{"name":"userDeptId","source":"CONTEXT_ORG","value":null}]', 'parameterized', 'seed-v246')
ON CONFLICT (id) DO NOTHING;
INSERT INTO public.ecos_rls_policy
    (id, policy_name, table_name, filter_expr, role_id, priority, enabled, description,
     predicate_template, bindings_json, source_kind, create_by)
VALUES
    ('rls-dept-scope-caf', 'rls-dept-scope', 'ecos_dw.fc_cost_allocation_fact', '1=1',
     'dept_user', 90, true, '部门用户-本部门维度',
     'department_id = :userDeptId',
     '[{"name":"userDeptId","source":"CONTEXT_ORG","value":null}]', 'parameterized', 'seed-v246')
ON CONFLICT (id) DO NOTHING;
INSERT INTO public.ecos_rls_policy
    (id, policy_name, table_name, filter_expr, role_id, priority, enabled, description,
     predicate_template, bindings_json, source_kind, create_by)
VALUES
    ('rls-dept-scope-rii', 'rls-dept-scope', 'ecos_dw.fc_resource_input_fact', '1=1',
     'dept_user', 90, true, '部门用户-本部门维度',
     'department_id = :userDeptId',
     '[{"name":"userDeptId","source":"CONTEXT_ORG","value":null}]', 'parameterized', 'seed-v246')
ON CONFLICT (id) DO NOTHING;

-- rls-finance-all: 财务负责人唯一允许 1=1（全量可见），登记于 asset.guard_combo
INSERT INTO public.ecos_rls_policy
    (id, policy_name, table_name, filter_expr, role_id, priority, enabled, description,
     predicate_template, bindings_json, source_kind, create_by)
VALUES
    ('rls-finance-all-pcf', 'rls-finance-all', 'ecos_dw.fc_project_period_fact', '1=1',
     'finance_lead', 200, true, '财务负责人-全量(唯一1=1主体)', '1=1',
     '[]', 'legacy_text', 'seed-v246')
ON CONFLICT (id) DO NOTHING;
INSERT INTO public.ecos_rls_policy
    (id, policy_name, table_name, filter_expr, role_id, priority, enabled, description,
     predicate_template, bindings_json, source_kind, create_by)
VALUES
    ('rls-finance-all-caf', 'rls-finance-all', 'ecos_dw.fc_cost_allocation_fact', '1=1',
     'finance_lead', 200, true, '财务负责人-全量(唯一1=1主体)', '1=1',
     '[]', 'legacy_text', 'seed-v246')
ON CONFLICT (id) DO NOTHING;
INSERT INTO public.ecos_rls_policy
    (id, policy_name, table_name, filter_expr, role_id, priority, enabled, description,
     predicate_template, bindings_json, source_kind, create_by)
VALUES
    ('rls-finance-all-rii', 'rls-finance-all', 'ecos_dw.fc_resource_input_fact', '1=1',
     'finance_lead', 200, true, '财务负责人-全量(唯一1=1主体)', '1=1',
     '[]', 'legacy_text', 'seed-v246')
ON CONFLICT (id) DO NOTHING;

-- ── 3. CLS 策略种子（非财务隐藏时薪/金额列, mode=deny）─────────────────────
INSERT INTO public.ecos_cls_policy
    (id, policy_name, table_name, visible_cols, blocked_cols, role_id, priority, enabled,
     description, column_name, mode, scope_type, scope_id, create_by)
VALUES
    ('cls-non-finance-hide-caf', 'cls-non-finance-hide', 'ecos_dw.fc_cost_allocation_fact',
     '[]', '["hourly_rate","amount"]', 'non_finance', 100, true,
     '非财务角色-隐藏时薪与金额列', NULL, 'deny', 'ROLE', 'non_finance', 'seed-v246')
ON CONFLICT (id) DO NOTHING;
INSERT INTO public.ecos_cls_policy
    (id, policy_name, table_name, visible_cols, blocked_cols, role_id, priority, enabled,
     description, column_name, mode, scope_type, scope_id, create_by)
VALUES
    ('cls-non-finance-hide-rii', 'cls-non-finance-hide', 'ecos_dw.fc_resource_input_fact',
     '[]', '["hourly_rate"]', 'non_finance', 100, true,
     '非财务角色-隐藏时薪列', NULL, 'deny', 'ROLE', 'non_finance', 'seed-v246')
ON CONFLICT (id) DO NOTHING;

-- ── 4. 列脱敏登记（mask-staff-ref: 工号哈希存储 + 展示掩码, STAFF_REF）─────
-- 脱敏策略经 ecos_security_asset.guard_combo='mask' 承载，asset_key 指向 staff_ref 列（§1 已播种）；
-- STAFF_REF 具体掩码参数由 security-engine DataMaskingService 按 strategy 应用，无需额外策略行。

-- ── DDL/Seed Lint Self-audit ──────────────────────────────────
-- [IR03] 只 INSERT + ON CONFLICT，无 DROP/ALTER 删列 ✓
-- [IR04] 无 SELECT * 拼接 ✓  [MC01] id 应用侧固定键(无 gen_random_uuid) ✓
-- [ST06] 种子数据落控制域策略表(安全域) ✓  数据契约表(fc_*)在业务域 ecos_dw，本脚本不写其 DDL
-- [DR05] is_deleted SMALLINT 走表默认值 ✓  [ST07] 策略/目录落主控制 schema public ✓
-- [幂等] 两表 seed 全 ON CONFLICT DO NOTHING ✓  （asset 走部分唯一索引 uniq_sec_asset_key，MySQL 镜像用普通唯一）
