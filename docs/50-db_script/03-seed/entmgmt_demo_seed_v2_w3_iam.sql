-- =============================================================================
-- ECOS「企业经营管控」演示数据集 二期 · W3 = 域0 安全中心（集团公司结构）
-- 句柄批次 : SEED-ENTMGMT-V2（控制域无 batch_id 列 ⇒ 以 id 前缀 ent2_ + created_by + description 标记反查）
-- 单一权威 : docs/30-设计/企业经营管控演示数据集二期-六域联动规划-2026-10-16.md §三 域0
-- 上游指令 : 用户 /goal 2026-10-16 第 0 项「以集团公司结构为背景，初始化组织机构、人员、角色、权限、安全策略」
-- 库       : 仅本地 dev（容器 ecos-postgres / 库 sys_man）。生产不适用。
-- 前置     : W0 备份已完成（本机 ~/ecos-backups/sys_man_pre_entmgmt_v2_2026-10-16.sql，sha256 fcd724bb070fd416…）
--
-- 列形态权威（2026-10-16 实跑 information_schema，非 DDL 文本推断）：
--   td_organization 13 列 / td_user 17 列（password NOT NULL）/ td_role 9 列 / td_permission 10 列（**无 status 列**）
--   td_role_permission(role_id,permission_id,created_time,created_by) / td_user_role(+org_id default '-1')
--   td_user_organization(is_primary default '1') / td_abac_policy 11 列 / td_role_security_profile / td_user_security_profile
--   全库对以上表零 CHECK 约束、零外键（沿用一期实测口径）⇒ 插入顺序只为语义可读。
--
-- 词表权威（实跑取得，禁自造）：
--   org_type  ⇒ 前端 OrgFormModal.tsx:63-65 选项只有 COMPANY|DEPARTMENT|TEAM；现库存量行是 CORPORATION（漂移 D-24，只登记不改）
--   status    ⇒ ACTIVE（td_organization/td_user/td_role 默认值一致）
--   audit_mode⇒ sys_dict: basic|detailed|comprehensive    clearance_level⇒ sys_dict: 1..5（L1内部…L5最高）
--   权限码    ⇒ 沿用现库 `resource:action` 文法与 perm_* 主键风格（实测 20 行样本）
--   ABAC subject ⇒ `role:<role_code>` 文法（实测 abac-allow-admin-all = 'role:ROLE_SUPER_ADMIN'）
--
-- 刻意的安全边界（不是遗漏，按规划册 §三 域0 与 §七）：
--   1) **不写任何可登录口令**：td_user.password 为 NOT NULL ⇒ 填字面量 'NOLOGIN:seed-entmgmt-v2'（非 BCrypt 形态，
--      物理上无法通过任何哈希校验），真实登录表 ecos_control.users 一行不增。演示人员=组织成员主数据，不可登录。
--   2) **不碰 ecos_rls_policy / ecos_cls_policy**：运行面 CONTEXT_ORG/CONTEXT_CLEARANCE 绑定恒 null ⇒ 谓词渲染 1=0 会断流；
--      且并行会话正在改 RLS/CLS 执行链（HEAD 三笔 B4-3c/3d）。域0「安全策略」落 td_abac_policy（有 sysman/security-engine
--      AbacController 读写面）与 role/user 安全画像（/api/v1/security-profiles 实测可用）。
--   3) 只 INSERT，不 UPDATE/DELETE 既有行（IR03）；一期两个部门 org_ent_ops/org_ent_fin 保持原位（事实表 department_id 指它们）。
--
-- 运行模式（同一文件，零改动切换）：
--   dry-run（默认，必然回滚）:
--     cat docs/50-db_script/03-seed/entmgmt_demo_seed_v2_w3_iam.sql \
--       | docker exec -i ecos-postgres psql -U postgres -d sys_man -v ON_ERROR_STOP=1 -q -f -
--   真跑: 追加 -v ecos_commit=1
--
-- 规范对齐：MC01 ⇒ 控制域沿用该表既有「可读逻辑键主键」风格（org_root/role_admin/perm_global_all/u-admin 同为存量），
--            批次可反查性由 ent2_ 前缀 + created_by='seed-entmgmt' 保证；MC03 ⇒ 统一 ON CONFLICT DO NOTHING（seed 豁免，同 v1 法）；
--            IR02 ⇒ 本件落 03-seed，手工 psql 执行。
-- =============================================================================

BEGIN;

-- ── A. 组织机构：org_root（既有）→ 集团 → 3 板块事业部 + 2 中心 → 7 部门/项目部，共新增 12 ──
INSERT INTO td_organization
  (org_id, org_name, org_code, parent_org_id, org_type, description, status, tenant_id, created_by, updated_by)
