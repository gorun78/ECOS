-- =============================================================================
-- ECOS「企业经营管控」初始化 seed v1（SEED-ENTMGMT-V1）—— 幂等、可重跑、可整批反查
-- =============================================================================
-- 单一权威说明 : docs/30-设计/企业经营管控演示数据集设计-2026-10-14.md（本脚本每一行的取值理由都在该册）
-- 上游依据     : docs/30-设计/ECOS详细设计说明书-从零实施版-2026-10-12.md 第 12 篇（场景年度经营预测确定性计算闭环）
-- 批准         : 指令 ECOS-DATA-01 v2 门 G2=全授权 / G3=甲（2026-10-14 用户裁决）
-- 库           : 仅本地 dev（容器 ecos-postgres / 库 sys_man）。生产不适用。
-- 前置         : entmgmt_demo_cleanup_v1.sql 已真跑（G1=B）；V267/V274/V282 已实跑。
--
-- 列形态权威（禁凭 DDL 文本推断）: 本脚本 17 张表的列清单/NOT NULL/CHECK 均取自 2026-10-14 实跑
--   information_schema + pg_constraint。三处会直接决定插入成败的实测结论：
--     1) ck_kbp_status: ecos_kb_profile.status ∈ {DRAFT, PUBLISHED, SUPERSEDED} —— 写 'ACTIVE' 必失败
--     2) ck_kba_status/ck_kba_vt/ck_kba_sem: 假设表三枚枚举，取值须逐字命中
--     3) chk_bsl_link_type: 连边类型 ∈ {MAPPING, EXTRACTION, COGNITION, GOVERN, EXPOSE}
--   全库对本文 17 表零外键约束（pg_constraint contype='f' 实查为空）⇒ 插入顺序只为语义可读，非硬约束。
--
-- 运行模式（同一文件，零改动切换）：
--   dry-run（默认，必然回滚，只打印行数对账与自检结果）：
--     cat docs/50-db_script/03-seed/entmgmt_demo_seed_v1.sql \
--       | docker exec -i ecos-postgres psql -U postgres -d sys_man -v ON_ERROR_STOP=1 -f -
--   真跑（显式传变量才提交）：
--     cat ... | docker exec -i ecos-postgres psql -U postgres -d sys_man -v ON_ERROR_STOP=1 \
--       -v ecos_commit=1 -f -
--
-- 规范豁免与对齐（逐条写在数据里，不靠口头声明）：
--   MC01 主键 VARCHAR(36) 应用侧生成 ⇒ id = md5('seed:entmgmt-v1:' ‖ 逻辑键)，32 位 hex，确定性 ⇒ 重跑幂等
--   MC03 禁 ON CONFLICT ⇒ seed 场景按数据库规范 v1.4 豁免，统一 DO NOTHING
--   MC02 JSON→TEXT        ⇒ 快照/维度列按实跑列型存 text/jsonb，不做隐式转换
--   DR06/DR07/DR08         ⇒ 公共尾列齐 dq_status/is_active/batch_id/source_ref/evidence_ref/domain/version_no/is_deleted/审计四列
--   IR02 Flyway 永久禁用   ⇒ 本件落 03-seed，不进 02-flyway-migration，手工 psql 执行
--   IR03 只加不删          ⇒ 本脚本纯 INSERT，不 UPDATE/DELETE 既有行
--
-- 演示句柄（G3=甲 零 DDL 约定，全部 seed 行统一携带）：
--   batch_id='SEED-ENTMGMT-V1' / source_ref='seed:entmgmt-v1' / evidence_ref='seed:entmgmt-v1:evidence'
--   create_by=update_by='seed-entmgmt' / domain='entmgmt' / scenario_id='sc_entmgmt'
--   ⚠ 无 is_demo 列、UI 无 DEMO 徽标（设计书 line 7319 要求的偏差，登记 D-03，用户批准的已知缺口）
--
-- 明确不 seed（诚实边界，理由见设计册 §7）：
--   ecos_dw.ecos_application_forecast_run / ecos_dw.ecos_forecast_input_snapshot /
--   ecos_control.ecos_fc_action_ext  ⇒ 结果载体；ForecastRunGateway SPI 无主 bean ⇒ 提交预测恒 501，
--   编造结果行＝虚假验收。SECURITY_POLICY / INTERFACE 两类绑定不建 ⇒ 载体表不存在（D-08），禁造悬空 target_ref。
-- =============================================================================

\set ON_ERROR_STOP on
\echo '>>> SEED-ENTMGMT-V1 开始（事务内，末尾按行数对账判定提交/回滚）'
BEGIN;

-- ─────────────────────────────────────────────────────────────────────────────
-- 1. 组织（ecos_control.td_organization，2 行）
--    现库清理后仅剩 org_root；department_id 一律取真实 org_id ⇒ 任何按组织维度的 join/RLS 都落得到行
-- ─────────────────────────────────────────────────────────────────────────────
INSERT INTO ecos_control.td_organization
    (org_id, org_name, org_code, parent_org_id, org_type, description, status, remark, tenant_id,
     created_time, created_by, updated_time, updated_by)
VALUES
    ('org_ent_ops', '工程运营部', 'ENT-OPS', 'org_root', 'DEPARTMENT',
     '企业经营管控演示：交付侧项目承接部门（p-ent-001 主责 / p-ent-002 独责）', 'ACTIVE',
     'SEED-ENTMGMT-V1', NULL, now(), 'seed-entmgmt', now(), 'seed-entmgmt'),
    ('org_ent_fin', '财务资金部', 'ENT-FIN', 'org_root', 'DEPARTMENT',
     '企业经营管控演示：资金与回款侧部门（p-ent-001 伙伴归属 / p-ent-003 独责 / 公共成本池归口）', 'ACTIVE',
     'SEED-ENTMGMT-V1', NULL, now(), 'seed-entmgmt', now(), 'seed-entmgmt')
ON CONFLICT (org_id) DO NOTHING;

-- ─────────────────────────────────────────────────────────────────────────────
-- 2. 绑定骨架 3 张载体表（本体实体 3 / 知识资产 1 / Agent 1）
--    设计册 §6.2：六类绑定只绑 4 类，DATASET 复用现库真实行 ds-pg-demo（不新造数据源）
--
--    ⚠ 漂移 D-13（实测代码，非本文推测）：OntologyDao.findAllOntologies()（:307-310）在 V200 治理后
--      硬编码 return Collections.emptyList()（旧本体主表 ecos_ontology 已 DROP，无实存源），而
--      ScenarioOptionsController.callRemoteObjects()（:141-190）是先列 ontology 再取其 entities ⇒
--      GET /workspace/scenarios/options/available/objects 恒返回空池，与本文 seed 无关。
--      实体行仍照 seed：绑定 target_ref 必须指向真实存在的行（可反查），池不可达属代码待整改。
--    ⚠ 漂移 D-14：IAgentProfileService 全仓零实现（仅接口 services/sysman/.../IAgentProfileService.java），
--      AgentProfileController 的 @Autowired(required=false) 字段为 null ⇒ /agent/profiles 恒 dependencyUnavailable。
--      同理：ecos_agent 行照 seed（target_ref 有真实载体），池不可达属代码缺口。
--    ⚠ ontology_id 列 NOT NULL 但父表已在 V200 DROP ⇒ 任何取值都无载体可指，取可读常量 'ont_entmgmt' 并如实登记，不伪装成应用产物。
-- ─────────────────────────────────────────────────────────────────────────────
INSERT INTO ecos_ontology.ecos_ontology_entity
    (id, ontology_id, code, name, description, entity_type, sort_order,
     created_at, updated_at, domain_id, tenant_id, is_deleted, status,
     create_by, update_by, create_time, update_time, version_no, domain)
VALUES
    (md5('seed:entmgmt-v1:entity:ent_project'), 'ont_entmgmt', 'ent_project', '经营项目',
     '年度经营管控的对象主体：承接合同、归集成本与人力，是三级平衡的项目层聚合键', 'BUSINESS', 10,
     now(), now(), NULL, NULL, 0, 'ACTIVE', 'seed-entmgmt', 'seed-entmgmt', now(), now(), '1', 'entmgmt'),
    (md5('seed:entmgmt-v1:entity:ent_contract'), 'ont_entmgmt', 'ent_contract', '合同',
     '合同月额 contract_base 的来源对象；一个项目可对应一份主合同', 'BUSINESS', 20,
     now(), now(), NULL, NULL, 0, 'ACTIVE', 'seed-entmgmt', 'seed-entmgmt', now(), now(), '1', 'entmgmt'),
    (md5('seed:entmgmt-v1:entity:ent_cost_item'), 'ont_entmgmt', 'ent_cost_item', '成本科目',
     'SALARY/MANAGEMENT/MARKETING 三类成本事实的归集对象；含跨项目公共池', 'BUSINESS', 30,
     now(), now(), NULL, NULL, 0, 'ACTIVE', 'seed-entmgmt', 'seed-entmgmt', now(), now(), '1', 'entmgmt')
