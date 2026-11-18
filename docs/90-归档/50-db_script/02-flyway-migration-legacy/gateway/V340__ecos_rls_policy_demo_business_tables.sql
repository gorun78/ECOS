-- ============================================================
-- V340__ecos_rls_policy_demo_business_tables.sql — 补业务/演示表 RLS 策略
-- 追溯: 产品级逐功能验证 D102 / D126（2026-11-17 活体取证）
--   GET /api/v1/ecos/objects/Customer → 200 {"data":[],"total":0}
--   POST /api/v1/security/rls/apply {tableName:"demo_customer"} →
--        {"noPolicyMatched":true,"rlsDenyReason":"RLS_NO_POLICY_MATCHED","whereClause":"1=0"}
--   L4: SELECT count(*) FROM ecos_control.demo_customer → 15
--   即库里有数据、接口返回空 —— 读面被「无匹配策略」fail-closed 拦成空。
--
-- 根因: ecos_control.ecos_rls_policy 现有 13 条策略全部落在 scenario_* / caliber /
--       ecos_data_asset / knowledge_article 上，demo_* 与 ecos_biz_* 零覆盖。
--       行级安全实现是「未匹配策略即 1=0」，故这些表对所有主体恒返空。
--
-- ★ 语义声明（必读，落库前已向用户确认）★
--   本批策略的 filter_expr 均为 '1=1'，即「命中角色的主体可读全表」。
--   这不是真正的多租户/部门隔离：dev 库被测表没有租户/部门列
--   （demo_customer = id,name,industry,region,level,credit_score,status,created_at；
--     td_user.user_id='u-d01' 的 tenant_id 为空），数据模型上无隔离落点。
--   故本批的定位是「解除 fail-closed 空读面，让功能面可验证」，
--   NOT 「建立隔离」。生产若需隔离，须先给业务表加租户/部门列，再改写 filter_expr。
--   该限制已在 docs/50-测试/ECOS待裁决事项清单-2026-11-17.md A 项登记。
--
-- 形态对齐: 沿用表中既有 13 条策略的 legacy_text 形态
--   （source_kind='legacy_text'、predicate_template 留空、filter_expr 为裸文本），
--   不新造 parameterized 形态（现网 0 处使用，贸然引入会扩大解析面）。
--
-- 角色面: 补 role_admin（既有）、dept_user、non_finance 三档。
--   u-d01 实测持 dept_user + non_finance（td_user_role），此前在六域一律 403。
--   role_id 匹配走 dao.findPoliciesForUser：
--     role_id IS NULL OR role_id IN (SELECT role_id FROM td_user_role WHERE user_id=?)
--
-- 幂等: 先按本次的固定 id 集删除，再整批插入（门禁 Mc03 推荐的显式 DELETE+INSERT
-- 形态，避开方言专有的 upsert 语法）。重放零副作用，且不触碰既有 13 条策略。
DELETE FROM ecos_control.ecos_rls_policy
 WHERE created_by = 'V340'
   AND id IN ('rls_demo_customer_admin','rls_demo_supplier_admin','rls_demo_invoice_admin',
              'rls_biz_project_admin','rls_biz_contract_admin','rls_agent_admin',
              'rls_demo_customer_dept','rls_demo_supplier_dept','rls_demo_invoice_dept',
              'rls_biz_project_dept','rls_biz_contract_dept','rls_agent_dept',
              'rls_demo_customer_nonfin','rls_biz_project_nonfin','rls_biz_contract_nonfin');
-- 纪律: DR01~DR08 / ST07 / IR02 / IR03 —— 纯 INSERT 种子，不建表、不改列、不删数据。
-- 号段: 接 V339；本脚本落地后下一号 V341（须同批核对 MigrationVersionUniquenessTest
--       钉值与 Mc02/Mc03/Dr 三枚 SCRIPT_FLOOR）。
-- ============================================================

-- ── 1) admin：解除既有管理面空读（与既有 13 条的 role_admin 形态一致）──
INSERT INTO ecos_control.ecos_rls_policy
    (id, policy_name, table_name, filter_expr, role_id, user_id, priority, enabled,
     description, source_kind, domain, created_by)