VALUES
  ('org_ent2_group',        '华东建设集团（演示）',     'ENT2-GROUP',    'org_root',           'COMPANY',    '[SEED-ENTMGMT-V2] 集团总部，经营管控演示数据的最高归口', 'ACTIVE', 'tenant-a', 'seed-entmgmt', 'seed-entmgmt'),
  ('org_ent2_infra',        '基础设施事业部',           'ENT2-INFRA',    'org_ent2_group',     'COMPANY',    '[SEED-ENTMGMT-V2] 板块公司：市政与公路工程',            'ACTIVE', 'tenant-a', 'seed-entmgmt', 'seed-entmgmt'),
  ('org_ent2_energy',       '能源环保事业部',           'ENT2-ENERGY',   'org_ent2_group',     'COMPANY',    '[SEED-ENTMGMT-V2] 板块公司：电力与环保工程',            'ACTIVE', 'tenant-a', 'seed-entmgmt', 'seed-entmgmt'),
  ('org_ent2_digital',      '数字建造事业部',           'ENT2-DIGITAL',  'org_ent2_group',     'COMPANY',    '[SEED-ENTMGMT-V2] 板块公司：智慧建造与软件交付',        'ACTIVE', 'tenant-a', 'seed-entmgmt', 'seed-entmgmt'),
  ('org_ent2_fin_center',   '集团财务共享中心',         'ENT2-FINCEN',   'org_ent2_group',     'DEPARTMENT', '[SEED-ENTMGMT-V2] 集中核算与资金归口',                  'ACTIVE', 'tenant-a', 'seed-entmgmt', 'seed-entmgmt'),
  ('org_ent2_ops_center',   '集团运营管理中心',         'ENT2-OPSCEN',   'org_ent2_group',     'DEPARTMENT', '[SEED-ENTMGMT-V2] 计划、产值与履约归口',                'ACTIVE', 'tenant-a', 'seed-entmgmt', 'seed-entmgmt'),
  ('org_ent2_infra_p1',     '基础设施第一项目部',       'ENT2-IFRA-P1',  'org_ent2_infra',     'DEPARTMENT', '[SEED-ENTMGMT-V2] 项目履约与成本归集单元',              'ACTIVE', 'tenant-a', 'seed-entmgmt', 'seed-entmgmt'),
  ('org_ent2_infra_p2',     '基础设施第二项目部',       'ENT2-IFRA-P2',  'org_ent2_infra',     'DEPARTMENT', '[SEED-ENTMGMT-V2] 项目履约与成本归集单元',              'ACTIVE', 'tenant-a', 'seed-entmgmt', 'seed-entmgmt'),
  ('org_ent2_infra_fin',    '基础设施财务部',           'ENT2-IFRA-FIN', 'org_ent2_infra',     'DEPARTMENT', '[SEED-ENTMGMT-V2] 板块财务与回款',                        'ACTIVE', 'tenant-a', 'seed-entmgmt', 'seed-entmgmt'),
  ('org_ent2_energy_p1',    '能源项目部',               'ENT2-ENRG-P1',  'org_ent2_energy',    'DEPARTMENT', '[SEED-ENTMGMT-V2] 项目履约与成本归集单元',              'ACTIVE', 'tenant-a', 'seed-entmgmt', 'seed-entmgmt'),
  ('org_ent2_energy_fin',   '能源财务部',               'ENT2-ENRG-FIN', 'org_ent2_energy',    'DEPARTMENT', '[SEED-ENTMGMT-V2] 板块财务与回款',                        'ACTIVE', 'tenant-a', 'seed-entmgmt', 'seed-entmgmt'),
  ('org_ent2_digital_p1',   '数字建造项目部',           'ENT2-DGTL-P1',  'org_ent2_digital',   'DEPARTMENT', '[SEED-ENTMGMT-V2] 项目履约与工时归集单元',              'ACTIVE', 'tenant-a', 'seed-entmgmt', 'seed-entmgmt')
ON CONFLICT (org_id) DO NOTHING;

-- ── B. 角色：复用既有 5 个平台角色，新增 5 个经营管控角色（parent_role_id 不臆造层级，留 NULL）──
INSERT INTO td_role (role_id, role_name, role_code, description, tenant_id, status, created_by, updated_by)
VALUES
  ('role_ent2_exec',    '集团经营决策层', 'ENT_EXEC',    '[SEED-ENTMGMT-V2] 集团层面看全量经营指标与预测结果',       'tenant-a', 'ACTIVE', 'seed-entmgmt', 'seed-entmgmt'),
  ('role_ent2_group_fin','集团财务共享',  'GROUP_FIN',   '[SEED-ENTMGMT-V2] 资金/成本/回款口径的集中核算与复核',     'tenant-a', 'ACTIVE', 'seed-entmgmt', 'seed-entmgmt'),
  ('role_ent2_segment_gm','板块经营负责人','SEGMENT_GM', '[SEED-ENTMGMT-V2] 本板块产值、成本与履约，跨板块不可见',   'tenant-a', 'ACTIVE', 'seed-entmgmt', 'seed-entmgmt'),
  ('role_ent2_proj_mgr', '项目部经营主管','PROJ_MGR',    '[SEED-ENTMGMT-V2] 项目部事实填报与动作发起',               'tenant-a', 'ACTIVE', 'seed-entmgmt', 'seed-entmgmt'),
  ('role_ent2_sec_audit','安全审计员',    'SEC_AUDITOR', '[SEED-ENTMGMT-V2] 审计、脱敏与策略台账复核',               'tenant-a', 'ACTIVE', 'seed-entmgmt', 'seed-entmgmt')
ON CONFLICT (role_id) DO NOTHING;