ON CONFLICT (id) DO NOTHING;

INSERT INTO ecos_knowledge.knowledge_article
    (id, title, content, source, domain, tags, status,
     created_at, updated_at, category, source_type, tags_json,
     create_time, update_time, create_by, update_by, version_no, is_deleted, trace_id)
VALUES
    (md5('seed:entmgmt-v1:knowledge:entmgmt-caliber-notes'),
     '企业经营管控口径与规则说明（2026 年度演示）',
     '四条口径的业务含义与边界：1) 权责发生制验收确认额=合同月额×部门归属比率×验收实现率；'
     || '2) 收付实现制回款额同式但取回款实现率；3) 经营毛利额=验收确认额−项目级成本（公共成本池不参与项目级毛利）；'
     || '4) 综合实现率=验收确认额/合同月额。三级平衡以 compareTo==0 判等，金额精度 NUMERIC(18,2)，比率 NUMERIC(5,4)。'
     || '本条为 SEED-ENTMGMT-V1 演示数据集的知识载体，不含任何真实企业经营结论。',
     'seed:entmgmt-v1', 'entmgmt', '["entmgmt","caliber","demo"]'::jsonb, 'ACTIVE',
     now(), now(), '经营管控', 'MANUAL', '["entmgmt","caliber","demo"]',
     now(), now(), 'seed-entmgmt', 'seed-entmgmt', '1', 0, NULL)
ON CONFLICT (id) DO NOTHING;
-- 选项池可见性实测依据：KnowledgeLifecycleService.rawStatuses()（:149-161）把生命周期态 active
-- 映射为原始态 ('active','published','ready')，KnowledgeLifecycleDao.listAssetsByStatus（:30-37）
-- 用 lower(status) IN (…) 过滤 ⇒ 本行 status='ACTIVE' 经 lower() 命中 'active'，KNOWLEDGE_BASE 池可列出该行；
-- 请求不带 status 时（:72-74）走 listAssets() 全列，同样可见。

INSERT INTO ecos_ai.ecos_agent
    (id, name, model_provider, model_name, system_prompt, tools, knowledge, status,
     created_at, updated_at)
VALUES
    (md5('seed:entmgmt-v1:agent:entmgmt-operating-copilot'),
     '经营管控助理', 'deepseek', 'deepseek-chat',
     '面向 2026 年度经营管控场景的分析助理：只在已 seed 的口径/事实范围内作答，结果面未产出一律如实说明。',
     '[]', '[]', 'ACTIVE', now(), now())
ON CONFLICT (id) DO NOTHING;

-- ─────────────────────────────────────────────────────────────────────────────
-- 3. 口径主表 4 + 口径版本 4（ACTIVE × APPROVED 成对，缺一即 UI 与 /calibers/{code}/current 双空）
--    载体订正（设计册 §5.1 / D-01）：公式实存 ecos_ontology.ecos_caliber_version.formula，
--    不是设计书写的 ecos_control.ecos_fc_caliber（后者只是引用视图）。
--    文法硬约束（CaliberDslParser.java:11-22, 242-256）：formula := IDENT '=' expr ⇒ 必须赋值形式（R-公式-1）；
--    无比较运算符 ⇒ 设计书伪码 IF(收入>0,…) 当前不可表达（D-05），本数据集四式只用四则 + ROUND。
--    git_ref 如实 NULL：本数据集只有"在用版本"，无历史版本可指，不编造 commit hash（后端规范 §十一）。
-- ─────────────────────────────────────────────────────────────────────────────
INSERT INTO ecos_ontology.ecos_caliber
    (id, code, name, status, unit, currency, owner_role, dimension_json,
     create_time, update_time, create_by, update_by, is_deleted, version_no, domain)
VALUES
    (md5('seed:entmgmt-v1:caliber:accrual_revenue_amount'), 'accrual_revenue_amount', '权责发生制验收确认额',
     'ACTIVE', 'AMOUNT', 'CNY', 'finance_analyst', NULL,
     now(), now(), 'seed-entmgmt', 'seed-entmgmt', 0, '1', 'entmgmt'),
    (md5('seed:entmgmt-v1:caliber:cash_collection_amount'), 'cash_collection_amount', '收付实现制回款额',
     'ACTIVE', 'AMOUNT', 'CNY', 'finance_analyst', NULL,
     now(), now(), 'seed-entmgmt', 'seed-entmgmt', 0, '1', 'entmgmt'),
    (md5('seed:entmgmt-v1:caliber:operating_profit_amount'), 'operating_profit_amount', '经营毛利额',
     'ACTIVE', 'AMOUNT', 'CNY', 'finance_analyst', NULL,
     now(), now(), 'seed-entmgmt', 'seed-entmgmt', 0, '1', 'entmgmt'),
    (md5('seed:entmgmt-v1:caliber:ent_realization_ratio'), 'ent_realization_ratio', '综合实现率',
     'ACTIVE', 'RATE', NULL, 'finance_analyst', NULL,
     now(), now(), 'seed-entmgmt', 'seed-entmgmt', 0, '1', 'entmgmt')
ON CONFLICT (id) DO NOTHING;

INSERT INTO ecos_ontology.ecos_caliber_version
    (id, caliber_id, version_no, formula, additive, period_granularity, status,
     approved_by, approved_at, git_ref,
     create_time, update_time, create_by, update_by, is_deleted, domain)
SELECT
    md5('seed:entmgmt-v1:caliberver:' || c.code),
    c.id, '1', v.formula, v.additive, 'MONTH', 'APPROVED',
    'seed-entmgmt', now(), NULL,
    now(), now(), 'seed-entmgmt', 'seed-entmgmt', 0, 'entmgmt'
FROM ecos_ontology.ecos_caliber c
JOIN (VALUES
    -- additive：AMOUNT=1 可加；RATE=0 不可加（发布门禁的可加性守卫 ECOS-ONTO-022 同形适用，避免留坑）
    ('accrual_revenue_amount',
     'accrual_revenue_amount = ROUND(contract_base * attribution_ratio * realization_rate, 2)', 1::smallint),
    ('cash_collection_amount',
     'cash_collection_amount = ROUND(contract_base[COLLECTION] * attribution_ratio * realization_rate, 2)', 1::smallint),
    ('operating_profit_amount',
     'operating_profit_amount = accrual_revenue_amount - cost_amount', 1::smallint),
    ('ent_realization_ratio',
     'ent_realization_ratio = accrual_revenue_amount / contract_base', 0::smallint)
) AS v(code, formula, additive) ON v.code = c.code
WHERE c.domain = 'entmgmt' AND c.is_deleted = 0
ON CONFLICT (id) DO NOTHING;

-- ─────────────────────────────────────────────────────────────────────────────
-- 4. 指标注册表 4 行（PUBLISHED，expression 与 caliber_version.formula 逐字一致）
--    发布门禁 rule 4（CaliberService.java:252-281）：PUBLISHED 且 caliber_id 空 / 绑定版本非 APPROVED ⇒ ECOS-ONTO-040
--    ⇒ 本批三条件同时满足：caliber_id 非空 + 版本 APPROVED + 快照已冻结。
--    caliber_snapshot 键序照 CaliberService.buildSnapshot()（:286-294）：caliber_id/formula/version_no/frozen_at
--    entity_code 如实 NULL：全库零行指向本数据集实体的 ecos_entity_table_mapping（V281 新建表），
--    填了就是把指标挂到一个没有登记表上（禁造悬空引用，同 D-08 口径）。
--    aggregation 列（SUM/RATIO）实跑核查：全仓 Java 零读点 ⇒ 纯登记标签，不参与计算，无枚举校验风险。
-- ─────────────────────────────────────────────────────────────────────────────
INSERT INTO ecos_ontology.ecos_metric_definition
    (id, code, name, expression, aggregation, entity_code, caliber_id, formula_version, caliber_snapshot,
     status, create_time, update_time, create_by, update_by, is_deleted, version_no, domain)