VALUES
    ('rls_demo_customer_admin',  'demo-read-all', 'demo_customer',  '1=1', 'role_admin', NULL, 20, true, 'V340/D126：对象浏览器 Customer 读面解除 fail-closed', 'legacy_text', 'default', 'V340'),
    ('rls_demo_supplier_admin',  'demo-read-all', 'demo_supplier',  '1=1', 'role_admin', NULL, 20, true, 'V340/D126：对象浏览器 Supplier 读面解除 fail-closed', 'legacy_text', 'default', 'V340'),
    ('rls_demo_invoice_admin',   'demo-read-all', 'demo_invoice',   '1=1', 'role_admin', NULL, 20, true, 'V340/D126：对象浏览器 Invoice 读面解除 fail-closed',  'legacy_text', 'default', 'V340'),
    ('rls_biz_project_admin',    'biz-read-all',  'ecos_biz_project','1=1','role_admin', NULL, 20, true, 'V340/D126：项目面读通道解除 fail-closed',          'legacy_text', 'default', 'V340'),
    ('rls_biz_contract_admin',   'biz-read-all',  'ecos_biz_contract','1=1','role_admin',NULL, 20, true, 'V340/D126：合同面读通道解除 fail-closed',          'legacy_text', 'default', 'V340'),
    ('rls_agent_admin',          'agent-read-all','ecos_agent',     '1=1', 'role_admin', NULL, 20, true, 'V340/D102：智能体读面解除 fail-closed',             'legacy_text', 'default', 'V340');


-- ── 2) 低权角色：dept_user / non_finance（dev 无隔离列的显式放行）──
-- 说明：此处 '1=1' 的语义是「dev 权益放行」，非生产隔离口径。见文件头 ★ 段。
INSERT INTO ecos_control.ecos_rls_policy
    (id, policy_name, table_name, filter_expr, role_id, user_id, priority, enabled,
     description, source_kind, domain, created_by)
VALUES
    ('rls_demo_customer_dept',   'demo-read-dept', 'demo_customer',  '1=1', 'dept_user', NULL, 30, true, 'V340/D102：dev 权益放行（无隔离列，见文件头声明）', 'legacy_text', 'default', 'V340'),
    ('rls_demo_supplier_dept',   'demo-read-dept', 'demo_supplier',  '1=1', 'dept_user', NULL, 30, true, 'V340/D102：dev 权益放行（无隔离列，见文件头声明）', 'legacy_text', 'default', 'V340'),
    ('rls_demo_invoice_dept',    'demo-read-dept', 'demo_invoice',   '1=1', 'dept_user', NULL, 30, true, 'V340/D102：dev 权益放行（无隔离列，见文件头声明）', 'legacy_text', 'default', 'V340'),
    ('rls_biz_project_dept',     'biz-read-dept',  'ecos_biz_project','1=1','dept_user', NULL, 30, true, 'V340/D102：dev 权益放行（无隔离列，见文件头声明）', 'legacy_text', 'default', 'V340'),
    ('rls_biz_contract_dept',    'biz-read-dept',  'ecos_biz_contract','1=1','dept_user',NULL, 30, true, 'V340/D102：dev 权益放行（无隔离列，见文件头声明）', 'legacy_text', 'default', 'V340'),
    ('rls_agent_dept',           'agent-read-dept','ecos_agent',     '1=1', 'dept_user', NULL, 30, true, 'V340/D102：dev 权益放行（无隔离列，见文件头声明）', 'legacy_text', 'default', 'V340'),
    ('rls_demo_customer_nonfin', 'demo-read-nonfin','demo_customer', '1=1', 'non_finance', NULL, 40, true, 'V340/D102：dev 权益放行（非财务角色）',              'legacy_text', 'default', 'V340'),
    ('rls_biz_project_nonfin',   'biz-read-nonfin', 'ecos_biz_project','1=1','non_finance', NULL, 40, true, 'V340/D102：dev 权益放行（非财务角色）',              'legacy_text', 'default', 'V340'),
    ('rls_biz_contract_nonfin',  'biz-read-nonfin', 'ecos_biz_contract','1=1','non_finance',NULL, 40, true, 'V340/D102：dev 权益放行（非财务角色）',              'legacy_text', 'default', 'V340');


-- ── 回滚说明 ──────────────────────────────────────────────
-- 本批为纯种子 INSERT，回滚即删这 15 行（按 id 前固定前缀，不误删既有 13 条）：
--   DELETE FROM ecos_control.ecos_rls_policy
--    WHERE created_by = 'V340' AND id LIKE 'rls_%';
-- 删后读面恢复 fail-closed（whereClause 1=0），属预期。

-- ── DDL Lint Self-audit ──────────────────────────────────────
-- [DR01] 全程 schema 限定（ecos_control.）✓ [DR02/03] 无新表 ✓ [DR04] 无 JSONB ✓
-- [DR05] 无布尔列新增 ✓ [DR06~08] 不涉基线列增删 ✓ [MC01~03] 不涉主键/金额 ✓
-- [ST07] ecos_control 为控制域主 schema ✓
-- [IR02] 仅文件（dev 实跑见台账 D102/D126）✓ [IR03] 只 INSERT 种子，不删列/表 ✓