-- ── C. 权限：覆盖六域资源面，新增 45 条（现库 20 条只有 ai/data-source/ontology/pipeline 等零星资源）──
INSERT INTO td_permission (permission_id, permission_code, permission_name, resource_id, action, description, tenant_id, created_by, updated_by)
VALUES
  -- 域0 安全中心
  ('perm_ent2_org_r','organization:read','组织只读','organization','read','[SEED-ENTMGMT-V2] 机构树与组织成员查看','tenant-a','seed-entmgmt','seed-entmgmt'),
  ('perm_ent2_org_w','organization:*','组织全权限','organization','*','[SEED-ENTMGMT-V2] 机构增删改与挂接','tenant-a','seed-entmgmt','seed-entmgmt'),
  ('perm_ent2_user_r','user:read','用户只读','user','read','[SEED-ENTMGMT-V2] 人员主数据查看','tenant-a','seed-entmgmt','seed-entmgmt'),
  ('perm_ent2_user_w','user:*','用户全权限','user','*','[SEED-ENTMGMT-V2] 人员维护','tenant-a','seed-entmgmt','seed-entmgmt'),
  ('perm_ent2_role_r','role:read','角色只读','role','read','[SEED-ENTMGMT-V2] 角色与授权查看','tenant-a','seed-entmgmt','seed-entmgmt'),
  ('perm_ent2_role_w','role:*','角色全权限','role','*','[SEED-ENTMGMT-V2] 角色与授权维护','tenant-a','seed-entmgmt','seed-entmgmt'),
  ('perm_ent2_policy_r','security-policy:read','安全策略只读','security-policy','read','[SEED-ENTMGMT-V2] ABAC/画像策略台账查看','tenant-a','seed-entmgmt','seed-entmgmt'),
  ('perm_ent2_policy_w','security-policy:*','安全策略全权限','security-policy','*','[SEED-ENTMGMT-V2] 策略增删改','tenant-a','seed-entmgmt','seed-entmgmt'),
  ('perm_ent2_audit_r','audit:read','审计只读','audit','read','[SEED-ENTMGMT-V2] 审计日志查看','tenant-a','seed-entmgmt','seed-entmgmt'),
  ('perm_ent2_audit_x','audit:export','审计导出','audit','export','[SEED-ENTMGMT-V2] 审计导出（受水印义务约束）','tenant-a','seed-entmgmt','seed-entmgmt'),
  ('perm_ent2_mask_r','mask:read','脱敏只读','mask','read','[SEED-ENTMGMT-V2] 脱敏策略查看','tenant-a','seed-entmgmt','seed-entmgmt'),
  ('perm_ent2_mask_w','mask:*','脱敏全权限','mask','*','[SEED-ENTMGMT-V2] 脱敏策略维护','tenant-a','seed-entmgmt','seed-entmgmt'),
  -- 域1 数据工作台
  ('perm_ent2_asset_r','data-asset:read','数据资产只读','data-asset','read','[SEED-ENTMGMT-V2] 资产目录查看','tenant-a','seed-entmgmt','seed-entmgmt'),
  ('perm_ent2_asset_w','data-asset:*','数据资产全权限','data-asset','*','[SEED-ENTMGMT-V2] 资产维护','tenant-a','seed-entmgmt','seed-entmgmt'),
  ('perm_ent2_asset_pub','data-asset:publish','数据资产上架','data-asset','publish','[SEED-ENTMGMT-V2] CURATED 层上架（markCurated）','tenant-a','seed-entmgmt','seed-entmgmt'),
  ('perm_ent2_pipe_r','pipeline:read','管道只读','pipeline','read','[SEED-ENTMGMT-V2] 管道定义与运行记录查看','tenant-a','seed-entmgmt','seed-entmgmt'),
  ('perm_ent2_pipe_x','pipeline:execute','管道执行','pipeline','execute','[SEED-ENTMGMT-V2] 触发管道运行（ABAC 咽喉动作）','tenant-a','seed-entmgmt','seed-entmgmt'),
  ('perm_ent2_dq_r','dq-rule:read','质量规则只读','dq-rule','read','[SEED-ENTMGMT-V2] DQ 规则查看','tenant-a','seed-entmgmt','seed-entmgmt'),
  ('perm_ent2_dq_x','dq-rule:execute','质量规则执行','dq-rule','execute','[SEED-ENTMGMT-V2] DQ 评估与门禁触发','tenant-a','seed-entmgmt','seed-entmgmt'),
  ('perm_ent2_ds_conn','data-source:connect','数据源连通','data-source','connect','[SEED-ENTMGMT-V2] 连接测试','tenant-a','seed-entmgmt','seed-entmgmt'),
  ('perm_ent2_cat_w','data-catalog:write','目录写入','data-catalog','write','[SEED-ENTMGMT-V2] 目录登记','tenant-a','seed-entmgmt','seed-entmgmt'),
  ('perm_ent2_meta_c','metadata:collect','元数据采集','metadata','collect','[SEED-ENTMGMT-V2] 元数据采集任务','tenant-a','seed-entmgmt','seed-entmgmt'),
  -- 域2 本体工作台
  ('perm_ent2_onto_w','ontology:write','本体写入','ontology','write','[SEED-ENTMGMT-V2] 对象/属性/关系维护','tenant-a','seed-entmgmt','seed-entmgmt'),
  ('perm_ent2_onto_pub','ontology:publish','本体发布','ontology','publish','[SEED-ENTMGMT-V2] 本体版本发布','tenant-a','seed-entmgmt','seed-entmgmt'),
  ('perm_ent2_metric_r','metric:read','指标只读','metric','read','[SEED-ENTMGMT-V2] 指标定义查看','tenant-a','seed-entmgmt','seed-entmgmt'),
  ('perm_ent2_metric_w','metric:*','指标全权限','metric','*','[SEED-ENTMGMT-V2] 指标维护','tenant-a','seed-entmgmt','seed-entmgmt'),
  ('perm_ent2_cal_r','caliber:read','口径只读','caliber','read','[SEED-ENTMGMT-V2] 口径与公式查看','tenant-a','seed-entmgmt','seed-entmgmt'),
  ('perm_ent2_cal_pub','caliber:publish','口径发布','caliber','publish','[SEED-ENTMGMT-V2] 口径四关后发布','tenant-a','seed-entmgmt','seed-entmgmt'),
  ('perm_ent2_glos_r','glossary:read','术语只读','glossary','read','[SEED-ENTMGMT-V2] 业务术语查看','tenant-a','seed-entmgmt','seed-entmgmt'),
  ('perm_ent2_glos_w','glossary:*','术语全权限','glossary','*','[SEED-ENTMGMT-V2] 术语维护','tenant-a','seed-entmgmt','seed-entmgmt'),
  ('perm_ent2_rule_r','ontology-rule:read','本体规则只读','ontology-rule','read','[SEED-ENTMGMT-V2] 规则查看','tenant-a','seed-entmgmt','seed-entmgmt'),
  ('perm_ent2_rule_w','ontology-rule:*','本体规则全权限','ontology-rule','*','[SEED-ENTMGMT-V2] 规则维护','tenant-a','seed-entmgmt','seed-entmgmt'),
  ('perm_ent2_act_r','action-definition:read','动作定义只读','action-definition','read','[SEED-ENTMGMT-V2] Action 查看','tenant-a','seed-entmgmt','seed-entmgmt'),
  ('perm_ent2_act_x','action-definition:execute','动作执行','action-definition','execute','[SEED-ENTMGMT-V2] Action 发起（结果面禁 seed）','tenant-a','seed-entmgmt','seed-entmgmt'),
  ('perm_ent2_ifc_r','interface:read','接口只读','interface','read','[SEED-ENTMGMT-V2] 接口登记查看','tenant-a','seed-entmgmt','seed-entmgmt'),
  ('perm_ent2_ifc_x','interface:execute','接口调用','interface','execute','[SEED-ENTMGMT-V2] 接口调用','tenant-a','seed-entmgmt','seed-entmgmt'),
  ('perm_ent2_wfl_r','workflow:read','流程只读','workflow','read','[SEED-ENTMGMT-V2] 工作流查看','tenant-a','seed-entmgmt','seed-entmgmt'),
  ('perm_ent2_wfl_ap','workflow:approve','流程审批','workflow','approve','[SEED-ENTMGMT-V2] 审批节点操作','tenant-a','seed-entmgmt','seed-entmgmt'),
  -- 域3 知识工作台
  ('perm_ent2_kb_r','knowledge-base:read','知识库只读','knowledge-base','read','[SEED-ENTMGMT-V2] 文档与知识体系查看','tenant-a','seed-entmgmt','seed-entmgmt'),
  ('perm_ent2_kb_w','knowledge-base:*','知识库全权限','knowledge-base','*','[SEED-ENTMGMT-V2] 知识维护','tenant-a','seed-entmgmt','seed-entmgmt'),
  ('perm_ent2_kex_x','knowledge-extract:execute','知识抽取执行','knowledge-extract','execute','[SEED-ENTMGMT-V2] 从数据/本体层导入知识','tenant-a','seed-entmgmt','seed-entmgmt'),
  ('perm_ent2_cog_r','cognitive:read','认知只读','cognitive','read','[SEED-ENTMGMT-V2] 心智模型/证据/假设查看','tenant-a','seed-entmgmt','seed-entmgmt'),
  ('perm_ent2_cog_x','cognitive:reason','认知推理','cognitive','reason','[SEED-ENTMGMT-V2] 推理与诊断触发','tenant-a','seed-entmgmt','seed-entmgmt'),
  -- 域4 AI 工作台
  ('perm_ent2_agent_r','agent:read','智能体只读','agent','read','[SEED-ENTMGMT-V2] 智能体注册查看','tenant-a','seed-entmgmt','seed-entmgmt'),
  ('perm_ent2_agent_run','agent:run','智能体运行','agent','run','[SEED-ENTMGMT-V2] 智能体调用','tenant-a','seed-entmgmt','seed-entmgmt'),
  ('perm_ent2_agent_w','agent:*','智能体全权限','agent','*','[SEED-ENTMGMT-V2] 智能体与编排维护','tenant-a','seed-entmgmt','seed-entmgmt'),
  ('perm_ent2_wag_r','wagent:read','WAgent 只读','wagent','read','[SEED-ENTMGMT-V2] WAgent 运行台账查看','tenant-a','seed-entmgmt','seed-entmgmt'),
  ('perm_ent2_wag_run','wagent:run','WAgent 运行','wagent','run','[SEED-ENTMGMT-V2] WAgent 作业发起','tenant-a','seed-entmgmt','seed-entmgmt'),
  ('perm_ent2_tool_r','tool:read','工具只读','tool','read','[SEED-ENTMGMT-V2] 工具目录查看','tenant-a','seed-entmgmt','seed-entmgmt'),
  ('perm_ent2_tool_x','tool:execute','工具执行','tool','execute','[SEED-ENTMGMT-V2] 受控工具调用','tenant-a','seed-entmgmt','seed-entmgmt'),
  ('perm_ent2_grd_r','guardrail:read','护栏只读','guardrail','read','[SEED-ENTMGMT-V2] 护栏策略查看','tenant-a','seed-entmgmt','seed-entmgmt'),
  ('perm_ent2_grd_w','guardrail:*','护栏全权限','guardrail','*','[SEED-ENTMGMT-V2] 护栏策略维护','tenant-a','seed-entmgmt','seed-entmgmt'),
  -- 域5 场景工作台
  ('perm_ent2_scen_r','scenario:read','场景只读','scenario','read','[SEED-ENTMGMT-V2] 场景与装配查看','tenant-a','seed-entmgmt','seed-entmgmt'),
  ('perm_ent2_scen_w','scenario:*','场景全权限','scenario','*','[SEED-ENTMGMT-V2] 场景维护','tenant-a','seed-entmgmt','seed-entmgmt'),
  ('perm_ent2_scen_pub','scenario:publish','场景发布','scenario','publish','[SEED-ENTMGMT-V2] 装配完备后发布（孤岛闸）','tenant-a','seed-entmgmt','seed-entmgmt'),
  ('perm_ent2_fc_r','forecast:read','预测只读','forecast','read','[SEED-ENTMGMT-V2] 预测运行与明细查看','tenant-a','seed-entmgmt','seed-entmgmt'),
  ('perm_ent2_fc_run','forecast:run','预测运行','forecast','run','[SEED-ENTMGMT-V2] 预测作业发起','tenant-a','seed-entmgmt','seed-entmgmt'),
  ('perm_ent2_bp_r','blueprint:read','蓝图只读','blueprint','read','[SEED-ENTMGMT-V2] 场景蓝图查看','tenant-a','seed-entmgmt','seed-entmgmt'),
  ('perm_ent2_bp_w','blueprint:write','蓝图写入','blueprint','write','[SEED-ENTMGMT-V2] 场景蓝图维护','tenant-a','seed-entmgmt','seed-entmgmt')