SELECT
    md5('seed:entmgmt-v1:metric:' || v.code),
    v.code, v.name, v.formula, v.aggregation, NULL, v.caliber_id, '1',
    json_build_object(
        'caliber_id', v.caliber_id,
        'formula',    v.formula,
        'version_no', '1',
        'frozen_at',  to_char(now(), 'YYYY-MM-DD"T"HH24:MI:SS"Z"')
    )::text,
    'PUBLISHED', now(), now(), 'seed-entmgmt', 'seed-entmgmt', 0, '1', 'entmgmt'
FROM (
    SELECT c.code AS code, c.name AS name, cv.formula AS formula, c.id AS caliber_id,
           CASE WHEN c.unit = 'RATE' THEN 'RATIO' ELSE 'SUM' END AS aggregation
    FROM ecos_ontology.ecos_caliber c
    JOIN ecos_ontology.ecos_caliber_version cv ON cv.caliber_id = c.id AND cv.status = 'APPROVED'
    WHERE c.domain = 'entmgmt' AND c.is_deleted = 0 AND c.status = 'ACTIVE'
) v
ON CONFLICT (id) DO NOTHING;

-- ─────────────────────────────────────────────────────────────────────────────
-- 5. 期间状态 12 行（2026-01..06 CLOSED + 07..12 OPEN）
--    依据 PeriodStatusMapper.xml:12-36：selectClosedPeriods 只认 status='CLOSED' AND is_deleted=0；
--    updateOpenToClosed 要求既存行 status='OPEN' ⇒ OPEN 行也显式写入，关账面可视且可走正常流转。
--    ACTUAL 事实只到 2026-10：DqRowGateImpl F11（period 不得晚于 YearMonth.now()，今日 2026-10）。
-- ─────────────────────────────────────────────────────────────────────────────
INSERT INTO ecos_data.ecos_data_period_status
    (id, period, status, closed_by, closed_at, close_reason, close_detail_json,
     domain, version_no, is_deleted, create_by, update_by, create_time, update_time)
SELECT
    md5('seed:entmgmt-v1:period:' || m.period),
    m.period,
    CASE WHEN m.period <= '2026-06' THEN 'CLOSED' ELSE 'OPEN' END,
    CASE WHEN m.period <= '2026-06' THEN 'seed-entmgmt' ELSE NULL END,
    CASE WHEN m.period <= '2026-06'
         THEN (to_date(m.period || '-01', 'YYYY-MM-DD') + interval '1 month')::timestamp - interval '1 day'
         ELSE NULL END,
    CASE WHEN m.period <= '2026-06' THEN 'FY2026 上半年结账完成（演示数据集 SEED-ENTMGMT-V1）' ELSE NULL END,
    CASE WHEN m.period <= '2026-06'
         THEN '{"batch":"SEED-ENTMGMT-V1","note":"演示关账，非真实财务结账"}'
         ELSE NULL END,
    'entmgmt', '1', 0, 'seed-entmgmt', 'seed-entmgmt', now(), now()
FROM (VALUES ('2026-01'),('2026-02'),('2026-03'),('2026-04'),('2026-05'),('2026-06'),
             ('2026-07'),('2026-08'),('2026-09'),('2026-10'),('2026-11'),('2026-12')) AS m(period)
ON CONFLICT (id) DO NOTHING;

-- ─────────────────────────────────────────────────────────────────────────────
-- 6. 项目归属 4 行（主数据完备性三规则的目标形态）
--    MD-C1 不触发（STAGE 行 contract_base 全非空）；MD-C2 不触发（每 project/period 命中覆盖窗归属行）；
--    MD-C3 恰好压线不触发（p-ent-001 两行合计 =1.0000，判定是 compareTo(ONE)>0 ⇒ 等于 1 合法）
--    —— 把"合计恰为 1 是合法"这条边界变成可验数据（MasterDataCompletenessChecker）。
-- ─────────────────────────────────────────────────────────────────────────────
INSERT INTO ecos_dw.ecos_biz_project_attribution
    (id, project_id, contract_id, department_id, attribution_ratio, attribution_type,
     effective_from, effective_to, source_evidence, dq_status, is_active,
     batch_id, source_ref, evidence_ref, domain, version_no, is_deleted,
     create_time, update_time, create_by, update_by)
VALUES
    (md5('seed:entmgmt-v1:attr:p-ent-001:org_ent_ops'), 'p-ent-001', 'c-ent-101', 'org_ent_ops',
     0.6000, 'PRIMARY', '2026-01', NULL, 'seed:entmgmt-v1', 'PASSED', 1,
     'SEED-ENTMGMT-V1', 'seed:entmgmt-v1', 'seed:entmgmt-v1:evidence', 'entmgmt', '1', 0,
     now(), now(), 'seed-entmgmt', 'seed-entmgmt'),
    (md5('seed:entmgmt-v1:attr:p-ent-001:org_ent_fin'), 'p-ent-001', 'c-ent-101', 'org_ent_fin',
     0.4000, 'PARTNER', '2026-01', NULL, 'seed:entmgmt-v1', 'PASSED', 1,
     'SEED-ENTMGMT-V1', 'seed:entmgmt-v1', 'seed:entmgmt-v1:evidence', 'entmgmt', '1', 0,
     now(), now(), 'seed-entmgmt', 'seed-entmgmt'),
    (md5('seed:entmgmt-v1:attr:p-ent-002:org_ent_ops'), 'p-ent-002', 'c-ent-102', 'org_ent_ops',
     1.0000, 'PRIMARY', '2026-01', NULL, 'seed:entmgmt-v1', 'PASSED', 1,
     'SEED-ENTMGMT-V1', 'seed:entmgmt-v1', 'seed:entmgmt-v1:evidence', 'entmgmt', '1', 0,
     now(), now(), 'seed-entmgmt', 'seed-entmgmt'),
    (md5('seed:entmgmt-v1:attr:p-ent-003:org_ent_fin'), 'p-ent-003', 'c-ent-103', 'org_ent_fin',
     1.0000, 'PRIMARY', '2026-01', NULL, 'seed:entmgmt-v1', 'PASSED', 1,
     'SEED-ENTMGMT-V1', 'seed:entmgmt-v1', 'seed:entmgmt-v1:evidence', 'entmgmt', '1', 0,
     now(), now(), 'seed-entmgmt', 'seed-entmgmt')
ON CONFLICT (id) DO NOTHING;

-- ─────────────────────────────────────────────────────────────────────────────
-- 7. 阶段事实 160 行（set-based 生成：amount 由三个因子现算，不写死数字）
--    公式（第 12 篇 12.3.1）：amount = contract_base × attribution_ratio × realization_rate
--    取值纪律：contract_base 取万元级整数倍 ⇒ 4 位小数比率相乘必精确到 2 位 ⇒ 无舍入残差，
--    三级平衡（12.3.3 compareTo==0）与 DQ F03（scale≤2、非负）同时成立。
--    行数分解（设计册 §4.2）：组合 10 × ACTUAL 10 期 = 100，组合 10 × PLAN 6 期 = 60 ⇒ 160
--      ACCEPTANCE/COLLECTION 各 4 组合（p1 双部门 + p2 + p3）；SUBCONTRACT 只 2 组合（p1 ops 0.6 + p2 ops 1.0）
--    对账锚点（T5 必须逐条复算）：验收 ACTUAL 21,925,000.00｜验收 PLAN 13,775,000.00
--      回款 ACTUAL 20,000,000.00｜回款 PLAN 13,500,000.00｜分包 ACTUAL 9,256,800.00｜分包 PLAN 6,201,600.00
--    列值与 FactImportService.hydrate()（:530-549）逐字对齐：dq_status='PASSED'/is_active=1/version_no='1'/is_deleted=0
--    曲线含同号小幅偏差（如 2026-09 验收 0.9200 实 vs 0.9000 计）⇒ "计划—实际偏差"叙事真实存在，非全等致回测恒 0。
-- ─────────────────────────────────────────────────────────────────────────────
WITH proj(project_id, contract_base) AS (
    VALUES ('p-ent-001', 1200000::numeric),
           ('p-ent-002',  800000::numeric),
           ('p-ent-003',  500000::numeric)
), combo(stage, project_id, department_id, attribution_ratio) AS (
    VALUES ('ACCEPTANCE', 'p-ent-001', 'org_ent_ops', 0.6000::numeric),
           ('ACCEPTANCE', 'p-ent-001', 'org_ent_fin', 0.4000::numeric),
           ('ACCEPTANCE', 'p-ent-002', 'org_ent_ops', 1.0000::numeric),
           ('ACCEPTANCE', 'p-ent-003', 'org_ent_fin', 1.0000::numeric),
           ('COLLECTION', 'p-ent-001', 'org_ent_ops', 0.6000::numeric),
           ('COLLECTION', 'p-ent-001', 'org_ent_fin', 0.4000::numeric),
           ('COLLECTION', 'p-ent-002', 'org_ent_ops', 1.0000::numeric),
           ('COLLECTION', 'p-ent-003', 'org_ent_fin', 1.0000::numeric),
           ('SUBCONTRACT','p-ent-001', 'org_ent_ops', 0.6000::numeric),
           ('SUBCONTRACT','p-ent-002', 'org_ent_ops', 1.0000::numeric)
), rate(stage, fact_type, period, realization_rate) AS (
    VALUES ('ACCEPTANCE','ACTUAL','2026-01',0.8000::numeric),('ACCEPTANCE','ACTUAL','2026-02',0.8200),
           ('ACCEPTANCE','ACTUAL','2026-03',0.8500),('ACCEPTANCE','ACTUAL','2026-04',0.8600),
           ('ACCEPTANCE','ACTUAL','2026-05',0.8800),('ACCEPTANCE','ACTUAL','2026-06',0.9000),
           ('ACCEPTANCE','ACTUAL','2026-07',0.9100),('ACCEPTANCE','ACTUAL','2026-08',0.9000),
           ('ACCEPTANCE','ACTUAL','2026-09',0.9200),('ACCEPTANCE','ACTUAL','2026-10',0.9300),
           ('ACCEPTANCE','PLAN','2026-07',0.9000),('ACCEPTANCE','PLAN','2026-08',0.9000),
           ('ACCEPTANCE','PLAN','2026-09',0.9000),('ACCEPTANCE','PLAN','2026-10',0.9200),
           ('ACCEPTANCE','PLAN','2026-11',0.9400),('ACCEPTANCE','PLAN','2026-12',0.9500),
           ('COLLECTION','ACTUAL','2026-01',0.7000::numeric),('COLLECTION','ACTUAL','2026-02',0.7200),
           ('COLLECTION','ACTUAL','2026-03',0.7500),('COLLECTION','ACTUAL','2026-04',0.7800),
           ('COLLECTION','ACTUAL','2026-05',0.8000),('COLLECTION','ACTUAL','2026-06',0.8200),
           ('COLLECTION','ACTUAL','2026-07',0.8400),('COLLECTION','ACTUAL','2026-08',0.8500),
           ('COLLECTION','ACTUAL','2026-09',0.8600),('COLLECTION','ACTUAL','2026-10',0.8800),
           ('COLLECTION','PLAN','2026-07',0.8600),('COLLECTION','PLAN','2026-08',0.8800),
           ('COLLECTION','PLAN','2026-09',0.9000),('COLLECTION','PLAN','2026-10',0.9000),
           ('COLLECTION','PLAN','2026-11',0.9200),('COLLECTION','PLAN','2026-12',0.9400),
           ('SUBCONTRACT','ACTUAL','2026-01',0.5500::numeric),('SUBCONTRACT','ACTUAL','2026-02',0.5600),
           ('SUBCONTRACT','ACTUAL','2026-03',0.5800),('SUBCONTRACT','ACTUAL','2026-04',0.6000),
           ('SUBCONTRACT','ACTUAL','2026-05',0.6000),('SUBCONTRACT','ACTUAL','2026-06',0.6200),
           ('SUBCONTRACT','ACTUAL','2026-07',0.6300),('SUBCONTRACT','ACTUAL','2026-08',0.6400),
           ('SUBCONTRACT','ACTUAL','2026-09',0.6500),('SUBCONTRACT','ACTUAL','2026-10',0.6600),
           ('SUBCONTRACT','PLAN','2026-07',0.6500),('SUBCONTRACT','PLAN','2026-08',0.6600),
           ('SUBCONTRACT','PLAN','2026-09',0.6700),('SUBCONTRACT','PLAN','2026-10',0.6800),
           ('SUBCONTRACT','PLAN','2026-11',0.7000),('SUBCONTRACT','PLAN','2026-12',0.7200)
)
INSERT INTO ecos_dw.ecos_biz_stage_fact
    (id, project_id, department_id, period, stage, fact_type,
     contract_base, attribution_ratio, realization_rate, amount, currency, source_type,
     dq_status, is_active, batch_id, source_ref, evidence_ref,
     domain, version_no, is_deleted, create_time, update_time, create_by, update_by)
SELECT
    md5('seed:entmgmt-v1:stage:' || c.stage || ':' || c.project_id || ':' || c.department_id
        || ':' || r.period || ':' || r.fact_type),
    c.project_id, c.department_id, r.period, c.stage, r.fact_type,
    p.contract_base, c.attribution_ratio, r.realization_rate,
    round(p.contract_base * c.attribution_ratio * r.realization_rate, 2),
    'CNY', 'MANUAL',
    'PASSED', 1, 'SEED-ENTMGMT-V1', 'seed:entmgmt-v1', 'seed:entmgmt-v1:evidence',
    'entmgmt', '1', 0, now(), now(), 'seed-entmgmt', 'seed-entmgmt'
FROM combo c
JOIN proj p  ON p.project_id = c.project_id
JOIN rate r  ON r.stage = c.stage
ON CONFLICT (id) DO NOTHING;

-- ─────────────────────────────────────────────────────────────────────────────
-- 8. 成本事实 160 行（9 条项目级序列 + 1 条公共池序列 × 16 期）
--    DQ F08：所有 ALLOCATED 行必带 allocation_rule_ref（缺则 REJECT）⇒ 本批 6 条 ALLOCATED 序列全部带引用
--    DQ F09：COST 行一律带 source_ref/evidence_ref
--    is_pool：公共池行 project_id IS NULL + is_pool=1（实跑 CHECK 只有 ck_bcf_is_pool ∈{0,1}，
--             stage/fact_type/cost_category 无 CHECK，枚举纯靠代码约束）
--    对账锚点：ACTUAL (405,000+273,000+192,000)×10 + 80,000×10 = 9,500,000.00
--              PLAN   870,000×6 + 80,000×6 = 5,700,000.00
-- ─────────────────────────────────────────────────────────────────────────────
WITH series(series_key, project_id, is_pool, department_id, cost_category,
            direct_or_allocated, allocation_rule_ref, monthly_amount) AS (
    VALUES ('p-ent-001|SALARY',      'p-ent-001', 0::smallint, 'org_ent_ops', 'SALARY',     'DIRECT',    NULL, 320000::numeric),
           ('p-ent-001|MANAGEMENT',  'p-ent-001', 0, 'org_ent_ops', 'MANAGEMENT', 'ALLOCATED', 'alloc-rule-001',  60000::numeric),
           ('p-ent-001|MARKETING',   'p-ent-001', 0, 'org_ent_ops', 'MARKETING',  'ALLOCATED', 'alloc-rule-001',  25000::numeric),
           ('p-ent-002|SALARY',      'p-ent-002', 0, 'org_ent_ops', 'SALARY',     'DIRECT',    NULL, 210000::numeric),
           ('p-ent-002|MANAGEMENT',  'p-ent-002', 0, 'org_ent_ops', 'MANAGEMENT', 'ALLOCATED', 'alloc-rule-001',  45000::numeric),
           ('p-ent-002|MARKETING',   'p-ent-002', 0, 'org_ent_ops', 'MARKETING',  'ALLOCATED', 'alloc-rule-001',  18000::numeric),
           ('p-ent-003|SALARY',      'p-ent-003', 0, 'org_ent_fin', 'SALARY',     'DIRECT',    NULL, 150000::numeric),
           ('p-ent-003|MANAGEMENT',  'p-ent-003', 0, 'org_ent_fin', 'MANAGEMENT', 'ALLOCATED', 'alloc-rule-001',  30000::numeric),
           ('p-ent-003|MARKETING',   'p-ent-003', 0, 'org_ent_fin', 'MARKETING',  'ALLOCATED', 'alloc-rule-001',  12000::numeric),
           ('POOL|MANAGEMENT',       NULL,        1, 'org_ent_fin', 'MANAGEMENT', 'ALLOCATED', 'alloc-rule-002',  80000::numeric)
), span(fact_type, period) AS (
    VALUES ('ACTUAL','2026-01'),('ACTUAL','2026-02'),('ACTUAL','2026-03'),('ACTUAL','2026-04'),
           ('ACTUAL','2026-05'),('ACTUAL','2026-06'),('ACTUAL','2026-07'),('ACTUAL','2026-08'),
           ('ACTUAL','2026-09'),('ACTUAL','2026-10'),
           ('PLAN','2026-07'),('PLAN','2026-08'),('PLAN','2026-09'),('PLAN','2026-10'),
           ('PLAN','2026-11'),('PLAN','2026-12')
)
INSERT INTO ecos_dw.ecos_biz_cost_fact
    (id, project_id, is_pool, department_id, period, cost_category, direct_or_allocated,
     allocation_rule_ref, amount, currency, fact_type,
     dq_status, is_active, batch_id, source_ref, evidence_ref,
     domain, version_no, is_deleted, create_time, update_time, create_by, update_by)