ON CONFLICT (permission_id) DO NOTHING;

-- ── D. 角色-权限绑定：新增角色各得其所，既有 4 个引擎角色补上新增资源面 ──
INSERT INTO td_role_permission (role_id, permission_id, created_by)
SELECT r.role_id, p.permission_id, 'seed-entmgmt'
FROM (VALUES
  ('role_ent2_exec',    ARRAY['organization:read','user:read','role:read','security-policy:read','audit:read','mask:read',
                              'data-asset:read','data-asset:publish','pipeline:read','dq-rule:read','caliber:read','caliber:publish',
                              'metric:read','metric:*','glossary:read','ontology:read','ontology:publish','knowledge-base:read',
                              'cognitive:read','cognitive:reason','agent:read','agent:run','wagent:read','wagent:run',
                              'scenario:read','scenario:publish','forecast:read','forecast:run','blueprint:read','interface:read','action-definition:execute']),
  ('role_ent2_group_fin',ARRAY['data-asset:read','dq-rule:read','dq-rule:execute','caliber:read','metric:read','forecast:read','forecast:run',
                              'scenario:read','organization:read','audit:read','interface:read','action-definition:execute','pipeline:read','ontology:read','knowledge-base:read','cognitive:read']),
  ('role_ent2_segment_gm',ARRAY['data-asset:read','pipeline:read','pipeline:execute','dq-rule:read','metric:read','caliber:read',
                              'scenario:read','forecast:read','action-definition:read','action-definition:execute','organization:read','knowledge-base:read','ontology:read','cognitive:read','agent:read']),
  ('role_ent2_proj_mgr', ARRAY['data-asset:read','dq-rule:read','metric:read','caliber:read','glossary:read','scenario:read',
                              'action-definition:read','action-definition:execute','pipeline:read','knowledge-base:read','ontology:read']),
  ('role_ent2_sec_audit',ARRAY['organization:read','user:read','role:read','security-policy:read','audit:read','audit:export',
                              'mask:read','mask:*','guardrail:read','guardrail:*','pipeline:read','scenario:read','data-asset:read']),
  ('role_data_manager',  ARRAY['data-asset:*','data-asset:publish','pipeline:execute','pipeline:read','dq-rule:execute','metadata:collect','data-catalog:write','lineage:read','data-source:connect']),
  ('role_ontology_designer',ARRAY['ontology:write','ontology:publish','metric:*','caliber:publish','glossary:*','ontology-rule:*','action-definition:read','interface:read','workflow:read']),
  ('role_knowledge_engineer',ARRAY['knowledge-base:*','knowledge-extract:execute','cognitive:read','cognitive:reason','glossary:read','workflow:read']),
  ('role_analyst',       ARRAY['data-asset:read','metric:read','caliber:read','scenario:read','forecast:read','cognitive:read','agent:read','knowledge-base:read'])
) AS r(role_id, codes), td_permission p
WHERE p.permission_code = ANY(r.codes)
  AND p.permission_id LIKE 'perm_ent2_%'
  -- 既有 20 行权限不在本次新增集内，故只匹配 perm_ent2_ 前缀，避免重复插旧绑定