SELECT
    md5('seed:entmgmt-v1:cost:' || s.series_key || ':' || sp.fact_type || ':' || sp.period),
    s.project_id, s.is_pool, s.department_id, sp.period, s.cost_category, s.direct_or_allocated,
    s.allocation_rule_ref, s.monthly_amount, 'CNY', sp.fact_type,
    'PASSED', 1, 'SEED-ENTMGMT-V1', 'seed:entmgmt-v1', 'seed:entmgmt-v1:evidence',
    'entmgmt', '1', 0, now(), now(), 'seed-entmgmt', 'seed-entmgmt'
FROM series s CROSS JOIN span sp
ON CONFLICT (id) DO NOTHING;

-- ─────────────────────────────────────────────────────────────────────────────
-- 9. 资源事实 96 行（6 名人员 × 16 期）
--    work_hours = fte × 168（月标准工时），hourly_rate 与 fte 相乘即该人该月工资口径，
--    与 §8 的 SALARY 月额同量级但不做强一致（两者是两条独立事实链，第 12 篇未要求互相轧平）。
--    staff_ref_hash 诚实声明（D-06）：DQ 只校验 64 位十六进制形态 ⇒ 此处用 md5‖md5 拼 64 hex，
--    是**合成假名标识，不是真实 SHA-256 摘要**。真摘要应由导入侧 runtime 底座产出，不在数据面伪造"摘要来源"。
-- ─────────────────────────────────────────────────────────────────────────────
WITH staff(project_id, department_id, staff_key, fte, work_hours, hourly_rate) AS (
    VALUES ('p-ent-001', 'org_ent_ops', 'ent-staff-01', 1.00::numeric, 168.0::numeric, 120.00::numeric),
           ('p-ent-001', 'org_ent_ops', 'ent-staff-02', 0.60, 100.8, 120.00),
           ('p-ent-002', 'org_ent_ops', 'ent-staff-03', 0.80, 134.4, 110.00),
           ('p-ent-002', 'org_ent_ops', 'ent-staff-04', 0.50,  84.0, 110.00),
           ('p-ent-003', 'org_ent_fin', 'ent-staff-05', 0.60, 100.8,  95.00),
           ('p-ent-003', 'org_ent_fin', 'ent-staff-06', 0.40,  67.2,  95.00)
), span(fact_type, period) AS (
    VALUES ('ACTUAL','2026-01'),('ACTUAL','2026-02'),('ACTUAL','2026-03'),('ACTUAL','2026-04'),
           ('ACTUAL','2026-05'),('ACTUAL','2026-06'),('ACTUAL','2026-07'),('ACTUAL','2026-08'),
           ('ACTUAL','2026-09'),('ACTUAL','2026-10'),
           ('PLAN','2026-07'),('PLAN','2026-08'),('PLAN','2026-09'),('PLAN','2026-10'),
           ('PLAN','2026-11'),('PLAN','2026-12')
)
INSERT INTO ecos_dw.ecos_biz_resource_fact
    (id, project_id, department_id, period, staff_ref_hash, staff_hash_algo,
     fte, work_hours, hourly_rate, cost_category, fact_type,
     dq_status, is_active, batch_id, source_ref, evidence_ref,
     domain, version_no, is_deleted, create_time, update_time, create_by, update_by)
SELECT
    md5('seed:entmgmt-v1:res:' || t.project_id || ':' || t.staff_key || ':' || sp.fact_type || ':' || sp.period),
    t.project_id, t.department_id, sp.period,
    md5('seed:entmgmt-v1:staff:' || t.staff_key) || md5('seed:entmgmt-v1:staff2:' || t.staff_key),
    'SHA-256',
    t.fte, t.work_hours, t.hourly_rate, 'SALARY', sp.fact_type,
    'PASSED', 1, 'SEED-ENTMGMT-V1', 'seed:entmgmt-v1', 'seed:entmgmt-v1:evidence',
    'entmgmt', '1', 0, now(), now(), 'seed-entmgmt', 'seed-entmgmt'
FROM staff t CROSS JOIN span sp
ON CONFLICT (id) DO NOTHING;

-- ─────────────────────────────────────────────────────────────────────────────
-- 10. 分布画像 4 行（分位数从上一节刚落的 160 行事实现算，禁手填统计数字）
--     V282 已在 ecos_kb_profile 补 8 个统计内联列（27→35 列，实跑复验）⇒ 画像可落定义态表
--     D-11 说明：V176 设计的拆分表 ecos_dw.ecos_kb_profile_stats 从未落地且 Java 零引用 ⇒ 向实现对齐
--     p10/p50/p90 = percentile_cont(...)；ci_low/ci_high 取 p10/p90（第 12 篇 12.3.4 BUILD_INTERVAL 语义）
--     ⇒ p10 ≤ p50 ≤ p90 由 percentile_cont 天然满足，非人为排布
--     group_dims_json 取 {"department":"ALL"}（场景级分组）：画像按 metric_code 一行，
--     设计册 §6.4 原文写部门分组，与"一指标一行"不可同时成立 ⇒ 已回写订正该册
--     status 实跑 CHECK 只允许 DRAFT/PUBLISHED/SUPERSEDED ⇒ 取 PUBLISHED（设计册 §6.4 原文 'ACTIVE' 会插入失败，已订正）
--     毛利分布刻意排除公共池行（project_id IS NULL）：公共池未按项目分摊，计入即伪造项目级归属
-- ─────────────────────────────────────────────────────────────────────────────
WITH monthly_base AS (
    SELECT s.project_id, s.period,
           sum(CASE WHEN s.stage = 'ACCEPTANCE' THEN s.amount ELSE 0::numeric END) AS accrual_revenue,
           sum(CASE WHEN s.stage = 'COLLECTION' THEN s.amount ELSE 0::numeric END) AS cash_collection,
           max(s.contract_base) AS contract_base
    FROM ecos_dw.ecos_biz_stage_fact s
    WHERE s.batch_id = 'SEED-ENTMGMT-V1' AND s.fact_type = 'ACTUAL' AND s.is_deleted = 0
    GROUP BY s.project_id, s.period
), monthly_cost AS (
    SELECT c.project_id, c.period, sum(c.amount) AS cost_amount
    FROM ecos_dw.ecos_biz_cost_fact c
    WHERE c.batch_id = 'SEED-ENTMGMT-V1' AND c.fact_type = 'ACTUAL' AND c.is_deleted = 0
      AND c.project_id IS NOT NULL
    GROUP BY c.project_id, c.period
), series(metric_code, value) AS (
    SELECT 'accrual_revenue_amount', s.amount
      FROM ecos_dw.ecos_biz_stage_fact s
     WHERE s.batch_id = 'SEED-ENTMGMT-V1' AND s.stage = 'ACCEPTANCE'
       AND s.fact_type = 'ACTUAL' AND s.is_deleted = 0
    UNION ALL
    SELECT 'cash_collection_amount', s.amount
      FROM ecos_dw.ecos_biz_stage_fact s
     WHERE s.batch_id = 'SEED-ENTMGMT-V1' AND s.stage = 'COLLECTION'
       AND s.fact_type = 'ACTUAL' AND s.is_deleted = 0
    UNION ALL
    SELECT 'operating_profit_amount', b.accrual_revenue - coalesce(k.cost_amount, 0::numeric)
      FROM monthly_base b
      LEFT JOIN monthly_cost k ON k.project_id = b.project_id AND k.period = b.period
    UNION ALL
    SELECT 'ent_realization_ratio', round(b.accrual_revenue / b.contract_base, 4)
      FROM monthly_base b
)
INSERT INTO ecos_knowledge.ecos_kb_profile
    (id, profile_key, metric_code, group_dims_json, window_from, window_to, stats_ref,
     ci_level, stats_method, confidence, degrade_from, degrade_path_json, applicable_condition,
     source_query_ref, profile_version, status, approved_by, approved_time, task_id, trace_id,
     domain, version_no, is_deleted, create_by, update_by, create_time, update_time,
     sample_count, missing_rate, p10, p50, p90, mean_value, ci_low, ci_high)
SELECT
    md5('seed:entmgmt-v1:profile:' || g.metric_code),
    'prof_entmgmt_' || g.metric_code,
    g.metric_code,
    '{"department":"ALL"}',
    '2026-01', '2026-10', NULL,
    '95', 'T_APPROX',
    CASE WHEN g.n >= 30 THEN 'MEDIUM' ELSE 'LOW' END,
    NULL, NULL,
    '基于 SEED-ENTMGMT-V1 事实批次 2026-01..2026-10 ACTUAL 行分布，适用于 FY2026 年度内 11/12 月的外推下限/中位/上限',
    'seed:entmgmt-v1', '1', 'PUBLISHED', 'seed-entmgmt', now(), NULL, NULL,
    'entmgmt', '1', 0, 'seed-entmgmt', 'seed-entmgmt', now(), now(),
    g.n::int,
    0.0000,   -- 本数据集该维度下 amount/value 无 NULL；T5 用 SQL 独立复算，不采信本列自陈
    round(g.p10::numeric, 4), round(g.p50::numeric, 4), round(g.p90::numeric, 4),
    round(g.mean_v::numeric, 4), round(g.p10::numeric, 4), round(g.p90::numeric, 4)
FROM (
    SELECT s.metric_code,
           count(*)::int AS n,
           avg(s.value)  AS mean_v,
           percentile_cont(0.10) WITHIN GROUP (ORDER BY s.value) AS p10,
           percentile_cont(0.50) WITHIN GROUP (ORDER BY s.value) AS p50,
           percentile_cont(0.90) WITHIN GROUP (ORDER BY s.value) AS p90
    FROM series s
    GROUP BY s.metric_code
) g
ON CONFLICT (id) DO NOTHING;

-- ─────────────────────────────────────────────────────────────────────────────
-- 11. 情景假设 4 行（value_ref 如实 NULL —— 见下）
--     D-02 实测：V177 第 2 段要建的 ecos_dw.ecos_kb_assumption_value 未在库，Java 侧对该值表引用 0 处
--     ⇒ 假设的"数值面"在当前实现中不存在，只登记定义行，不指向不存在的值表
--     枚举逐字命中实跑 CHECK：ck_kba_status/ck_kba_vt/ck_kba_sem
--     CONSERVATIVE(收入) 的 base_assumption_id 指向同指标的 BASE 行 ⇒ 假设血缘真实可查，非全 NULL 摆设
-- ─────────────────────────────────────────────────────────────────────────────
INSERT INTO ecos_knowledge.ecos_kb_assumption
    (id, assumption_key, scenario_type, metric_code, dim_scope_json,
     value_type, value_ref, value_semantic, base_assumption_id,
     valid_from, valid_to, reason, evidence_ref, is_unverified, expire_at,
     assumption_version, status, approved_by, approved_time, trace_id,
     domain, version_no, is_deleted, create_by, update_by, create_time, update_time)
VALUES
    (md5('seed:entmgmt-v1:assumption:BASE:accrual_revenue_amount'),
     'as_ent_base_accrual_revenue', 'BASE', 'accrual_revenue_amount', '{"department":"ALL","fiscal_year":"2026"}',
     'RATE', NULL, 'ABSOLUTE', NULL,
     '2026-11', '2026-12',
     '基准情景：11/12 月验收实现率沿用画像 prof_entmgmt_accrual_revenue_amount 的分布中位数（p50，由 2026-01..10 ACTUAL 行经 percentile_cont 现算），不假设新增签单。',
     'seed:entmgmt-v1:evidence', 0, NULL,
     '1', 'ACTIVE', 'seed-entmgmt', now(), NULL,
     'entmgmt', '1', 0, 'seed-entmgmt', 'seed-entmgmt', now(), now()),
    (md5('seed:entmgmt-v1:assumption:CONSERVATIVE:accrual_revenue_amount'),
     'as_ent_conservative_accrual_revenue', 'CONSERVATIVE', 'accrual_revenue_amount', '{"department":"ALL","fiscal_year":"2026"}',
     'RATE', NULL, 'DELTA', md5('seed:entmgmt-v1:assumption:BASE:accrual_revenue_amount'),
     '2026-11', '2026-12',
     '保守情景：验收实现率相对 BASE 行下调，下调幅度上限取画像 p50−p10 的间距（同一画像行可复算），用于置信区间下限。',
     'seed:entmgmt-v1:evidence', 0, NULL,
     '1', 'ACTIVE', 'seed-entmgmt', now(), NULL,
     'entmgmt', '1', 0, 'seed-entmgmt', 'seed-entmgmt', now(), now()),
    (md5('seed:entmgmt-v1:assumption:AGGRESSIVE:cash_collection_amount'),
     'as_ent_aggressive_cash_collection', 'AGGRESSIVE', 'cash_collection_amount', '{"department":"ALL","fiscal_year":"2026"}',
     'RATE', NULL, 'DELTA', NULL,
     '2026-11', '2026-12',
     '激进情景：回款实现率相对画像 prof_entmgmt_cash_collection_amount 上沿取值，幅度不超过 p90−p50；假设资金面不出现新的账期约束。',
     'seed:entmgmt-v1:evidence', 0, NULL,
     '1', 'ACTIVE', 'seed-entmgmt', now(), NULL,
     'entmgmt', '1', 0, 'seed-entmgmt', 'seed-entmgmt', now(), now()),
    (md5('seed:entmgmt-v1:assumption:BASE:operating_profit_amount'),
     'as_ent_base_operating_profit', 'BASE', 'operating_profit_amount', '{"department":"ALL","fiscal_year":"2026"}',
     'AMOUNT', NULL, 'DELTA', NULL,
     '2026-11', '2026-12',
     '基准情景：11/12 月项目级成本沿用 2026-01..10 常量序列（p-ent-001 405,000／p-ent-002 273,000／p-ent-003 192,000）+ 公共池 80,000，与成本事实 §8 同源；毛利分布不含公共池分摊。',
     'seed:entmgmt-v1:evidence', 0, NULL,
     '1', 'ACTIVE', 'seed-entmgmt', now(), NULL,
     'entmgmt', '1', 0, 'seed-entmgmt', 'seed-entmgmt', now(), now())
ON CONFLICT (id) DO NOTHING;

-- ─────────────────────────────────────────────────────────────────────────────
-- 12. 场景 1 + 绑定 4 + 连边 3
--     场景表在 ecos_ai（ecos_business_scenario），连边表在 ecos_control（ecos_scenario_binding_link）
--     —— 实测 schema 归属不同，workspace DAO 用不带 schema 的名字靠 currentSchema 链解析（登记为本文实测补充）
--     D-07：应用生成场景 ID 形如 sc_+UUID 前 12 位（ScenarioService.java:88），seed 用可读固定 ID
--           sc_entmgmt 便于反查，差异公开登记、不伪装成应用产物；绑定/连边 ID 则**沿用应用形态**
--           （'sb_'+md5 前 12 位 / 'bsl_'+md5 前 12 位，对齐 ScenarioService.java:290 与
--            ScenarioBindingLinkService.java:136），确定性且形状一致。
--     metrics 列 jsonb 且必须是 JSON 对象（ScenarioService.toMetricsJson 存 Map<String,Object>）
--     budget 列实跑是 character varying ⇒ 按列形态存字符串，不改成数值列（IR03）
--     三枚对账值 = §7 验收 ACTUAL 合计 21,925,000.00 / 回款 ACTUAL 合计 20,000,000.00 / 成本 ACTUAL 合计 9,500,000.00
--     连边三条逐一对应 FixedDenominatorEdgePolicy 的必需边 ⇒ IslandBindingGuard 无孤岛，
--     但完整度 coverage 的真实预期是 0.0（EdgeContractProbe 依赖的 ContractProbeFacade 无主 bean，D-12/B-2），
--     不是 1.0 —— 这是代码缺口，不由数据面伪装。source_contract 如实留 NULL（不声明未验证的契约）。
-- ─────────────────────────────────────────────────────────────────────────────
INSERT INTO ecos_ai.ecos_business_scenario
    (id, name, description, business_goal, department, priority, status, budget,
     safety_index_target, actual_safety_index, metrics,
     create_time, update_time, create_by, update_by, is_deleted)