ON CONFLICT (role_id, permission_id) DO NOTHING;

-- 补：既有权限里 role_analyst 缺 data-source:read / kg:read 之类的旧码不做变更（IR03 只加不删，此处也不重复插旧绑定）

-- ── E. 人员：12 人（password 填不可登录哨兵值；真实登录表 users 不动）──
INSERT INTO td_user (user_id, username, password, email, mobile_tel1, status, locked, mfa_enabled, tenant_id, created_by, updated_by)
VALUES
  ('u_ent2_group_gm',  'ent2_group_gm',  'NOLOGIN:seed-entmgmt-v2','zheng.guodong@entmgmt.demo','13800000001','ACTIVE','0',false,'tenant-a','seed-entmgmt','seed-entmgmt'),
  ('u_ent2_group_cfo', 'ent2_group_cfo', 'NOLOGIN:seed-entmgmt-v2','lin.zhou@entmgmt.demo',     '13800000002','ACTIVE','0',false,'tenant-a','seed-entmgmt','seed-entmgmt'),
  ('u_ent2_infra_gm',  'ent2_infra_gm',  'NOLOGIN:seed-entmgmt-v2','chen.mingyuan@entmgmt.demo','13800000003','ACTIVE','0',false,'tenant-a','seed-entmgmt','seed-entmgmt'),
  ('u_ent2_energy_gm', 'ent2_energy_gm', 'NOLOGIN:seed-entmgmt-v2','huang.zhiyuan@entmgmt.demo','13800000004','ACTIVE','0',false,'tenant-a','seed-entmgmt','seed-entmgmt'),
  ('u_ent2_digital_gm','ent2_digital_gm','NOLOGIN:seed-entmgmt-v2','wu.zihan@entmgmt.demo',     '13800000005','ACTIVE','0',false,'tenant-a','seed-entmgmt','seed-entmgmt'),
  ('u_ent2_infra_fin', 'ent2_infra_fin', 'NOLOGIN:seed-entmgmt-v2','zhou.ya@entmgmt.demo',      '13800000006','ACTIVE','0',false,'tenant-a','seed-entmgmt','seed-entmgmt'),
  ('u_ent2_energy_fin','ent2_energy_fin','NOLOGIN:seed-entmgmt-v2','zheng.kai@entmgmt.demo',    '13800000007','ACTIVE','0',false,'tenant-a','seed-entmgmt','seed-entmgmt'),
  ('u_ent2_proj_a',    'ent2_proj_a',    'NOLOGIN:seed-entmgmt-v2','sun.li@entmgmt.demo',       '13800000008','ACTIVE','0',false,'tenant-a','seed-entmgmt','seed-entmgmt'),
  ('u_ent2_proj_b',    'ent2_proj_b',    'NOLOGIN:seed-entmgmt-v2','ma.tao@entmgmt.demo',       '13800000009','ACTIVE','0',false,'tenant-a','seed-entmgmt','seed-entmgmt'),
  ('u_ent2_proj_c',    'ent2_proj_c',    'NOLOGIN:seed-entmgmt-v2','hu.ning@entmgmt.demo',      '13800000010','ACTIVE','0',false,'tenant-a','seed-entmgmt','seed-entmgmt'),
  ('u_ent2_data_gov',  'ent2_data_gov',  'NOLOGIN:seed-entmgmt-v2','tang.yu@entmgmt.demo',      '13800000011','ACTIVE','0',false,'tenant-a','seed-entmgmt','seed-entmgmt'),
  ('u_ent2_sec_aud',   'ent2_sec_aud',   'NOLOGIN:seed-entmgmt-v2','qin.lang@entmgmt.demo',     '13800000012','ACTIVE','0',false,'tenant-a','seed-entmgmt','seed-entmgmt')
ON CONFLICT (user_id) DO NOTHING;

-- ── F. 人员-组织（is_primary='1'）与人员-角色（org_id 落真实机构行，不用默认 '-1'）──
INSERT INTO td_user_organization (user_id, org_id, is_primary, created_by) VALUES
  ('u_ent2_group_gm','org_ent2_group','1','seed-entmgmt'),
  ('u_ent2_group_cfo','org_ent2_fin_center','1','seed-entmgmt'),
  ('u_ent2_infra_gm','org_ent2_infra','1','seed-entmgmt'),
  ('u_ent2_energy_gm','org_ent2_energy','1','seed-entmgmt'),
  ('u_ent2_digital_gm','org_ent2_digital','1','seed-entmgmt'),
  ('u_ent2_infra_fin','org_ent2_infra_fin','1','seed-entmgmt'),
  ('u_ent2_energy_fin','org_ent2_energy_fin','1','seed-entmgmt'),
  ('u_ent2_proj_a','org_ent2_infra_p1','1','seed-entmgmt'),
  ('u_ent2_proj_b','org_ent2_energy_p1','1','seed-entmgmt'),
  ('u_ent2_proj_c','org_ent2_digital_p1','1','seed-entmgmt'),
  ('u_ent2_data_gov','org_ent2_ops_center','1','seed-entmgmt'),
  ('u_ent2_sec_aud','org_ent2_group','1','seed-entmgmt')