VALUES
    ('sc_entmgmt', '企业经营管控（2026 年度）',
     '以三个在营项目、两个部门归属、FY2026 逐月期间为范围的经营管控场景。'
     || '本数据集只铺输入面（口径/指标/事实/主数据/期间/画像/假设），'
     || '结果面（预测运行、明细、回测、动作）一律为空：预测编排 SPI 无实现，编造即虚假验收。',
     '验证 2026 年度经营预测闭环的输入完备性：主数据完备性四规则零发现、口径四关可通过、三级平衡可纯 SQL 复算。',
     'org_ent_ops', 'HIGH', 'ACTIVE', '24000000.00',
     NULL, NULL,
     '{"targetRevenue":21925000.00,"targetCollection":20000000.00,"actualCost":9500000.00}'::jsonb,
     now(), now(), 'seed-entmgmt', 'seed-entmgmt', 0)
ON CONFLICT (id) DO NOTHING;

INSERT INTO ecos_ai.ecos_scenario_binding
    (id, scenario_id, binding_type, target_ref, remark,
     create_time, update_time, create_by, update_by, is_deleted, target_id, target_type)
VALUES
    ('sb_' || substr(md5('seed:entmgmt-v1:binding:DATASET'), 1, 12),
     'sc_entmgmt', 'DATASET', 'ds-pg-demo',
     'SEED-ENTMGMT-V1：绑定现库真实数据源行（不新造数据源，避免悬空 target_ref）',
     now(), now(), 'seed-entmgmt', 'seed-entmgmt', 0, 'ds-pg-demo', 'DATASET'),
    ('sb_' || substr(md5('seed:entmgmt-v1:binding:OBJECT_TYPE'), 1, 12),
     'sc_entmgmt', 'OBJECT_TYPE', md5('seed:entmgmt-v1:entity:ent_project'),
     'SEED-ENTMGMT-V1：指向本册新 seed 的本体实体行；选项池因 findAllOntologies() 恒空而不可列（D-13）',
     now(), now(), 'seed-entmgmt', 'seed-entmgmt', 0,
     md5('seed:entmgmt-v1:entity:ent_project'), 'OBJECT_TYPE'),
    ('sb_' || substr(md5('seed:entmgmt-v1:binding:KNOWLEDGE_BASE'), 1, 12),
     'sc_entmgmt', 'KNOWLEDGE_BASE', md5('seed:entmgmt-v1:knowledge:entmgmt-caliber-notes'),
     'SEED-ENTMGMT-V1：指向本册新 seed 的知识资产行（status=ACTIVE，经 lower() 命中 active 池）',
     now(), now(), 'seed-entmgmt', 'seed-entmgmt', 0,
     md5('seed:entmgmt-v1:knowledge:entmgmt-caliber-notes'), 'KNOWLEDGE_BASE'),
    ('sb_' || substr(md5('seed:entmgmt-v1:binding:AI_AGENT'), 1, 12),
     'sc_entmgmt', 'AI_AGENT', md5('seed:entmgmt-v1:agent:entmgmt-operating-copilot'),
     'SEED-ENTMGMT-V1：指向本册新 seed 的 ecos_agent 行；/agent/profiles 因 IAgentProfileService 零实现而恒不可达（D-14）',
     now(), now(), 'seed-entmgmt', 'seed-entmgmt', 0,
     md5('seed:entmgmt-v1:agent:entmgmt-operating-copilot'), 'AI_AGENT')
ON CONFLICT (id) DO NOTHING;
-- SECURITY_POLICY / INTERFACE 两类不绑：载体表 ecos_security_policy / ecos_interface_ref 实测不存在（D-08）

INSERT INTO ecos_control.ecos_scenario_binding_link
    (id, scenario_id, source_binding_id, target_binding_id, link_type, source_contract, remark,
     version_no, create_by, update_by, is_deleted, domain)
VALUES
    ('bsl_' || substr(md5('seed:entmgmt-v1:link:MAPPING'), 1, 12), 'sc_entmgmt',
     'sb_' || substr(md5('seed:entmgmt-v1:binding:DATASET'), 1, 12),
     'sb_' || substr(md5('seed:entmgmt-v1:binding:OBJECT_TYPE'), 1, 12),
     'MAPPING', NULL, 'SEED-ENTMGMT-V1：数据源 → 本体实体（必需边 1/3）',
     '1', 'seed-entmgmt', 'seed-entmgmt', 0, 'entmgmt'),
    ('bsl_' || substr(md5('seed:entmgmt-v1:link:EXTRACTION'), 1, 12), 'sc_entmgmt',
     'sb_' || substr(md5('seed:entmgmt-v1:binding:OBJECT_TYPE'), 1, 12),
     'sb_' || substr(md5('seed:entmgmt-v1:binding:KNOWLEDGE_BASE'), 1, 12),
     'EXTRACTION', NULL, 'SEED-ENTMGMT-V1：本体实体 → 知识资产（必需边 2/3）',
     '1', 'seed-entmgmt', 'seed-entmgmt', 0, 'entmgmt'),
    ('bsl_' || substr(md5('seed:entmgmt-v1:link:COGNITION'), 1, 12), 'sc_entmgmt',
     'sb_' || substr(md5('seed:entmgmt-v1:binding:KNOWLEDGE_BASE'), 1, 12),
     'sb_' || substr(md5('seed:entmgmt-v1:binding:AI_AGENT'), 1, 12),
     'COGNITION', NULL, 'SEED-ENTMGMT-V1：知识资产 → AI Agent（必需边 3/3）',
     '1', 'seed-entmgmt', 'seed-entmgmt', 0, 'entmgmt')
ON CONFLICT (id) DO NOTHING;

-- ─────────────────────────────────────────────────────────────────────────────
-- 13. 提交前硬对账：行数逐表必须与设计册声明值完全相等，任一不符即 RAISE EXCEPTION ⇒ 整个事务回滚
--     合计 467 行 / 17 表（2+3+1+1+4+4+4+12+4+160+160+96+4+4+1+4+3）
--     这里不做"打印后人工看一眼"，而是让不等情况下根本提交不了（用户纪律：绿必须出自真实跑通的判定）
-- ─────────────────────────────────────────────────────────────────────────────
DO $$
DECLARE
    r   record;
    bad integer := 0;