ON CONFLICT (user_id, org_id) DO NOTHING;

INSERT INTO td_user_role (user_id, role_id, org_id, created_by) VALUES
  ('u_ent2_group_gm','role_ent2_exec','org_ent2_group','seed-entmgmt'),
  ('u_ent2_group_cfo','role_ent2_group_fin','org_ent2_fin_center','seed-entmgmt'),
  ('u_ent2_infra_gm','role_ent2_segment_gm','org_ent2_infra','seed-entmgmt'),
  ('u_ent2_energy_gm','role_ent2_segment_gm','org_ent2_energy','seed-entmgmt'),
  ('u_ent2_digital_gm','role_ent2_segment_gm','org_ent2_digital','seed-entmgmt'),
  ('u_ent2_infra_fin','role_ent2_group_fin','org_ent2_infra_fin','seed-entmgmt'),
  ('u_ent2_energy_fin','role_ent2_group_fin','org_ent2_energy_fin','seed-entmgmt'),
  ('u_ent2_proj_a','role_ent2_proj_mgr','org_ent2_infra_p1','seed-entmgmt'),
  ('u_ent2_proj_b','role_ent2_proj_mgr','org_ent2_energy_p1','seed-entmgmt'),
  ('u_ent2_proj_c','role_ent2_proj_mgr','org_ent2_digital_p1','seed-entmgmt'),
  ('u_ent2_data_gov','role_data_manager','org_ent2_ops_center','seed-entmgmt'),
  ('u_ent2_sec_aud','role_ent2_sec_audit','org_ent2_group','seed-entmgmt')
ON CONFLICT (user_id, role_id) DO NOTHING;

-- ── G. 安全画像：sys_dict 词表 clearance_level 1..5 / audit_mode basic|detailed|comprehensive ──
--    同时补 u-admin 的用户画像行 ⇒ 收口历史缺陷「用户安全配置不存在: u-admin」（现库该表只有 _global_default_/_tenant_default_/_org_finance_ 三条伪主体）
INSERT INTO td_user_security_profile (user_id, clearance_level, linked_workstation, audit_mode, sandbox_mandatory, is_default, tenant_id, scope_type)
VALUES
  ('u-admin',          5, '',             'comprehensive', false, false, 'tenant-a', 'USER'),
  ('u_ent2_group_gm',  5, 'WS-GROUP-01',  'comprehensive', true,  false, 'tenant-a', 'USER'),
  ('u_ent2_group_cfo', 4, 'WS-FINCEN-01', 'detailed',      false, false, 'tenant-a', 'USER'),
  ('u_ent2_infra_gm',  3, 'WS-INFRA-01',  'detailed',      false, false, 'tenant-a', 'USER'),
  ('u_ent2_energy_gm', 3, 'WS-ENERGY-01', 'detailed',      false, false, 'tenant-a', 'USER'),
  ('u_ent2_digital_gm',3, 'WS-DIGITAL-01','detailed',      false, false, 'tenant-a', 'USER'),
  ('u_ent2_infra_fin', 3, 'WS-INFRA-FIN', 'detailed',      false, false, 'tenant-a', 'USER'),
  ('u_ent2_energy_fin',3, 'WS-ENERGY-FIN','detailed',      false, false, 'tenant-a', 'USER'),
  ('u_ent2_proj_a',    2, 'WS-P1',        'basic',         false, false, 'tenant-a', 'USER'),
  ('u_ent2_proj_b',    2, 'WS-P2',        'basic',         false, false, 'tenant-a', 'USER'),
  ('u_ent2_proj_c',    2, 'WS-P3',        'basic',         false, false, 'tenant-a', 'USER'),
  ('u_ent2_data_gov',  2, 'WS-OPS-01',    'detailed',      false, false, 'tenant-a', 'USER'),
  ('u_ent2_sec_aud',   5, 'WS-AUDIT-01',  'comprehensive', false, false, 'tenant-a', 'USER')
ON CONFLICT (user_id) DO NOTHING;

INSERT INTO td_role_security_profile (role_id, clearance_level, linked_workstation, audit_mode, sandbox_mandatory, tenant_id, scope_type)
VALUES
  ('role_ent2_exec',     5, '',             'comprehensive', true,  'tenant-a', 'ROLE'),
  ('role_ent2_group_fin',4, '',             'detailed',      false, 'tenant-a', 'ROLE'),
  ('role_ent2_segment_gm',3,'',             'detailed',      false, 'tenant-a', 'ROLE'),
  ('role_ent2_proj_mgr', 2, '',             'basic',         false, 'tenant-a', 'ROLE'),
  ('role_ent2_sec_audit',5, '',             'comprehensive', false, 'tenant-a', 'ROLE')
ON CONFLICT (role_id) DO NOTHING;

-- ── H. ABAC 策略台账（td_abac_policy；subject 文法实测为 role:<role_code>）──
-- priority 语义（由存量行反推，只登记不裁决）：abac-allow-admin-all=1 … abac-deny-all-default=9999 ⇒ 数值越小越优先。
-- ⚠ 该表已无运行时裁决消费者（workspace 侧 H10-T1 起改经 security-engine REST，最终落 OPA），本组行的语义 = 策略台账/展示面。
--   运行面真值仍由 OPA rego 决定（见规划册 §四 甲项）。
INSERT INTO td_abac_policy (policy_id, policy_name, subject_condition, resource_condition, action_condition, environment_condition, effect, priority, scope_type, scope_id)
VALUES
  ('abac_ent2_exec_read',      'ent2-集团决策层读全量经营集','role:ENT_EXEC',   'dataset:entmgmt:*',            'read',   NULL,'allow',30,'GLOBAL',NULL),
  ('abac_ent2_groupfin_amount','ent2-集团财务读金额与回款',   'role:GROUP_FIN',  'dataset:entmgmt:amount:*',     'read',   NULL,'allow',25,'GLOBAL',NULL),
  ('abac_ent2_segment_own',    'ent2-板块负责人读本板块',     'role:SEGMENT_GM', 'dataset:entmgmt:segment:*',    'read',   NULL,'allow',40,'ORG',    'org_ent2_group'),
  ('abac_ent2_proj_own_org',   'ent2-项目部主管读本项目部',   'role:PROJ_MGR',   'dataset:entmgmt:project:*',    'read',   NULL,'allow',45,'ORG',    NULL),
  ('abac_ent2_proj_cost_deny', 'ent2-项目部不可读集团成本明细','role:PROJ_MGR',  'dataset:entmgmt:cost:detail',  'read',   NULL,'deny', 20,'ORG',    NULL),
  ('abac_ent2_audit_read',     'ent2-审计员读审计与脱敏',     'role:SEC_AUDITOR','audit:*',                      'read',   NULL,'allow',30,'GLOBAL',NULL)
ON CONFLICT (policy_id) DO NOTHING;

-- ── 自检（真跑与 dry-run 都打印，便于对账）────────────────────────────────────
SELECT 'A 组织新增' as chk, count(*) as n FROM td_organization WHERE org_id LIKE 'org\_ent2\_%' ESCAPE '\'
UNION ALL SELECT 'B 角色新增', count(*) FROM td_role WHERE role_id LIKE 'role\_ent2\_%' ESCAPE '\'
UNION ALL SELECT 'C 权限新增', count(*) FROM td_permission WHERE permission_id LIKE 'perm\_ent2\_%' ESCAPE '\'
UNION ALL SELECT 'D 角色权限绑定', count(*) FROM td_role_permission rp JOIN td_permission p ON p.permission_id=rp.permission_id WHERE p.permission_id LIKE 'perm\_ent2\_%' ESCAPE '\'
UNION ALL SELECT 'E 人员新增', count(*) FROM td_user WHERE user_id LIKE 'u\_ent2\_%' ESCAPE '\'
UNION ALL SELECT 'F1 人员组织', count(*) FROM td_user_organization WHERE user_id LIKE 'u\_ent2\_%' ESCAPE '\'
UNION ALL SELECT 'F2 人员角色', count(*) FROM td_user_role WHERE user_id LIKE 'u\_ent2\_%' ESCAPE '\'
UNION ALL SELECT 'G1 用户画像', count(*) FROM td_user_security_profile WHERE user_id LIKE 'u\_ent%' ESCAPE '\'
UNION ALL SELECT 'G2 角色画像', count(*) FROM td_role_security_profile WHERE role_id LIKE 'role\_ent2\_%' ESCAPE '\'
UNION ALL SELECT 'H ABAC 策略', count(*) FROM td_abac_policy WHERE policy_id LIKE 'abac\_ent2\_%' ESCAPE '\'
UNION ALL SELECT 'I 真实登录表未增行（应为 1）', count(*) FROM users
UNION ALL SELECT 'J 无口令泄漏（应为 0）', count(*) FROM td_user WHERE password NOT LIKE 'NOLOGIN:%' AND password <> (select password from td_user where user_id='u-admin')
UNION ALL SELECT 'K 机构链完整（parent 均存在，0=通过）', count(*) FROM td_organization c WHERE c.parent_org_id IS NOT NULL AND NOT EXISTS (SELECT 1 FROM td_organization p WHERE p.org_id=c.parent_org_id)
UNION ALL SELECT 'L 人员角色指向真实角色（0=通过）', count(*) FROM td_user_role ur WHERE NOT EXISTS (SELECT 1 FROM td_role r WHERE r.role_id=ur.role_id)
UNION ALL SELECT 'M 人员组织指向真实机构（0=通过）', count(*) FROM td_user_organization uo WHERE NOT EXISTS (SELECT 1 FROM td_organization o WHERE o.org_id=uo.org_id)
UNION ALL SELECT 'N 绑定孤儿权限（0=通过）', count(*) FROM td_role_permission rp WHERE NOT EXISTS (SELECT 1 FROM td_permission p WHERE p.permission_id=rp.permission_id);

-- ── 提交门：未传 -v ecos_commit=1 一律回滚（默认 dry-run）────────────────────
\if :{?ecos_commit}
  \echo '>>> MODE = COMMIT（真跑，SEED-ENTMGMT-V2 W3 将落库）'
COMMIT;
\else
  \echo '>>> MODE = DRY-RUN（回滚，库未改变）'
ROLLBACK;
\endif