BEGIN
    FOR r IN
        SELECT t.tbl, t.got, t.want
        FROM (VALUES
            ('ecos_control.td_organization',
                (SELECT count(*) FROM ecos_control.td_organization WHERE created_by = 'seed-entmgmt'), 2),
            ('ecos_ontology.ecos_ontology_entity',
                (SELECT count(*) FROM ecos_ontology.ecos_ontology_entity WHERE create_by = 'seed-entmgmt'), 3),
            ('ecos_knowledge.knowledge_article',
                (SELECT count(*) FROM ecos_knowledge.knowledge_article WHERE create_by = 'seed-entmgmt'), 1),
            ('ecos_ai.ecos_agent',
                (SELECT count(*) FROM ecos_ai.ecos_agent WHERE id = md5('seed:entmgmt-v1:agent:entmgmt-operating-copilot')), 1),
            ('ecos_ontology.ecos_caliber',
                (SELECT count(*) FROM ecos_ontology.ecos_caliber WHERE domain = 'entmgmt'), 4),
            ('ecos_ontology.ecos_caliber_version',
                (SELECT count(*) FROM ecos_ontology.ecos_caliber_version WHERE domain = 'entmgmt' AND status = 'APPROVED'), 4),
            ('ecos_ontology.ecos_metric_definition',
                (SELECT count(*) FROM ecos_ontology.ecos_metric_definition WHERE domain = 'entmgmt' AND status = 'PUBLISHED'), 4),
            ('ecos_data.ecos_data_period_status',
                (SELECT count(*) FROM ecos_data.ecos_data_period_status WHERE domain = 'entmgmt'), 12),
            ('ecos_dw.ecos_biz_project_attribution',
                (SELECT count(*) FROM ecos_dw.ecos_biz_project_attribution WHERE batch_id = 'SEED-ENTMGMT-V1'), 4),
            ('ecos_dw.ecos_biz_stage_fact',
                (SELECT count(*) FROM ecos_dw.ecos_biz_stage_fact WHERE batch_id = 'SEED-ENTMGMT-V1'), 160),
            ('ecos_dw.ecos_biz_cost_fact',
                (SELECT count(*) FROM ecos_dw.ecos_biz_cost_fact WHERE batch_id = 'SEED-ENTMGMT-V1'), 160),
            ('ecos_dw.ecos_biz_resource_fact',
                (SELECT count(*) FROM ecos_dw.ecos_biz_resource_fact WHERE batch_id = 'SEED-ENTMGMT-V1'), 96),
            ('ecos_knowledge.ecos_kb_profile',
                (SELECT count(*) FROM ecos_knowledge.ecos_kb_profile WHERE domain = 'entmgmt'), 4),
            ('ecos_knowledge.ecos_kb_assumption',
                (SELECT count(*) FROM ecos_knowledge.ecos_kb_assumption WHERE domain = 'entmgmt'), 4),
            ('ecos_ai.ecos_business_scenario',
                (SELECT count(*) FROM ecos_ai.ecos_business_scenario WHERE id = 'sc_entmgmt'), 1),
            ('ecos_ai.ecos_scenario_binding',
                (SELECT count(*) FROM ecos_ai.ecos_scenario_binding WHERE scenario_id = 'sc_entmgmt'), 4),
            ('ecos_control.ecos_scenario_binding_link',
                (SELECT count(*) FROM ecos_control.ecos_scenario_binding_link WHERE scenario_id = 'sc_entmgmt'), 3)
        ) AS t(tbl, got, want)
        WHERE t.got <> t.want
    LOOP
        RAISE NOTICE '行数对账不符: % got=% want=%', r.tbl, r.got, r.want;
        bad := bad + 1;
    END LOOP;

    IF bad > 0 THEN
        RAISE EXCEPTION 'SEED-ENTMGMT-V1 行数对账失败（% 项不符）⇒ 事务回滚，库不留半成品', bad;
    END IF;

    -- 算术锚点硬判定（设计册 §4.2 十锚点中的六枚金额合计，逐条 compareTo 相等）
    IF (SELECT sum(amount) FROM ecos_dw.ecos_biz_stage_fact
         WHERE batch_id = 'SEED-ENTMGMT-V1' AND stage = 'ACCEPTANCE' AND fact_type = 'ACTUAL')
       <> 21925000.00 THEN RAISE EXCEPTION '锚点失败：验收 ACTUAL 合计 ≠ 21,925,000.00'; END IF;
    IF (SELECT sum(amount) FROM ecos_dw.ecos_biz_stage_fact
         WHERE batch_id = 'SEED-ENTMGMT-V1' AND stage = 'ACCEPTANCE' AND fact_type = 'PLAN')
       <> 13775000.00 THEN RAISE EXCEPTION '锚点失败：验收 PLAN 合计 ≠ 13,775,000.00'; END IF;
    IF (SELECT sum(amount) FROM ecos_dw.ecos_biz_stage_fact
         WHERE batch_id = 'SEED-ENTMGMT-V1' AND stage = 'COLLECTION' AND fact_type = 'ACTUAL')
       <> 20000000.00 THEN RAISE EXCEPTION '锚点失败：回款 ACTUAL 合计 ≠ 20,000,000.00'; END IF;
    IF (SELECT sum(amount) FROM ecos_dw.ecos_biz_stage_fact
         WHERE batch_id = 'SEED-ENTMGMT-V1' AND stage = 'COLLECTION' AND fact_type = 'PLAN')
       <> 13500000.00 THEN RAISE EXCEPTION '锚点失败：回款 PLAN 合计 ≠ 13,500,000.00'; END IF;
    IF (SELECT sum(amount) FROM ecos_dw.ecos_biz_stage_fact
         WHERE batch_id = 'SEED-ENTMGMT-V1' AND stage = 'SUBCONTRACT' AND fact_type = 'ACTUAL')
       <> 9256800.00 THEN RAISE EXCEPTION '锚点失败：分包 ACTUAL 合计 ≠ 9,256,800.00'; END IF;
    IF (SELECT sum(amount) FROM ecos_dw.ecos_biz_stage_fact
         WHERE batch_id = 'SEED-ENTMGMT-V1' AND stage = 'SUBCONTRACT' AND fact_type = 'PLAN')
       <> 6201600.00 THEN RAISE EXCEPTION '锚点失败：分包 PLAN 合计 ≠ 6,201,600.00'; END IF;
    IF (SELECT sum(amount) FROM ecos_dw.ecos_biz_cost_fact
         WHERE batch_id = 'SEED-ENTMGMT-V1' AND fact_type = 'ACTUAL')
       <> 9500000.00 THEN RAISE EXCEPTION '锚点失败：成本 ACTUAL 合计 ≠ 9,500,000.00'; END IF;
    IF (SELECT sum(amount) FROM ecos_dw.ecos_biz_cost_fact
         WHERE batch_id = 'SEED-ENTMGMT-V1' AND fact_type = 'PLAN')
       <> 5700000.00 THEN RAISE EXCEPTION '锚点失败：成本 PLAN 合计 ≠ 5,700,000.00'; END IF;

    -- 每行 amount 必须等于自身三因子之积（三级平衡的下层保证；残差非 0 直接拒收）
    IF EXISTS (SELECT 1 FROM ecos_dw.ecos_biz_stage_fact
                WHERE batch_id = 'SEED-ENTMGMT-V1'
                  AND amount <> round(contract_base * attribution_ratio * realization_rate, 2)) THEN
        RAISE EXCEPTION '平衡校验失败：存在 amount ≠ contract_base × attribution_ratio × realization_rate 的阶段行';
    END IF;

    -- 归属合计边界：p-ent-001 必须恰为 1.0000（MD-C3 判定 >1 才算超配，等于 1 是本数据集刻意选的合法边界）
    IF (SELECT sum(attribution_ratio) FROM ecos_dw.ecos_biz_project_attribution
         WHERE batch_id = 'SEED-ENTMGMT-V1' AND project_id = 'p-ent-001') <> 1.0000 THEN
        RAISE EXCEPTION '边界校验失败：p-ent-001 归属合计 ≠ 1.0000';
    END IF;

    -- 画像单调性 p10 ≤ p50 ≤ p90（由 percentile_cont 保证，此处仍做真实判定）
    IF EXISTS (SELECT 1 FROM ecos_knowledge.ecos_kb_profile
                WHERE domain = 'entmgmt' AND NOT (p10 <= p50 AND p50 <= p90)) THEN
        RAISE EXCEPTION '画像校验失败：存在 p10 > p50 或 p50 > p90 的画像行';
    END IF;

    RAISE NOTICE 'SEED-ENTMGMT-V1 自检通过：17 表 / 467 行 / 8 锚点 / 平衡与边界与画像单调性均真实判定';
END $$;

-- ─────────────────────────────────────────────────────────────────────────────
-- 14. 人读汇总（dry-run 与真跑都会打印，便于把证据贴进验收记录）
-- ─────────────────────────────────────────────────────────────────────────────
SELECT '结果面必须为 0（不 seed 结果，见设计册 §7 B-1）' AS check_item,
       (SELECT count(*) FROM ecos_dw.ecos_application_forecast_run)
     + (SELECT count(*) FROM ecos_dw.ecos_forecast_input_snapshot)
     + (SELECT count(*) FROM ecos_control.ecos_fc_action_ext) AS must_be_zero
UNION ALL
SELECT '阶段事实按期间计数（ACTUAL 应为 2026-01..10，PLAN 应为 2026-07..12）',
       count(*) FILTER (WHERE fact_type = 'ACTUAL') FROM ecos_dw.ecos_biz_stage_fact
       WHERE batch_id = 'SEED-ENTMGMT-V1'
UNION ALL
SELECT '画像分位数现算结果 p50（accrual_revenue_amount）',
       round(p50, 2)::int FROM ecos_knowledge.ecos_kb_profile
       WHERE profile_key = 'prof_entmgmt_accrual_revenue_amount';

-- ── 提交门：未传 -v ecos_commit=1 一律回滚（默认 dry-run）────────────────────
\if :{?ecos_commit}
  \echo '>>> MODE = COMMIT（真跑，SEED-ENTMGMT-V1 将落库）'
COMMIT;
\else
  \echo '>>> MODE = DRY-RUN（回滚，库未改变）'
ROLLBACK;
\endif
